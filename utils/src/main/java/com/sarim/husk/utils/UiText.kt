package com.sarim.husk.utils

import android.content.Context
import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.res.stringResource
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import java.io.Serializable

/** Text that can be produced without resolving Android resources eagerly. */
@Parcelize
sealed class UiText : Parcelable {
    /** Already-resolved text that does not require localization. */
    data class DynamicString(
        /** Literal text displayed to the user. */
        val value: String,
    ) : UiText()

    /** A string resource resolved against the current locale at render time. */
    class StringResource(
        /** Android string resource identifier. */
        @get:StringRes val resId: Int,
        /** Optional formatting arguments supplied to the resource. */
        val args: @RawValue Array<out Serializable> = emptyArray(),
    ) : UiText()

    /** Resolves this value against the current Compose configuration. */
    @Suppress("SpreadOperator")
    @Composable
    @NonRestartableComposable
    fun asString(): String =
        if (this is DynamicString) {
            value
        } else {
            this as StringResource
            stringResource(resId, *args)
        }

    /** Resolves this value outside composition using [context]. */
    @Suppress("SpreadOperator")
    fun asString(context: Context): String =
        when (this) {
            is DynamicString -> value
            is StringResource -> context.getString(resId, *args)
        }
}
