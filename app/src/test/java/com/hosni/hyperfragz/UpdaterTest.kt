package com.hosni.hyperfragz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun testBuildNumber() {
        assertEquals(14, Updater.testBuildNumber("1.2.0-test.14"))
        assertEquals(0, Updater.testBuildNumber("1.2.0"))
        assertEquals(0, Updater.testBuildNumber("1.2.0-test."))
    }

    @Test fun releaseVersionsStillCompareAsBefore() {
        assertTrue(Updater.isNewer("1.10.0", "1.9.2"))
        assertFalse(Updater.isNewer("1.2.0", "1.2.0"))
    }
}
