// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.common

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import com.metallic.chiaki.R

object ThemeManager
{
	/** Colors resolved from a theme overlay, used to draw preview swatches. */
	data class Palette(
		val background: Int,
		val surface: Int,
		val accent: Int,
		val onSurface: Int,
		val onSurfaceVariant: Int,
		val outline: Int
	)

	fun overlayFor(theme: Preferences.Theme): Int = when(theme)
	{
		Preferences.Theme.ANOMALY -> R.style.ThemeOverlay_Anomaly_Cyan
		Preferences.Theme.ELECTRIC_VIOLET -> R.style.ThemeOverlay_Anomaly_ElectricViolet
		Preferences.Theme.GRAPHITE -> R.style.ThemeOverlay_Anomaly_Graphite
		Preferences.Theme.EMERALD -> R.style.ThemeOverlay_Anomaly_Emerald
		Preferences.Theme.RUST -> R.style.ThemeOverlay_Anomaly_Rust
		Preferences.Theme.HONEY -> R.style.ThemeOverlay_Anomaly_Honey
		Preferences.Theme.LAPIZ -> R.style.ThemeOverlay_Anomaly_Lapiz
	}

	fun apply(activity: AppCompatActivity, theme: Preferences.Theme)
	{
		activity.theme.applyStyle(overlayFor(theme), true)
	}

	@SuppressLint("ResourceType")
	fun palette(context: Context, theme: Preferences.Theme): Palette
	{
		val wrapper = ContextThemeWrapper(context, R.style.AppTheme)
		applyOverlay(wrapper.theme, theme)
		val attrs = wrapper.obtainStyledAttributes(intArrayOf(
			android.R.attr.colorBackground,
			com.google.android.material.R.attr.colorSurface,
			androidx.appcompat.R.attr.colorAccent,
			com.google.android.material.R.attr.colorOnSurface,
			com.google.android.material.R.attr.colorOnSurfaceVariant,
			com.google.android.material.R.attr.colorOutline
		))
		try
		{
			return Palette(
				attrs.getColor(0, Color.BLACK),
				attrs.getColor(1, Color.BLACK),
				attrs.getColor(2, Color.WHITE),
				attrs.getColor(3, Color.WHITE),
				attrs.getColor(4, Color.LTGRAY),
				attrs.getColor(5, Color.DKGRAY)
			)
		}
		finally
		{
			attrs.recycle()
		}
	}

	private fun applyOverlay(theme: android.content.res.Resources.Theme, themeId: Preferences.Theme)
	{
		theme.applyStyle(overlayFor(themeId), true)
	}
}
