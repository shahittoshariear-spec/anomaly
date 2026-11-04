// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.settings

import android.os.Bundle
import androidx.recyclerview.widget.GridLayoutManager
import com.metallic.chiaki.common.Preferences
import com.metallic.chiaki.common.ThemedActivity
import com.metallic.chiaki.databinding.ActivityThemePickerBinding

/**
 * Full-screen palette picker. Selecting a card writes the preference and rebuilds this
 * screen with a crossfade, so the new colors are visible immediately.
 */
class ThemePickerActivity: ThemedActivity()
{
	private lateinit var binding: ActivityThemePickerBinding

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		binding = ActivityThemePickerBinding.inflate(layoutInflater)
		setContentView(binding.root)

		setSupportActionBar(binding.toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		supportActionBar?.setDisplayShowHomeEnabled(true)
		binding.toolbar.setNavigationOnClickListener { finish() }

		val preferences = Preferences(this)
		val adapter = ThemePreviewAdapter { theme ->
			if(theme != preferences.theme)
			{
				preferences.theme = theme
				recreateWithThemeTransition()
			}
		}
		adapter.selected = preferences.theme

		binding.themeRecyclerView.layoutManager = GridLayoutManager(this, 2)
		binding.themeRecyclerView.adapter = adapter
		binding.themeRecyclerView.itemAnimator = null
	}
}
