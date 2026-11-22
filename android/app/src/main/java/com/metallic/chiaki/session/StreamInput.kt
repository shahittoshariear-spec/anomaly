package com.metallic.chiaki.session

import android.content.Context
import android.hardware.*
import android.view.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import com.metallic.chiaki.common.Preferences
import com.metallic.chiaki.lib.ControllerState
import kotlin.math.abs

class StreamInput(val context: Context, val preferences: Preferences)
{
	companion object
	{
		// 50 Hz is plenty for controller motion and is much easier on battery and thermals
		// than the old 250 Hz sampling rate.
		private const val SENSOR_SAMPLING_PERIOD_US = 20000
	}
	var controllerStateChangedCallback: ((ControllerState) -> Unit)? = null

	// Reused so that every input event does not allocate a new ControllerState (plus a
	// touch array and two intermediate states from the `or` operator). Input events can
	// arrive at 125-250 Hz; the garbage that produced was the main source of GC jitter
	// on the input path. The receiver (JNI) copies the state synchronously.
	private val combinedControllerState = ControllerState()

	val controllerState: ControllerState get()
	{
		val controllerState = combinedControllerState
		val touchState = touchControllerState

		controllerState.buttons = sensorControllerState.buttons or keyControllerState.buttons or
				motionControllerState.buttons or touchState.buttons
		controllerState.l2State = maxOf3(sensorControllerState.l2State, keyControllerState.l2State, motionControllerState.l2State)
		controllerState.r2State = maxOf3(sensorControllerState.r2State, keyControllerState.r2State, motionControllerState.r2State)
		controllerState.leftX = maxAbs(sensorControllerState.leftX, keyControllerState.leftX, motionControllerState.leftX)
		controllerState.leftY = maxAbs(sensorControllerState.leftY, keyControllerState.leftY, motionControllerState.leftY)
		controllerState.rightX = maxAbs(sensorControllerState.rightX, keyControllerState.rightX, motionControllerState.rightX)
		controllerState.rightY = maxAbs(sensorControllerState.rightY, keyControllerState.rightY, motionControllerState.rightY)

		// Touches only ever come from the touch controller state (the other sources
		// leave id at -1), so copying the references is equivalent to the old `or`.
		controllerState.touches[0] = touchState.touches[0]
		controllerState.touches[1] = touchState.touches[1]

		controllerState.gyroX = sensorControllerState.gyroX
		controllerState.gyroY = sensorControllerState.gyroY
		controllerState.gyroZ = sensorControllerState.gyroZ
		controllerState.accelX = sensorControllerState.accelX
		controllerState.accelY = sensorControllerState.accelY
		controllerState.accelZ = sensorControllerState.accelZ
		controllerState.orientX = sensorControllerState.orientX
		controllerState.orientY = sensorControllerState.orientY
		controllerState.orientZ = sensorControllerState.orientZ
		controllerState.orientW = sensorControllerState.orientW

		// Rotation only affects the motion sensor values. Skip the per-event
		// getSystemService/display lookups for the common gamepad-only case.
		if(motionEnabled)
		{
			val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
			@Suppress("DEPRECATION")
			when(windowManager.defaultDisplay.rotation)
			{
				Surface.ROTATION_90 -> {
					controllerState.accelX *= -1.0f
					controllerState.accelZ *= -1.0f
					controllerState.gyroX *= -1.0f
					controllerState.gyroZ *= -1.0f
					controllerState.orientX *= -1.0f
					controllerState.orientZ *= -1.0f
				}
				else -> {}
			}
		}

		// prioritize motion controller's l2 and r2 over key
		// (some controllers send only key, others both but key earlier than full press)
		if(motionControllerState.l2State > 0U)
			controllerState.l2State = motionControllerState.l2State
		if(motionControllerState.r2State > 0U)
			controllerState.r2State = motionControllerState.r2State

		return controllerState
	}

	private fun maxAbs(a: Short, b: Short) = if(abs(a.toInt()) > abs(b.toInt())) a else b
	private fun maxAbs(a: Short, b: Short, c: Short) = maxAbs(maxAbs(a, b), c)
	private fun maxOf3(a: UByte, b: UByte, c: UByte) = when
	{
		a >= b && a >= c -> a
		b >= c -> b
		else -> c
	}

	private val sensorControllerState = ControllerState() // from Motion Sensors
	private val keyControllerState = ControllerState() // from KeyEvents
	private val motionControllerState = ControllerState() // from MotionEvents
	var touchControllerState = ControllerState()
		set(value)
		{
			field = value
			controllerStateUpdated()
		}

	private val swapCrossMoon = preferences.swapCrossMoon
	private val motionEnabled = preferences.motionEnabled
	private val dockButton = preferences.dockButton

