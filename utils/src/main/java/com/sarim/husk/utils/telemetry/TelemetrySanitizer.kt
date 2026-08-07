package com.sarim.husk.utils.telemetry

/** Redacts common identifying or secret values before diagnostics leave the device. */
class TelemetrySanitizer {
    /** Returns [message] with identifying content redacted and its length capped. */
    fun sanitize(message: String): String =
        message
            .replace(EMAIL_PATTERN, REDACTED_EMAIL)
            .replace(SECRET_PATTERN, "$1=$REDACTED_VALUE")
            .replace(URL_QUERY_PATTERN, "$1?$REDACTED_QUERY")
            .take(MAX_MESSAGE_LENGTH)

    private companion object {
        const val MAX_MESSAGE_LENGTH = 500
        const val REDACTED_EMAIL = "[redacted-email]"
        const val REDACTED_VALUE = "[redacted]"
        const val REDACTED_QUERY = "[redacted]"

        val EMAIL_PATTERN = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
        val SECRET_PATTERN =
            Regex(
                "(?i)\\b(password|token|authorization|cookie|api[_-]?key)\\s*[=:]\\s*[^\\s,;]+",
            )
        val URL_QUERY_PATTERN = Regex("(https?://[^?\\s]+)\\?\\S+")
    }
}
