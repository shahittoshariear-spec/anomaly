// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.stream

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Matrix
import android.hardware.input.InputManager
import android.os.*
import android.view.*
import android.widget.EditText
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import com.metallic.chiaki.common.ThemedActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.metallic.chiaki.R
import com.metallic.chiaki.common.Preferences
import com.metallic.chiaki.common.ext.viewModelFactory
import com.metallic.chiaki.databinding.ActivityStreamBinding
import com.metallic.chiaki.lib.ConnectInfo
import com.metallic.chiaki.lib.ConnectVideoProfile
import com.metallic.chiaki.session.*
import com.metallic.chiaki.touchcontrols.DefaultTouchControlsFragment
import com.metallic.chiaki.touchcontrols.TouchControlsFragment
import com.metallic.chiaki.touchcontrols.TouchpadOnlyFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.math.min

private sealed class DialogContents
private object StreamQuitDialog: DialogContents()
private object CreateErrorDialog: DialogContents()
private object PinRequestDialog: DialogContents()

class StreamActivity : ThemedActivity()
{
	companion object
	{
		const val EXTRA_CONNECT_INFO = "connect_info"
		private const val HIDE_UI_TIMEOUT_MS = 2000L
	}

	private lateinit var viewModel: StreamViewModel
	private lateinit var binding: ActivityStreamBinding
	private lateinit var insetsController: WindowInsetsControllerCompat

	private val uiVisibilityHandler = Handler(Looper.getMainLooper())

	private var updatingControlsSwitch = false
	private var controlsChoiceOverridden = false
	private var sessionPaused = false
	private var stopping = false

	private val controlsJobs = mutableMapOf<Fragment, Job>()