	private val sensorEventListener = object: SensorEventListener {
		override fun onSensorChanged(event: SensorEvent)
		{
			when(event.sensor.type)
			{
				Sensor.TYPE_ACCELEROMETER -> {
					sensorControllerState.accelX = event.values[1] / SensorManager.GRAVITY_EARTH
					sensorControllerState.accelY = event.values[2] / SensorManager.GRAVITY_EARTH
					sensorControllerState.accelZ = event.values[0] / SensorManager.GRAVITY_EARTH
				}
				Sensor.TYPE_GYROSCOPE -> {
					sensorControllerState.gyroX = event.values[1]
					sensorControllerState.gyroY = event.values[2]
					sensorControllerState.gyroZ = event.values[0]
				}
				Sensor.TYPE_ROTATION_VECTOR -> {
					val q = floatArrayOf(0f, 0f, 0f, 0f)
					SensorManager.getQuaternionFromVector(q, event.values)
					sensorControllerState.orientX = q[2]
					sensorControllerState.orientY = q[3]
					sensorControllerState.orientZ = q[1]
					sensorControllerState.orientW = q[0]
				}
				else -> return
			}
			controllerStateUpdated()
		}

		override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
	}

	private val motionLifecycleObserver = object: LifecycleObserver {
		@OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
		fun onResume()
		{
			val samplingPeriodUs = SENSOR_SAMPLING_PERIOD_US
			val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
			listOfNotNull(
				sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
				sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
			).forEach {
				sensorManager.registerListener(sensorEventListener, it, samplingPeriodUs)
			}
		}

		@OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
		fun onPause()
		{
			val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
			sensorManager.unregisterListener(sensorEventListener)
		}
	}

	fun observe(lifecycleOwner: LifecycleOwner)
	{
		if(motionEnabled)
			lifecycleOwner.lifecycle.addObserver(motionLifecycleObserver)
	}

	private fun controllerStateUpdated()
	{
		controllerStateChangedCallback?.let { it(controllerState) }
	}

	fun dispatchKeyEvent(event: KeyEvent): Boolean
	{
		//Log.i("StreamSession", "key event $event")
		if(event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP)
			return false

		when(event.keyCode)
		{
			KeyEvent.KEYCODE_BUTTON_L2 -> {
				keyControllerState.l2State = if(event.action == KeyEvent.ACTION_DOWN) UByte.MAX_VALUE else 0U
				return true
			}
			KeyEvent.KEYCODE_BUTTON_R2 -> {
				keyControllerState.r2State = if(event.action == KeyEvent.ACTION_DOWN) UByte.MAX_VALUE else 0U
				return true
			}
		}

		// PS/Home/dock buttons arrive as BUTTON_MODE on most pads and as BUTTON_C on
		// some (EASYSMX M20 among them). HOME/ASSIST cover pads that emulate the
		// system keys instead. Route all of them through the user's mapping.
		if(event.keyCode == KeyEvent.KEYCODE_BUTTON_C ||
			event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE ||
			event.keyCode == KeyEvent.KEYCODE_HOME ||
			event.keyCode == KeyEvent.KEYCODE_ASSIST)
		{
			val pressed = event.action == KeyEvent.ACTION_DOWN
			when(dockButton)
			{
				Preferences.DockButton.L2 -> keyControllerState.l2State = if(pressed) UByte.MAX_VALUE else 0U
				Preferences.DockButton.R2 -> keyControllerState.r2State = if(pressed) UByte.MAX_VALUE else 0U
				else -> {
					val buttonMask = dockButton.buttonMask ?: return true // "Do nothing"
					keyControllerState.buttons = when(event.action)
					{
						KeyEvent.ACTION_DOWN -> keyControllerState.buttons or buttonMask
						KeyEvent.ACTION_UP -> keyControllerState.buttons and buttonMask.inv()
						else -> keyControllerState.buttons
					}
				}
			}
			controllerStateUpdated()
			return true
		}

		val buttonMask: UInt = when(event.keyCode)
		{
			// dpad may arrive as either HAT axes or key events depending on the controller
			KeyEvent.KEYCODE_DPAD_LEFT -> ControllerState.BUTTON_DPAD_LEFT
			KeyEvent.KEYCODE_DPAD_RIGHT -> ControllerState.BUTTON_DPAD_RIGHT
			KeyEvent.KEYCODE_DPAD_UP -> ControllerState.BUTTON_DPAD_UP
			KeyEvent.KEYCODE_DPAD_DOWN -> ControllerState.BUTTON_DPAD_DOWN
			KeyEvent.KEYCODE_BUTTON_A -> if(swapCrossMoon) ControllerState.BUTTON_MOON else ControllerState.BUTTON_CROSS
			KeyEvent.KEYCODE_BUTTON_B -> if(swapCrossMoon) ControllerState.BUTTON_CROSS else ControllerState.BUTTON_MOON
			KeyEvent.KEYCODE_BUTTON_X -> if(swapCrossMoon) ControllerState.BUTTON_PYRAMID else ControllerState.BUTTON_BOX
			KeyEvent.KEYCODE_BUTTON_Y -> if(swapCrossMoon) ControllerState.BUTTON_BOX else ControllerState.BUTTON_PYRAMID
			KeyEvent.KEYCODE_BUTTON_L1 -> ControllerState.BUTTON_L1
			KeyEvent.KEYCODE_BUTTON_R1 -> ControllerState.BUTTON_R1
			KeyEvent.KEYCODE_BUTTON_THUMBL -> ControllerState.BUTTON_L3
			KeyEvent.KEYCODE_BUTTON_THUMBR -> ControllerState.BUTTON_R3
			KeyEvent.KEYCODE_BUTTON_SELECT -> ControllerState.BUTTON_SHARE
			KeyEvent.KEYCODE_BUTTON_START -> ControllerState.BUTTON_OPTIONS
			else -> return false
		}

		keyControllerState.buttons = keyControllerState.buttons.run {
			when(event.action)
			{
				KeyEvent.ACTION_DOWN -> this or buttonMask
				KeyEvent.ACTION_UP -> this and buttonMask.inv()
				else -> this
			}
		}

		controllerStateUpdated()
		return true
	}

