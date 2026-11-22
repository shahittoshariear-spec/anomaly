// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.common

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import com.metallic.chiaki.R
import com.metallic.chiaki.lib.Codec
import com.metallic.chiaki.lib.ConnectVideoProfile
import com.metallic.chiaki.lib.ControllerState
import com.metallic.chiaki.lib.VideoFPSPreset
import com.metallic.chiaki.lib.VideoResolutionPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min

class Preferences(context: Context)
{
	enum class Resolution(val value: String, @StringRes val title: Int, val preset: VideoResolutionPreset)
	{
		RES_360P("360p", R.string.preferences_resolution_title_360p, VideoResolutionPreset.RES_360P),
		RES_540P("540p", R.string.preferences_resolution_title_540p, VideoResolutionPreset.RES_540P),
		RES_720P("720p", R.string.preferences_resolution_title_720p, VideoResolutionPreset.RES_720P),
		RES_1080P("1080p", R.string.preferences_resolution_title_1080p, VideoResolutionPreset.RES_1080P),
	}

	enum class FPS(val value: String, @StringRes val title: Int, val preset: VideoFPSPreset)
	{
		FPS_30("30", R.string.preferences_fps_title_30, VideoFPSPreset.FPS_30),
		FPS_60("60", R.string.preferences_fps_title_60, VideoFPSPreset.FPS_60)
	}

	enum class Codec(val value: String, @StringRes val title: Int, val codec: com.metallic.chiaki.lib.Codec)
	{
		CODEC_H264("h264", R.string.preferences_codec_title_h264, com.metallic.chiaki.lib.Codec.CODEC_H264),
		CODEC_H265("h265", R.string.preferences_codec_title_h265, com.metallic.chiaki.lib.Codec.CODEC_H265)
	}

	companion object
	{
		val resolutionDefault = Resolution.RES_720P
		val resolutionAll = Resolution.values()
		val fpsDefault = FPS.FPS_60
		val fpsAll = FPS.values()
		val codecDefault = Codec.CODEC_H265
		val codecAll = Codec.values()
	}

	enum class VirtualControlsMode(val value: String, @StringRes val title: Int)
	{
		ALWAYS_SHOW("always_show", R.string.virtual_controls_always_show),
		HIDE_WITH_CONTROLLER("hide_with_controller", R.string.virtual_controls_hide_with_controller),
		ALWAYS_HIDE("always_hide", R.string.virtual_controls_always_hide),
		ASK("ask", R.string.virtual_controls_ask);

		companion object
		{
			val all = values()
			fun fromValue(value: String?) = all.firstOrNull { it.value == value } ?: HIDE_WITH_CONTROLLER
		}
	}

	enum class Theme(val value: String, @StringRes val title: Int)
	{
		ANOMALY("anomaly", R.string.theme_anomaly),
		ELECTRIC_VIOLET("electric_violet", R.string.theme_electric_violet),
		GRAPHITE("graphite", R.string.theme_graphite),
		EMERALD("emerald", R.string.theme_emerald),
		RUST("rust", R.string.theme_rust),
		HONEY("honey", R.string.theme_honey),
		LAPIZ("lapiz", R.string.theme_lapiz);

		companion object
		{
			val all = values()
			fun fromValue(value: String?) = all.firstOrNull { it.value == value } ?: ELECTRIC_VIOLET
		}
	}

