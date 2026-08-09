package com.sarim.husk.ar

/**
 * What a caller wants told about a drag.
 *
 * Grouped because the three arrive from one gesture and a caller almost always wants all of them:
 * when it began, what it looks like now, and what it finished as.
 */
data class TraceListener(
    /** The finger landed. Reported once per drag, not on every movement. */
    val onStarted: () -> Unit = {},
    /** The outline as it currently stands, while the finger is still down. */
    val onChanged: (TracedEllipse) -> Unit = {},
    /** The finished outline, if the drag was ever more than a tap. */
    val onCommitted: (TracedEllipse) -> Unit = {},
)
