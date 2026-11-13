package com.mansaketh.chitta.core.intake

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PingContractTest {
    @Test fun sha256IsTheKnownDigestOfAbc() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", PingContract.sha256Hex("abc".toByteArray()))
    }

    @Test fun pinMatchesAnyCaseAndColonsAndRefusesOthers() {
        val cert = "abc".toByteArray()
        val pin = "BA:78:16:BF:8F:01:CF:EA:41:41:40:DE:5D:AE:22:23:B0:03:61:A3:96:17:7A:9C:B4:10:FF:61:F2:00:15:AD"
        assertTrue(PingContract.isPinned(cert, listOf(pin)))
        assertFalse(PingContract.isPinned("abd".toByteArray(), listOf(pin)))
        assertFalse(PingContract.isPinned(cert, emptyList())) // an empty list refuses everybody
        assertFalse(PingContract.isPinned(null)); assertFalse(PingContract.isPinned(ByteArray(0)))
        assertFalse(PingContract.isPinned(cert)) // not Ping's real certificate
    }

    @Test fun everySignerMustBePinned() {
        val pin = listOf(PingContract.sha256Hex("a".toByteArray()))
        assertTrue(PingContract.allPinned(listOf("a".toByteArray()), pin))
        assertFalse(PingContract.allPinned(listOf("a".toByteArray(), "b".toByteArray()), pin))
        assertFalse(PingContract.allPinned(emptyList(), pin)); assertFalse(PingContract.allPinned(null, pin))
    }

    @Test fun logoAddressUsesTheBrandKeyThenTheSenderAndRefusesOddInput() {
        assertEquals("content://com.ping.sms.logos/brand/axisbank", PingContract.logoUri("axisbank", "AX-AXISBK-S"))
        assertEquals("content://com.ping.sms.logos/sender/AX-AXISBK-S", PingContract.logoUri("../x", "AX-AXISBK-S"))
        assertNull(PingContract.logoUri(null, "919800000000")) // a phone number has no brand
        assertNull(PingContract.logoUri(null, "a/b"))
        assertNull(PingContract.logoUri("Bad Key", null))
    }
}
