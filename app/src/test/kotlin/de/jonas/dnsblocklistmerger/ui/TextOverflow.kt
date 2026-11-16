package de.jonas.dnsblocklistmerger.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.text.TextLayoutResult

/** True when the text of this node is cut off (ellipsis or clipped). */
fun SemanticsNodeInteraction.isTextCut(): Boolean {
    val results = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
    return results.single().hasVisualOverflow
}
