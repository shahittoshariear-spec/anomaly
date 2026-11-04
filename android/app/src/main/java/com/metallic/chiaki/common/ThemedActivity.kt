// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

/**
 * Holds the screen capture taken just before a theme change so the rebuilt activity can
 * fade in over it. This turns `recreate()` into a smooth crossfade instead of a flash.
 */
private object ThemeTransitionSnapshot
{
	var bitmap: Bitmap? = null
}

/**
 * Base activity that applies the user's selected color theme before the view hierarchy
 * is created, and recreates itself if the theme changes while it is in the background.
 */
abstract class ThemedActivity: AppCompatActivity()
{
	private var appliedTheme = Preferences.Theme.ELECTRIC_VIOLET
	private var snapshotOverlay: ImageView? = null
	private var snapshotBitmap: Bitmap? = null

	override fun onCreate(savedInstanceState: Bundle?)
	{
		val theme = Preferences(this).theme
		appliedTheme = theme
		ThemeManager.apply(this, theme)
		super.onCreate(savedInstanceState)
		window.decorView.post { fadeInFromSnapshot() }
	}

	override fun onResume()
	{
		super.onResume()
		val theme = Preferences(this).theme
		if(theme != appliedTheme)
		{
			appliedTheme = theme
			captureSnapshot()
			recreate()
		}
	}

	/**
	 * Rebuilds the activity with the current theme while crossfading from a capture of the
	 * old screen, so color changes feel instant rather than like a restart.
	 */
	protected fun recreateWithThemeTransition()
	{
		if(isFinishing || isDestroyed)
			return
		captureSnapshot()
		if(Build.VERSION.SDK_INT >= 34)
			overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
		else
			@Suppress("DEPRECATION")
			overridePendingTransition(0, 0)
		recreate()
	}

	private fun captureSnapshot()
	{
		val decor = window.decorView
		if(decor.width <= 0 || decor.height <= 0)
			return
		try
		{
			val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
			decor.draw(Canvas(bitmap))
			ThemeTransitionSnapshot.bitmap?.recycle()
			ThemeTransitionSnapshot.bitmap = bitmap
		}
		catch(_: OutOfMemoryError)
		{
			ThemeTransitionSnapshot.bitmap = null
		}
	}

	private fun fadeInFromSnapshot()
	{
		val snapshot = ThemeTransitionSnapshot.bitmap ?: return
		ThemeTransitionSnapshot.bitmap = null
		val root = window.decorView as? ViewGroup ?: return
		val overlay = ImageView(this).apply {
			setImageBitmap(snapshot)
			scaleType = ImageView.ScaleType.FIT_XY
			isClickable = false
			isFocusable = false
			importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
		}
		snapshotOverlay = overlay
		snapshotBitmap = snapshot
		root.addView(overlay, ViewGroup.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
		overlay.alpha = 1f
		overlay.animate()
			.alpha(0f)
			.setDuration(300L)
			.setInterpolator(DecelerateInterpolator())
			.withEndAction {
				root.removeView(overlay)
				if(!snapshot.isRecycled)
					snapshot.recycle()
				if(snapshotOverlay === overlay)
				{
					snapshotOverlay = null
					snapshotBitmap = null
				}
			}
			.start()
	}

	override fun onDestroy()
	{
		snapshotOverlay?.let { overlay ->
			(overlay.parent as? ViewGroup)?.removeView(overlay)
		}
		snapshotOverlay = null
		snapshotBitmap?.takeIf { !it.isRecycled }?.recycle()
		snapshotBitmap = null
		super.onDestroy()
	}
}
