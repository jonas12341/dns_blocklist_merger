package de.jonas.dnsblocklistmerger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionTest {

    private fun bytes(vararg domains: String) = domains.sumOf { Selection.lineBytes(it) }

    private fun settings(maxBytes: Long, omitSubdomains: Boolean = true, a: Int = 10, b: Int = 1) =
        SelectionSettings(maxBytes = maxBytes, a = a, b = b, omitSubdomains = omitSubdomains)

    private fun SelectionResult.status(domain: String) = domains.single { it.domain == domain }.status

    @Test
    fun outputIsAdblockSyntaxSortedDigitsFirstEachOnce() {
        val result = Selection.compute(
            listOf(SourceList(1, listOf("zeta.example", "alpha.example", "9.example")), SourceList(1, listOf("alpha.example"))),
            emptySet(),
            settings(4096L * 1024),
        )
        assertEquals(listOf("9.example", "alpha.example", "zeta.example"), result.entries)
        assertEquals("||9.example^\n||alpha.example^\n||zeta.example^\n", result.render())
        assertEquals(result.render().toByteArray().size.toLong(), result.bytes)
    }

    @Test
    fun requiredEntriesAreWrittenEvenWhenAloneOverTheLimit() {
        val result = Selection.compute(
            listOf(SourceList(1, listOf("a.example", "b.example")), SourceList(2, listOf("c.example"))),
            emptySet(),
            settings(maxBytes = 5),
        )
        assertEquals(listOf("a.example", "b.example"), result.entries)
        assertEquals(bytes("a.example", "b.example"), result.bytes)
        assertEquals(result.bytes, result.requiredBytes)
        assertEquals(1, result.cutByLimit)
        assertEquals(DomainStatus.CUT_BY_LIMIT, result.status("c.example"))
    }

    @Test
    fun fillsExactlyUpToTheLimit() {
        val required = "req.example"
        val fill = listOf("aa.example", "bb.example", "cc.example")
        val limit = bytes(required, "aa.example", "bb.example")
        val result = Selection.compute(
            listOf(SourceList(1, listOf(required)), SourceList(2, fill)),
            emptySet(),
            settings(limit),
        )
        assertEquals(limit, result.bytes)
        assertEquals(listOf("aa.example", "bb.example", "req.example"), result.entries)
        assertEquals(1, result.cutByLimit)
    }

    @Test
    fun oneByteBelowTheLimitLeavesTheLastEntryOut() {
        val limit = bytes("req.example", "aa.example") - 1
        val result = Selection.compute(
            listOf(SourceList(1, listOf("req.example")), SourceList(2, listOf("aa.example"))),
            emptySet(),
            settings(limit),
        )
        assertEquals(listOf("req.example"), result.entries)
    }

    @Test
    fun rankUsesPriorityValueAndFrequency() {
        // Priorities 1..3: value 3 for P1, 2 for P2, 1 for P3.
        val lists = listOf(
            SourceList(1, listOf("top.example")),
            SourceList(2, listOf("p2.example")),
            SourceList(3, listOf("p3often.example", "p3.example")),
            SourceList(3, listOf("p3often.example")),
        )
        val result = Selection.compute(lists, emptySet(), settings(4096L * 1024, a = 10, b = 1))
        val ranks = result.domains.associate { it.domain to it.rank }
        assertEquals(31L, ranks["top.example"])
        assertEquals(21L, ranks["p2.example"])
        assertEquals(12L, ranks["p3often.example"])
        assertEquals(11L, ranks["p3.example"])

        // With room for one extra entry the best rank wins.
        val tight = Selection.compute(lists, emptySet(), settings(bytes("top.example", "p2.example")))
        assertEquals(listOf("p2.example", "top.example"), tight.entries)
    }

    @Test
    fun frequencyCanOutweighPriorityWhenBIsLarge() {
        val lists = listOf(
            SourceList(1, listOf("top.example")),
            SourceList(2, listOf("p2.example")),
            SourceList(3, listOf("many.example")),
            SourceList(3, listOf("many.example")),
            SourceList(3, listOf("many.example")),
        )
        val result = Selection.compute(lists, emptySet(), settings(bytes("top.example", "many.example"), a = 1, b = 10))
        assertEquals(listOf("many.example", "top.example"), result.entries)
    }

    @Test
    fun equalPrioritiesAreAllRequired() {
        val result = Selection.compute(
            listOf(SourceList(1, listOf("a.example")), SourceList(1, listOf("b.example")), SourceList(2, listOf("c.example"))),
            emptySet(),
            settings(maxBytes = 1),
        )
        assertEquals(listOf("a.example", "b.example"), result.entries)
        assertEquals(DomainStatus.REQUIRED, result.status("a.example"))
        assertEquals(DomainStatus.REQUIRED, result.status("b.example"))
    }

    @Test
    fun topPriorityIgnoresEmptyLists() {
        val result = Selection.compute(
            listOf(SourceList(1, emptyList()), SourceList(2, listOf("a.example"))),
            emptySet(),
            settings(maxBytes = 1),
        )
        assertEquals(2, result.topPriority)
        assertEquals(listOf("a.example"), result.entries)
    }

    @Test
    fun exceptionsAreNeverWrittenNotEvenWhenRequired() {
        val result = Selection.compute(
            listOf(SourceList(1, listOf("a.example", "b.example")), SourceList(2, listOf("c.example"))),
            setOf("a.example", "c.example"),
            settings(4096L * 1024),
        )
        assertEquals(listOf("b.example"), result.entries)
        assertEquals(2, result.exceptions)
        assertEquals(DomainStatus.EXCEPTION, result.status("a.example"))
    }

    @Test
    fun subdomainsAreDroppedWhenParentIsWritten() {
        val result = Selection.compute(
            listOf(SourceList(1, listOf("example.com", "ads.example.com", "x.y.example.com", "other.org"))),
            emptySet(),
            settings(4096L * 1024),
        )
        assertEquals(listOf("example.com", "other.org"), result.entries)
        assertEquals(2, result.droppedSubdomains)
        assertEquals(DomainStatus.SUBDOMAIN, result.status("ads.example.com"))
    }

    @Test
    fun subdomainsAreKeptWhenSwitchIsOff() {
        val result = Selection.compute(
            listOf(SourceList(1, listOf("example.com", "ads.example.com"))),
            emptySet(),
            settings(4096L * 1024, omitSubdomains = false),
        )
        assertEquals(listOf("ads.example.com", "example.com"), result.entries)
        assertEquals(0, result.droppedSubdomains)
    }

    @Test
    fun subdomainStaysWhenParentIsAnException() {
        val result = Selection.compute(
            listOf(SourceList(1, listOf("example.com", "ads.example.com"))),
            setOf("example.com"),
            settings(4096L * 1024),
        )
        assertEquals(listOf("ads.example.com"), result.entries)
    }

    @Test
    fun parentReplacesWrittenChildEvenWhenTheFileIsFull() {
        // The child ranks higher and fills the file; the shorter parent still fits in its place.
        val lists = listOf(
            SourceList(1, listOf("top.example")),
            SourceList(2, listOf("ads.example.com")),
            SourceList(3, listOf("example.com")),
        )
        val result = Selection.compute(lists, emptySet(), settings(bytes("top.example", "ads.example.com")))
        assertEquals(listOf("example.com", "top.example"), result.entries)
        assertEquals(DomainStatus.SUBDOMAIN, result.status("ads.example.com"))
        assertEquals(0, result.cutByLimit)
    }

    @Test
    fun subdomainIsWrittenWhenParentDoesNotFit() {
        val lists = listOf(
            SourceList(1, listOf("top.example")),
            SourceList(2, listOf("ads.example.com")),
            SourceList(3, listOf("longer-parent-name.org", "x.longer-parent-name.org")),
        )
        val result = Selection.compute(lists, emptySet(), settings(bytes("top.example", "ads.example.com")))
        assertEquals(listOf("ads.example.com", "top.example"), result.entries)
        assertEquals(DomainStatus.CUT_BY_LIMIT, result.status("longer-parent-name.org"))
        assertEquals(DomainStatus.CUT_BY_LIMIT, result.status("x.longer-parent-name.org"))
    }

    @Test
    fun addingAParentLaterReplacesItsAlreadyWrittenChildren() {
        val lists = listOf(
            SourceList(1, listOf("top.example")),
            SourceList(2, listOf("a.example.com", "b.example.com")),
            SourceList(3, listOf("example.com", "z.example")),
        )
        val result = Selection.compute(lists, emptySet(), settings(4096L * 1024))
        assertEquals(listOf("example.com", "top.example", "z.example"), result.entries)
        assertEquals(bytes("example.com", "top.example", "z.example"), result.bytes)
        assertEquals(DomainStatus.SUBDOMAIN, result.status("a.example.com"))
        assertEquals(2, result.droppedSubdomains)
    }

    @Test
    fun noListsGiveAnEmptyFile() {
        val result = Selection.compute(emptyList(), emptySet(), settings(1024))
        assertTrue(result.entries.isEmpty())
        assertEquals("", result.render())
        assertEquals(null, result.topPriority)
    }

    @Test
    fun largeInputStaysFast() {
        val big = (0 until 300_000).map { "host$it.example${it % 50}.com" }
        val start = System.nanoTime()
        val result = Selection.compute(
            listOf(SourceList(1, big.take(1000)), SourceList(2, big), SourceList(3, big.drop(100_000))),
            emptySet(),
            settings(4096L * 1024),
        )
        val millis = (System.nanoTime() - start) / 1_000_000
        assertTrue(result.bytes <= 4096L * 1024)
        assertTrue("took $millis ms", millis < 5_000)
    }
}
