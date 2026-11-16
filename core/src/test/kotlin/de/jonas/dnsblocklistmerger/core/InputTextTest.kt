package de.jonas.dnsblocklistmerger.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InputTextTest {
    @Test
    fun removesAllWhitespaceFromUrlsAndDomains() {
        assertEquals("https://big.oisd.nl/list.txt", InputText.withoutWhitespace(" https://big. oisd. nl/list. txt \n"))
        assertEquals("login.example.com", InputText.withoutWhitespace("login. example. com"))
    }

    @Test
    fun keepsSpacesInSmbFolderNamesButNotAfterDots() {
        assertEquals("smb://fritz.box/FRITZ.NAS/Mein Ordner/blocklist.txt",
            InputText.withoutSpacesAfterDots(" smb://fritz. box/FRITZ. NAS/Mein Ordner/blocklist. txt "))
    }
}
