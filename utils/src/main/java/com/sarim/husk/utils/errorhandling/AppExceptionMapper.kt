package com.sarim.husk.utils.errorhandling

import com.sarim.husk.utils.R
import com.sarim.husk.utils.UiText
import java.io.IOException

/** Returns a user-facing message for this exception. */
fun Throwable.toUiText(): UiText =
    when (this) {
        is IOException -> UiText.StringResource(R.string.error_connection)
        is AppException.InvalidState -> UiText.StringResource(R.string.error_invalid_state)
        is AppException.Unknown -> UiText.StringResource(R.string.error_unknown)
        else -> UiText.StringResource(R.string.error_unknown)
    }
