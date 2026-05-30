package com.rsilverst.mememeupscotty.ui

import com.rsilverst.mememeupscotty.R
import com.rsilverst.mememeupscotty.ui.viewmodel.ImageModel

// Title-case short label + single-glyph marker + name/description string
// resources for each ImageModel. Lives outside the enum so the UI layer
// owns its own presentation, separate from the viewmodel's data identity.

internal val ImageModel.shortLabel: String
    get() = when (this) {
        ImageModel.JUGGERNAUT   -> "Juggernaut"
        ImageModel.STABILITY    -> "Stability"
        ImageModel.REALVIS      -> "RealVis"
        ImageModel.FLUX_SCHNELL -> "Flux Schnell"
        ImageModel.DREAMSHAPER  -> "DreamShaper"
        ImageModel.BLUE_PENCIL  -> "Blue Pencil"
        ImageModel.PROTEUS      -> "Proteus"
    }

internal val ImageModel.shortGlyph: String
    get() = when (this) {
        ImageModel.JUGGERNAUT   -> "J"
        ImageModel.STABILITY    -> "S"
        ImageModel.REALVIS      -> "R"
        ImageModel.FLUX_SCHNELL -> "F"
        ImageModel.DREAMSHAPER  -> "D"
        ImageModel.BLUE_PENCIL  -> "B"
        ImageModel.PROTEUS      -> "P"
    }

internal val ImageModel.displayNameRes: Int
    get() = when (this) {
        ImageModel.JUGGERNAUT   -> R.string.model_juggernaut_name
        ImageModel.STABILITY    -> R.string.model_stability_name
        ImageModel.REALVIS      -> R.string.model_realvis_name
        ImageModel.FLUX_SCHNELL -> R.string.model_flux_name
        ImageModel.DREAMSHAPER  -> R.string.model_dreamshaper_name
        ImageModel.BLUE_PENCIL  -> R.string.model_bluepencil_name
        ImageModel.PROTEUS      -> R.string.model_proteus_name
    }

internal val ImageModel.descriptionRes: Int
    get() = when (this) {
        ImageModel.JUGGERNAUT   -> R.string.model_juggernaut_desc
        ImageModel.STABILITY    -> R.string.model_stability_desc
        ImageModel.REALVIS      -> R.string.model_realvis_desc
        ImageModel.FLUX_SCHNELL -> R.string.model_flux_desc
        ImageModel.DREAMSHAPER  -> R.string.model_dreamshaper_desc
        ImageModel.BLUE_PENCIL  -> R.string.model_bluepencil_desc
        ImageModel.PROTEUS      -> R.string.model_proteus_desc
    }
