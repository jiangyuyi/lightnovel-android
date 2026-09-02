package io.github.jiangyuyi.lightnovel.feature.reader

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal enum class ReaderLinkKind {
    DIRECT_DOWNLOAD,
    INTERNAL_WEB,
    EXTERNAL_WEB,
}

internal data class ReaderLinkTarget(
    val url: String,
    val kind: ReaderLinkKind,
    val host: String,
    val suggestedFileName: String,
)

internal object ReaderLinkPolicy {
    private const val SITE_ORIGIN = "https://www.lightnovel.fun/"
    private val siteHosts = setOf("lightnovel.fun", "www.lightnovel.fun", "api.lightnovel.fun", "res.lightnovel.fun")
    private val networkDiskHosts = setOf(
        "pan.baidu.com",
        "aliyundrive.com",
        "www.aliyundrive.com",
        "alipan.com",
        "www.alipan.com",
        "drive.uc.cn",
        "www.123pan.com",
        "123pan.com",
        "lanzou.com",
        "lanzoui.com",
        "lanzoux.com",
    )
    private val downloadExtensions = setOf("epub", "mobi", "azw3", "pdf", "zip", "rar", "7z", "txt")

    fun normalize(rawUrl: String): String? = runCatching {
        val decoded = rawUrl.trim().replace("&amp;", "&", ignoreCase = true)
        if (decoded.isBlank() || decoded.startsWith("#")) return null
        val resolved = URI(SITE_ORIGIN).resolve(decoded)
        if (resolved.scheme?.lowercase() !in setOf("http", "https") || resolved.host.isNullOrBlank()) return null
        resolved.normalize().toASCIIString()
    }.getOrNull()

    fun classify(rawUrl: String, label: String = ""): ReaderLinkTarget? {
        val url = normalize(rawUrl) ?: return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host.orEmpty().lowercase()
        val pathName = uri.path.orEmpty().substringAfterLast('/').substringBefore('?')
        val extension = pathName.substringAfterLast('.', "").lowercase()
        val siteLink = host in siteHosts
        val networkDisk = host in networkDiskHosts || networkDiskHosts.any { host.endsWith(".$it") }
        val looksLikeDownload = extension in downloadExtensions ||
            uri.path.orEmpty().contains("/download", ignoreCase = true) ||
            (siteLink && label.contains("下载"))
        val kind = when {
            networkDisk -> ReaderLinkKind.EXTERNAL_WEB
            looksLikeDownload -> ReaderLinkKind.DIRECT_DOWNLOAD
            siteLink -> ReaderLinkKind.INTERNAL_WEB
            else -> ReaderLinkKind.EXTERNAL_WEB
        }
        return ReaderLinkTarget(
            url = url,
            kind = kind,
            host = host,
            suggestedFileName = suggestedFileName(pathName, label, extension),
        )
    }

    private fun suggestedFileName(pathName: String, label: String, extension: String): String {
        val decodedPath = runCatching { URLDecoder.decode(pathName, StandardCharsets.UTF_8.name()) }.getOrDefault(pathName)
        val source = decodedPath.takeIf { it.contains('.') }
            ?: label.trim().takeIf(String::isNotBlank)
            ?: "轻之国度下载"
        val safe = source.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(120).trim('.', ' ')
        if (safe.isBlank()) return "轻之国度下载${extension.takeIf(String::isNotBlank)?.let { ".$it" }.orEmpty()}"
        return if (safe.contains('.') || extension.isBlank()) safe else "$safe.$extension"
    }
}
