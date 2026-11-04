// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.stream

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import com.metallic.chiaki.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The transition shown when a Remote Play session opens.
 *
 * A swirl of hairline streaks converges on the Anomaly mark, the mark resolves with a
 * shockwave, then the streaks and sparks blow outward and the overlay fades to reveal the
 * stream. Colors follow the active theme, and a tap skips the sequence.
 */
class LaunchAnimationView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null
): View(context, attrs)
{
	private companion object
	{
		private const val DURATION_MS = 2600L
		private const val LINE_COUNT = 34
		private const val SPARK_COUNT = 26
	}

	private val density = resources.displayMetrics.density

	private val accent = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorAccent, 0xFF00E5FF.toInt())
	private val background = MaterialColors.getColor(context, android.R.attr.colorBackground, 0xFF05080B.toInt())
	private val flashColor = ColorUtils.blendARGB(accent, Color.WHITE, 0.55f)

	private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = accent
		style = Paint.Style.STROKE
		strokeCap = Paint.Cap.ROUND
		strokeWidth = 1.1f * density
	}
	private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = accent
		style = Paint.Style.FILL
	}
	private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = accent
		style = Paint.Style.STROKE
		strokeWidth = 1.4f * density
	}
	private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = ColorUtils.blendARGB(accent, Color.WHITE, 0.35f)
		style = Paint.Style.FILL
	}
	private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		isFilterBitmap = true
	}
	private val backdropPaint = Paint()
	private val flashPaint = Paint()

	private val logoRect = RectF()
	private val ringRect = RectF()

	private var vignette: RadialGradient? = null
	private var flashGradient: LinearGradient? = null

	private val lineAngles = FloatArray(LINE_COUNT)
	private val lineDelays = FloatArray(LINE_COUNT)
	private val sparkAngles = FloatArray(SPARK_COUNT)
	private val sparkDistances = FloatArray(SPARK_COUNT)
	private val sparkRadii = FloatArray(SPARK_COUNT)
	private val sparkDelays = FloatArray(SPARK_COUNT)

	private var logo: Bitmap? = null
	private var progress = 0f
	private var started = false
	private var finished = false
	private var animator: ValueAnimator? = null
	private var finishedCallback: (() -> Unit)? = null

	private var centerX = 0f
	private var centerY = 0f
	private var baseRadius = 0f
	private var outerRadius = 0f

	init
	{
		val source = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher_foreground)
			?: BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
		logo = source?.let { trimTransparent(it) }
		isFocusable = false
		isClickable = true
		setOnClickListener { finishNow() }
	}

	fun start(onFinished: () -> Unit)
	{
		if(started)
			return
		started = true
		finishedCallback = onFinished
		if(width > 0 && height > 0)
			begin()
		else
			post { begin() }
	}

	private fun begin()
	{
		if(animator != null || finished || !isAttachedToWindow)
			return
		animator = ValueAnimator.ofFloat(0f, 1f).apply {
			duration = DURATION_MS
			interpolator = LinearInterpolator()
			addUpdateListener {
				progress = it.animatedValue as Float
				alpha = 1f - smooth(clamp((progress - 0.76f) / 0.24f))
				invalidate()
			}
			addListener(object: AnimatorListenerAdapter()
			{
				override fun onAnimationEnd(animation: Animator) = finishNow()
			})
			start()
		}
	}

	private fun finishNow()
	{
		if(finished)
			return
		finished = true
		val running = animator
		animator = null
		running?.removeAllListeners()
		running?.cancel()
		visibility = GONE
		finishedCallback?.invoke()
		finishedCallback = null
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int)
	{
		super.onSizeChanged(w, h, oldw, oldh)
		if(w <= 0 || h <= 0)
			return

		centerX = w / 2f
		centerY = h / 2f
		baseRadius = min(w, h) * 0.30f
		outerRadius = hypot(w.toFloat(), h.toFloat()) * 0.62f

		val random = Random(0x4E4F4D41)
		for(i in 0 until LINE_COUNT)
		{
			lineAngles[i] = (i.toFloat() / LINE_COUNT) * 2f * PI.toFloat() +
					(random.nextFloat() - 0.5f) * 0.16f
			lineDelays[i] = random.nextFloat() * 0.14f
		}
		for(i in 0 until SPARK_COUNT)
		{
			sparkAngles[i] = random.nextFloat() * 2f * PI.toFloat()
			sparkDistances[i] = 0.45f + random.nextFloat() * 1.5f
			sparkRadii[i] = (0.8f + random.nextFloat() * 1.6f) * density
			sparkDelays[i] = random.nextFloat() * 0.22f
		}

		val edge = ColorUtils.setAlphaComponent(background, 235)
		vignette = RadialGradient(centerX, centerY, max(w, h) * 0.78f,
			intArrayOf(Color.TRANSPARENT, ColorUtils.setAlphaComponent(background, 90), edge),
			floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP)

		flashGradient = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
			intArrayOf(Color.TRANSPARENT, ColorUtils.setAlphaComponent(flashColor, 110), Color.TRANSPARENT),
			floatArrayOf(0.32f, 0.5f, 0.68f), Shader.TileMode.CLAMP)

		if(started)
			begin()
	}

	override fun onAttachedToWindow()
	{
		super.onAttachedToWindow()
		if(started)
			begin()
	}

	override fun onDetachedFromWindow()
	{
		val running = animator
		animator = null
		running?.removeAllListeners()
		running?.cancel()
		super.onDetachedFromWindow()
	}

	override fun onDraw(canvas: Canvas)
	{
		super.onDraw(canvas)
		if(width == 0 || height == 0)
			return

		val p = progress
		val w = width.toFloat()
		val h = height.toFloat()

		// Vignette that frames the mark and lifts as the stream is revealed.
		vignette?.let {
			backdropPaint.shader = it
			backdropPaint.alpha = (255 * (1f - smooth(clamp((p - 0.58f) / 0.30f)) * 0.92f)).toInt()
			canvas.drawRect(0f, 0f, w, h, backdropPaint)
			backdropPaint.shader = null
		}

		// Rotation gives the converging streaks a slow spiral drift.
		val drift = p * 0.55f
		drawStreaks(canvas, p, drift)
		drawImpactRings(canvas, p)
		drawOrbitArcs(canvas, p)
		drawFlash(canvas, p, w, h)
		drawLogo(canvas, p)
		drawSparks(canvas, p)
	}

	private fun drawStreaks(canvas: Canvas, p: Float, drift: Float)
	{
		for(i in 0 until LINE_COUNT)
		{
			val angle = lineAngles[i] + drift
			val ux = cos(angle)
			val uy = sin(angle)

			if(p < 0.34f)
			{
				val t = smooth(clamp((p - lineDelays[i] * 0.25f) / 0.30f))
				if(t <= 0f)
					continue
				val start = outerRadius * (1f - 0.12f * t)
				val inner = baseRadius * 0.54f
				val end = start + (inner - start) * t
				linePaint.alpha = (215 * t).toInt().coerceIn(0, 255)
				linePaint.strokeWidth = (0.7f + 0.9f * t) * density
				canvas.drawLine(
					centerX + ux * start, centerY + uy * start,
					centerX + ux * end, centerY + uy * end,
					linePaint
				)
			}
			else if(p > 0.58f)
			{
				val b = smooth(clamp((p - 0.58f - lineDelays[i] * 0.35f) / 0.38f))
				if(b <= 0f)
					continue
				val start = baseRadius * 0.54f
				val end = start + (outerRadius * 1.25f - start) * (b * b)
				linePaint.alpha = (210 * (1f - b)).toInt().coerceIn(0, 255)
				linePaint.strokeWidth = (1.8f - 1.2f * b) * density
				canvas.drawLine(
					centerX + ux * start, centerY + uy * start,
					centerX + ux * end, centerY + uy * end,
					linePaint
				)
				linePaint.alpha = (70 * (1f - b)).toInt().coerceIn(0, 255)
				val tail = end - baseRadius * 0.18f
				canvas.drawLine(
					centerX + ux * tail, centerY + uy * tail,
					centerX + ux * end, centerY + uy * end,
					linePaint
				)
			}
		}
	}

	private fun drawImpactRings(canvas: Canvas, p: Float)
	{
		for(i in 0 until 2)
		{
			val offset = i * 0.06f
			val t = clamp((p - 0.30f - offset) / 0.22f)
			if(t <= 0f || t >= 1f)
				continue
			ringPaint.alpha = (190 * (1f - t)).toInt()
			ringPaint.strokeWidth = (2.2f - 1.2f * t) * density
			canvas.drawCircle(centerX, centerY, baseRadius * (0.46f + 1.35f * t), ringPaint)
		}
	}

	private fun drawOrbitArcs(canvas: Canvas, p: Float)
	{
		val visible = smooth(clamp((p - 0.24f) / 0.12f)) *
				(1f - smooth(clamp((p - 0.56f) / 0.12f)))
		if(visible <= 0f)
			return
		ringPaint.alpha = (120 * visible).toInt()
		ringPaint.strokeWidth = 1.1f * density
		val radius = baseRadius * 0.78f
		ringRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
		canvas.drawArc(ringRect, p * 540f, 78f, false, ringPaint)
		canvas.drawArc(ringRect, p * 540f + 190f, 46f, false, ringPaint)
	}

	private fun drawFlash(canvas: Canvas, p: Float, w: Float, h: Float)
	{
		val t = clamp((p - 0.28f) / 0.20f)
		if(t <= 0f || t >= 1f)
			return
		flashPaint.shader = flashGradient
		flashPaint.alpha = (90 * sin(t * PI.toFloat())).toInt()
		canvas.drawRect(0f, 0f, w, h, flashPaint)
		flashPaint.shader = null
		flashPaint.alpha = 255
	}

	private fun drawLogo(canvas: Canvas, p: Float)
	{
		val logo = logo ?: return
		val appear = clamp((p - 0.14f) / 0.18f)
		if(appear <= 0f)
			return

		val reveal = easeOutBack(smooth(appear))
		val breathe = 1f + 0.025f * sin(clamp((p - 0.32f) / 0.28f) * PI.toFloat())
		val fade = smooth(clamp((p - 0.62f) / 0.20f))
		val scale = (0.78f + 0.22f * reveal) * breathe

		val target = baseRadius * 0.62f * scale
		val aspect = logo.width.toFloat() / logo.height.toFloat()
		val lw = if(aspect >= 1f) target else target * aspect
		val lh = if(aspect >= 1f) target / aspect else target
		logoRect.set(centerX - lw / 2f, centerY - lh / 2f, centerX + lw / 2f, centerY + lh / 2f)

		glowPaint.alpha = (28 * smooth(appear) * (1f - fade * 0.8f)).toInt()
		canvas.drawCircle(centerX, centerY, baseRadius * 0.78f, glowPaint)
		glowPaint.alpha = (14 * smooth(appear) * (1f - fade * 0.8f)).toInt()
		canvas.drawCircle(centerX, centerY, baseRadius * 1.02f, glowPaint)

		logoPaint.alpha = (250 * smooth(appear) * (1f - fade)).toInt().coerceIn(0, 255)
		canvas.drawBitmap(logo, null, logoRect, logoPaint)
	}

	private fun drawSparks(canvas: Canvas, p: Float)
	{
		val s = clamp((p - 0.58f) / 0.42f)
		if(s <= 0f)
			return
		for(i in 0 until SPARK_COUNT)
		{
			val t = clamp((s - sparkDelays[i]) / (1f - sparkDelays[i]))
			if(t <= 0f || t >= 1f)
				continue
			val distance = baseRadius * (0.58f + sparkDistances[i] * t)
			sparkPaint.alpha = (200 * (1f - t) * (1f - t)).toInt()
			canvas.drawCircle(
				centerX + cos(sparkAngles[i]) * distance,
				centerY + sin(sparkAngles[i]) * distance,
				sparkRadii[i] * (1f - t * 0.5f),
				sparkPaint
			)
		}
	}

	private fun trimTransparent(source: Bitmap): Bitmap
	{
		val pixels = IntArray(source.width * source.height)
		source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
		var left = source.width
		var top = source.height
		var right = -1
		var bottom = -1
		for(y in 0 until source.height)
		{
			val row = y * source.width
			for(x in 0 until source.width)
			{
				if(Color.alpha(pixels[row + x]) > 8)
				{
					if(x < left) left = x
					if(x > right) right = x
					if(y < top) top = y
					if(y > bottom) bottom = y
				}
			}
		}
		if(right < left || bottom < top)
			return source
		return Bitmap.createBitmap(source, left, top, right - left + 1, bottom - top + 1)
	}

	private fun clamp(value: Float) = max(0f, min(1f, value))

	private fun smooth(value: Float) = value * value * (3f - 2f * value)

	private fun easeOutBack(value: Float): Float
	{
		val c1 = 1.70158f
		val c3 = c1 + 1f
		val v = value - 1f
		return 1f + c3 * v * v * v + c1 * v * v
	}
}
