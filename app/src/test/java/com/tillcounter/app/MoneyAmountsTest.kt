package com.tillcounter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyAmountsTest {
    @Test fun signedWholeAndFractionalEntries() {
        assertEquals(-250L, MoneyAmounts.parseCents("-2.50"))
        assertEquals(-50L, MoneyAmounts.parseCents("-0.5"))
        assertEquals(-1200L, MoneyAmounts.parseCents("-12"))
        assertEquals(125L, MoneyAmounts.parseCents("1.25"))
        assertEquals(0L, MoneyAmounts.parseCents("-0.00"))
    }

    @Test fun signedDisplayPlacesMinusBeforeDollarSymbol() {
        assertEquals("-$2.50", MoneyAmounts.display("-2.50"))
        assertEquals("-$0.00", MoneyAmounts.display("-"))
        assertEquals("$0.00", MoneyAmounts.display(""))
    }

    @Test fun malformedAndOverflowingInputIsRejected() {
        listOf("", "-", "--2", "2-", "-1.234", "1..2", "a1", "9223372036854775808")
            .forEach { assertNull("Unexpectedly parsed $it", MoneyAmounts.parseCents(it)) }
    }
}
