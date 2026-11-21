// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object UpdateInstaller
{
	private const val PROVIDER_SUFFIX = ".fileprovider"

	fun canInstall(context: Context): Boolean =
		Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
			context.packageManager.canRequestPackageInstalls()

	fun openPermissionSettings(context: Context)
	{
		if(Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
			return
		context.startActivity(Intent(
			Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
			Uri.parse("package:${context.packageName}")
		).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
	}

	fun download(
		context: Context,
		url: String,
		fileName: String,
		onProgress: (downloaded: Long, total: Long) -> Unit
	): File
	{
		val directory = File(context.cacheDir, "updates").apply { mkdirs() }
		val target = File(directory, fileName)
		val connection = (URL(url).openConnection() as HttpURLConnection).apply {
			connectTimeout = 15000
			readTimeout = 15000
			requestMethod = "GET"
			setRequestProperty("User-Agent", "Anomaly-Android")
			instanceFollowRedirects = true
		}

		try
		{
			if(connection.responseCode != HttpURLConnection.HTTP_OK)
				throw IOException("HTTP ${connection.responseCode}")

			val total = connection.contentLengthLong
			var downloaded = 0L
			connection.inputStream.use { input ->
				target.outputStream().use { output ->
					val buffer = ByteArray(64 * 1024)
					while(true)
					{
						val read = input.read(buffer)
						if(read <= 0)
							break
						output.write(buffer, 0, read)
						downloaded += read
						onProgress(downloaded, total)
					}
				}
			}
			onProgress(downloaded, downloaded)
		}
		finally
		{
			connection.disconnect()
		}

		return target
	}

	fun install(context: Context, apk: File)
	{
		val uri = FileProvider.getUriForFile(
			context,
			context.packageName + PROVIDER_SUFFIX,
			apk
		)
		val intent = Intent(Intent.ACTION_VIEW).apply {
			setDataAndType(uri, "application/vnd.android.package-archive")
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
		}
		context.startActivity(intent)
	}
}
