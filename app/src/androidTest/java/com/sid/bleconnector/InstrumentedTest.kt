package com.sid.bleconnector

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Test

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class InstrumentedTest {
	@Test
	fun useAppContext() = Assert.assertEquals(
		"com.sid.bleconnector",
		InstrumentationRegistry.getInstrumentation().targetContext.packageName
	)

	@Test
	fun testBLE() {
		BLEService(InstrumentationRegistry.getInstrumentation().targetContext).apply {
			connect("00:F6:20:3F:40:9B")

			val intent = Intent()
			val data = byteArrayOf(
				0x01,
				0x02,
				0x03,
				0x04,
				0x05,
				0x06,
				0x07,
				0x08,
				0x09,
				0x10,
				0x0A,
				0x0B,
				0x0C,
				0x0D,
				0x0E,
				0x0F
			)

			intent.putExtra("data", data)
			sendBroadcast(intent)

			disconnect()
		}
	}
}
