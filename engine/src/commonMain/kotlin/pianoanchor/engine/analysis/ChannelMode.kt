package pianoanchor.engine.analysis

import pianoanchor.core.model.ControlChange

/** All Sound Off (MIDI 1.0 channel mode message, control change 120): every sounding note stops at once. */
private const val ALL_SOUND_OFF_CONTROLLER = 120

/**
 * All Notes Off and the mode changes that imply it (MIDI 1.0 channel mode messages: control change 123, and Omni
 * Off/On and Mono/Poly On at 124–127): every key comes up, the pedal still holds. 127 is the highest controller
 * number, so "123 or more" is the whole range.
 */
private const val ALL_NOTES_OFF_FIRST_CONTROLLER = 123

/** True for CC120 (All Sound Off) and CC123–127 (All Notes Off, Omni Off/On, Mono/Poly On): every key comes up. */
public fun ControlChange.releasesAllKeys(): Boolean =
    controller == ALL_SOUND_OFF_CONTROLLER || controller >= ALL_NOTES_OFF_FIRST_CONTROLLER

/** True for CC120 (All Sound Off) only: notes the sustain pedal holds stop too. */
public fun ControlChange.silencesAll(): Boolean = controller == ALL_SOUND_OFF_CONTROLLER
