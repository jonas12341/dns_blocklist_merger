package de.jonas.dnsblocklistmerger.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceholderTest {
    @Test
    fun coreModuleIsOnTheTestClasspath() {
        assertEquals("core", CoreInfo.NAME)
    }
}
