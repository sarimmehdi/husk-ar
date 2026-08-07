package com.sarim.husk.utils.errorhandling

/** Marker for typed errors carried by [Result]. */
typealias RootError = Error

/** Represents either a successful value or a typed failure. */
sealed interface Result<out D, out E : RootError> {
    /** A successfully produced value. */
    data class Success<out D, out E : RootError>(
        /** Value produced by the operation. */
        val data: D,
    ) : Result<D, E>

    /** A typed operation failure. */
    data class Error<out D, out E : RootError>(
        /** Failure produced by the operation. */
        val error: E,
    ) : Result<D, E>
}

/** Returns this value wrapped as a successful [Result]. */
fun <T, E : RootError> T.asSuccess(): Result<T, E> = Result.Success(this)

/** Transforms a successful value while passing a failure through unchanged. */
fun <D, E : RootError, R> Result<D, E>.map(transform: (D) -> R): Result<R, E> =
    when (this) {
        is Result.Success -> Result.Success(transform(data))
        is Result.Error -> Result.Error(error)
    }
