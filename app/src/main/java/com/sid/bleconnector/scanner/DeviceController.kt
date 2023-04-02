package com.sid.bleconnector.scanner

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ExpandableListView
import android.widget.SimpleExpandableListAdapter
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.sid.bleconnector.BLEService
import com.sid.bleconnector.MainActivity.Companion.TAG
import com.sid.bleconnector.R
import java.util.UUID

class DeviceController : AppCompatActivity() {
	private var mConnectionState: TextView? = null
	private var mDataField: TextView? = null
	private var mDeviceName: String? = null
	private var mDeviceAddress: String? = null
	private var mGattServicesList: ExpandableListView? = null
	private var mNotifyCharacteristic: BluetoothGattCharacteristic? = null

	private var mBLEService: BLEService? = null
	private var mGattCharacteristics: MutableList<ArrayList<BluetoothGattCharacteristic>>? = null
	private var mConnected = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.gatt_services_characteristics)

		mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME).toString()
		mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS).toString()
		findViewById<TextView>(R.id.device_address).text = mDeviceAddress

		mGattServicesList = findViewById(android.R.id.list)
		mGattServicesList!!.setOnChildClickListener(ExpandableListView.OnChildClickListener { _, _, groupPosition, childPosition, _ ->
			if (mGattCharacteristics != null) {
				val characteristic = mGattCharacteristics!![groupPosition][childPosition]
				val charProp = characteristic.properties

				if (charProp or BluetoothGattCharacteristic.PROPERTY_READ > 0) {
					mBLEService!!.setCharacteristicNotification(mNotifyCharacteristic!!, false)
					mBLEService!!.readCharacteristic(characteristic)

					mNotifyCharacteristic = characteristic
				}

				if (charProp or BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) {
					mNotifyCharacteristic = characteristic
					mBLEService!!.setCharacteristicNotification(characteristic, true)
				}

				return@OnChildClickListener true
			}

			false
		})

		mConnectionState = findViewById(R.id.connection_state)
		mDataField = findViewById(R.id.data_value)

		val gattServiceIntent = Intent(this, BLEService::class.java)
		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE)

		onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				if (mConnected) disconnectFromDevice()
				finish()
			}
		})
	}

	override fun onResume() {
		super.onResume()

		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter())
		if (mBLEService != null) connectToDevice()
	}

	fun connectToDevice() = if (mBLEService!!.connect(mDeviceAddress!!)) {
		Log.d(TAG, "Connected")

		mConnected = true
		invalidateOptionsMenu()
		updateConnectionState("Connected")

		displayData(mBLEService!!.getData())
		displayGattServices(mBLEService!!.getSupportedGattServices())
	} else {
		Log.e(TAG, "Unable to connect to device")
		finish()
	}

	private fun disconnectFromDevice() {
		if (mBLEService != null) {
			mBLEService!!.disconnect()
			Log.d(TAG, "Disconnected")

			mConnected = false
			invalidateOptionsMenu()
			updateConnectionState("Disconnected")
		}
	}

	override fun onPause() {
		super.onPause()
		unregisterReceiver(mGattUpdateReceiver)
	}

	override fun onDestroy() {
		super.onDestroy()

		unbindService(mServiceConnection)
		mBLEService!!.close()
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		super.onCreateOptionsMenu(menu)
		menuInflater.inflate(R.menu.gatt_services, menu)

		if (mConnected) {
			menu.findItem(R.id.menu_connect).isVisible = false
			menu.findItem(R.id.menu_disconnect).isVisible = true
		} else {
			menu.findItem(R.id.menu_connect).isVisible = true
			menu.findItem(R.id.menu_disconnect).isVisible = false
		}

		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			R.id.menu_connect -> {
				connectToDevice()
				return true
			}

			R.id.menu_disconnect -> {
				disconnectFromDevice()
				return true
			}
		}

		return super.onOptionsItemSelected(item)
	}

	private val mServiceConnection: ServiceConnection = object : ServiceConnection {
		override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
			mBLEService = (service as BLEService.LocalBinder).service

			if (!mBLEService!!.initialize()) {
				Snackbar.make(
					findViewById(android.R.id.content),
					"Unable to initialize Bluetooth",
					Snackbar.LENGTH_SHORT
				).show()

				Log.e(TAG, "Unable to initialize Bluetooth")
				finish()
			}

			connectToDevice()
		}

		override fun onServiceDisconnected(componentName: ComponentName) {
			mBLEService!!.close()
		}
	}

	private val mGattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent) {

			when (intent.action) {
				"ACTION_GATT_CONNECTED" -> {
					mConnected = true
					updateConnectionState("Connected")
					invalidateOptionsMenu()
				}

				"ACTION_GATT_DISCONNECTED" -> {
					mConnected = false
					updateConnectionState("Disconnected")
					invalidateOptionsMenu()
					clearUI()
				}

				"ACTION_GATT_SERVICES_DISCOVERED" -> displayGattServices(mBLEService!!.getSupportedGattServices())
				"ACTION_DATA_AVAILABLE" -> {
					displayData(intent.getStringExtra("EXTRA_DATA"))
				}
			}
		}
	}

	fun clearUI() {
		mGattServicesList!!.setAdapter(null as SimpleExpandableListAdapter?)
		mDataField!!.text = buildString { append("No data") }
	}

	fun updateConnectionState(text: String) = runOnUiThread { mConnectionState!!.text = text }

	fun displayData(data: String?) {
		if (data != null) mDataField!!.text = data
	}

	fun displayGattServices(gattServices: List<BluetoothGattService>?) {
		if (gattServices == null) return
		var uuid: UUID

		val gattServiceData = ArrayList<HashMap<String, String?>>()
		val gattCharacteristicData = ArrayList<ArrayList<HashMap<String, String?>>>()
		mGattCharacteristics = ArrayList()

		for (gattService in gattServices) {
			val currentServiceData = HashMap<String, String?>()
			uuid = gattService.uuid

			currentServiceData["NAME"] = "Bluetooth Service"
			currentServiceData["UUID"] = uuid.toString()
			gattServiceData.add(currentServiceData)

			val gattCharacteristicGroupData = ArrayList<HashMap<String, String?>>()
			val gattCharacteristics = gattService.characteristics
			mGattCharacteristics!!.add(gattCharacteristics as ArrayList<BluetoothGattCharacteristic>)

			for (gattCharacteristic in gattCharacteristics) {
				val currentCharaData = HashMap<String, String?>()
				uuid = gattCharacteristic.uuid

				currentCharaData["NAME"] = "Bluetooth Service"
				currentCharaData["UUID"] = uuid.toString()

				gattCharacteristicGroupData.add(currentCharaData)
			}

			gattCharacteristicData.add(gattCharacteristicGroupData)
		}

		val gattServiceAdapter = SimpleExpandableListAdapter(
			this,
			gattServiceData,
			android.R.layout.simple_expandable_list_item_2,
			arrayOf("NAME", "UUID"),
			intArrayOf(android.R.id.text1, android.R.id.text2),
			gattCharacteristicData,
			android.R.layout.simple_expandable_list_item_2,
			arrayOf("NAME", "UUID"),
			intArrayOf(android.R.id.text1, android.R.id.text2)
		)

		mGattServicesList!!.setAdapter(gattServiceAdapter)
	}

	private fun makeGattUpdateIntentFilter(): IntentFilter {
		val intentFilter = IntentFilter()

		intentFilter.addAction("ACTION_GATT_CONNECTED")
		intentFilter.addAction("ACTION_GATT_DISCONNECTED")
		intentFilter.addAction("ACTION_GATT_SERVICES_DISCOVERED")
		intentFilter.addAction("ACTION_DATA_AVAILABLE")

		return intentFilter
	}

	companion object {
		const val EXTRAS_DEVICE_NAME = "DEVICE_NAME"
		const val EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS"
	}
}
