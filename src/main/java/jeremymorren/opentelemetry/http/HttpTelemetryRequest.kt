package jeremymorren.opentelemetry.http

import jeremymorren.opentelemetry.models.Activity
import jeremymorren.opentelemetry.models.ObjectDictionary

/**
 * An HTTP request reconstructed from the attributes of a client (dependency) or server (request) span.
 *
 * Attribute names follow the OpenTelemetry HTTP semantic conventions
 * (https://opentelemetry.io/docs/specs/semconv/http/http-spans/). Captured headers are exposed there as
 * `http.request.header.<lowercased name>`, whose value is an array of strings; the pre-1.23 attribute
 * names (`http.method`, `http.url`, ...) are accepted as well, because instrumentation pinned to an
 * older schema still emits them.
 */
data class HttpTelemetryRequest(
    val method: String,
    val url: String,
    val headers: List<Pair<String, String>>,
) {
    /** curl command in the format produced by Chrome DevTools' "Copy as cURL (bash)". */
    fun toCurlBash(compressed: Boolean): String = toCurl(::escapeBash, "\\", "\n", compressed)

    /** curl command in the format produced by Chrome DevTools' "Copy as cURL (cmd)". */
    fun toCurlCmd(compressed: Boolean): String = toCurl(::escapeCmd, "^", "\r\n", compressed)

    /** The request in the JetBrains HTTP client (`.http` file) format. */
    fun toHttpRequest(): String {
        val newLine = System.lineSeparator()
        return buildString {
            append("### ").append(method).append(' ').append(url).append(newLine)
            append(method).append(' ').append(url).append(newLine)
            for ((name, value) in headers) {
                append(name).append(": ").append(value).append(newLine)
            }
        }
    }

    private fun toCurl(
        escape: (String) -> String,
        continuation: String,
        newLine: String,
        compressed: Boolean,
    ): String {
        val parts = mutableListOf<String>()
        // Brackets and braces are escaped so the URL survives shell globbing/brace expansion.
        parts += URL_SPECIALS.replace(escape(url)) { "\\" + it.value }
        // curl infers GET, so only other verbs need to be spelled out.
        if (method != "GET") {
            parts += "-X " + escape(method)
        }
        for ((name, value) in headers) {
            if (name.lowercase() in IGNORED_HEADERS) continue
            parts += "-H " + escape("$name: $value")
        }
        if (compressed) {
            parts += "--compressed"
        }

        val separator = if (parts.size >= 3) " $continuation$newLine  " else " "
        return "curl " + parts.joinToString(separator)
    }

    companion object {
        /**
         * Reconstructs the request an HTTP span describes, or null when the span is not an HTTP span or
         * carries too little information to build a request from.
         */
        @JvmStatic
        fun from(activity: Activity?): HttpTelemetryRequest? {
            val tags = activity?.tags ?: return null
            val method = tags.getString("http.request.method") ?: tags.getString("http.method") ?: return null
            val url = resolveUrl(tags) ?: return null
            return HttpTelemetryRequest(method, url, resolveHeaders(tags))
        }
    }
}

private const val REQUEST_HEADER_PREFIX = "http.request.header."

/**
 * Headers curl derives from the URL itself, plus `content-length`: the semantic conventions never carry a
 * request body, so a copied length would describe a body curl is not going to send.
 */
private val IGNORED_HEADERS =
    setOf("host", "method", "path", "scheme", "version", "authority", "protocol", "content-length")

private val URL_SPECIALS = Regex("[\\[{}\\]]")
private val BASH_NEEDS_ANSI_C = Regex("[\\x00-\\x1F\\x7F-\\x9F!']")
private val BASH_UNPRINTABLE = Regex("[\\x00-\\x1F\\x7F-\\x9F!]")
private val CMD_NEW_LINES = Regex("[\r\n]+")

private fun resolveUrl(tags: ObjectDictionary): String? {
    // Client spans record the whole URL; server spans record its parts.
    tags.getString("url.full")?.let { return it }
    tags.getString("http.url")?.let { return it }

    val path = tags.getString("url.path") ?: tags.getString("http.target") ?: return null
    val scheme = tags.getString("url.scheme") ?: tags.getString("http.scheme") ?: "http"
    val host = tags.getString("server.address")
        ?: tags.getString("http.host")
        ?: tags.getString("net.host.name")
        ?: "localhost"
    val port = tags.getString("server.port") ?: tags.getString("net.host.port")
    val query = tags.getString("url.query")

    return buildString {
        append(scheme).append("://").append(host)
        if (port != null && port != defaultPort(scheme)) {
            append(':').append(port)
        }
        append(path)
        if (!query.isNullOrEmpty()) {
            append('?').append(query)
        }
    }
}

private fun defaultPort(scheme: String) = if (scheme.equals("https", ignoreCase = true)) "443" else "80"

private fun resolveHeaders(tags: ObjectDictionary): List<Pair<String, String>> =
    tags.values
        .filterKeys { it.length > REQUEST_HEADER_PREFIX.length && it.startsWith(REQUEST_HEADER_PREFIX) }
        .mapNotNull { (key, value) ->
            headerValue(value)?.let { key.removePrefix(REQUEST_HEADER_PREFIX) to it }
        }
        .sortedBy { it.first }

private fun headerValue(value: Any?): String? = when (value) {
    null -> null
    // Semantic conventions model header values as an array, one entry per repetition of the header.
    is List<*> -> value.filterNotNull().joinToString(", ").ifEmpty { null }
    else -> value.toString()
}

/**
 * Chrome DevTools' `escapeStringPosix`: single quotes, falling back to ANSI-C quoting whenever the value
 * contains a quote, a control character or `!`.
 */
private fun escapeBash(value: String): String {
    if (!BASH_NEEDS_ANSI_C.containsMatchIn(value)) {
        return "'" + value + "'"
    }
    val quoted = value
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
    val escaped = BASH_UNPRINTABLE.replace(quoted) { match -> escapeBashCharacter(match.value[0]) }
    return "$" + "'" + escaped + "'"
}

private fun escapeBashCharacter(character: Char): String {
    val code = character.code
    return when {
        code < 16 -> "\\u000" + code.toString(16)
        code < 256 -> "\\x" + code.toString(16)
        else -> "\\u" + code.toString(16).padStart(4, '0')
    }
}

/**
 * Chrome DevTools' `escapeStringWin`: double quotes, with `%` neutralised so cmd.exe does not expand it,
 * and line breaks continued with `^`.
 */
private fun escapeCmd(value: String): String {
    val quoted = value
        .replace("\"", "\"\"")
        .replace("%", "\"%\"")
        .replace("\\", "\\\\")
    val escaped = CMD_NEW_LINES.replace(quoted) { match -> "\"^" + match.value + "\"" }
    return "\"" + escaped + "\""
}
