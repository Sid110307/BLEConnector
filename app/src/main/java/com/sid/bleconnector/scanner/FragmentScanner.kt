// TODO: Migrate to new BluetoothLeScanner and remove deprecated code:
@file:Suppress("DEPRECATION")

package com.sid.bleconnector.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS
import android.util.Log
import android.view.*
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.fragment.app.ListFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sid.bleconnector.MainActivity.Companion.TAG
import com.sid.bleconnector.MainActivity.Companion.notGrantedPermissions
import com.sid.bleconnector.R
import com.sid.bleconnector.databinding.FragmentScannerBinding

class FragmentScanner : ListFragment() {
	private var mLeDeviceListAdapter: LeDeviceListAdapter? = null
	private var bluetoothAdapter: BluetoothAdapter? = null

	private var mScanning = false

	private lateinit var binding: FragmentScannerBinding
	private lateinit var view: View

	@SuppressLint("MissingPermission")
	@RequiresApi(Build.VERSION_CODES.S)
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentScannerBinding.inflate(inflater, container, false)
		setHasOptionsMenu(true)
		view = binding.root

		bluetoothAdapter =
			(activity?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
		if (!bluetoothAdapter!!.isEnabled || !(activity?.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isLocationEnabled) showPermissionDialog()

		binding.list.setOnItemClickListener { _, _, position, _ ->
			if (notGrantedPermissions(
					requireActivity(),
					Manifest.permission.BLUETOOTH_CONNECT,
					Manifest.permission.ACCESS_FINE_LOCATION
				)
			) showPermissionDialog()

			val device = mLeDeviceListAdapter!!.getDevice(position)
			val intent = Intent(activity, DeviceController::class.java)

			intent.putExtra(DeviceController.EXTRAS_DEVICE_NAME, device.name)
			intent.putExtra(DeviceController.EXTRAS_DEVICE_ADDRESS, device.address)

			if (mScanning) {
				bluetoothAdapter!!.stopLeScan(mLeScanCallback)
				mScanning = false
			}

			startActivity(intent)
		}

		return view
	}

	@SuppressLint("MissingPermission")
	fun showPermissionDialog() {
		if (notGrantedPermissions(
				requireActivity(),
				Manifest.permission.BLUETOOTH_CONNECT,
				Manifest.permission.ACCESS_FINE_LOCATION
			)
		) MaterialAlertDialogBuilder(requireActivity())
			.setTitle("Permissions")
			.setMessage("Bluetooth and location services are required to use this app. Please enable them.")
			.setCancelable(false)
			.setPositiveButton(android.R.string.ok) { _, _ ->
				registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
					if (bluetoothAdapter!!.isEnabled) {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
							&& !(activity?.getSystemService(Context.LOCATION_SERVICE)
									as LocationManager).isLocationEnabled
						) startActivity(Intent(ACTION_LOCATION_SOURCE_SETTINGS))
					} else startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
				}.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
			}.show()
		else requestPermissions(
			arrayOf(
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH,
				Manifest.permission.ACCESS_FINE_LOCATION
			),
			1
		)
	}

	override fun onAttach(context: Context) {
		super.onAttach(context)

		mLeDeviceListAdapter = LeDeviceListAdapter()
		listAdapter = mLeDeviceListAdapter
	}

	override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
		super.onCreateOptionsMenu(menu, inflater)
		inflater.inflate(R.menu.main, menu)

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
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			R.id.menu_scan -> {
				if (bluetoothAdapter == null) Log.e(TAG, "Bluetooth adapter is null")
				else {
					mLeDeviceListAdapter!!.clear()
					scanLeDevice(true)
				}
			}

			R.id.menu_stop -> scanLeDevice(false)
			R.id.menu_about -> {
				MaterialAlertDialogBuilder(requireActivity())
					.setTitle("About")
					.setMessage("A BLE device scanner/connector for Android.\n\nMade by Siddharth Praveen Bharadwaj.")
					.setPositiveButton(android.R.string.ok) { _, _ -> }
					.show()
			}

			else -> super.onOptionsItemSelected(item)
		}

		return true
	}

	override fun onResume() {
		super.onResume()

		if (bluetoothAdapter != null) {
			if (!bluetoothAdapter!!.isEnabled) {
				if (mLeDeviceListAdapter != null) showPermissionDialog()
			} else {
				mLeDeviceListAdapter?.clear()
				mLeDeviceListAdapter?.notifyDataSetChanged()
				scanLeDevice(true)
			}
		}

		mLeDeviceListAdapter = LeDeviceListAdapter()
		binding.list.adapter = mLeDeviceListAdapter
		scanLeDevice(true)
	}

	override fun onPause() {
		super.onPause()

		if (bluetoothAdapter != null) {
			scanLeDevice(false)
			mLeDeviceListAdapter!!.clear()
		}
	}

	@SuppressLint("MissingPermission")
	private fun scanLeDevice(enable: Boolean) {
		if (enable) {
			Handler(Looper.getMainLooper()).postDelayed({
				mScanning = false
				bluetoothAdapter!!.stopLeScan(mLeScanCallback)

				activity?.invalidateOptionsMenu()
			}, SCAN_PERIOD)

			mScanning = true
			bluetoothAdapter!!.startLeScan(mLeScanCallback)
		} else {
			mScanning = false
			bluetoothAdapter!!.stopLeScan(mLeScanCallback)
		}

		activity?.invalidateOptionsMenu()
	}

	private inner class LeDeviceListAdapter : BaseAdapter() {
		private val mLeDevices: ArrayList<BluetoothDevice> = ArrayList()
		private val mInflater: LayoutInflater = this@FragmentScanner.layoutInflater

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
			val viewHolder: DeviceViewHolder

			if (viewObject == null) {
				viewObject = mInflater.inflate(R.layout.list_item_device, viewGroup, false)
				viewHolder = DeviceViewHolder()

				viewHolder.deviceAddress = viewObject.findViewById(R.id.device_address)
				viewHolder.deviceName = viewObject.findViewById(R.id.device_name)
				viewObject.tag = viewHolder
			} else viewHolder = viewObject.tag as DeviceViewHolder
			val device = mLeDevices[i]
			val deviceName = device.name

			if (deviceName != null && deviceName.isNotEmpty())
				viewHolder.deviceName!!.text = deviceName
			else viewHolder.deviceName!!.text = buildString { append("Unknown Device") }

			viewHolder.deviceAddress!!.text = device.address
			return viewObject
		}
	}

	private val mLeScanCallback =
		BluetoothAdapter.LeScanCallback { device, _, _ ->
			activity?.runOnUiThread {
				mLeDeviceListAdapter!!.addDevice(device)
				mLeDeviceListAdapter!!.notifyDataSetChanged()
			}
		}

	internal class DeviceViewHolder {
		var deviceName: TextView? = null
		var deviceAddress: TextView? = null
	}

	companion object {
		private const val SCAN_PERIOD: Long = 10000
	}
}
