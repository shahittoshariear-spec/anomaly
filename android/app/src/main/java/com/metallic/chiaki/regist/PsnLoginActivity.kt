// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.regist

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.metallic.chiaki.R
import com.metallic.chiaki.databinding.ActivityPsnLoginBinding
import org.json.JSONObject
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom

/**
 * Obtains the PSN Account ID required for PS5/PS4 registration.
 *
 * The user signs in to Sony's OAuth page in a WebView; the authorization code is
 * exchanged for a token, which is used to look up the numeric PSN user id. That id
 * is converted to the 8-byte little-endian account id Chiaki expects.
 */
class PsnLoginActivity: AppCompatActivity()
{
	companion object
	{
		const val EXTRA_ACCOUNT_ID = "psn_account_id"

		private const val CLIENT_ID = "ba495a24-818c-472b-b12d-ff231c1b5745"
		private const val CLIENT_SECRET = "mvaiZkRsAsI1IBkY"
		private const val TOKEN_URL = "https://auth.api.sonyentertainmentnetwork.com/2.0/oauth/token"
		private const val REDIRECT_PAGE = "https://remoteplay.dl.playstation.net/remoteplay/redirect"
		private const val SCOPE = "psn:clientapp referenceDataService:countryConfig.read pushNotification:webSocket.desktop.connect sessionManager:remotePlaySession.system.update"
		private const val DUID_PREFIX = "0000000700410080"
	}

	private lateinit var binding: ActivityPsnLoginBinding
	private var exchanging = false
	private var cancelled = false

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		binding = ActivityPsnLoginBinding.inflate(layoutInflater)
		setContentView(binding.root)

		setSupportActionBar(binding.toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		binding.toolbar.setNavigationOnClickListener { finish() }

		@SuppressLint("SetJavaScriptEnabled")
		val settings = binding.webView.settings
		settings.javaScriptEnabled = true
		settings.domStorageEnabled = true
		settings.databaseEnabled = true
		settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

		CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true)
		CookieManager.getInstance().setAcceptCookie(true)

		binding.webView.webViewClient = object: WebViewClient()
		{
			override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean
			{
				return handleUrl(request.url.toString())
			}

			override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?)
			{
				if(handleUrl(url))
					view.stopLoading()
			}

