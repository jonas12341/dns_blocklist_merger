package de.jonas.dnsblocklistmerger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlocklistParserTest {

    private val oisdBig = """
        [Adblock Plus]
        ! Version: 202611160506
        ! Title: oisd big
        ! Syntax: Adblock Plus Filter List

        ||0--foodwarez.da.ru^
        ||0-0-2day.vercel.app^
        ||0.myikas.com^
        ||000.gaysexe.free.fr^
    """.trimIndent()

    private val hageziPro = """
        [Adblock Plus]
        ! Title: HaGeZi's Multi PRO - extended protection (recommended)
        ! Syntax: AdBlock
        !
        ||telemetry.001-studio.com^
        ||ad.001zb.com^
        ||analytics.004gmbh.de^
        ||a8clk.011330.jp^
    """.trimIndent()

    private val stevenBlack = """
        # Title: StevenBlack/hosts
        #
        # ===============================================================
        127.0.0.1 localhost
        127.0.0.1 localhost.localdomain
        127.0.0.1 local
        255.255.255.255 broadcasthost
        ::1 localhost
        ::1 ip6-localhost
        fe80::1%lo0 localhost
        ff00::0 ip6-localnet
        ff02::1 ip6-allnodes
        0.0.0.0 0.0.0.0
        # Start StevenBlack
        0.0.0.0 ad-assets.futurecdn.net
        0.0.0.0 ck.getcookiestxt.com
        0.0.0.0 www.2no.co # trailing comment
        0.0.0.0 logitechlogitechglobal.112.2o7.net
    """.trimIndent()

    @Test
    fun parsesOisdBigExcerpt() {
        val result = BlocklistParser.parse(oisdBig)
        assertEquals(ListFormat.ADBLOCK, result.format)
        assertEquals(
            setOf("0--foodwarez.da.ru", "0-0-2day.vercel.app", "0.myikas.com", "000.gaysexe.free.fr"),
            result.domains,
        )
    }

    @Test
    fun parsesHageziProExcerpt() {
        val result = BlocklistParser.parse(hageziPro)
        assertEquals(ListFormat.ADBLOCK, result.format)
        assertEquals(
            setOf("telemetry.001-studio.com", "ad.001zb.com", "analytics.004gmbh.de", "a8clk.011330.jp"),
            result.domains,
        )
    }

    @Test
    fun parsesStevenBlackExcerptAndDropsLocalEntries() {
        val result = BlocklistParser.parse(stevenBlack)
        assertEquals(ListFormat.HOSTS, result.format)
        assertEquals(
            setOf("ad-assets.futurecdn.net", "ck.getcookiestxt.com", "www.2no.co", "logitechlogitechglobal.112.2o7.net"),
            result.domains,
        )
    }

    @Test
    fun parsesPlainDomainList() {
        val result = BlocklistParser.parse(
            """
            # my list
            Example.COM
            https://tracker.example.org:8443/path?x=1
            ads.example.net.

            *.wildcard.example
            """.trimIndent(),
        )
        assertEquals(ListFormat.DOMAINS, result.format)
        assertEquals(setOf("example.com", "tracker.example.org", "ads.example.net"), result.domains)
    }

    @Test
    fun skipsAdblockRulesThatAreNotWholeDomains() {
        val result = BlocklistParser.parse(
            """
            ||good.example^
            ||good2.example^|
            ||path.example/ads^
            ||opts.example^${'$'}third-party
            ||wild*.example^
            @@||allowed.example^
            example.com##.banner
            /regex[0-9]+/
            """.trimIndent(),
        )
        assertEquals(ListFormat.ADBLOCK, result.format)
        assertEquals(setOf("good.example", "good2.example"), result.domains)
    }

    @Test
    fun hostsLineWithSeveralNamesKeepsAll() {
        val result = BlocklistParser.parse("0.0.0.0 a.example b.example\t c.example")
        assertEquals(setOf("a.example", "b.example", "c.example"), result.domains)
    }

    @Test
    fun deduplicatesDomains() {
        val result = BlocklistParser.parse("||a.example^\n||A.example^\n||a.example.^")
        assertEquals(setOf("a.example"), result.domains)
    }

    @Test
    fun emptyInputIsAnEmptyDomainList() {
        val result = BlocklistParser.parse("! only comments\n\n")
        assertEquals(ListFormat.DOMAINS, result.format)
        assertEquals(emptySet<String>(), result.domains)
    }

    @Test
    fun normalizerRejectsInvalidNames() {
        val bad = listOf(
            "", "localhost", "localhost.localdomain", "0.0.0.0", "192.168.178.1", "[::1]", "::1",
            "-bad.example", "bad-.example", "a..example", "no_tld", "under score.example", "x.123",
        )
        for (name in bad) assertNull(name, DomainNormalizer.normalize(name))
    }

    @Test
    fun normalizerStripsSchemePortPathAndTrailingDot() {
        assertEquals("ads.example.com", DomainNormalizer.normalize("HTTP://Ads.Example.com.:80/x"))
    }

    @Test
    fun normalizerConvertsUnicodeToPunycode() {
        assertEquals("xn--mnchen-3ya.de", DomainNormalizer.normalize("münchen.de"))
    }
}
