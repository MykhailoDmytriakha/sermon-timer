package com.example.sermontimer.domain.model

import kotlinx.serialization.Serializable

/** Identifies the current phase of the three-part timer. */
@Serializable
enum class Segment {
    INTRO,
    MAIN,
    OUTRO,
    DONE;

    fun next(skipOutro: Boolean = false): Segment = when (this) {
        INTRO -> MAIN
        MAIN -> if (skipOutro) DONE else OUTRO
        OUTRO -> DONE
        DONE -> DONE
    }
}

