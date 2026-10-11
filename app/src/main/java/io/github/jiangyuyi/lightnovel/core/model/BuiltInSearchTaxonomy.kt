package io.github.jiangyuyi.lightnovel.core.model

/** Public taxonomy snapshot verified on 2026-10-12; never contains account data. */
object BuiltInSearchTaxonomy {
    val value = SearchTaxonomy(
        channels = listOf(
            SearchOption("lightnovel", "轻小说", "lightnovel"),
            SearchOption("original", "原创", "original"),
            SearchOption("fanfic", "同人", "fanfic"),
            SearchOption("epub", "EPUB", "epub"),
        ),
        // Same ordering and 30-tag limit as ApiParsers.taxonomy.
        tags = listOf(
            "最近更新", "WEB", "文库", "异世界", "校园", "恋爱", "青春", "日常",
            "百合", "后宫", "搞笑", "恋爱喜剧", "悬疑", "学园", "治愈", "推理",
            "黑暗", "奇幻", "科幻", "冒险", "转生", "地下城", "时空", "魔法",
            "青梅竹马", "精灵", "学姐", "勇者", "魔王", "圣女",
        ).map { SearchOption(it, it) },
    )
}
