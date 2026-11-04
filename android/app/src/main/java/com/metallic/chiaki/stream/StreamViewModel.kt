// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.stream

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import com.metallic.chiaki.common.LogManager
import com.metallic.chiaki.session.StreamSession
import com.metallic.chiaki.common.Preferences
import com.metallic.chiaki.lib.*
import com.metallic.chiaki.session.StreamInput

class StreamViewModel(val application: Application, val connectInfo: ConnectInfo): ViewModel()
{
	val preferences = Preferences(application)
	val logManager = LogManager(application)

	val input = StreamInput(application, preferences)
	val session = StreamSession(connectInfo, logManager, preferences.logVerbose, input)

	private val _virtualControlsVisible = MutableLiveData<Boolean>(true)
	val virtualControlsVisible: LiveData<Boolean> get() = _virtualControlsVisible

	private val _touchpadOnlyEnabled = MutableLiveData<Boolean>(preferences.touchpadOnlyEnabled)
	val touchpadOnlyEnabled: LiveData<Boolean> get() = _touchpadOnlyEnabled

	val defaultControlsVisible = MediatorLiveData<Boolean>().apply {
		fun update() { value = (_virtualControlsVisible.value ?: true) && !(_touchpadOnlyEnabled.value ?: false) }
		addSource(_virtualControlsVisible) { update() }
		addSource(_touchpadOnlyEnabled) { update() }
	}

	val touchpadControlsVisible = MediatorLiveData<Boolean>().apply {
		fun update() { value = (_virtualControlsVisible.value ?: true) && (_touchpadOnlyEnabled.value ?: false) }
		addSource(_virtualControlsVisible) { update() }
		addSource(_touchpadOnlyEnabled) { update() }
	}

	override fun onCleared()
	{
		super.onCleared()
		session.shutdown()
	}

	fun setVirtualControlsVisible(visible: Boolean)
	{
		_virtualControlsVisible.value = visible
	}

	fun setTouchpadOnlyEnabled(enabled: Boolean)
	{
		preferences.touchpadOnlyEnabled = enabled
		_touchpadOnlyEnabled.value = enabled
	}
}