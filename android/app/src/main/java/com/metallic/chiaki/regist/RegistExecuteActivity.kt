// SPDX-License-Identifier: AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.regist

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.metallic.chiaki.common.ThemedActivity
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.metallic.chiaki.R
import com.metallic.chiaki.common.MacAddress
import com.metallic.chiaki.common.ext.viewModelFactory
import com.metallic.chiaki.common.getDatabase
import com.metallic.chiaki.databinding.ActivityRegistExecuteBinding
import com.metallic.chiaki.lib.RegistInfo

class RegistExecuteActivity: ThemedActivity()
{
	companion object
	{
		const val EXTRA_REGIST_INFO = "regist_info"
		const val EXTRA_ASSIGN_MANUAL_HOST_ID = "assign_manual_host_id"

		const val RESULT_FAILED = Activity.RESULT_FIRST_USER
		private const val SUCCESS_RETURN_DELAY_MS = 1600L
	}

	private lateinit var viewModel: RegistExecuteViewModel
	private lateinit var binding: ActivityRegistExecuteBinding
	private val handler = Handler(Looper.getMainLooper())
	private var resultSet = false
	private var latestLog = ""
	private var duplicateDialog: AlertDialog? = null

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		binding = ActivityRegistExecuteBinding.inflate(layoutInflater)
		setContentView(binding.root)

		viewModel = ViewModelProvider(this, viewModelFactory { RegistExecuteViewModel(getDatabase(this)) })
			.get(RegistExecuteViewModel::class.java)

		viewModel.logText.observe(this, Observer { latestLog = it })

		viewModel.state.observe(this, Observer { state ->
			when(state)
			{
				RegistExecuteViewModel.State.RUNNING -> showRunning()
				RegistExecuteViewModel.State.SUCCESSFUL -> showSuccess()
				RegistExecuteViewModel.State.SUCCESSFUL_DUPLICATE -> showDuplicate()
				RegistExecuteViewModel.State.FAILED -> showFailed()
				RegistExecuteViewModel.State.STOPPED -> finishWithResult(Activity.RESULT_CANCELED)
				RegistExecuteViewModel.State.IDLE -> {}
			}
		})

		binding.detailsButton.setOnClickListener { showDetails() }
		binding.doneButton.setOnClickListener { finish() }

		val registInfo = IntentCompat.getParcelableExtra(intent, EXTRA_REGIST_INFO, RegistInfo::class.java)
		if(registInfo == null)
		{
			finish()
			return
		}
		viewModel.start(registInfo,
			if(intent.hasExtra(EXTRA_ASSIGN_MANUAL_HOST_ID))
				intent.getLongExtra(EXTRA_ASSIGN_MANUAL_HOST_ID, 0)
			else
				null)
	}

	override fun onStop()
	{
		super.onStop()
		viewModel.stop()
	}

	override fun onDestroy()
	{
		super.onDestroy()
		handler.removeCallbacksAndMessages(null)
		duplicateDialog?.dismiss()
	}

	private fun showRunning()
	{
		binding.progressBar.visibility = View.VISIBLE
		binding.detailsButton.visibility = View.GONE
		binding.doneButton.visibility = View.GONE
		binding.statusIcon.imageTintList = null
		binding.statusIcon.setImageResource(R.mipmap.ic_launcher_foreground)
		binding.statusIcon.alpha = 0.9f
		binding.titleTextView.setText(R.string.regist_status_running_title)
		binding.messageTextView.setText(R.string.regist_status_running_message)
	}

	private fun showSuccess()
	{
		if(resultSet)
			return
		resultSet = true
		setResult(RESULT_OK)
		binding.progressBar.visibility = View.GONE
		binding.detailsButton.visibility = View.GONE
		binding.doneButton.visibility = View.GONE
		binding.statusIcon.imageTintList = ColorStateList.valueOf(
			ContextCompat.getColor(this, R.color.accent))
		binding.statusIcon.setImageResource(R.drawable.ic_check_circle)
		binding.statusIcon.alpha = 1.0f
		binding.titleTextView.setText(R.string.regist_status_success_title)
		binding.messageTextView.setText(R.string.regist_status_success_message)
		handler.postDelayed({ finish() }, SUCCESS_RETURN_DELAY_MS)
	}

	private fun showDuplicate()
	{
		binding.progressBar.visibility = View.GONE
		binding.detailsButton.visibility = View.GONE
		binding.doneButton.visibility = View.GONE
		binding.statusIcon.imageTintList = null
		binding.statusIcon.setImageResource(R.mipmap.ic_launcher_foreground)
		binding.statusIcon.alpha = 0.9f
		binding.titleTextView.setText(R.string.regist_status_duplicate_title)
		binding.messageTextView.setText(R.string.regist_status_duplicate_message)
		showDuplicateDialog()
	}

	private fun showFailed()
	{
		if(!resultSet)
		{
			resultSet = true
			setResult(RESULT_FAILED)
		}
		binding.progressBar.visibility = View.GONE
		binding.statusIcon.imageTintList = null
		binding.statusIcon.setImageResource(R.drawable.ic_error_circle)
		binding.statusIcon.alpha = 1.0f
		binding.titleTextView.setText(R.string.regist_status_failed_title)
		binding.messageTextView.setText(R.string.regist_status_failed_message)
		binding.detailsButton.visibility = View.VISIBLE
		binding.doneButton.visibility = View.VISIBLE
	}

	private fun finishWithResult(resultCode: Int)
	{
		if(resultSet)
			return
		resultSet = true
		setResult(resultCode)
		finish()
	}

	private fun showDuplicateDialog()
	{
		if(duplicateDialog != null)
			return

		val macStr = viewModel.host?.serverMac?.let { MacAddress(it).toString() } ?: ""
		duplicateDialog = MaterialAlertDialogBuilder(this)
			.setTitle(R.string.regist_status_duplicate_title)
			.setMessage(getString(R.string.alert_regist_duplicate, macStr))
			.setNegativeButton(R.string.action_regist_discard) { _, _ ->
				duplicateDialog = null
				setResult(Activity.RESULT_CANCELED)
				finish()
			}
			.setPositiveButton(R.string.action_regist_overwrite) { _, _ ->
				duplicateDialog = null
				viewModel.saveHost()
			}
			.setOnCancelListener {
				duplicateDialog = null
				setResult(Activity.RESULT_CANCELED)
				finish()
			}
			.create()
			.also { it.show() }
	}

	private fun showDetails()
	{
		val view = layoutInflater.inflate(R.layout.dialog_registration_log, null)
		val logView = view.findViewById<android.widget.TextView>(R.id.logTextView)
		logView.text = latestLog.ifEmpty { getString(R.string.regist_status_no_details) }
		logView.movementMethod = ScrollingMovementMethod()
		MaterialAlertDialogBuilder(this)
			.setTitle(R.string.action_view_details)
			.setView(view)
			.setPositiveButton(android.R.string.ok, null)
			.show()
	}
}
