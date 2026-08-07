package com.sarim.husk.utils.errorhandling

/** Application-level exceptions raised outside the data layer. */
sealed class AppException(
    message: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    /** Indicates that the application reached an impossible state. */
    data class InvalidState(
        override val message: String? = null,
        override val cause: Throwable? = null,
    ) : AppException(message, cause)

    /** Represents an application failure without a more specific category. */
    data class Unknown(
        override val message: String? = null,
        override val cause: Throwable? = null,
    ) : AppException(message, cause)
}