	/**
	 * Controllers such as the EASYSMX M20 send their PS/Home/dock button as a regular
	 * key event. Android reports it as BUTTON_MODE or BUTTON_C depending on the pad, so
	 * both are routed through this mapping. `null` means the press is swallowed.
	 */
	enum class DockButton(val value: String, @StringRes val title: Int, val buttonMask: UInt?)
	{
		DEFAULT("default", R.string.dock_button_default, ControllerState.BUTTON_PS),
		OPTIONS("options", R.string.dock_button_options, ControllerState.BUTTON_OPTIONS),
		SHARE("share", R.string.dock_button_share, ControllerState.BUTTON_SHARE),
		TOUCHPAD("touchpad", R.string.dock_button_touchpad, ControllerState.BUTTON_TOUCHPAD),
		L3("l3", R.string.dock_button_l3, ControllerState.BUTTON_L3),
		R3("r3", R.string.dock_button_r3, ControllerState.BUTTON_R3),
		L2("l2", R.string.dock_button_l2, null),
		R2("r2", R.string.dock_button_r2, null),
		CROSS("cross", R.string.dock_button_cross, ControllerState.BUTTON_CROSS),
		MOON("moon", R.string.dock_button_moon, ControllerState.BUTTON_MOON),
		BOX("box", R.string.dock_button_box, ControllerState.BUTTON_BOX),
		PYRAMID("pyramid", R.string.dock_button_pyramid, ControllerState.BUTTON_PYRAMID),
		L1("l1", R.string.dock_button_l1, ControllerState.BUTTON_L1),
		R1("r1", R.string.dock_button_r1, ControllerState.BUTTON_R1),
		NONE("none", R.string.dock_button_none, null);

		companion object
		{
			val all = values()
			fun fromValue(value: String?) = all.firstOrNull { it.value == value } ?: DEFAULT
		}
	}

