package io.github.jiangyuyi.lightnovel.core.model

data class UserSummary(
    val uid: Long,
    val nickname: String,
    val avatarUrl: String? = null,
)

data class AccountProfile(
    val user: UserSummary,
    val signature: String = "",
    val levelName: String = "",
    val level: Int? = null,
    val coin: Int = 0,
    val fansCount: Int = 0,
    val followingCount: Int = 0,
    val postCount: Int = 0,
)

data class SocialUser(
    val user: UserSummary,
    val signature: String = "",
    val levelName: String = "",
    val followed: Boolean = false,
    val relationState: String = "",
)

data class Session(
    val loggedIn: Boolean = false,
    val securityKey: String = "",
    val uid: Long = 0,
    val user: UserSummary? = null,
)

data class BookSummary(
    val id: Long,
    val title: String,
    val author: String = "",
    val summary: String = "",
    val coverUrl: String? = null,
    val tags: List<String> = emptyList(),
    val volumeCount: Int = 0,
    val chapterCount: Int = 0,
    val wordCount: Long = 0,
    val score: Double? = null,
    val rank: Int? = null,
    val defaultVolumeId: Long? = null,
    val defaultChapterId: Long? = null,
    val inBookshelf: Boolean? = null,
    val unreadChapterCount: Int? = null,
)

data class BookDetail(
    val book: BookSummary,
    val publisher: UserSummary? = null,
    val alternateVersions: List<BookSummary> = emptyList(),
    val commentCount: Int = 0,
    val favoriteCount: Int = 0,
)

data class Volume(
    val id: Long,
    val bookId: Long,
    val title: String,
    val chapterCount: Int = 0,
    val firstChapterId: Long? = null,
    val lastChapterId: Long? = null,
)

data class ChapterSummary(
    val id: Long,
    val bookId: Long,
    val volumeId: Long,
    val title: String,
    val order: Int = 0,
    val wordCount: Long = 0,
    val locked: Boolean = false,
)

data class ChapterDetail(
    val chapter: ChapterSummary,
    val bookTitle: String,
    val volumeTitle: String,
    val bodyText: String,
    val bodyHtml: String = "",
    val previousChapterId: Long? = null,
    val nextChapterId: Long? = null,
)

data class ReaderBootstrap(
    val book: BookSummary,
    val chapterId: Long,
    val volumeId: Long? = null,
    val inBookshelf: Boolean = false,
    val resumeAvailable: Boolean = false,
)

data class Comment(
    val id: Long,
    val author: UserSummary,
    val content: String,
    val createdAt: String = "",
    val likeCount: Int = 0,
    val replyCount: Int = 0,
)

data class Page<T>(
    val items: List<T>,
    val page: Int = 1,
    val total: Int = items.size,
    val hasMore: Boolean = false,
)

data class SearchTaxonomy(
    val channels: List<SearchOption>,
    val tags: List<SearchOption>,
)

data class SearchOption(
    val id: String,
    val label: String,
    val workType: String = "",
)

enum class DiscoverChannel(val label: String) {
    HOT("热门"),
    RANK("排行"),
    NEW("新书"),
    ORIGINAL("原创"),
    FANFIC("同人"),
    EPUB("EPUB"),
    UPDATED("最近更新"),
    COLLECTION("合集"),
}

enum class ReaderFont(val label: String) {
    SANS("无衬线"),
    SERIF("衬线"),
    MONO("等宽"),
}

enum class ReaderTheme(val label: String) {
    WHITE("白色"),
    SEPIA("米黄"),
    GREEN("护眼绿"),
    DARK("深色"),
}

enum class ReaderMode(val label: String) {
    PAGED("左右翻页"),
    SCROLL("上下滚动"),
}

data class ReaderPreferences(
    val font: ReaderFont = ReaderFont.SERIF,
    val fontSize: Float = 19f,
    val lineHeight: Float = 1.7f,
    val horizontalPadding: Int = 22,
    val theme: ReaderTheme = ReaderTheme.SEPIA,
    val mode: ReaderMode = ReaderMode.PAGED,
)

data class LocalReadingProgress(
    val chapterId: Long,
    val paragraphIndex: Int,
    val percent: Int,
)

data class ReadingHistoryItem(
    val book: BookSummary,
    val lastChapterId: Long? = null,
    val lastChapterTitle: String = "",
    val readAt: String = "",
)

data class PublishedWork(
    val bookId: Long,
    val title: String,
    val coverUrl: String? = null,
    val author: String = "",
    val summary: String = "",
    val type: String = "",
    val status: String = "",
    val reviewStatus: String = "",
    val reviewText: String = "",
    val volumeCount: Int = 0,
    val chapterCount: Int = 0,
    val wordCount: Long = 0,
    val updatedAt: String = "",
)

enum class MessageCategory(val code: String, val label: String) {
    DM("dm", "私信"),
    REPLY("reply", "回复"),
    MENTION("mention", "@我"),
    LIKE("like", "点赞"),
    FAN("fan", "新粉丝"),
    SYSTEM("system", "系统"),
}

data class MessageSummary(
    val unreadCount: Int = 0,
    val replyCount: Int = 0,
    val mentionCount: Int = 0,
    val likeCount: Int = 0,
    val systemCount: Int = 0,
    val dmCount: Int = 0,
    val fanCount: Int = 0,
) {
    fun count(category: MessageCategory): Int = when (category) {
        MessageCategory.DM -> dmCount
        MessageCategory.REPLY -> replyCount
        MessageCategory.MENTION -> mentionCount
        MessageCategory.LIKE -> likeCount
        MessageCategory.FAN -> fanCount
        MessageCategory.SYSTEM -> systemCount
    }

    fun clear(category: MessageCategory): MessageSummary {
        val removed = count(category)
        return when (category) {
            MessageCategory.DM -> copy(unreadCount = (unreadCount - removed).coerceAtLeast(0), dmCount = 0)
            MessageCategory.REPLY -> copy(unreadCount = (unreadCount - removed).coerceAtLeast(0), replyCount = 0)
            MessageCategory.MENTION -> copy(unreadCount = (unreadCount - removed).coerceAtLeast(0), mentionCount = 0)
            MessageCategory.LIKE -> copy(unreadCount = (unreadCount - removed).coerceAtLeast(0), likeCount = 0)
            MessageCategory.FAN -> copy(unreadCount = (unreadCount - removed).coerceAtLeast(0), fanCount = 0)
            MessageCategory.SYSTEM -> copy(unreadCount = (unreadCount - removed).coerceAtLeast(0), systemCount = 0)
        }
    }
}

data class NotificationMessage(
    val id: String,
    val category: MessageCategory,
    val user: UserSummary? = null,
    val sourceName: String = "",
    val sourceAvatarUrl: String? = null,
    val title: String,
    val content: String,
    val quoteText: String = "",
    val relatedTitle: String = "",
    val createdAt: String = "",
    val unread: Boolean = false,
    val targetBookId: Long? = null,
    val targetVolumeId: Long? = null,
    val targetChapterId: Long? = null,
    val targetDynamicId: Long? = null,
    val targetCommentId: Long? = null,
    val targetReplyId: Long? = null,
    val targetUrl: String = "",
)

data class DmConversation(
    val id: String,
    val peerUid: Long,
    val user: UserSummary,
    val lastMessage: String = "",
    val unreadCount: Int = 0,
    val updatedAt: String = "",
    val canSend: Boolean = false,
    val denyReason: String = "",
)

data class DmMessage(
    val id: String,
    val conversationId: String = "",
    val sender: UserSummary,
    val content: String,
    val createdAt: String = "",
    val mine: Boolean = false,
)
