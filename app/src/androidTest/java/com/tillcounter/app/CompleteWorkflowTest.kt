package com.tillcounter.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompleteWorkflowTest {
    private lateinit var device: UiDevice
    private val pkg = "com.tillcounter.app"

    @Before fun launchClean() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)!!.apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        waitText("Store Charges", 12_000)
    }

    private fun waitText(text: String, timeout: Long = 4_000): UiObject2 =
        device.wait(Until.findObject(By.text(text)), timeout)
            ?: throw AssertionError("Missing UI text: $text")

    private fun waitDesc(desc: String, timeout: Long = 4_000): UiObject2 =
        device.wait(Until.findObject(By.desc(desc)), timeout)
            ?: throw AssertionError("Missing UI description: $desc")

    private fun tap(text: String) {
        waitText(text).click()
        device.waitForIdle(2_000)
        assertForeground()
    }

    private fun tapDesc(desc: String) {
        waitDesc(desc).click()
        device.waitForIdle(2_000)
        assertForeground()
    }

    private fun assertForeground() {
        assertTrue("Till Counter is not foreground", device.hasObject(By.pkg(pkg)))
    }

    private fun assertText(text: String) { waitText(text) }

    private fun digits(vararg keys: String) = keys.forEach { tap(it) }

    private fun scrollDown() {
        device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.8f)
        device.waitForIdle(1_000)
    }

    @Test fun completeWorkflowAndroid16() {
        // Settings and persistent base till.
        tapDesc("Settings"); assertText("SETTINGS")
        listOf("Store Charges","Gift Certificates","Vendor Coupons","Checks","Loans").forEach { name ->
            val cb = waitText(name)
            if (!cb.isChecked) cb.click()
        }
        assertTrue("Settings must not depend on Android EditText entry", !device.hasObject(By.clazz("android.widget.EditText")))
        digits("3","00",".","0","0")
        assertText("\$300.00")
        tap("DONE"); assertText("Store Charges")

        // Money editing, commit, remove, backspace, BACK and pending NEXT.
        // Add enough rows to overflow the history viewport. NEXT must remain visible without page scrolling.
        tap("9"); tap("C"); assertText("\$0.00")
        listOf("1","2","3","4","5").forEach { amount -> tap(amount); tap("+") }
        assertText("SUBTOTAL"); assertText("\$15.00")
        assertTrue("NEXT pushed off-screen by amount history", device.hasObject(By.text("NEXT")))
        repeat(5) { tap("Remove") }
        digits("1",".","0","0"); tap("+"); assertText("\$1.00"); tap("Remove"); assertText("\$0.00")
        digits("5","4",".","2","4"); tap("⌫"); tap("3"); assertText("\$54.23")
        tap("NEXT"); assertText("Gift Certificates")
        digits("1","0",".","0","0"); tap("NEXT"); assertText("Vendor Coupons")
        tap("BACK"); assertText("Gift Certificates"); assertText("\$10.00")
        tap("NEXT"); assertText("Vendor Coupons")
        digits("2",".","5","0"); tap("NEXT"); assertText("Checks")
        digits("3",".","7","5"); tap("NEXT"); assertText("Loans")
        digits("4",".","0","0"); tap("NEXT: CASH"); assertText("\$100 bills")

        // Every cash denomination receives nonzero data.
        listOf("1","2","3","4","5","6","7").forEach { n -> tap(n); tap("NEXT"); assertText("Cash") }
        listOf("\$1 coins","Half dollars","Quarters","Dimes","Nickels").forEach { label ->
            assertText(label); tap("1"); tap("ROLLS: 0"); tap("1"); tap("NEXT"); assertText("Cash")
        }
        assertText("Pennies")
        tap("9"); tap("C"); tap("1"); tap("3"); tap("⌫"); tap("2")
        tap("ROLLS: 0"); tap("1"); tap("FINISH"); assertText("Till Summary")

        // TOTAL is deliberately cash-only: 398.52; base 300 => drop 98.52.
        // Non-cash categories remain separately visible and excluded from TOTAL/drop.
        if (!device.hasObject(By.text("\$398.52"))) scrollDown()
        assertText("\$398.52"); assertText("BASE TILL  \$300.00"); assertText("DROP  \$98.52")
        listOf("\$54.23","\$10.00","\$2.50","\$3.75","\$4.00").forEach { assertText(it) }

        // Summary BACK preserves final denomination state.
        tap("BACK"); assertText("Pennies"); assertText("LOOSE: 12"); assertText("ROLLS: 1")
        tap("FINISH"); assertText("Till Summary")

        // NEW COUNT clears transaction state while Settings remain persistent in the app session.
        tap("NEW COUNT"); assertText("Store Charges"); assertText("\$0.00")
        tapDesc("Settings"); assertText("SETTINGS")
        assertText("\$300.00")
        assertTrue("Settings unexpectedly exposes Android EditText after NEW COUNT", !device.hasObject(By.clazz("android.widget.EditText")))
        listOf("Store Charges","Gift Certificates","Vendor Coupons","Checks","Loans").forEach { name ->
            assertTrue("\$name unexpectedly disabled", waitText(name).isChecked)
        }
        tap("DONE"); assertText("Store Charges")
        assertForeground()
    }
}
