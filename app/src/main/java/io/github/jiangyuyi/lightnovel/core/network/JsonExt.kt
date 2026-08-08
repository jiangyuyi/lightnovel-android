package io.github.jiangyuyi.lightnovel.core.network

import io.github.jiangyuyi.lightnovel.core.model.BookDetail
import io.github.jiangyuyi.lightnovel.core.model.BookSummary
import io.github.jiangyuyi.lightnovel.core.model.AccountProfile
import io.github.jiangyuyi.lightnovel.core.model.ChapterDetail
import io.github.jiangyuyi.lightnovel.core.model.ChapterSummary
import io.github.jiangyuyi.lightnovel.core.model.Comment
import io.github.jiangyuyi.lightnovel.core.model.DmConversation
import io.github.jiangyuyi.lightnovel.core.model.DmMessage
import io.github.jiangyuyi.lightnovel.core.model.MessageCategory
import io.github.jiangyuyi.lightnovel.core.model.MessageSummary
import io.github.jiangyuyi.lightnovel.core.model.NotificationMessage
import io.github.jiangyuyi.lightnovel.core.model.Page
import io.github.jiangyuyi.lightnovel.core.model.PublishedWork
import io.github.jiangyuyi.lightnovel.core.model.ReadingHistoryItem
import io.github.jiangyuyi.lightnovel.core.model.SearchOption
import io.github.jiangyuyi.lightnovel.core.model.SearchTaxonomy
import io.github.jiangyuyi.lightnovel.core.model.SocialUser
import io.github.jiangyuyi.lightnovel.core.model.UserSummary
import io.github.jiangyuyi.lightnovel.core.model.Volume
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

internal fun JsonObject.element(vararg keys: String): JsonElement? =
    keys.firstNotNullOfOrNull { key -> this[key]?.takeUnless { it is JsonNull } }

internal fun JsonObject.obj(vararg keys: String): JsonObject? = element(*keys) as? JsonObject

internal fun JsonObject.array(vararg keys: String): JsonArray =
    (element(*keys) as? JsonArray) ?: JsonArray(emptyList())

