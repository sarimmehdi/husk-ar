package com.sarim.husk.marker.presentation

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarim.husk.ar.PaperSize
import com.sarim.husk.ar.correctedWidthMillimetres
import com.sarim.husk.ar.printLayoutFor
import com.sarim.husk.marker.domain.model.Marker
import com.sarim.husk.marker.domain.model.MarkerId
import com.sarim.husk.marker.domain.model.MarkerOrigin
import com.sarim.husk.marker.domain.usecase.DeleteMarkerUseCase
import com.sarim.husk.marker.domain.usecase.ObserveMarkersUseCase
import com.sarim.husk.marker.domain.usecase.RecordPrintedWidthUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlin.math.roundToInt

/**
 * One marker, and exactly how to print it.
 *
 * Every number here is derived rather than written into a help page, because the printed width is
 * the scale of every measurement taken against this marker and a stale instruction would be worse
 * than none.
 */
@Parcelize
data class MarkerRow(
    /** The marker's id. */
    val id: String,
    /** What it is called. */
    val name: String,
    /** Whether it shipped with the app, and so cannot be deleted. */
    val isBundled: Boolean,
    /** How wide it should be once printed. */
    val printedWidthMillimetres: Int,
    /** How tall it will be at that width. */
    val printedHeightMillimetres: Int,
    /** Gap to leave at each side of the sheet. */
    val horizontalPaddingMillimetres: Int,
    /** Gap to leave at the top and bottom. */
    val verticalPaddingMillimetres: Int,
    /** What the printed reference bar should measure. */
    val referenceBarMillimetres: Int,
    /** Whether it fits the chosen paper at all. */
    val fitsOnPage: Boolean,
) : Parcelable

/** What the marker library draws. */
@Parcelize
data class MarkerLibraryScreenState(
    /** The markers, bundled first. */
    val markers: List<MarkerRow> = emptyList(),
    /** The paper the instructions are worked out for. */
    val paper: PaperSize = PaperSize.A4,
    /** Whether the store has yet to answer. */
    val isLoading: Boolean = true,
) : Parcelable

/** What the marker library can ask for. */
@Immutable
sealed interface MarkerLibraryScreenToViewModelEvents {
    /** Work the instructions out for a different sheet. */
    data class PaperChosen(
        /** The paper. */
        val paper: PaperSize,
    ) : MarkerLibraryScreenToViewModelEvents

    /**
     * Report what the printed reference bar actually measures.
     *
     * The step that makes the whole thing safe, since it recovers the true scale whatever the print
     * dialog did to the page.
     */
    data class ReferenceBarMeasured(
        /** Which marker was printed. */
        val id: String,
        /** What the bar reads, in millimetres. */
        val measuredMillimetres: Double,
    ) : MarkerLibraryScreenToViewModelEvents

    /** Remove an imported marker. */
    data class DeleteMarker(
        /** Which marker. */
        val id: String,
    ) : MarkerLibraryScreenToViewModelEvents
}

/** Everything the marker library needs from the domain. */
data class MarkerLibraryScreenUseCase(
    /** Supplies the markers. */
    val observeMarkersUseCase: ObserveMarkersUseCase,
    /** Records what a printed sheet actually measured. */
    val recordPrintedWidthUseCase: RecordPrintedWidthUseCase,
    /** Removes an imported marker. */
    val deleteMarkerUseCase: DeleteMarkerUseCase,
)

/** Drives the marker library and its printing instructions. */
class MarkerLibraryViewModel(
    private val useCases: MarkerLibraryScreenUseCase,
) : ViewModel() {
    private val paper = MutableStateFlow(PaperSize.A4)

    /** What the screen draws. */
    val state: StateFlow<MarkerLibraryScreenState> =
        combine(useCases.observeMarkersUseCase(), paper) { markers, sheet ->
            MarkerLibraryScreenState(
                markers = markers.map { it.toRow(sheet) },
                paper = sheet,
                isLoading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = MarkerLibraryScreenState(),
        )

    /** Handles [event]. */
    fun onEvent(event: MarkerLibraryScreenToViewModelEvents) {
        when (event) {
            is MarkerLibraryScreenToViewModelEvents.PaperChosen -> paper.value = event.paper
            is MarkerLibraryScreenToViewModelEvents.ReferenceBarMeasured -> correct(event)
            is MarkerLibraryScreenToViewModelEvents.DeleteMarker ->
                viewModelScope.launch { useCases.deleteMarkerUseCase(MarkerId(event.id)) }
        }
    }

    private fun correct(event: MarkerLibraryScreenToViewModelEvents.ReferenceBarMeasured) {
        viewModelScope.launch {
            val row = state.value.markers.firstOrNull { it.id == event.id } ?: return@launch
            useCases.recordPrintedWidthUseCase(
                MarkerId(event.id),
                correctedWidthMillimetres(
                    intendedWidthMillimetres = row.printedWidthMillimetres.toDouble(),
                    referenceBarMillimetres = row.referenceBarMillimetres.toDouble(),
                    measuredBarMillimetres = event.measuredMillimetres,
                ),
            )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun Marker.toRow(paper: PaperSize): MarkerRow {
    val layout =
        printLayoutFor(
            paper = paper,
            imagePixelWidth = imagePixelWidth,
            imagePixelHeight = imagePixelHeight,
            targetWidthMillimetres = printedWidthMillimetres,
        )
    return MarkerRow(
        id = id.value,
        name = name,
        isBundled = origin == MarkerOrigin.BUNDLED,
        printedWidthMillimetres = layout.imageWidthMillimetres.roundToInt(),
        printedHeightMillimetres = layout.imageHeightMillimetres.roundToInt(),
        horizontalPaddingMillimetres = layout.horizontalPaddingMillimetres.roundToInt(),
        verticalPaddingMillimetres = layout.verticalPaddingMillimetres.roundToInt(),
        referenceBarMillimetres = layout.referenceBarMillimetres.roundToInt(),
        fitsOnPage = layout.fitsOnPage,
    )
}
