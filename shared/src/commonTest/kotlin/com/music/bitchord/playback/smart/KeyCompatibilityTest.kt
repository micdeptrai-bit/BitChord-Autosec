package com.music.bitchord.playback.smart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Key compatibility follows the Camelot wheel, with the analyzer's own key names. */
class KeyCompatibilityTest {

    @Test
    fun keysLandOnTheirCamelotPosition() {
        assertEquals(8 to false, camelotWheelPosition("C major"))
        assertEquals(8 to true, camelotWheelPosition("A minor"))
        assertEquals(9 to false, camelotWheelPosition("G major"))
        assertEquals(7 to false, camelotWheelPosition("F major"))
        assertEquals(2 to false, camelotWheelPosition("F\u266F major"))
        assertEquals(2 to true, camelotWheelPosition("E\u266D minor"))
        assertEquals(1 to false, camelotWheelPosition("B major"))
        assertEquals(12 to false, camelotWheelPosition("E major"))
    }

    @Test
    fun unrecognisedKeysHaveNoPosition() {
        assertNull(camelotWheelPosition(""))
        assertNull(camelotWheelPosition("C"))
        assertNull(camelotWheelPosition("H major"))
        assertNull(camelotWheelPosition("C dorian"))
    }

    @Test
    fun theSameKeyTheRelativeAndOneWheelStepMix() {
        assertTrue(harmonicallyCompatible("C major", "C major"))
        assertTrue(harmonicallyCompatible("C major", "A minor"))
        assertTrue(harmonicallyCompatible("C major", "G major"))
        assertTrue(harmonicallyCompatible("C major", "F major"))
        assertTrue(harmonicallyCompatible("A minor", "E minor"))
        // Round the end of the wheel: 12B next to 1B.
        assertTrue(harmonicallyCompatible("B major", "E major"))
    }

    @Test
    fun aWholeToneOrATritoneApartClashes() {
        assertFalse(harmonicallyCompatible("C major", "D major"))
        assertFalse(harmonicallyCompatible("C major", "F\u266F major"))
        assertFalse(harmonicallyCompatible("C major", "D minor"))
        assertFalse(harmonicallyCompatible("A minor", "B minor"))
    }
}
