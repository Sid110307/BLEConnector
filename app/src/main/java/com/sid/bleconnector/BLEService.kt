package com.sid.bleconnector

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import com.sid.bleconnector.MainActivity.Companion.TAG
import com.sid.bleconnector.MainActivity.Companion.notGrantedPermissions
import java.util.*

class BLEService() : Service() {
	private var context: Context? = null

	constructor(ctx: Context?) : this() {
		context = ctx
	}

	private var mBluetoothManager: BluetoothManager? = null
	private var mBluetoothAdapter: BluetoothAdapter? = null
	private var mBluetoothDeviceAddress: String? = null
	private var mBluetoothGatt: BluetoothGatt? = null

	private var mConnectionState = STATE_DISCONNECTED

	private val mGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
		override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
			val intentAction: String

			@Suppress("MissingPermission")
			when (newState) {
				BluetoothProfile.STATE_CONNECTED -> {
					intentAction = "ACTION_GATT_CONNECTED"
					mConnectionState = STATE_CONNECTED

					broadcastUpdate(intentAction)
					if (notGrantedPermissions(
							(context ?: this) as Context,
							Manifest.permission.BLUETOOTH_CONNECT
						)
					) {
						startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
						return
					}

					mBluetoothGatt!!.discoverServices()
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

	fun broadcastUpdate(action: String) = (context ?: this).sendBroadcast(Intent(action))

	fun broadcastUpdate(
		@Suppress("SameParameterValue") action: String,
		characteristic: BluetoothGattCharacteristic
	) {
		val intent = Intent(action)

		// HACK: Parse data
		val data = characteristic.value
		if (data != null && data.isNotEmpty()) {
			val hexString = StringBuilder(data.size * 2)
			for (b in data) hexString.append(String.format("%02X", b))
			intent.putExtra("EXTRA_DATA", hexString.toString())
		}

		(context ?: this).sendBroadcast(intent)
	}

	fun readBroadcastData(intent: Intent): String? = intent.getStringExtra("EXTRA_DATA")

	inner class LocalBinder : Binder() {
		val service: BLEService
			get() = this@BLEService
	}

	override fun onBind(intent: Intent): IBinder = mBinder
	override fun onUnbind(intent: Intent): Boolean {
		close()
		return super.onUnbind(intent)
	}

	private val mBinder: IBinder = LocalBinder()

	fun initialize(): Boolean {
		if (context?.getSystemService(Context.BLUETOOTH_SERVICE) == null) {
			Toast.makeText(
				(context ?: this),
				"Unable to initialize Bluetooth Manager.",
				Toast.LENGTH_SHORT
			).show()

			Log.e(TAG, "Unable to initialize Bluetooth Manager.")
			return false
		}

		mBluetoothManager = context?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
		if (mBluetoothManager!!.adapter == null) {
			Toast.makeText(
				(context ?: this),
				"Unable to obtain a BluetoothAdapter.",
				Toast.LENGTH_SHORT
			).show()

			Log.e(TAG, "Unable to obtain a BluetoothAdapter.")
			return false
		}

		mBluetoothAdapter = mBluetoothManager!!.adapter
		return true
	}

	@SuppressLint("MissingPermission")
	fun connect(address: String): Boolean {
		if (notGrantedPermissions(
				(context ?: this),
				Manifest.permission.BLUETOOTH_CONNECT
			)
		) return false

		if (mBluetoothDeviceAddress != null && address == mBluetoothDeviceAddress) {
			Toast.makeText(
				(context ?: this),
				"Trying to connect...",
				Toast.LENGTH_SHORT
			).show()

			Log.d(TAG, "Trying to connect before checking permissions...")

			return if (notGrantedPermissions(
					(context ?: this),
					Manifest.permission.BLUETOOTH_CONNECT
				)
			) {
				startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
				false
			} else if (mBluetoothGatt != null && mBluetoothGatt!!.connect() && mBluetoothGatt!!.discoverServices()) {
				mConnectionState = STATE_CONNECTING
				Log.d(TAG, "Connecting to $address...")
				true
			} else {
				Toast.makeText(
					(context ?: this),
					"Unable to connect to GATT server.",
					Toast.LENGTH_LONG
				)
					.show()
				Log.e(TAG, "Unable to connect to GATT server.")

				false
			}
		}

		val device = mBluetoothAdapter!!.getRemoteDevice(address)
		if (device == null) {
			Toast.makeText(
				(context ?: this),
				"Device not found. Unable to connect.",
				Toast.LENGTH_SHORT
			).show()

			Log.e(TAG, "Device not found. Unable to connect.")
			return false
		}

		mBluetoothGatt = device.connectGatt(context, true, mGattCallback)
		Toast.makeText(
			(context ?: this),
			"Trying to connect...",
			Toast.LENGTH_SHORT
		).show()

		Log.e(TAG, "Trying to connect after GATT connection...")

		mBluetoothDeviceAddress = address
		mConnectionState = STATE_CONNECTING
		return true
	}

	@SuppressLint("MissingPermission")
	fun disconnect() {
		if (mBluetoothManager == null || mBluetoothManager!!.adapter == null || !mBluetoothManager!!.adapter.isEnabled) {
			Toast.makeText(
				(context ?: this),
				"Bluetooth Adapter is not initialized",
				Toast.LENGTH_SHORT
			).show()

			Log.e(TAG, "Bluetooth Adapter is not initialized")
			return
		}

		if (notGrantedPermissions((context ?: this), Manifest.permission.BLUETOOTH_CONNECT)) {
			startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
			return
		}

		mBluetoothGatt!!.disconnect()
	}

	@SuppressLint("MissingPermission")
	fun close() {
		if (notGrantedPermissions((context ?: this), Manifest.permission.BLUETOOTH_CONNECT)) {
			val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
			intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
			startActivity(intent)

			return
		}

		mBluetoothGatt!!.close()
	}

	@SuppressLint("MissingPermission")
	fun readCharacteristic(characteristic: BluetoothGattCharacteristic) {
		if (mBluetoothManager!!.adapter == null || !mBluetoothManager!!.adapter.isEnabled) {
			Toast.makeText(
				(context ?: this),
				"Bluetooth Adapter is not initialized",
				Toast.LENGTH_SHORT
			).show()

			Log.e(TAG, "Bluetooth Adapter is not initialized")
			return
		}

		if (notGrantedPermissions((context ?: this), Manifest.permission.BLUETOOTH_CONNECT)) {
			startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
			return
		}

		mBluetoothGatt!!.readCharacteristic(characteristic)
	}

	@SuppressLint("MissingPermission")
	fun setCharacteristicNotification(
		characteristic: BluetoothGattCharacteristic,
		enabled: Boolean
	) {
		if (mBluetoothManager!!.adapter == null || !mBluetoothManager!!.adapter.isEnabled) {
			Toast.makeText(
				(context ?: this),
				"Bluetooth Adapter is not initialized",
				Toast.LENGTH_SHORT
			).show()

			Log.e(TAG, "Bluetooth Adapter is not initialized")
			return
		}

		if (notGrantedPermissions((context ?: this), Manifest.permission.BLUETOOTH_CONNECT)) {
			startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
			return
		}

		mBluetoothGatt!!.setCharacteristicNotification(characteristic, enabled)

		val descriptor = characteristic.getDescriptor(BT_UUID)
		descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
		mBluetoothGatt!!.writeDescriptor(descriptor)
	}

	fun getSupportedGattServices(): List<BluetoothGattService>? = mBluetoothGatt!!.services
	fun getData(): String? = Intent().getStringExtra("EXTRA_DATA")

	companion object {
		private const val STATE_DISCONNECTED = 0
		private const val STATE_CONNECTING = 1
		private const val STATE_CONNECTED = 2

		val BT_UUID: UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
	}
}
