// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.settings

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.metallic.chiaki.R
import com.metallic.chiaki.common.Preferences
import com.metallic.chiaki.common.ThemeManager
import com.metallic.chiaki.databinding.ItemThemePaletteBinding

class ThemePreviewAdapter(
	private val onSelect: (Preferences.Theme) -> Unit
): RecyclerView.Adapter<ThemePreviewAdapter.ViewHolder>()
{
	private val themes = Preferences.Theme.all

	var selected: Preferences.Theme = Preferences.Theme.ELECTRIC_VIOLET
		set(value)
		{
			if(field == value)
				return
			field = value
			notifyDataSetChanged()
		}

	inner class ViewHolder(val binding: ItemThemePaletteBinding):
		RecyclerView.ViewHolder(binding.root)

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
		ViewHolder(ItemThemePaletteBinding.inflate(LayoutInflater.from(parent.context), parent, false))

	override fun getItemCount() = themes.size

	override fun onBindViewHolder(holder: ViewHolder, position: Int)
	{
		val theme = themes[position]
		val context = holder.itemView.context
		val palette = ThemeManager.palette(context, theme)
		val density = context.resources.displayMetrics.density
		val title = context.getString(theme.title)

		holder.binding.previewLayout.background = GradientDrawable().apply {
			cornerRadius = 16f * density
			setColor(palette.background)
		}
		holder.binding.surfaceChip.background = GradientDrawable().apply {
			cornerRadius = 12f * density
			setColor(palette.surface)
			setStroke((1f * density).toInt(), palette.outline)
		}
		holder.binding.accentDot.background = GradientDrawable().apply {
			shape = GradientDrawable.OVAL
			setColor(palette.accent)
		}

		holder.binding.nameTextView.text = title
		holder.itemView.contentDescription = title

		val isSelected = theme == selected
		holder.binding.checkImageView.visibility =
			if(isSelected) View.VISIBLE else View.INVISIBLE
		holder.binding.themeCard.strokeWidth =
			((if(isSelected) 2f else 1f) * density).toInt()
		holder.binding.themeCard.strokeColor =
			if(isSelected)
				palette.accent
			else
				MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOutline)

		holder.binding.themeCard.setOnClickListener {
			if(theme != selected)
				onSelect(theme)
		}
	}
}
