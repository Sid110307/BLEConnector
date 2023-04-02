package com.sid.bleconnector

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.sid.bleconnector.databinding.ActivityMainBinding
import com.sid.bleconnector.manual.FragmentManual
import com.sid.bleconnector.scanner.FragmentScanner

class MainActivity : AppCompatActivity() {

	private lateinit var binding: ActivityMainBinding

	@RequiresApi(Build.VERSION_CODES.S)
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityMainBinding.inflate(layoutInflater)
		val view = binding.root
		setContentView(view)

		registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
			if (it.containsValue(false)) Log.e(
				TAG,
				"Permissions not granted: ${it.filterValues { value -> !value }}"
			)
		}.launch(
			arrayOf(
				Manifest.permission.BLUETOOTH,
				Manifest.permission.BLUETOOTH_ADMIN,
				Manifest.permission.BLUETOOTH_SCAN,
				Manifest.permission.BLUETOOTH_CONNECT,
				Manifest.permission.ACCESS_FINE_LOCATION
			)
		)

		if (notGrantedPermissions(
				this,
				Manifest.permission.BLUETOOTH_CONNECT,
				Manifest.permission.ACCESS_FINE_LOCATION
			)
		) ActivityCompat.requestPermissions(
			this,
			arrayOf(
				Manifest.permission.BLUETOOTH_CONNECT,
				Manifest.permission.ACCESS_FINE_LOCATION
			),
			1
		)

		if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			Snackbar.make(view, "BLE is not supported on this device", Snackbar.LENGTH_SHORT).show()

			Log.e(TAG, "BLE is not supported on this device")
			Handler(Looper.getMainLooper()).postDelayed({ finish() }, 5000)
		}

		if (getSystemService(Context.BLUETOOTH_SERVICE) == null) {
			Snackbar.make(view, "Bluetooth is not supported on this device", Snackbar.LENGTH_SHORT)
				.show()

			Log.e(TAG, "Bluetooth is not supported on this device")
			Handler(Looper.getMainLooper()).postDelayed({ finish() }, 5000)
		}

		setFragment(FragmentScanner())
		binding.bottomNavigation.setOnItemSelectedListener {
			when (it.itemId) {
				R.id.scanner_mode -> if (checkFragment(FragmentScanner())) setFragment(
					FragmentScanner()
				)

				R.id.manual_mode -> if (checkFragment(FragmentManual())) setFragment(FragmentManual())
				else -> setFragment(FragmentScanner())
			}

			true
		}
	}

	private fun setFragment(fragment: androidx.fragment.app.Fragment) =
		supportFragmentManager.beginTransaction().replace(R.id.fragment_container, fragment)
			.commitNow()

	private fun checkFragment(fragment: androidx.fragment.app.Fragment): Boolean =
		supportFragmentManager.findFragmentById(R.id.fragment_container) != fragment

	companion object {
		val TAG: String = MainActivity::class.java.simpleName

		fun notGrantedPermissions(context: Context, vararg permissions: String): Boolean =
			permissions.all {
				return (ContextCompat.checkSelfPermission(
					context,
					it
				) != PackageManager.PERMISSION_GRANTED)
			}
	}
}