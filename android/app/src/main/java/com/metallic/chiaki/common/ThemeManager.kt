// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.common

import androidx.appcompat.app.AppCompatActivity
import com.metallic.chiaki.R

object ThemeManager
{
	fun apply(activity: AppCompatActivity, theme: Preferences.Theme)
	{
		val overlay = when(theme)
		{
			Preferences.Theme.ANOMALY -> R.style.ThemeOverlay_Anomaly_Cyan
			Preferences.Theme.ELECTRIC_VIOLET -> R.style.ThemeOverlay_Anomaly_ElectricViolet
			Preferences.Theme.GRAPHITE -> R.style.ThemeOverlay_Anomaly_Graphite
			Preferences.Theme.EMERALD -> R.style.ThemeOverlay_Anomaly_Emerald
			Preferences.Theme.RUST -> R.style.ThemeOverlay_Anomaly_Rust
			Preferences.Theme.HONEY -> R.style.ThemeOverlay_Anomaly_Honey
			Preferences.Theme.LAPIZ -> R.style.ThemeOverlay_Anomaly_Lapiz
		}
		if(overlay != 0)
			activity.theme.applyStyle(overlay, true)
	}
}
