// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.main

import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.PopupMenu
import androidx.core.graphics.ColorUtils
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.metallic.chiaki.R
import com.metallic.chiaki.common.DiscoveredDisplayHost
import com.metallic.chiaki.common.DisplayHost
import com.metallic.chiaki.common.ManualDisplayHost
import com.metallic.chiaki.common.ext.inflate
import com.metallic.chiaki.databinding.ItemDisplayHostBinding
import com.metallic.chiaki.lib.DiscoveryHost

class DisplayHostDiffCallback(val old: List<DisplayHost>, val new: List<DisplayHost>): DiffUtil.Callback()
{
	override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) = (old[oldItemPosition] == new[newItemPosition])
	override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) = (old[oldItemPosition] == new[newItemPosition])
	override fun getOldListSize() = old.size
	override fun getNewListSize() = new.size
}

class DisplayHostRecyclerViewAdapter(
	val clickCallback: (DisplayHost) -> Unit,
	val wakeupCallback: (DisplayHost) -> Unit,
	val editCallback: (DisplayHost) -> Unit,
	val deleteCallback: (DisplayHost) -> Unit,
	val renameCallback: (DisplayHost) -> Unit
): RecyclerView.Adapter<DisplayHostRecyclerViewAdapter.ViewHolder>()
{
	var hosts: List<DisplayHost> = listOf()
		set(value)
		{
			val diff = DiffUtil.calculateDiff(DisplayHostDiffCallback(field, value))
			field = value
			diff.dispatchUpdatesTo(this)
		}

	class ViewHolder(val binding: ItemDisplayHostBinding): RecyclerView.ViewHolder(binding.root)

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int)
		= ViewHolder(ItemDisplayHostBinding.inflate(LayoutInflater.from(parent.context), parent, false))

	override fun getItemCount() = hosts.count()

	override fun onBindViewHolder(holder: ViewHolder, position: Int)
	{
		val context = holder.itemView.context
		val host = hosts[position]
		val accent = MaterialColors.getColor(holder.itemView, androidx.appcompat.R.attr.colorAccent)
		val density = context.resources.displayMetrics.density
		holder.binding.also {
			it.nameTextView.text = host.name?.ifEmpty { context.getString(R.string.manual_console_name) }
				?: context.getString(R.string.manual_console_name)
			it.hostTextView.text = context.getString(R.string.display_host_host, host.host)
			val id = host.id
			it.idTextView.text =
				if(id != null)
					context.getString(
						if(host.isRegistered)
							R.string.display_host_id_registered
						else
							R.string.display_host_id_unregistered,
						id)
				else
					""
			it.bottomInfoTextView.text = (host as? DiscoveredDisplayHost)?.discoveredHost?.let { discoveredHost ->
				if(discoveredHost.runningAppName != null || discoveredHost.runningAppTitleid != null)
					context.getString(R.string.display_host_app_title_id, discoveredHost.runningAppName ?: "", discoveredHost.runningAppTitleid ?: "")
				else
					""
			} ?: ""
			it.iconBadge.background = GradientDrawable().apply {
				cornerRadius = 16f * density
				setColor(ColorUtils.setAlphaComponent(accent, 0x24))
				setStroke((1f * density).toInt(), ColorUtils.setAlphaComponent(accent, 0x40))
			}
			val discoveredState = (host as? DiscoveredDisplayHost)?.discoveredHost?.state
			if(discoveredState == DiscoveryHost.State.READY || discoveredState == DiscoveryHost.State.STANDBY)
			{
				val ready = discoveredState == DiscoveryHost.State.READY
				val chipColor = if(ready)
					accent
				else
					MaterialColors.getColor(it.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
				it.statusTextView.visibility = View.VISIBLE
				it.statusTextView.setText(if(ready) R.string.console_status_ready else R.string.console_status_standby)
				it.statusTextView.setTextColor(chipColor)
				it.statusTextView.background = GradientDrawable().apply {
					cornerRadius = 100f * density
					setColor(ColorUtils.setAlphaComponent(chipColor, 0x26))
				}
			}
			else
				it.statusTextView.visibility = View.GONE
			it.stateIndicatorImageView.setImageResource(
				when
				{
					host is DiscoveredDisplayHost -> when(host.discoveredHost.state)
					{
						DiscoveryHost.State.STANDBY -> if(host.isPS5) R.drawable.ic_console_ps5_standby else R.drawable.ic_console_standby
						DiscoveryHost.State.READY -> if(host.isPS5) R.drawable.ic_console_ps5_ready else R.drawable.ic_console_ready
						else -> if(host.isPS5) R.drawable.ic_console_ps5 else R.drawable.ic_console
					}
					host.isPS5 -> R.drawable.ic_console_ps5
					else -> R.drawable.ic_console_simple
				}
			)
			it.root.setOnClickListener { clickCallback(host) }

			val canWakeup = host.registeredHost != null
			val canEditDelete = host is ManualDisplayHost
			if(canWakeup || canEditDelete || host.canRename)
			{
				it.menuButton.isVisible = true
				it.menuButton.setOnClickListener { _ ->
					val menu = PopupMenu(context, it.menuButton)
					menu.menuInflater.inflate(R.menu.display_host, menu.menu)
					menu.menu.findItem(R.id.action_wakeup).isVisible = canWakeup
					menu.menu.findItem(R.id.action_edit).isVisible = canEditDelete
					menu.menu.findItem(R.id.action_delete).isVisible = canEditDelete
					menu.menu.findItem(R.id.action_rename).isVisible = host.canRename
					menu.setOnMenuItemClickListener { menuItem ->
						when(menuItem.itemId)
						{
							R.id.action_wakeup -> wakeupCallback(host)
							R.id.action_edit -> editCallback(host)
							R.id.action_delete -> deleteCallback(host)
							R.id.action_rename -> renameCallback(host)
							else -> return@setOnMenuItemClickListener false
						}
						true
					}
					menu.show()
				}
			}
			else
			{
				it.menuButton.isGone = true
				it.menuButton.setOnClickListener(null)
			}
		}
	}
}