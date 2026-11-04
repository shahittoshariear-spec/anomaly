// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.settings

import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import com.metallic.chiaki.databinding.ActivityControllerTestBinding

/**
 * Shows how Android reports the connected gamepad. Useful for verifying that a
 * third-party controller's buttons and axes are mapped the way the stream expects.
 */
class ControllerTestActivity: AppCompatActivity()
{
	private lateinit var binding: ActivityControllerTestBinding

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		binding = ActivityControllerTestBinding.inflate(layoutInflater)
		setContentView(binding.root)

		setSupportActionBar(binding.toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		binding.toolbar.setNavigationOnClickListener { finish() }
	}

	override fun onResume()
	{
		super.onResume()
		binding.deviceText.text = describeDevices()
	}

	override fun dispatchKeyEvent(event: KeyEvent): Boolean
	{
		if(event.keyCode == KeyEvent.KEYCODE_BACK)
			return super.dispatchKeyEvent(event)

		val action = when(event.action)
		{
			KeyEvent.ACTION_DOWN -> "DOWN"
			KeyEvent.ACTION_UP -> "UP"
			else -> return super.dispatchKeyEvent(event)
		}

		binding.eventText.text = getString(
			com.metallic.chiaki.R.string.controller_test_key,
			KeyEvent.keyCodeToString(event.keyCode),
			event.keyCode,
			action,
			event.scanCode,
			event.device?.name ?: "?")
		return true
	}

	override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean
	{
		if(event.source and InputDevice.SOURCE_CLASS_JOYSTICK == 0)
			return super.dispatchGenericMotionEvent(event)

		val axes = listOf(
			"X" to MotionEvent.AXIS_X,
			"Y" to MotionEvent.AXIS_Y,
			"Z" to MotionEvent.AXIS_Z,
			"RZ" to MotionEvent.AXIS_RZ,
			"RX" to MotionEvent.AXIS_RX,
			"RY" to MotionEvent.AXIS_RY,
			"HAT_X" to MotionEvent.AXIS_HAT_X,
			"HAT_Y" to MotionEvent.AXIS_HAT_Y,
			"LTRIGGER" to MotionEvent.AXIS_LTRIGGER,
			"RTRIGGER" to MotionEvent.AXIS_RTRIGGER,
			"BRAKE" to MotionEvent.AXIS_BRAKE,
			"GAS" to MotionEvent.AXIS_GAS
		).mapNotNull { (name, axis) ->
			val value = event.getAxisValue(axis)
			if(value == 0f) null else "%s = %.3f".format(name, value)
		}

		if(axes.isNotEmpty())
		{
			binding.eventText.text = getString(
				com.metallic.chiaki.R.string.controller_test_motion,
				event.device?.name ?: "?",
				axes.joinToString("\n"))
		}
		return true
	}

	private fun describeDevices(): String
	{
		val builder = StringBuilder()
		for(id in InputDevice.getDeviceIds())
		{
			val device = InputDevice.getDevice(id) ?: continue
			val isGamepad = device.sources and InputDevice.SOURCE_CLASS_JOYSTICK != 0 ||
					device.sources and InputDevice.SOURCE_GAMEPAD != 0
			if(!isGamepad)
				continue

			builder.append(getString(
				com.metallic.chiaki.R.string.controller_test_device,
				device.name, device.id, device.vendorId, device.productId, device.sources))

			for((name, axis) in listOf(
				"X" to MotionEvent.AXIS_X,
				"Y" to MotionEvent.AXIS_Y,
				"Z" to MotionEvent.AXIS_Z,
				"RZ" to MotionEvent.AXIS_RZ,
				"LTRIGGER" to MotionEvent.AXIS_LTRIGGER,
				"RTRIGGER" to MotionEvent.AXIS_RTRIGGER
			))
			{
				val range = device.getMotionRange(axis, device.sources) ?: continue
				builder.append("    $name: %.2f .. %.2f\n".format(range.min, range.max))
			}
			builder.append('\n')
		}

		if(builder.isEmpty())
			builder.append(getString(com.metallic.chiaki.R.string.controller_test_none))

		return builder.toString()
	}
}
