package com.sid.bleconnector

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ExpandableListView
import android.widget.SimpleExpandableListAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class DeviceController : AppCompatActivity() {
	private lateinit var mConnectionState: TextView
	private lateinit var mDataField: TextView
	private lateinit var mDeviceName: String
	private lateinit var mDeviceAddress: String
	private lateinit var mGattServicesList: ExpandableListView
	private lateinit var mNotifyCharacteristic: BluetoothGattCharacteristic

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
		mGattServicesList.emptyView = findViewById(android.R.id.empty)
		mGattServicesList.setOnChildClickListener(ExpandableListView.OnChildClickListener { _, _, groupPosition, childPosition, _ ->
			if (mGattCharacteristics != null) {
				val characteristic = mGattCharacteristics!![groupPosition][childPosition]
				val charProp = characteristic.properties

				if (charProp or BluetoothGattCharacteristic.PROPERTY_READ > 0) {
					mBLEService!!.setCharacteristicNotification(mNotifyCharacteristic, false)
					mBLEService!!.readCharacteristic(characteristic)
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
	}

	override fun onResume() {
		super.onResume()

		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter())
		mBLEService?.connect(mDeviceAddress)
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
				mBLEService!!.connect(mDeviceAddress)
				return true
			}
			R.id.menu_disconnect -> {
				mBLEService!!.disconnect()
				return true
			}
		}

		return super.onOptionsItemSelected(item)
	}

	private val mServiceConnection: ServiceConnection = object : ServiceConnection {
		override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
			mBLEService = (service as BLEService.LocalBinder).service

			if (!mBLEService!!.initialize()) {
				Toast.makeText(
					this@DeviceController,
					"Unable to initialize Bluetooth",
					Toast.LENGTH_SHORT
				).show()

				Log.e(TAG, "Unable to initialize Bluetooth")
				finish()
			}

			mBLEService!!.connect(mDeviceAddress)
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
				"ACTION_GATT_SERVICES_DISCOVERED" -> {
					@Suppress("UNCHECKED_CAST")
					displayGattServices(mBLEService!!.getSupportedGattServices() as List<BluetoothGattService>?)
				}
				"ACTION_DATA_AVAILABLE" -> {
					displayData(intent.getStringExtra("EXTRA_DATA"))
				}
			}
		}
	}

	fun clearUI() {
		mGattServicesList.setAdapter(null as SimpleExpandableListAdapter?)
		mDataField.text = buildString { append("No data") }
	}

	fun updateConnectionState(text: String) =
		runOnUiThread { mConnectionState.text = text }

	fun displayData(data: String?) {
		if (data != null) mDataField.text = data
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

			currentServiceData["NAME"] = GattAttributes.lookup(uuid, "Unknown Service")
			currentServiceData["UUID"] = uuid.toString()
			gattServiceData.add(currentServiceData)

			val gattCharacteristicGroupData = ArrayList<HashMap<String, String?>>()
			val gattCharacteristics = gattService.characteristics
			mGattCharacteristics!!.add(gattCharacteristics as ArrayList<BluetoothGattCharacteristic>)

			for (gattCharacteristic in gattCharacteristics) {
				val currentCharaData = HashMap<String, String?>()
				uuid = gattCharacteristic.uuid

				currentCharaData["NAME"] = GattAttributes.lookup(uuid, "Unknown Characteristic")
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

		mGattServicesList.setAdapter(gattServiceAdapter)
	}

	private fun makeGattUpdateIntentFilter(): IntentFilter {
		val intentFilter = IntentFilter()

		intentFilter.addAction("ACTION_GATT_CONNECTED")
		intentFilter.addAction("ACTION_GATT_DISCONNECTED")
		intentFilter.addAction("ACTION_GATT_SERVICES_DISCOVERED")
		intentFilter.addAction("ACTION_DATA_AVAILABLE")

		return intentFilter
	}

	class GattAttributes {
		companion object {
			private val attributes = HashMap<UUID, String>()

			init {
				attributes[UUID.fromString(BLEService.BT_UUID.toString())] = "Bluetooth Service"
			}

			fun lookup(uuid: UUID, defaultName: String): String {
				val name = attributes[uuid]
				return name ?: defaultName
			}
		}
	}

	companion object {
		const val EXTRAS_DEVICE_NAME = "DEVICE_NAME"
		const val EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS"

		const val TAG = "DeviceController"
	}
}
