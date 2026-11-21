// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.update

import androidx.fragment.app.FragmentActivity
import android.widget.ProgressBar
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.metallic.chiaki.R
import com.metallic.chiaki.common.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object UpdateDialogs
{
	private val checking = AtomicBoolean(false)

	/**
	 * Look for a newer release. In silent mode nothing is shown unless an update
	 * exists, which is what the startup check uses.
	 */
	fun check(activity: FragmentActivity, silent: Boolean)
	{
		if(!checking.compareAndSet(false, true))
			return

		val appContext = activity.applicationContext
		val current = UpdateChecker.currentVersion(appContext)

		activity.lifecycleScope.launch {
			val info = withContext(Dispatchers.IO) {
				try { UpdateChecker.fetchLatest() } catch(e: Exception) { null }
			}
			Preferences(appContext).lastUpdateCheck = System.currentTimeMillis()
			checking.set(false)

			if(info == null)
			{
				if(!silent)
					toast(activity, R.string.update_check_failed)
				return@launch
			}

			if(!UpdateChecker.isNewer(info.version, current))
			{
				if(!silent)
					toast(activity, R.string.update_up_to_date)
				return@launch
			}

			showAvailable(activity, info, current)
		}
	}

	private fun showAvailable(activity: FragmentActivity, info: UpdateInfo, current: String)
	{
		val notes = info.notes
			.takeIf { it.isNotBlank() }
			?.let { if(it.length > 1200) it.take(1200) + "…" else it }
			?: activity.getString(R.string.update_available_message)

		MaterialAlertDialogBuilder(activity)
			.setTitle(activity.getString(R.string.update_available_title, info.version))
			.setMessage(activity.getString(R.string.update_current_version, current) + "\n\n" + notes)
			.setPositiveButton(R.string.update_install) { _, _ -> downloadAndInstall(activity, info) }
			.setNegativeButton(R.string.update_later, null)
			.setNeutralButton(R.string.update_browser) { _, _ ->
				runCatching {
					activity.startActivity(android.content.Intent(
						android.content.Intent.ACTION_VIEW,
						android.net.Uri.parse(info.pageUrl)
					))
				}
			}
			.show()
	}

	private fun downloadAndInstall(activity: FragmentActivity, info: UpdateInfo)
	{
		if(!UpdateInstaller.canInstall(activity))
		{
			MaterialAlertDialogBuilder(activity)
				.setTitle(R.string.update_permission_title)
				.setMessage(R.string.update_permission_message)
				.setPositiveButton(R.string.update_permission_settings) { _, _ ->
					UpdateInstaller.openPermissionSettings(activity)
				}
				.setNegativeButton(android.R.string.cancel, null)
				.show()
			return
		}

		val progressBar = ProgressBar(
			activity, null, android.R.attr.progressBarStyleHorizontal
		).apply {
			isIndeterminate = true
			max = 100
		}

		val padding = (16 * activity.resources.displayMetrics.density).toInt()
		val container = android.widget.FrameLayout(activity).apply {
			setPadding(padding, padding / 2, padding, 0)
			addView(progressBar, android.widget.FrameLayout.LayoutParams(
				android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
				android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
			))
		}

		val dialog = MaterialAlertDialogBuilder(activity)
			.setTitle(R.string.update_downloading)
			.setView(container)
			.setCancelable(false)
			.create()
		dialog.show()

		val lastPercent = AtomicLong(-1)

		activity.lifecycleScope.launch {
			try
			{
				val file = withContext(Dispatchers.IO) {
					UpdateInstaller.download(activity.applicationContext, info.apkUrl, info.apkName) { downloaded, total ->
						if(total > 0)
						{
							val percent = (downloaded * 100 / total).coerceIn(0, 100)
							if(lastPercent.getAndSet(percent) != percent)
								activity.runOnUiThread {
									progressBar.isIndeterminate = false
									progressBar.progress = percent.toInt()
								}
						}
					}
				}

				dialog.dismiss()
				UpdateInstaller.install(activity.applicationContext, file)
			}
			catch(e: Exception)
			{
				dialog.dismiss()
				toast(
					activity,
					activity.getString(R.string.update_download_failed, e.message ?: e.javaClass.simpleName)
				)
			}
		}
	}

	private fun toast(activity: FragmentActivity, stringRes: Int) =
		toast(activity, activity.getString(stringRes))

	private fun toast(activity: FragmentActivity, message: String)
	{
		if(activity.isFinishing || activity.isDestroyed)
			return
		Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
	}
}
