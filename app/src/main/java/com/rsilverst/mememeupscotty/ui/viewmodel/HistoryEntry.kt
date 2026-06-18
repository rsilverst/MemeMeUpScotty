package com.rsilverst.mememeupscotty.ui.viewmodel

import com.squareup.moshi.JsonClass
import java.io.File

// A single image loaded onto the canvas, plus the context that produced it
// (prompt / model / seed) and the editable captions sitting on top of it.
//
// Captions are stored as plain data (text + position + size + style), NOT as
// baked pixels — reloading an entry from history rehydrates them as live,
// editable overlays. The image is only ever flattened with its captions at
// Save / Share time. provenance fields are null for gallery-picked images and
// for legacy entries restored from the pre-metadata persistence format.
data class HistoryEntry(
    val file: File,
    val prompt: String? = null,
    val modelId: String? = null,
    val seed: Int? = null,
    val captions: CaptionSnapshot = CaptionSnapshot()
)

// Editable caption layout for the two caption slots. Pure data so it round-trips
// through DataStore (see MainViewModel persistence) and stays free of Compose UI
// types — the UI layer converts to/from Offset / Size / CaptionStyle at the edge.
@JsonClass(generateAdapter = true)
data class CaptionSnapshot(
    val top: CaptionData = CaptionData(),
    val bottom: CaptionData = CaptionData(),
    // Side length (px) of the square canvas the offsets were last edited on.
    // Lets a smaller surface (e.g. a history thumbnail) place the captions
    // proportionally. Null until a caption is edited / for legacy entries.
    val refSize: Float? = null
)

// One caption's text, on-canvas transform, and style. Offset/size are stored as
// primitives (px); width/height are null until the user resizes (auto-fit until
// then). Style enums are stored by name and mapped back to CaptionFont / Fill /
// Align in the UI layer.
@JsonClass(generateAdapter = true)
data class CaptionData(
    val text: String = "",
    val visible: Boolean = true,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val width: Float? = null,
    val height: Float? = null,
    val font: String = "IMPACT",
    val fill: String = "WHITE",
    val align: String = "CENTER",
    val outline: Boolean = true,
    val allCaps: Boolean = true
)

// On-disk shape of a HistoryEntry. Identical to HistoryEntry except the image is
// stored by file name (resolved against the history dir on load) so the JSON is
// portable across reinstalls / path changes.
@JsonClass(generateAdapter = true)
data class HistoryEntryDto(
    val file: String,
    val prompt: String? = null,
    val modelId: String? = null,
    val seed: Int? = null,
    val captions: CaptionSnapshot = CaptionSnapshot()
)

fun HistoryEntry.toDto(): HistoryEntryDto =
    HistoryEntryDto(file.name, prompt, modelId, seed, captions)

fun HistoryEntryDto.toEntry(historyDir: File): HistoryEntry =
    HistoryEntry(File(historyDir, file), prompt, modelId, seed, captions)
