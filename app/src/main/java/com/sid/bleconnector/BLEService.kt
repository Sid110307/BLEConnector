package com.sid.bleconnector

import android.Manifest
import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.sid.bleconnector.DeviceController.Companion.TAG
import java.util.*

class BLEService : Service() {
	private lateinit var mBluetoothManager: BluetoothManager
	private lateinit var mBluetoothAdapter: BluetoothAdapter
	private lateinit var mBluetoothDeviceAddress: String
	private lateinit var mBluetoothGatt: BluetoothGatt

	private var mConnectionState = STATE_DISCONNECTED

	private val mGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
		override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
			val intentAction: String

			when (newState) {
				BluetoothProfile.STATE_CONNECTED -> {
					intentAction = "ACTION_GATT_CONNECTED"
					mConnectionState = STATE_CONNECTED

					broadcastUpdate(intentAction)
					if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
						startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
						return
					}
					mBluetoothGatt.discoverServices()
				}
				BluetoothProfile.STATE_DISCONNECTED -> {
					intentAction = "ACTION_GATT_DISCONNECTED"
					mConnectionState = STATE_DISCONNECTED

					broadcastUpdate(intentAction)
				}
			}
		}

		override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
			if (status == BluetoothGatt.GATT_SUCCESS) broadcastUpdate(
				"ACTION_GATT_SERVICES_DISCOVERED"
			)
		}

		override fun onCharacteristicRead(
			gatt: BluetoothGatt,
			characteristic: BluetoothGattCharacteristic,
			status: Int
		) {
			if (status == BluetoothGatt.GATT_SUCCESS) broadcastUpdate(
				"ACTION_DATA_AVAILABLE",
				characteristic
			)
		}

		override fun onCharacteristicChanged(
			gatt: BluetoothGatt,
			characteristic: BluetoothGattCharacteristic
		) = broadcastUpdate("ACTION_DATA_AVAILABLE", characteristic)
	}

	fun broadcastUpdate(action: String) = sendBroadcast(Intent(action))

	fun broadcastUpdate(
		@Suppress("SameParameterValue") action: String,
		characteristic: BluetoothGattCharacteristic
	) {
		val intent = Intent(action)

		// HACK: Parse data
		val data = characteristic.value
		if (data != null && data.isNotEmpty()) {
			val hexString = StringBuilder(data.size * 2)
			for (b in data) {
				hexString.append(String.format("%02X", b))
			}
			intent.putExtra("EXTRA_DATA", hexString.toString())
		}

		sendBroadcast(intent)
	}

	inner class LocalBinder : Binder() {
		val service: BLEService
			get() = this@BLEService
	}

	override fun onBind(intent: Intent?): IBinder = mBinder

	override fun onUnbind(intent: Intent?): Boolean {
		close()
		return super.onUnbind(intent)
	}

	private val mBinder: IBinder = LocalBinder()

	fun initialize(): Boolean {
		if (getSystemService(Context.BLUETOOTH_SERVICE) == null) {
			Toast.makeText(
				this,
				"Unable to initialize Bluetooth Manager.",
				Toast.LENGTH_SHORT
			).show()

			Log.e(TAG, "Unable to initialize Bluetooth Manager.")
			return false
		}

		mBluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

		if (mBluetoothManager.adapter == null) {
			Toast.makeText(
				this,
				"Unable to obtain a BluetoothAdapter.",
				Toast.LENGTH_SHORT
			).show()

			Log.e(TAG, "Unable to obtain a BluetoothAdapter.")
			return false
		}

		mBluetoothAdapter = mBluetoothManager.adapter
		return true
	}

	fun connect(address: String?): Boolean {
		if (address == null) {
			Toast.makeText(
				this,
				"Unspecified address.",
				Toast.LENGTH_SHORT
			).show()

			Log.e(TAG, "Unspecified address.")
			return false
		}

		if (ActivityCompat.checkSelfPermission(
				this,
				Manifest.permission.BLUETOOTH_CONNECT
			) != PackageManager.PERMISSION_GRANTED
		) return false

		if (mBluetoothDeviceAddress.isNotEmpty() && address == mBluetoothDeviceAddress && mBluetoothGatt.connect()) {
			Toast.makeText(
				this,
				"Trying to connect...",
				Toast.LENGTH_SHORT
			).show()

			return if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
				startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
				false
			} else if (mBluetoothGatt.connect()) {
				mConnectionState = STATE_CONNECTING
				true
			} else {
				Toast.makeText(this, "Unable to connect to GATT server.", Toast.LENGTH_LONG).show()
				Log.e(TAG, "Unable to connect to GATT server.")

				false
			}
		}

		val device = mBluetoothAdapter.getRemoteDevice(address)
		if (device == null) {
			Toast.makeText(
				this,
				"Device not found.  Unable to connect.",
				Toast.LENGTH_SHORT
			).show()

			Log.e(TAG, "Device not found.  Unable to connect.")
			return false
		}

		mBluetoothGatt = device.connectGatt(this, true, mGattCallback)
		Toast.makeText(
			this,
			"Trying to connect...",
			Toast.LENGTH_SHORT
		).show()

		mBluetoothDeviceAddress = address
		mConnectionState = STATE_CONNECTING
		return true
	}

	fun disconnect() {
		if (mBluetoothManager.adapter == null || !mBluetoothManager.adapter.isEnabled) {
			Toast.makeText(
				this,
				"Bluetooth Adapter is not initialized",
				Toast.LENGTH_SHORT
			).show()

			Log.e(TAG, "Bluetooth Adapter is not initialized")
			return
		}

		if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
			startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
			return
		}

		mBluetoothGatt.disconnect()
	}

	fun close() {
		if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
			val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
			intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
			startActivity(intent)

			return
		}

		mBluetoothGatt.close()
	}

	fun readCharacteristic(characteristic: BluetoothGattCharacteristic?) {
		if (mBluetoothManager.adapter == null || !mBluetoothManager.adapter.isEnabled) {
			Toast.makeText(
				this,
				"Bluetooth Adapter is not initialized",
				Toast.LENGTH_SHORT
			).show()

			Log.e(TAG, "Bluetooth Adapter is not initialized")
			return
		}

		if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
			startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
			return
		}

		mBluetoothGatt.readCharacteristic(characteristic)
	}

	fun setCharacteristicNotification(
		characteristic: BluetoothGattCharacteristic,
		enabled: Boolean
	) {
		if (mBluetoothManager.adapter == null || !mBluetoothManager.adapter.isEnabled) {
			Toast.makeText(
				this,
				"Bluetooth Adapter is not initialized",
				Toast.LENGTH_SHORT
			).show()

			Log.e(TAG, "Bluetooth Adapter is not initialized")
			return
		}

		if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
			startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
			return
		}

		mBluetoothGatt.setCharacteristicNotification(characteristic, enabled)

		val descriptor = characteristic.getDescriptor(BT_UUID)
		descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
		mBluetoothGatt.writeDescriptor(descriptor)
	}

	fun getSupportedGattServices(): List<BluetoothGattService?>? = mBluetoothGatt.services

	companion object {
		private const val STATE_DISCONNECTED = 0
		private const val STATE_CONNECTING = 1
		private const val STATE_CONNECTED = 2

		val BT_UUID: UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
	}
}