	private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
	private val sharedPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
		when(key)
		{
			resolutionKey, fpsKey, codecKey -> _bitrateAutoFlow.value = bitrateAuto
		}
	}.also { sharedPreferences.registerOnSharedPreferenceChangeListener(it) }

	private val resources = context.resources

	val discoveryEnabledKey get() = resources.getString(R.string.preferences_discovery_enabled_key)
	var discoveryEnabled
		get() = sharedPreferences.getBoolean(discoveryEnabledKey, true)
		set(value) { sharedPreferences.edit().putBoolean(discoveryEnabledKey, value).apply() }

	val onScreenControlsEnabledKey get() = resources.getString(R.string.preferences_on_screen_controls_enabled_key)
	var onScreenControlsEnabled
		get() = sharedPreferences.getBoolean(onScreenControlsEnabledKey, true)
		set(value) { sharedPreferences.edit().putBoolean(onScreenControlsEnabledKey, value).apply() }

	val virtualControlsModeKey get() = resources.getString(R.string.preferences_virtual_controls_mode_key)
	var virtualControlsMode: VirtualControlsMode
		get()
		{
			val stored = sharedPreferences.getString(virtualControlsModeKey, null)
			if(stored != null)
				return VirtualControlsMode.fromValue(stored)
			// Migrate the old on/off switch: off meant the user wanted them hidden.
			return if(onScreenControlsEnabled) VirtualControlsMode.HIDE_WITH_CONTROLLER else VirtualControlsMode.ALWAYS_HIDE
		}
		set(value) { sharedPreferences.edit().putString(virtualControlsModeKey, value.value).apply() }

	val themeKey get() = resources.getString(R.string.preferences_theme_key)
	var theme: Theme
		get() = Theme.fromValue(sharedPreferences.getString(themeKey, null))
		set(value) { sharedPreferences.edit().putString(themeKey, value.value).apply() }

	val fullscreenKey get() = resources.getString(R.string.preferences_fullscreen_key)
	var fullscreen
		get() = sharedPreferences.getBoolean(fullscreenKey, true)
		set(value) { sharedPreferences.edit().putBoolean(fullscreenKey, value).apply() }

	val autoLandscapeKey get() = resources.getString(R.string.preferences_auto_landscape_key)
	var autoLandscape
		get() = sharedPreferences.getBoolean(autoLandscapeKey, true)
		set(value) { sharedPreferences.edit().putBoolean(autoLandscapeKey, value).apply() }

	val autoUpdateCheckKey get() = resources.getString(R.string.update_auto_check_key)
	var autoUpdateCheck
		get() = sharedPreferences.getBoolean(autoUpdateCheckKey, true)
		set(value) { sharedPreferences.edit().putBoolean(autoUpdateCheckKey, value).apply() }

	private val lastUpdateCheckKey = "last_update_check"
	var lastUpdateCheck
		get() = sharedPreferences.getLong(lastUpdateCheckKey, 0L)
		set(value) { sharedPreferences.edit().putLong(lastUpdateCheckKey, value).apply() }

	val touchpadOnlyEnabledKey get() = resources.getString(R.string.preferences_touchpad_only_enabled_key)
	var touchpadOnlyEnabled
		get() = sharedPreferences.getBoolean(touchpadOnlyEnabledKey, false)
		set(value) { sharedPreferences.edit().putBoolean(touchpadOnlyEnabledKey, value).apply() }

	val rumbleEnabledKey get() = resources.getString(R.string.preferences_rumble_enabled_key)
	var rumbleEnabled
		get() = sharedPreferences.getBoolean(rumbleEnabledKey, true)
		set(value) { sharedPreferences.edit().putBoolean(rumbleEnabledKey, value).apply() }

	val motionEnabledKey get() = resources.getString(R.string.preferences_motion_enabled_key)
	var motionEnabled
		get() = sharedPreferences.getBoolean(motionEnabledKey, false)
		set(value) { sharedPreferences.edit().putBoolean(motionEnabledKey, value).apply() }

	val buttonHapticEnabledKey get() = resources.getString(R.string.preferences_button_haptic_enabled_key)
	var buttonHapticEnabled
		get() = sharedPreferences.getBoolean(buttonHapticEnabledKey, true)
		set(value) { sharedPreferences.edit().putBoolean(buttonHapticEnabledKey, value).apply() }

	val logVerboseKey get() = resources.getString(R.string.preferences_log_verbose_key)
	var logVerbose
		get() = sharedPreferences.getBoolean(logVerboseKey, false)
		set(value) { sharedPreferences.edit().putBoolean(logVerboseKey, value).apply() }

	val swapCrossMoonKey get() = resources.getString(R.string.preferences_swap_cross_moon_key)
	var swapCrossMoon
		get() = sharedPreferences.getBoolean(swapCrossMoonKey, false)
		set(value) { sharedPreferences.edit().putBoolean(swapCrossMoonKey, value).apply() }

	val dockButtonKey get() = resources.getString(R.string.preferences_dock_button_key)
	var dockButton: DockButton
		get() = DockButton.fromValue(sharedPreferences.getString(dockButtonKey, null))
		set(value) { sharedPreferences.edit().putString(dockButtonKey, value.value).apply() }

	val resolutionKey get() = resources.getString(R.string.preferences_resolution_key)
	var resolution
		get() = sharedPreferences.getString(resolutionKey, resolutionDefault.value)?.let { value ->
			Resolution.values().firstOrNull { it.value == value }
		} ?: resolutionDefault
		set(value) { sharedPreferences.edit().putString(resolutionKey, value.value).apply() }

	val fpsKey get() = resources.getString(R.string.preferences_fps_key)
	var fps
		get() = sharedPreferences.getString(fpsKey, fpsDefault.value)?.let { value ->
			FPS.values().firstOrNull { it.value == value }
		}  ?: fpsDefault
		set(value) { sharedPreferences.edit().putString(fpsKey, value.value).apply() }

	fun validateBitrate(bitrate: Int) = max(2000, min(50000, bitrate))
	val bitrateKey get() = resources.getString(R.string.preferences_bitrate_key)
	var bitrate
		get() = sharedPreferences.getInt(bitrateKey, 0).let { if(it == 0) null else validateBitrate(it) }
		set(value) { sharedPreferences.edit().putInt(bitrateKey, if(value != null) validateBitrate(value) else 0).apply() }
	val bitrateAuto get() = videoProfileDefaultBitrate.bitrate
	private val _bitrateAutoFlow by lazy { MutableStateFlow(bitrateAuto) }
	val bitrateAutoFlow: StateFlow<Int> get() = _bitrateAutoFlow.asStateFlow()

	val codecKey get() = resources.getString(R.string.preferences_codec_key)
	var codec
		get() = sharedPreferences.getString(codecKey, codecDefault.value)?.let { value ->
			Codec.values().firstOrNull { it.value == value }
		}  ?: codecDefault
		set(value) { sharedPreferences.edit().putString(codecKey, value.value).apply() }

	private val videoProfileDefaultBitrate get() = ConnectVideoProfile.preset(resolution.preset, fps.preset, codec.codec)
	val videoProfile get() = videoProfileDefaultBitrate.let {
		val bitrate = bitrate
		if(bitrate == null)
			it
		else
			it.copy(bitrate = bitrate)
	}
}