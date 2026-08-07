package com.sarim.husk.utils.errorui

import androidx.compose.runtime.Immutable
import com.sarim.husk.utils.UiText

/** Loading and error state understood by the shared screen container. */
@Immutable
data class LoadableScreenUiState(
    /** Whether the first load is still running. */
    val isLoading: Boolean,
    /** Failure that prevented any content from loading. */
    val loadError: UiText?,
    /** Failure during a refresh while content remains visible. */
    val refreshError: UiText?,
    /** Durable banner that remains until its cause is resolved. */
    val persistentMessage: PersistentMessage? = null,
)
