// TODO: Migrate to new BluetoothLeScanner and remove deprecated code:
@file:Suppress("DEPRECATION")

package com.sid.bleconnector

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.checkSelfPermission
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sid.bleconnector.DeviceController.Companion.TAG

class MainActivity : AppCompatActivity() {
	private lateinit var mLeDeviceListAdapter: LeDeviceListAdapter
	private lateinit var bluetoothAdapter: BluetoothAdapter

	private var mScanning = false

	@RequiresApi(Build.VERSION_CODES.S)
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
			if (it.containsValue(false)) showPermissionDialog()
		}.launch(
			arrayOf(
				Manifest.permission.BLUETOOTH,
				Manifest.permission.BLUETOOTH_ADMIN,
				Manifest.permission.BLUETOOTH_SCAN,
				Manifest.permission.BLUETOOTH_CONNECT,
				Manifest.permission.ACCESS_FINE_LOCATION
			)
		)

		if (checkSelfPermission(
				this,
				Manifest.permission.BLUETOOTH_CONNECT
			) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(
				this,
				Manifest.permission.ACCESS_FINE_LOCATION
			) != PackageManager.PERMISSION_GRANTED
		) {
			requestPermissions(
				this,
				arrayOf(
					Manifest.permission.BLUETOOTH_CONNECT,
					Manifest.permission.ACCESS_FINE_LOCATION
				),
				1
			)

			return
		}

		if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(
				this,
				"BLE is not supported on this device",
				Toast.LENGTH_SHORT
			).show()

			Log.e(TAG, "BLE is not supported on this device")
			finish()
		}

		if (getSystemService(Context.BLUETOOTH_SERVICE) == null) {
			Toast.makeText(
				this,
				"Bluetooth is not supported on this device",
				Toast.LENGTH_SHORT
			).show()

			Log.e(TAG, "Bluetooth is not supported on this device")
			finish()
		}

		bluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

		findViewById<ListView>(android.R.id.list).emptyView = findViewById(R.id.empty)
		findViewById<SwipeRefreshLayout>(R.id.main_layout).also {
			it.setColorSchemeResources(
				R.color.teal_200,
				R.color.teal_700,
				R.color.purple_200,
				R.color.purple_500,
				R.color.purple_700
			)
			it.setOnRefreshListener {
				scanLeDevice(!mScanning)
				it.isRefreshing = false
			}
		}

		// BUG: https://issue.com/issues/5270086/android-bluetooth-le-scanner-does-not-work-on-android-10-and-above
		findViewById<ListView>(android.R.id.list).setOnItemClickListener { _, _, position, _ ->
			val device = mLeDeviceListAdapter.getDevice(position)
			val intent = Intent(this, DeviceController::class.java)

			intent.putExtra(DeviceController.EXTRAS_DEVICE_NAME, device.name)
			intent.putExtra(DeviceController.EXTRAS_DEVICE_ADDRESS, device.address)

			if (mScanning) {
				bluetoothAdapter.stopLeScan(mLeScanCallback)
				mScanning = false
			}

			startActivity(intent)
		}
	}

	private fun showPermissionDialog() {
		(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MaterialAlertDialogBuilder(this) else AlertDialog.Builder(
			this
		))
			.setTitle("Permissions")
			.setMessage("Bluetooth and location permissions are required to use this app. Please enable them.")
			.setCancelable(false)
			.setPositiveButton(android.R.string.ok) { _, _ ->
				if (checkSelfPermission(
						this,
						Manifest.permission.BLUETOOTH_CONNECT
					) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(
						this,
						Manifest.permission.ACCESS_FINE_LOCATION
					) != PackageManager.PERMISSION_GRANTED
				) {
					requestPermissions(
						this,
						arrayOf(
							(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT),
							Manifest.permission.ACCESS_FINE_LOCATION
						),
						1
					)

					return@setPositiveButton
				}

				startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 1)
			}.show()
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		super.onCreateOptionsMenu(menu)
		menuInflater.inflate(R.menu.main, menu)

		if (!mScanning) {
			menu.findItem(R.id.menu_stop).isVisible = false
			menu.findItem(R.id.menu_scan).isVisible = true
			menu.findItem(R.id.menu_refresh).actionView = null
		} else {
			menu.findItem(R.id.menu_stop).isVisible = true
			menu.findItem(R.id.menu_scan).isVisible = false
			menu.findItem(R.id.menu_refresh)
				.setActionView(R.layout.actionbar_indeterminate_progress)
		}

		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			R.id.menu_scan -> {
				mLeDeviceListAdapter.clear()
				scanLeDevice(true)
			}
			R.id.menu_stop -> scanLeDevice(false)
			R.id.menu_about -> {
				MaterialAlertDialogBuilder(this)
					.setTitle("About")
					.setMessage("A simple BLE device scanner/connector for Android.\n\nMade by Siddharth Praveen Bharadwaj.")
					.setPositiveButton(android.R.string.ok) { _, _ -> }
					.show()
			}
		}

		return true
	}

	override fun onResume() {
		super.onResume()
		showPermissionDialog()

		if (!bluetoothAdapter.isEnabled) {
			mLeDeviceListAdapter.clear()
			scanLeDevice(true)
		}

		mLeDeviceListAdapter = LeDeviceListAdapter()
		findViewById<ListView>(android.R.id.list).adapter = mLeDeviceListAdapter
		scanLeDevice(true)
	}

	override fun onPause() {
		super.onPause()

		scanLeDevice(false)
		mLeDeviceListAdapter.clear()
	}

	@SuppressLint("MissingPermission")
	private fun scanLeDevice(enable: Boolean) {
		if (enable) {
			Handler(Looper.getMainLooper()).postDelayed({
				mScanning = false
				bluetoothAdapter.stopLeScan(mLeScanCallback)

				invalidateOptionsMenu()
			}, SCAN_PERIOD)

			mScanning = true
			bluetoothAdapter.startLeScan(mLeScanCallback)
		} else {
			mScanning = false
			bluetoothAdapter.stopLeScan(mLeScanCallback)
		}

		invalidateOptionsMenu()
	}

	private inner class LeDeviceListAdapter : BaseAdapter() {
		private val mLeDevices: ArrayList<BluetoothDevice> = ArrayList()
		private val mInflater: LayoutInflater = this@MainActivity.layoutInflater

		fun addDevice(device: BluetoothDevice) {
			if (!mLeDevices.contains(device)) mLeDevices.add(device)
		}

		fun getDevice(position: Int): BluetoothDevice = mLeDevices[position]
		fun clear() = mLeDevices.clear()

		override fun getCount(): Int = mLeDevices.size
		override fun getItem(i: Int): Any = mLeDevices[i]
		override fun getItemId(i: Int): Long = i.toLong()

		@SuppressLint("MissingPermission")
		override fun getView(i: Int, view: View?, viewGroup: ViewGroup?): View? {
			var viewObject: View? = view
			val viewHolder: ViewHolder

			if (viewObject == null) {
				viewObject = mInflater.inflate(R.layout.listitem_device, viewGroup, false)
				viewHolder = ViewHolder()

				viewHolder.deviceAddress = viewObject.findViewById(R.id.device_address)
				viewHolder.deviceName = viewObject.findViewById(R.id.device_name)
				viewObject.tag = viewHolder
			} else viewHolder = viewObject.tag as ViewHolder
			val device = mLeDevices[i]
			val deviceName = device.name

			if (deviceName != null && deviceName.isNotEmpty()) viewHolder.deviceName!!.text =
				deviceName
			else viewHolder.deviceName!!.text = buildString { append("Unknown Device") }

			viewHolder.deviceAddress!!.text = device.address
			return viewObject
		}
	}

	private val mLeScanCallback =
		BluetoothAdapter.LeScanCallback { device, _, _ ->
			runOnUiThread {
				mLeDeviceListAdapter.addDevice(device)
				mLeDeviceListAdapter.notifyDataSetChanged()
			}
		}

	internal class ViewHolder {
		var deviceName: TextView? = null
		var deviceAddress: TextView? = null
	}

	companion object {
		private const val SCAN_PERIOD: Long = 10000
	}
}