			override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError)
			{
				if(request.isForMainFrame && !exchanging)
					showError(error.description?.toString() ?: getString(R.string.psn_login_error))
			}
		}

		binding.webView.loadUrl(buildLoginUrl())
	}

	private fun buildLoginUrl(): String
	{
		val duid = DUID_PREFIX + ByteArray(16).also { SecureRandom().nextBytes(it) }
			.joinToString("") { "%02x".format(it) }

		return "https://auth.api.sonyentertainmentnetwork.com/2.0/oauth/authorize" +
				"?service_entity=" + Uri.encode("urn:service-entity:psn") +
				"&response_type=code" +
				"&client_id=" + CLIENT_ID +
				"&redirect_uri=" + Uri.encode(REDIRECT_PAGE) +
				"&scope=" + Uri.encode(SCOPE) +
				"&request_locale=en_US&ui=pr&service_logo=ps&layout_type=popup&smcid=remoteplay" +
				"&prompt=always&PlatformPrivacyWs1=minimal&duid=" + duid + "&"
	}

	private fun handleUrl(url: String): Boolean
	{
		if(exchanging || !url.startsWith(REDIRECT_PAGE))
			return false

		val code = Uri.parse(url).getQueryParameter("code")
		if(code.isNullOrEmpty())
		{
			showError(getString(R.string.psn_login_no_code))
			return true
		}

		exchangeCode(code)
		return true
	}

	private fun exchangeCode(code: String)
	{
		exchanging = true
		binding.webView.visibility = View.INVISIBLE
		binding.statusOverlay.visibility = View.VISIBLE
		binding.statusText.setText(R.string.psn_login_exchanging)
		binding.retryButton.visibility = View.GONE

		Thread {
			try
			{
				val accessToken = requestAccessToken(code)
				val userId = requestUserId(accessToken)
				val accountId = accountIdFromUserId(userId)

				runOnUiThread {
					if(cancelled || isFinishing || isDestroyed)
						return@runOnUiThread
					setResult(RESULT_OK, Intent().putExtra(EXTRA_ACCOUNT_ID, accountId))
					finish()
				}
			}
			catch(e: Exception)
			{
				runOnUiThread {
					if(cancelled || isFinishing || isDestroyed)
						return@runOnUiThread
					showError(e.message ?: getString(R.string.psn_login_error))
				}
			}
		}.start()
	}

	private fun requestAccessToken(code: String): String
	{
		val body = "grant_type=authorization_code" +
				"&code=" + URLEncoder.encode(code, "UTF-8") +
				"&scope=" + URLEncoder.encode(SCOPE, "UTF-8") +
				"&redirect_uri=" + URLEncoder.encode(REDIRECT_PAGE, "UTF-8") + "&"

		val connection = URL(TOKEN_URL).openConnection() as HttpURLConnection
		try
		{
			connection.requestMethod = "POST"
			connection.doOutput = true
			connection.connectTimeout = 30000
			connection.readTimeout = 30000
			connection.setRequestProperty("Authorization", "Basic " + Base64.encodeToString(
				"$CLIENT_ID:$CLIENT_SECRET".toByteArray(), Base64.NO_WRAP))
			connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

			connection.outputStream.use { it.write(body.toByteArray()) }

			val response = readResponse(connection)
			return JSONObject(response).optString("access_token").ifEmpty {
				throw IllegalStateException("Token response did not contain an access token")
			}
		}
		finally
		{
			connection.disconnect()
		}
	}

	private fun requestUserId(accessToken: String): String
	{
		val connection = URL("$TOKEN_URL/$accessToken").openConnection() as HttpURLConnection
		try
		{
			connection.requestMethod = "GET"
			connection.connectTimeout = 30000
			connection.readTimeout = 30000
			connection.setRequestProperty("Authorization", "Basic " + Base64.encodeToString(
				"$CLIENT_ID:$CLIENT_SECRET".toByteArray(), Base64.NO_WRAP))
			connection.setRequestProperty("Accept", "application/json")

			val response = readResponse(connection)
			return JSONObject(response).optString("user_id").ifEmpty {
				throw IllegalStateException("Account response did not contain a user id")
			}
		}
		finally
		{
			connection.disconnect()
		}
	}

	private fun readResponse(connection: HttpURLConnection): String
	{
		val code = connection.responseCode
		val stream = if(code in 200..299) connection.inputStream else connection.errorStream
		val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
		if(code !in 200..299)
			throw IllegalStateException("PSN request failed (HTTP $code)")
		return text
	}

	private fun accountIdFromUserId(userId: String): String
	{
		val value = BigInteger(userId)
		val bytes = ByteArray(8) { i -> value.shiftRight(8 * i).toByte() }
		return Base64.encodeToString(bytes, Base64.NO_WRAP)
	}

	private fun showError(message: String)
	{
		if(cancelled || isFinishing || isDestroyed)
			return
		exchanging = false
		binding.statusOverlay.visibility = View.VISIBLE
		binding.webView.visibility = View.INVISIBLE
		binding.progressBar.visibility = View.GONE
		binding.statusText.text = message
		binding.retryButton.visibility = View.VISIBLE
		binding.retryButton.setOnClickListener {
			binding.statusOverlay.visibility = View.GONE
			binding.webView.visibility = View.VISIBLE
			binding.progressBar.visibility = View.VISIBLE
			binding.webView.loadUrl(buildLoginUrl())
		}
	}

	override fun onDestroy()
	{
		cancelled = true
		binding.webView.stopLoading()
		binding.webView.loadUrl("about:blank")
		binding.webView.destroy()
		super.onDestroy()
	}
}