	private val inputManager by lazy { getSystemService(Context.INPUT_SERVICE) as InputManager }
	private val inputDeviceListener = object: InputManager.InputDeviceListener
	{
		override fun onInputDeviceAdded(deviceId: Int) = updateVirtualControlsForDeviceChange()
		override fun onInputDeviceRemoved(deviceId: Int) = updateVirtualControlsForDeviceChange()
		override fun onInputDeviceChanged(deviceId: Int) = updateVirtualControlsForDeviceChange()
	}

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)

		val connectInfo = IntentCompat.getParcelableExtra(intent, EXTRA_CONNECT_INFO, ConnectInfo::class.java)
		if(connectInfo == null)
		{
			finish()
			return
		}

		viewModel = ViewModelProvider(this, viewModelFactory {
			StreamViewModel(application, connectInfo)
		})[StreamViewModel::class.java]

		viewModel.input.observe(this)

		binding = ActivityStreamBinding.inflate(layoutInflater)
		setContentView(binding.root)
		binding.launchAnimation.start { binding.launchAnimation.visibility = View.GONE }
		binding.fullscreenButton.setOnClickListener { toggleFullscreen() }
		updateFullscreenToggle()
		onBackPressedDispatcher.addCallback(this) { stopSessionAndFinish() }

		supportFragmentManager.fragments.forEach {
			if(it is TouchControlsFragment)
				attachControlsFragment(it)
		}

		WindowCompat.setDecorFitsSystemWindows(window, false)
		insetsController = WindowCompat.getInsetsController(window, window.decorView)
		insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

		ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
			val systemBars = insets.isVisible(WindowInsetsCompat.Type.systemBars())
			if(systemBars)
				showOverlay()
			else
				hideOverlay()
			insets
		}

		viewModel.virtualControlsVisible.observe(this, Observer { visible ->
			updatingControlsSwitch = true
			binding.onScreenControlsSwitch.isChecked = visible
			updatingControlsSwitch = false
		})
		binding.onScreenControlsSwitch.setOnCheckedChangeListener { _, isChecked ->
			if(updatingControlsSwitch)
				return@setOnCheckedChangeListener
			controlsChoiceOverridden = true
			viewModel.setVirtualControlsVisible(isChecked)
			showOverlay()
		}

		viewModel.touchpadOnlyEnabled.observe(this, Observer {
			if(binding.touchpadOnlySwitch.isChecked != it)
			{
				updatingControlsSwitch = true
				binding.touchpadOnlySwitch.isChecked = it
				updatingControlsSwitch = false
			}
		})
		binding.touchpadOnlySwitch.setOnCheckedChangeListener { _, isChecked ->
			if(updatingControlsSwitch)
				return@setOnCheckedChangeListener
			viewModel.setTouchpadOnlyEnabled(isChecked)
			showOverlay()
		}

		binding.displayModeToggle.addOnButtonCheckedListener { _, _, _ ->
			adjustStreamViewAspect()
			showOverlay()
		}

		binding.surfaceView.setOnClickListener { showOverlay() }

		//viewModel.session.attachToTextureView(textureView)
		viewModel.session.attachToSurfaceView(binding.surfaceView)
		viewModel.session.state.observe(this, Observer { this.stateChanged(it) })
		adjustStreamViewAspect()
		applyVirtualControlsMode()

		if(Preferences(this).rumbleEnabled)
		{
			val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
			// Rumble packets arrive at frame rate. Restarting a one-shot vibration for
			// every packet meant a cancel() + vibrate() pair on the main thread up to
			// 60 times per second, which delays controller event dispatch and drains
			// battery. Only react when the amplitude meaningfully changes, and refresh
			// the one-shot a few times per second so long rumbles keep going.
			var lastAmplitude = -1
			var lastIssuedAt = 0L
			viewModel.session.rumbleState.observe(this, Observer {
				val amplitude = min(255, (it.left.toInt() + it.right.toInt()) / 2)
				val now = SystemClock.uptimeMillis()

				if(amplitude == 0)
				{
					if(lastAmplitude != 0)
					{
						vibrator.cancel()
						lastAmplitude = 0
					}
					return@Observer
				}

				val changed = lastAmplitude < 0 || kotlin.math.abs(amplitude - lastAmplitude) >= 8
				if(!changed && now - lastIssuedAt < 400)
					return@Observer

				lastAmplitude = amplitude
				lastIssuedAt = now
				if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
					vibrator.vibrate(VibrationEffect.createOneShot(1000, amplitude))
				else
					vibrator.vibrate(1000)
			})
		}
	}


	override fun onAttachFragment(fragment: Fragment)
	{
		super.onAttachFragment(fragment)
		if(fragment is TouchControlsFragment && ::viewModel.isInitialized)
			attachControlsFragment(fragment)
	}

	private fun attachControlsFragment(fragment: TouchControlsFragment)
	{
		if(controlsJobs.containsKey(fragment))
			return
		controlsJobs[fragment] = fragment.controllerState
			.onEach { viewModel.input.touchControllerState = it }
			.launchIn(lifecycleScope)
		fragment.virtualControlsVisible =
			if(fragment is TouchpadOnlyFragment) viewModel.touchpadControlsVisible else viewModel.defaultControlsVisible
	}

	override fun onResume()
	{
		super.onResume()
		sessionPaused = false
		inputManager.registerInputDeviceListener(inputDeviceListener, null)
		applyFullscreen()
		viewModel.session.resume()
	}

	override fun onPause()
	{
		sessionPaused = true
		inputManager.unregisterInputDeviceListener(inputDeviceListener)
		super.onPause()
		viewModel.session.pause()
	}

	override fun onDestroy()
	{
		super.onDestroy()
		controlsJobs.values.forEach { it.cancel() }
		controlsJobs.clear()
		uiVisibilityHandler.removeCallbacks(hideSystemUIRunnable)
	}

	private fun applyVirtualControlsMode()
	{
		when(viewModel.preferences.virtualControlsMode)
		{
			Preferences.VirtualControlsMode.ALWAYS_SHOW -> viewModel.setVirtualControlsVisible(true)
			Preferences.VirtualControlsMode.ALWAYS_HIDE -> viewModel.setVirtualControlsVisible(false)
			Preferences.VirtualControlsMode.HIDE_WITH_CONTROLLER ->
				viewModel.setVirtualControlsVisible(!hasPhysicalController())
			Preferences.VirtualControlsMode.ASK -> binding.root.post { askAboutVirtualControls() }
		}
	}

	private fun askAboutVirtualControls()
	{
		if(isFinishing || isDestroyed)
			return
		MaterialAlertDialogBuilder(this)
			.setTitle(R.string.virtual_controls_ask_title)
			.setMessage(R.string.virtual_controls_ask_message)
			.setPositiveButton(R.string.virtual_controls_show) { _, _ -> viewModel.setVirtualControlsVisible(true) }
			.setNegativeButton(R.string.virtual_controls_hide) { _, _ -> viewModel.setVirtualControlsVisible(false) }
			.setOnCancelListener { viewModel.setVirtualControlsVisible(false) }
			.show()
	}

	private fun updateVirtualControlsForDeviceChange()
	{
		if(viewModel.preferences.virtualControlsMode != Preferences.VirtualControlsMode.HIDE_WITH_CONTROLLER)
			return
		if(controlsChoiceOverridden)
			return
		viewModel.setVirtualControlsVisible(!hasPhysicalController())
	}

	private fun hasPhysicalController(): Boolean
	{
		return InputDevice.getDeviceIds().any { id ->
			val device = InputDevice.getDevice(id) ?: return@any false
			(device.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
					(device.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
		}
	}

	private fun reconnect()
	{
		viewModel.session.shutdown()
		viewModel.session.resume()
	}

	private val hideSystemUIRunnable = Runnable {
		hideOverlay()
		if(viewModel.preferences.fullscreen)
			insetsController.hide(WindowInsetsCompat.Type.systemBars())
	}

	private fun showOverlay()
	{
		binding.overlay.animate().cancel()
		binding.overlay.isVisible = true
		binding.overlay.animate()
			.alpha(1.0f)
			.setListener(object: AnimatorListenerAdapter()
			{
				override fun onAnimationEnd(animation: Animator)
				{
					binding.overlay.alpha = 1.0f
				}
			})
		uiVisibilityHandler.removeCallbacks(hideSystemUIRunnable)
		uiVisibilityHandler.postDelayed(hideSystemUIRunnable, HIDE_UI_TIMEOUT_MS)
	}

	private fun hideOverlay()
	{
		binding.overlay.animate().cancel()
		binding.overlay.animate()
			.alpha(0.0f)
			.setListener(object: AnimatorListenerAdapter()
			{
				override fun onAnimationEnd(animation: Animator)
				{
					binding.overlay.isGone = true
				}
			})
	}

	override fun onWindowFocusChanged(hasFocus: Boolean)
	{
		super.onWindowFocusChanged(hasFocus)
		if(hasFocus)
			applyFullscreen()
	}

	private fun applyFullscreen()
	{
		if(viewModel.preferences.fullscreen)
			insetsController.hide(WindowInsetsCompat.Type.systemBars())
		else
			insetsController.show(WindowInsetsCompat.Type.systemBars())
		updateFullscreenToggle()
	}

	private fun toggleFullscreen()
	{
		viewModel.preferences.fullscreen = !viewModel.preferences.fullscreen
		applyFullscreen()
		showOverlay()
	}

	private fun updateFullscreenToggle()
	{
		val fullscreen = viewModel.preferences.fullscreen
		binding.fullscreenButton.setImageResource(
			if(fullscreen) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen)
		binding.fullscreenButton.contentDescription = getString(
			if(fullscreen) R.string.stream_fullscreen_exit else R.string.stream_fullscreen_enter)
	}

	private fun stopSessionAndFinish()
	{
		if(stopping)
			return
		stopping = true
		sessionPaused = true
		viewModel.session.shutdown()
		Toast.makeText(this, R.string.stream_stopped, Toast.LENGTH_SHORT).show()
		finish()
	}

	private var dialogContents: DialogContents? = null
	private var dialog: AlertDialog? = null
		set(value)
		{
			field = value
			if(value == null)
				dialogContents = null
		}

	private fun stateChanged(state: StreamState)
	{
		if(sessionPaused)
			return
		binding.progressBar.visibility = if(state == StreamStateConnecting) View.VISIBLE else View.GONE

		when(state)
		{
			is StreamStateQuit ->
			{
				if(dialogContents != StreamQuitDialog)
				{
					if(state.reason.isError)
					{
						dialog?.dismiss()
						val reasonStr = state.reasonString
						dialogContents = StreamQuitDialog
						dialog = MaterialAlertDialogBuilder(this)
							.setMessage(getString(R.string.alert_message_session_quit, state.reason.toString())
									+ (if(reasonStr != null) "\n$reasonStr" else ""))
							.setPositiveButton(R.string.action_reconnect) { _, _ ->
								reconnect()
							}
							.setNegativeButton(R.string.action_quit_session) { _, _ ->
								finish()
							}
							.setOnCancelListener { finish() }
							.setOnDismissListener { dialog = null }
							.create()
						dialog?.show()
					}
					else
						finish()
				}
			}

			is StreamStateCreateError ->
			{
				if(dialogContents != CreateErrorDialog)
				{
					dialog?.dismiss()
					dialogContents = CreateErrorDialog
					dialog = MaterialAlertDialogBuilder(this)
						.setMessage(getString(R.string.alert_message_session_create_error, state.error.errorCode.toString()))
						.setNegativeButton(R.string.action_quit_session) { _, _ -> finish() }
						.setOnCancelListener { finish() }
						.setOnDismissListener { dialog = null }
						.create()
					dialog?.show()
				}
			}

			is StreamStateLoginPinRequest ->
			{
				if(dialogContents != PinRequestDialog)
				{
					dialog?.dismiss()

					val view = layoutInflater.inflate(R.layout.dialog_login_pin, null)
					val pinEditText = view.findViewById<EditText>(R.id.pinEditText)

					dialogContents = PinRequestDialog
					dialog = MaterialAlertDialogBuilder(this)
						.setMessage(
							if(state.pinIncorrect)
								R.string.alert_message_login_pin_request_incorrect
							else
								R.string.alert_message_login_pin_request)
						.setView(view)
						.setPositiveButton(R.string.action_login_pin_connect) { _, _ ->
							viewModel.session.setLoginPin(pinEditText.text.toString())
						}
						.setNegativeButton(R.string.action_quit_session) { _, _ -> finish() }
						.setOnCancelListener { finish() }
						.setOnDismissListener { dialog = null }
						.create()
					dialog?.show()
				}
			}
			else ->{}
		}
	}

	private fun adjustTextureViewAspect(textureView: TextureView)
	{
		val trans = TextureViewTransform(viewModel.session.connectInfo.videoProfile, textureView)
		val resolution = trans.resolutionFor(TransformMode.fromButton(binding.displayModeToggle.checkedButtonId))
		Matrix().also {
			textureView.getTransform(it)
			it.setScale(resolution.width / trans.viewWidth, resolution.height / trans.viewHeight)
			it.postTranslate((trans.viewWidth - resolution.width) * 0.5f, (trans.viewHeight - resolution.height) * 0.5f)
			textureView.setTransform(it)
		}
	}

	private fun adjustSurfaceViewAspect()
	{
		val videoProfile = viewModel.session.connectInfo.videoProfile
		binding.aspectRatioLayout.aspectRatio = videoProfile.width.toFloat() / videoProfile.height.toFloat()
		binding.aspectRatioLayout.mode = TransformMode.fromButton(binding.displayModeToggle.checkedButtonId)
	}

	private fun adjustStreamViewAspect() = adjustSurfaceViewAspect()

	override fun dispatchKeyEvent(event: KeyEvent) = viewModel.input.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
	override fun onGenericMotionEvent(event: MotionEvent) = viewModel.input.onGenericMotionEvent(event) || super.onGenericMotionEvent(event)
}

enum class TransformMode
{
	FIT,
	STRETCH,
	ZOOM;

	companion object
	{
		fun fromButton(displayModeButtonId: Int)
			= when (displayModeButtonId)
			{
				R.id.display_mode_stretch_button -> STRETCH
				R.id.display_mode_zoom_button -> ZOOM
				else -> FIT
			}
	}
}

class TextureViewTransform(private val videoProfile: ConnectVideoProfile, private val textureView: TextureView)
{
	private val contentWidth : Float get() = videoProfile.width.toFloat()
	private val contentHeight : Float get() = videoProfile.height.toFloat()
	val viewWidth : Float get() = textureView.width.toFloat()
	val viewHeight : Float get() = textureView.height.toFloat()
	private val contentAspect : Float get() =  contentHeight / contentWidth

	fun resolutionFor(mode: TransformMode): Resolution
		= when(mode)
		{
			TransformMode.STRETCH -> strechedResolution
			TransformMode.ZOOM -> zoomedResolution
			TransformMode.FIT -> normalResolution
		}

	private val strechedResolution get() = Resolution(viewWidth, viewHeight)

	private val zoomedResolution get() =
		if(viewHeight > viewWidth * contentAspect)
		{
			val zoomFactor = viewHeight / contentHeight
			Resolution(contentWidth * zoomFactor, viewHeight)
		}
		else
		{
			val zoomFactor = viewWidth / contentWidth
			Resolution(viewWidth, contentHeight * zoomFactor)
		}

	private val normalResolution get() =
		if(viewHeight > viewWidth * contentAspect)
			Resolution(viewWidth, viewWidth * contentAspect)
		else
			Resolution(viewHeight / contentAspect, viewHeight)
}


data class Resolution(val width: Float, val height: Float)
