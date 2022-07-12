package com.sid.bleconnector

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowBluetoothDevice

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(RobolectricTestRunner::class)
class UnitTest {
	@Test
	fun checkAppContext() = Assert.assertEquals(
		"com.sid.bleconnector",
		InstrumentationRegistry.getInstrumentation().targetContext.packageName
	)

	@Test
	fun testBLE() {
		BLEService(InstrumentationRegistry.getInstrumentation().targetContext).apply {
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
			).toString()

			// TODO: Migrate to new ShadowBluetoothDevice function
//			connect(ShadowBluetoothDevice.newInstance(ByteArray(6).also {
//				Random.nextBytes(it)
//				it[0] = (it[0] and 254.toByte())
//			}.joinToString(":") { String.format("%02X", it) }).address)
			connect(ShadowBluetoothDevice.newInstance("27:62:86:20:15:50").address)
			println("Sending data: $data...")

			broadcastUpdate(data)
			disconnect()
			println("Disconnected.")
		}
	}
}
