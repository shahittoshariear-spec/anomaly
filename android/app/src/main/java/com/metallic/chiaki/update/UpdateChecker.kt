// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.update

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
	val version: String,
	val notes: String,
	val apkUrl: String,
	val apkName: String,
	val pageUrl: String
)

object UpdateChecker
{
	private const val LATEST_RELEASE_URL =
		"https://api.github.com/repos/shahittoshariear-spec/anomaly/releases/latest"
	private const val USER_AGENT = "Anomaly-Android"

	fun currentVersion(context: Context): String =
		try
		{
			@Suppress("DEPRECATION")
			context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
		}
		catch(e: Exception) { "0" }

	fun fetchLatest(): UpdateInfo?
	{
		val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
			connectTimeout = 8000
			readTimeout = 8000
			requestMethod = "GET"
			setRequestProperty("Accept", "application/vnd.github+json")
			setRequestProperty("User-Agent", USER_AGENT)
		}

		try
		{
			if(connection.responseCode != HttpURLConnection.HTTP_OK)
				return null

			val body = connection.inputStream.bufferedReader().use { it.readText() }
			val json = JSONObject(body)
			val version = json.optString("tag_name").removePrefix("v").trim()
			if(version.isEmpty())
				return null

			val assets = json.optJSONArray("assets") ?: return null
			var apkUrl = ""
			var apkName = ""
			for(i in 0 until assets.length())
			{
				val asset = assets.getJSONObject(i)
				val name = asset.optString("name")
				if(name.endsWith(".apk", ignoreCase = true))
				{
					apkUrl = asset.optString("browser_download_url")
					apkName = name
					break
				}
			}

			if(apkUrl.isEmpty())
				return null

			return UpdateInfo(
				version = version,
				notes = json.optString("body").trim(),
				apkUrl = apkUrl,
				apkName = apkName,
				pageUrl = json.optString("html_url")
			)
		}
		finally
		{
			connection.disconnect()
		}
	}

	fun isNewer(latest: String, current: String): Boolean
	{
		fun parts(value: String) = value.split('.').mapNotNull { it.toIntOrNull() }
		val a = parts(latest)
		val b = parts(current)
		for(i in 0 until maxOf(a.size, b.size))
		{
			val x = a.getOrElse(i) { 0 }
			val y = b.getOrElse(i) { 0 }
			if(x != y)
				return x > y
		}
		return false
	}
}