	fun onGenericMotionEvent(event: MotionEvent): Boolean
	{
		if(event.source and InputDevice.SOURCE_CLASS_JOYSTICK != InputDevice.SOURCE_CLASS_JOYSTICK)
			return false
		fun Float.signedAxis() = (this * Short.MAX_VALUE).toInt().toShort()
		fun Float.unsignedAxis() = (this.coerceIn(0f, 1f) * UByte.MAX_VALUE.toFloat()).toUInt().toUByte()

		// Third-party pads are inconsistent about which axis ids they use. Prefer the
		// standard axes and fall back to the alternatives when the standard one is idle.
		fun axisOrFallback(primary: Int, fallback: Int): Float
		{
			val value = event.getAxisValue(primary)
			return if(value != 0f) value else event.getAxisValue(fallback)
		}

		// Triggers may rest at the minimum of their reported range (-1 on some pads).
		fun trigger(axis: Int): Float
		{
			val range = event.device?.getMotionRange(axis)
			val value = event.getAxisValue(axis)
			if(range == null || range.max <= range.min)
				return value.coerceIn(0f, 1f)
			return ((value - range.min) / (range.max - range.min)).coerceIn(0f, 1f)
		}

		motionControllerState.leftX = event.getAxisValue(MotionEvent.AXIS_X).signedAxis()
		motionControllerState.leftY = event.getAxisValue(MotionEvent.AXIS_Y).signedAxis()
		motionControllerState.rightX = axisOrFallback(MotionEvent.AXIS_Z, MotionEvent.AXIS_RX).signedAxis()
		motionControllerState.rightY = axisOrFallback(MotionEvent.AXIS_RZ, MotionEvent.AXIS_RY).signedAxis()
		// AXIS_BRAKE/AXIS_GAS are used for the triggers by some generic HID gamepads.
		motionControllerState.l2State = maxOf(
			trigger(MotionEvent.AXIS_LTRIGGER),
			trigger(MotionEvent.AXIS_BRAKE)).unsignedAxis()
		motionControllerState.r2State = maxOf(
			trigger(MotionEvent.AXIS_RTRIGGER),
			trigger(MotionEvent.AXIS_GAS)).unsignedAxis()
		motionControllerState.buttons = motionControllerState.buttons.let {
			val dpadX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
			val dpadY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
			val dpadButtons =
				(if(dpadX > 0.5f) ControllerState.BUTTON_DPAD_RIGHT else 0U) or
						(if(dpadX < -0.5f) ControllerState.BUTTON_DPAD_LEFT else 0U) or
						(if(dpadY > 0.5f) ControllerState.BUTTON_DPAD_DOWN else 0U) or
						(if(dpadY < -0.5f) ControllerState.BUTTON_DPAD_UP else 0U)
			it and (ControllerState.BUTTON_DPAD_RIGHT or
					ControllerState.BUTTON_DPAD_LEFT or
					ControllerState.BUTTON_DPAD_DOWN or
					ControllerState.BUTTON_DPAD_UP).inv() or
					dpadButtons
		}
		//Log.i("StreamSession", "motionEvent => $motionControllerState")
		controllerStateUpdated()
		return true
	}
}