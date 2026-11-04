// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.common

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Base activity that applies the user's selected color theme before the view hierarchy
 * is created, and recreates itself if the theme changes while it is in the background.
 */
abstract class ThemedActivity: AppCompatActivity()
{
	private var appliedTheme = Preferences.Theme.ELECTRIC_VIOLET

	override fun onCreate(savedInstanceState: Bundle?)
	{
		val theme = Preferences(this).theme
		appliedTheme = theme
		ThemeManager.apply(this, theme)
		super.onCreate(savedInstanceState)
	}

	override fun onResume()
	{
		super.onResume()
		val theme = Preferences(this).theme
		if(theme != appliedTheme)
		{
			appliedTheme = theme
			recreate()
		}
	}
}