internal fun JsonObject.string(vararg keys: String): String =
    (element(*keys) as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

internal fun JsonObject.long(vararg keys: String): Long =
    (element(*keys) as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() } ?: 0L

internal fun JsonObject.int(vararg keys: String): Int =
    (element(*keys) as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() } ?: 0

internal fun JsonObject.double(vararg keys: String): Double? =
    (element(*keys) as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

internal fun JsonObject.bool(vararg keys: String): Boolean? {
    val primitive = element(*keys) as? JsonPrimitive ?: return null
    primitive.booleanOrNull?.let { return it }
    primitive.intOrNull?.let { return it != 0 }
    return when (primitive.contentOrNull?.lowercase()) {
        "1", "true", "yes", "add", "in_shelf" -> true
        "0", "false", "no", "remove" -> false
        else -> null
    }
}

object ApiParsers {
    fun user(source: JsonObject?): UserSummary? {
        source ?: return null
        val uid = source.long("uid", "id", "user_id")
        val nickname = source.string("nickname", "name", "username")
        if (uid == 0L && nickname.isBlank()) return null
        return UserSummary(
            uid = uid,
            nickname = nickname.ifBlank { "用户$uid" },
            avatarUrl = source.string("avatar_url", "avatar", "avatarUrl").ifBlank { null },
        )
    }

    fun book(source: JsonObject): BookSummary {
        val readState = source.obj("read_state", "readState")
        val stats = source.obj("stats")
        val rating = source.obj("rating")
        val tags = source.array("visible_tags", "tags", "reason_tags")
            .mapNotNull { element ->
                when (element) {
                    is JsonPrimitive -> element.contentOrNull
                    is JsonObject -> element.string("label", "name", "text").ifBlank { null }
                    else -> null
                }
            }
            .distinct()
        return BookSummary(
            id = source.long("book_id", "id"),
            title = source.string("title", "book_title").ifBlank { "未命名作品" },
            author = source.string("author_name", "author", "authorName"),
            summary = source.string("summary_short", "summary", "content_preview", "intro"),
            coverUrl = source.string(
                "cover_url",
                "cover",
                "banner_url",
                "image",
                "book_cover",
                "pic_url",
                "pic",
            ).ifBlank { null },
            tags = tags,
            volumeCount = source.int("volume_count").takeIf { it > 0 }
                ?: stats?.int("volume_count")
                ?: readState?.int("volume_count")
                ?: 0,
            chapterCount = source.int("chapter_count", "published_chapter_count").takeIf { it > 0 }
                ?: stats?.int("chapter_count", "published_chapter_count")
                ?: readState?.int("published_chapter_count")
                ?: 0,
            wordCount = source.long("word_count").takeIf { it > 0 }
                ?: stats?.long("word_count")
                ?: 0,
            score = source.double("rating_score_10", "rating_stars_average", "score")
                ?: rating?.double("score_10", "stars_average", "score"),
            rank = source.int("rank_position").takeIf { it > 0 },
            defaultVolumeId = source.long("default_volume_id").takeIf { it > 0 }
                ?: readState?.long("default_volume_id")?.takeIf { it > 0 },
            defaultChapterId = source.long("default_chapter_id", "last_read_chapter_id").takeIf { it > 0 }
                ?: readState?.long("default_chapter_id")?.takeIf { it > 0 },
            inBookshelf = source.bool("in_shelf", "in_collection", "favorited", "inBookshelf"),
            unreadChapterCount = source.int("unread_chapter_count").takeIf { it > 0 },
        )
    }

    fun bookDetail(source: JsonObject): BookDetail {
        val interaction = source.obj("interaction_stats", "book_interaction")
        val alternate = source.array("alternate_versions", "alternateVersions")
            .mapNotNull { (it as? JsonObject)?.let(::book) }
            .filter { it.id > 0 }
        return BookDetail(
            book = book(source),
            publisher = user(source.obj("poster_user", "publisher", "user")),
            alternateVersions = alternate,
            commentCount = interaction?.int("comment_count") ?: source.int("comment_count"),
            favoriteCount = interaction?.int("favorite_count") ?: source.int("favorite_count"),
        )
    }

    fun volume(source: JsonObject): Volume = Volume(
        id = source.long("volume_id", "id"),
        bookId = source.long("book_id"),
        title = source.string("title", "volume_title").ifBlank { "未命名分卷" },
        chapterCount = source.int("chapter_count"),
        firstChapterId = source.long("first_chapter_id").takeIf { it > 0 },
        lastChapterId = source.long("last_chapter_id").takeIf { it > 0 },
    )

    fun chapter(source: JsonObject): ChapterSummary = ChapterSummary(
        id = source.long("chapter_id", "id"),
        bookId = source.long("book_id"),
        volumeId = source.long("volume_id"),
        title = source.string("title", "chapter_title").ifBlank { "未命名章节" },
        order = source.int("chapter_no", "order_no", "sort_index"),
        wordCount = source.long("word_count"),
        locked = source.bool("locked") == true || source.bool("unlocked") == false,
    )

    fun chapterDetail(source: JsonObject): ChapterDetail {
        val body = source.obj("body_snapshot", "body", "content")
        val navigation = source.obj("navigation", "chapter_navigation", "nav")
        return ChapterDetail(
            chapter = chapter(source),
            bookTitle = source.string("book_title"),
            volumeTitle = source.string("volume_title", "origin_volume_title"),
            bodyText = body?.string("body_text", "text", "content_text").orEmpty(),
            bodyHtml = body?.string("body_html", "html", "content_html").orEmpty(),
            previousChapterId = navigation?.navigationId("prev_chapter", "previous_chapter", "prev")
                ?: source.navigationId("prev_chapter", "previous_chapter", "prev_chapter_id"),
            nextChapterId = navigation?.navigationId("next_chapter", "next")
                ?: source.navigationId("next_chapter", "next_chapter_id"),
        )
    }

    private fun JsonObject.navigationId(vararg keys: String): Long? {
        val element = element(*keys) ?: return null
        return element.navigationId()
    }

    private fun JsonElement.navigationId(): Long? = when (this) {
        is JsonPrimitive -> (longOrNull ?: contentOrNull?.toLongOrNull())?.takeIf { it > 0 }
        is JsonObject -> long("chapter_id", "id", "chapterId").takeIf { it > 0 }
            ?: element("chapter", "value")?.navigationId()
        is JsonArray -> firstNotNullOfOrNull { it.navigationId() }
        else -> null
    }

    fun comment(source: JsonObject): Comment {
        val author = user(source.obj("user", "author", "sender", "poster_user"))
            ?: UserSummary(0, "匿名用户")
        return Comment(
            id = source.long("comment_id", "id"),
            author = author,
            content = source.string("content", "content_text", "body", "text"),
            createdAt = source.string("created_at", "time", "createdAt"),
            likeCount = source.int("like_count", "likes"),
            replyCount = source.int("reply_count", "replies"),
        )
    }

    fun accountProfile(source: JsonObject): AccountProfile {
        val profile = source.obj("profile", "user") ?: source
        val stats = source.obj("stats") ?: profile.obj("stats") ?: source
        val balance = profile.obj("balance")
        val level = profile.obj("level")
        val group = profile.obj("user_group", "group", "rank")
        val user = user(profile) ?: UserSummary(
            uid = profile.long("uid", "user_id", "id"),
            nickname = profile.string("nickname", "username", "name").ifBlank { "已登录用户" },
            avatarUrl = profile.string("avatar", "avatar_url", "avatarUrl").ifBlank { null },
        )
        return AccountProfile(
            user = user,
            signature = profile.string("sign", "signature"),
            levelName = profile.string(
                "level_name",
                "levelName",
                "level_title",
                "group_name",
                "user_group_name",
                "rank_name",
                "role_name",
            ).ifBlank { group?.string("name", "title").orEmpty() },
            level = profile.int("level").takeIf { it > 0 }
                ?: level?.int("level")?.takeIf { it > 0 },
            coin = profile.int("coin", "light_coin", "lightCoin", "balance").takeIf { it > 0 }
                ?: balance?.int("coin", "light_coin", "lightCoin")
                ?: 0,
            fansCount = stats.int("followers", "fans", "fans_count").takeIf { it > 0 }
                ?: profile.int("followers", "fans", "fans_count", "fansCount"),
            followingCount = stats.int("following", "following_count").takeIf { it > 0 }
                ?: profile.int("following", "following_count", "followingCount"),
            postCount = stats.int("publish_articles", "post_count", "posts").takeIf { it > 0 }
                ?: profile.int("publish_articles", "post_count", "posts", "postCount"),
        )
    }

    fun socialUser(source: JsonObject): SocialUser? {
        val profile = source.obj("profile", "user") ?: source
        val parsed = user(profile) ?: return null
        val relation = source.obj("relation", "interaction_state")
            ?: profile.obj("relation", "interaction_state")
        val group = profile.obj("level", "user_group", "group", "rank")
        return SocialUser(
            user = parsed,
            signature = profile.string("sign", "signature"),
            levelName = profile.string(
                "level_name",
                "levelName",
                "level_title",
                "group_name",
                "user_group_name",
                "rank_name",
                "role_name",
            ).ifBlank { group?.string("name", "title").orEmpty() },
            followed = profile.bool(
                "followed",
                "is_followed",
                "isFollowing",
                "is_following",
                "has_followed",
                "hasFollowed",
            ) ?: relation?.bool("followed", "is_followed", "isFollowing", "is_following") ?: false,
            relationState = profile.string("relation_state", "relationState")
                .ifBlank { relation?.string("relation_state", "relationState").orEmpty() },
        )
    }

    fun socialPage(source: JsonObject, requestedPage: Int, pageSize: Int): Page<SocialUser> {
        val items = listObjects(source).mapNotNull(::socialUser).filter { it.user.uid > 0 }
        return page(source, items, requestedPage, pageSize)
    }

    fun readingHistoryPage(source: JsonObject, requestedPage: Int, pageSize: Int): Page<ReadingHistoryItem> {
        val items = listObjects(source).mapNotNull { item ->
            val book = book(item)
            if (book.id <= 0) return@mapNotNull null
            val history = item.obj("history", "read_state")
            ReadingHistoryItem(
                book = book,
                lastChapterId = item.long("last_read_chapter_id", "lastReadChapterId").takeIf { it > 0 }
                    ?: history?.long("last_read_chapter_id", "chapter_id", "default_chapter_id")?.takeIf { it > 0 }
                    ?: book.defaultChapterId,
                lastChapterTitle = item.string("last_read_chapter_title", "chapter_title", "latest_chapter_title")
                    .ifBlank { history?.string("last_read_chapter_title", "chapter_title", "title").orEmpty() },
                readAt = item.string("read_at", "last_read_at")
                    .ifBlank { history?.string("read_at", "last_read_at", "updated_at").orEmpty() },
            )
        }
        return page(source, items, requestedPage, pageSize)
    }

    fun publishedWorksPage(source: JsonObject, requestedPage: Int, pageSize: Int): Page<PublishedWork> {
        val items = listObjects(source).mapNotNull { item ->
            val bookId = item.long("book_id", "bookId", "id")
            if (bookId <= 0) return@mapNotNull null
            val meta = item.obj("meta_json", "metaJson")
            val review = item.obj("review_state", "reviewState")
            val rawStatus = meta?.string("serialize_status", "serial_status")
                .orEmpty().ifBlank { item.string("serial_status", "serialStatus", "status_text") }
            val numericStatus = item.int("status")
            val status = when {
                rawStatus == "已完结" -> "已完本"
                rawStatus.isNotBlank() -> rawStatus
                meta?.bool("is_completed") == true || item.bool("is_completed", "isCompleted") == true -> "已完本"
                numericStatus == 2 -> "已隐藏"
                numericStatus == 0 -> "草稿中"
                else -> "连载中"
            }
            val reviewStatus = review?.string("review_status", "reviewStatus")
                .orEmpty().ifBlank { item.string("review_status", "reviewStatus") }
            val reviewText = review?.string("progress_text", "progressText").orEmpty().ifBlank {
                when (reviewStatus) {
                    "pending" -> "审核中"
                    "approved" -> "审核通过"
                    "rejected" -> "审核未通过"
                    else -> ""
                }
            }
            PublishedWork(
                bookId = bookId,
                title = item.string("title").ifBlank { "未命名作品" },
                coverUrl = item.string("cover_url", "coverUrl", "cover").ifBlank { null },
                author = item.string("author_name", "author", "authorName"),
                summary = item.string("summary", "long_summary", "longSummary"),
                type = meta?.string("type").orEmpty().ifBlank { item.string("type") },
                status = status,
                reviewStatus = reviewStatus,
                reviewText = reviewText,
                volumeCount = item.int("volume_count", "volumeCount"),
                chapterCount = item.int("chapter_count", "chapterCount"),
                wordCount = item.long("word_count", "wordCount"),
                updatedAt = item.string("updated_at", "updatedAt"),
            )
        }
        return page(source, items, requestedPage, pageSize)
    }

    fun messageSummary(source: JsonObject): MessageSummary {
        val summary = source.obj("summary", "unread") ?: source
        return MessageSummary(
            unreadCount = summary.int("unread_count", "unreadCount", "total_unread"),
            replyCount = summary.int("reply_count", "replies"),
            mentionCount = summary.int("mention_count", "mentions"),
            likeCount = summary.int("like_count", "likes"),
            systemCount = summary.int("system_count", "notifications"),
            dmCount = summary.int("dm_count", "dm_unread"),
            fanCount = summary.int("fan_count", "fans"),
        )
    }

    fun messagesPage(
        source: JsonObject,
        category: MessageCategory,
        requestedPage: Int,
        pageSize: Int,
    ): Page<NotificationMessage> {
        val defaultTitle = when (category) {
            MessageCategory.REPLY -> "回复我的"
            MessageCategory.MENTION -> "提到了我"
            MessageCategory.LIKE -> "收到的赞"
            MessageCategory.FAN -> "新的粉丝"
            MessageCategory.SYSTEM -> "系统通知"
            MessageCategory.DM -> "私信"
        }
        val items = listObjects(source).mapNotNull { item ->
            val id = item.string("message_id", "id")
            if (id.isBlank()) return@mapNotNull null
            val sender = user(item.obj("user", "sender"))
            val title = item.string("title", "category_text").ifBlank { defaultTitle }
            NotificationMessage(
                id = id,
                category = category,
                user = sender,
                sourceName = item.string("source_name").ifBlank { sender?.nickname.orEmpty() },
                sourceAvatarUrl = item.string("source_avatar")
                    .ifBlank { sender?.avatarUrl.orEmpty() }
                    .ifBlank { null },
                title = title,
                content = item.string("content", "content_text", "message").ifBlank { title },
                quoteText = item.string("quote_text"),
                relatedTitle = item.string("related_title"),
                createdAt = item.string("created_at", "time"),
                unread = item.bool("unread") ?: false,
                targetBookId = item.long("target_book_id").takeIf { it > 0 },
                targetVolumeId = item.long("target_volume_id").takeIf { it > 0 },
                targetChapterId = item.long("target_chapter_id").takeIf { it > 0 },
                targetDynamicId = item.long("target_dynamic_id").takeIf { it > 0 },
                targetCommentId = item.long("target_comment_id", "root_comment_id").takeIf { it > 0 },
                targetReplyId = item.long("target_reply_id").takeIf { it > 0 },
                targetUrl = item.string(
                    "target_url",
                    "content_target_url",
                    "quote_target_url",
                    "related_target_url",
                ),
            )
        }
        return page(source, items, requestedPage, pageSize).copy(page = requestedPage)
    }

    fun dmConversations(source: JsonObject): List<DmConversation> = listObjects(source).mapNotNull { item ->
        val user = user(item.obj("user", "peer", "peer_user", "user_info", "sender"))
            ?: return@mapNotNull null
        val peerUid = item.long("peer_uid").takeIf { it > 0 } ?: user.uid.takeIf { it > 0 }
            ?: return@mapNotNull null
        val id = item.string("conversation_id", "thread_id", "id").ifBlank { "peer-$peerUid" }
        val lastMessage = item.obj("last_message", "latest_message", "lastMessage", "last_message_info")
        DmConversation(
            id = id,
            peerUid = peerUid,
            user = user,
            lastMessage = item.string(
                "last_message",
                "last_message_text",
                "lastMessage",
                "summary",
                "content",
                "content_text",
            ).ifBlank {
                lastMessage?.string("content", "content_text", "body", "text", "message").orEmpty()
            },
            unreadCount = item.int("unread_count", "unreadCount", "unread"),
            updatedAt = item.string("updated_at", "updatedAt", "last_message_at", "time").ifBlank {
                lastMessage?.string("created_at", "createdAt", "sent_at", "time").orEmpty()
            },
            canSend = item.bool("can_send", "canSend") ?: false,
            denyReason = item.string("deny_reason", "denyReason", "dm_disabled_reason"),
        )
    }

    fun dmMessages(source: JsonObject, fallbackPeer: UserSummary): List<DmMessage> = listObjects(source).mapNotNull { item ->
        val id = item.string("message_id", "id")
        if (id.isBlank()) return@mapNotNull null
        val sender = user(item.obj("sender", "user", "author", "peer")) ?: fallbackPeer
        val content = item.string("content", "content_text", "body", "text")
        if (content.isBlank()) return@mapNotNull null
        DmMessage(
            id = id,
            conversationId = item.string("conversation_id", "thread_id"),
            sender = sender,
            content = content,
            createdAt = item.string("created_at", "createdAt", "sent_at", "time"),
            mine = item.bool("mine", "is_mine", "from_me") ?: false,
        )
    }

    fun booksPage(source: JsonObject, requestedPage: Int = 1): Page<BookSummary> {
        val list = sequenceOf("list", "cards", "ranking_list", "items", "books")
            .map { source.array(it) }
            .firstOrNull { it.isNotEmpty() }
            ?: JsonArray(emptyList())
        val pagination = source.obj("pagination", "page_info")
        val page = pagination?.int("page", "cur")?.takeIf { it >= 0 } ?: requestedPage
        val total = pagination?.int("total", "count") ?: list.size
        val nextPage = pagination?.int("next") ?: 0
        val pageSize = pagination?.int("page_size", "size") ?: list.size.coerceAtLeast(1)
        val hasMore = pagination?.bool("has_next")
            ?: (nextPage > 0 || page * pageSize < total)
        return Page(
            items = list.mapNotNull { (it as? JsonObject)?.let(::book) }.filter { it.id > 0 },
            page = page,
            total = total,
            hasMore = hasMore,
        )
    }

    private fun listObjects(source: JsonObject): List<JsonObject> {
        val direct = sequenceOf("list", "cards", "items", "books", "messages", "conversations")
            .map { source.array(it) }
            .firstOrNull { it.isNotEmpty() }
        val nested = source.obj("data", "d")?.let { data ->
            sequenceOf("list", "cards", "items", "books", "messages", "conversations")
                .map { data.array(it) }
                .firstOrNull { it.isNotEmpty() }
        }
        return (direct ?: nested ?: source.array("data", "d"))
            .mapNotNull { it as? JsonObject }
    }

    private fun <T> page(
        source: JsonObject,
        items: List<T>,
        requestedPage: Int,
        requestedPageSize: Int,
    ): Page<T> {
        val pagination = source.obj("pagination", "page_info", "pageInfo")
            ?: source.obj("data", "d")?.obj("pagination", "page_info", "pageInfo")
        val page = pagination?.int("page", "cur", "current_page")?.takeIf { it >= 0 } ?: requestedPage
        val total = pagination?.int("total", "count")?.takeIf { it >= 0 }
            ?: source.int("total").takeIf { it > 0 }
            ?: items.size
        val actualSize = pagination?.int("page_size", "pageSize", "size").takeIf { it != null && it > 0 }
            ?: requestedPageSize.coerceAtLeast(1)
        val next = pagination?.int("next") ?: 0
        val hasMore = pagination?.bool("has_more", "hasMore", "has_next")
            ?: (next > 0 || page * actualSize < total)
        return Page(items, page, total, hasMore)
    }

    fun taxonomy(source: JsonObject): SearchTaxonomy {
        val channels = source.array("channels").mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj.string("code", "id", "jump_value")
            val label = obj.string("label", "title", "name")
            if (id.isBlank() || label.isBlank()) null else SearchOption(id, label, obj.obj("filter")?.string("work_type").orEmpty())
        }
        val tags = source.array("tabs").flatMap { tab ->
            (tab as? JsonObject)?.array("groups").orEmpty().flatMap { group ->
                (group as? JsonObject)?.array("sections").orEmpty().flatMap { section ->
                    (section as? JsonObject)?.array("tag_items").orEmpty().mapNotNull { tag ->
                        val obj = tag as? JsonObject ?: return@mapNotNull null
                        val id = obj.string("jump_value", "code", "alias", "title", "name", "label")
                        val label = obj.string("title", "name", "label", "tag_name")
                        if (id.isBlank() || label.isBlank()) null else SearchOption(id, label)
                    }
                }
            }
        }.distinctBy { it.id }.take(30)
        return SearchTaxonomy(channels, tags)
    }
}
