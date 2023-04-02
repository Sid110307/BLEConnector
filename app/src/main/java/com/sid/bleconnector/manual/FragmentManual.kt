package com.sid.bleconnector.manual

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.sid.bleconnector.BLEService
import com.sid.bleconnector.databinding.FragmentManualBinding

class FragmentManual : Fragment() {
	private lateinit var binding: FragmentManualBinding

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentManualBinding.inflate(inflater, container, false)
		val view = binding.root

		val deviceAddress = binding.deviceAddress
		val connectionStatus = binding.connectionStatus
		val connectBtn = binding.connectButton

		val input = binding.inputText
		val output = binding.outputText
		val sendBtn = binding.sendButton

		deviceAddress.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

			override fun afterTextChanged(s: Editable?) {
				if (s.toString().length == 2) deviceAddress.append(":")
				if (s.toString().length == 5) deviceAddress.append(":")
				if (s.toString().length == 8) deviceAddress.append(":")
				if (s.toString().length == 11) deviceAddress.append(":")
				if (s.toString().length == 14) deviceAddress.append(":")
			}
		})

		connectionStatus.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

			override fun afterTextChanged(s: Editable?) {
				if (s.toString() == "Connected") connectionStatus.setTextColor(Color.GREEN)
				else connectionStatus.setTextColor(Color.RED)
			}
		})

		connectBtn.setOnClickListener {
			if (deviceAddress.text.trim().isEmpty()) {
				deviceAddress.error = "Device address cannot be empty"
				return@setOnClickListener
			}

			BLEService(activity).also {
				it.initialize()

				connectionStatus.text = buildString { append("Connecting...") }
				it.connect(deviceAddress.text.toString())

				connectionStatus.text = buildString { append("Connected") }
				connectBtn.isClickable = false
				deviceAddress.isClickable = false

				if (input.text.trim().isNotEmpty()) {
					sendBtn.isClickable = true

					sendBtn.setOnClickListener { _ ->
						it.broadcastUpdate(input.text.toString())
						input.text.clear()

						output.text =
							buildString { append(it.readBroadcastData(Intent("ACTION_DATA_AVAILABLE"))) }
					}
				}

				it.disconnect()
			}
		}

		sendBtn.setOnClickListener {
			sendBtn.isClickable = false

			BLEService(activity).also {
				it.initialize()
				it.connect(deviceAddress.text.toString())

				it.broadcastUpdate(input.text.toString())
				input.text.clear()

				output.text =
					buildString { append(it.readBroadcastData(Intent("ACTION_DATA_AVAILABLE"))) }

				it.disconnect()
			}
		}

		return view
	}
}