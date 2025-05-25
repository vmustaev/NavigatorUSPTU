package com.example.svg_parsing

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.caverock.androidsvg.SVG
import kotlin.math.*

class MapView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var svg: SVG? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())

    private var scaleFactor = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    var svgWidth = 0f
    var svgHeight = 0f
    private var initialScale = 1f

    data class Marker(
        val x: Float,
        val y: Float,
        val name: String
    ) {
        fun getAbsX(svgWidth: Float) = x * svgWidth
        fun getAbsY(svgHeight: Float) = y * svgHeight
    }
    data class Route(val startId: Int, val endId: Int)

    private val markers = mutableListOf<Marker>()
    private val routes = mutableListOf<Route>()

    fun loadSVGFromAssets(filename: String) {
        try {
            context.assets.open(filename).use { stream ->
                svg = SVG.getFromInputStream(stream).apply {
                    svgWidth = documentWidth
                    svgHeight = documentHeight

                    post {
                        initialScale = min(width / svgWidth, height / svgHeight) * 0.9f
                        scaleFactor = initialScale
                        centerView()
                        invalidate()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    fun clearRoutes() {
        routes.clear()
        invalidate()
    }

    fun clearMarkers() {
        markers.clear()
        invalidate()
    }

    private fun centerView() {
        offsetX = (width - svgWidth * scaleFactor) / 2
        offsetY = (height - svgHeight * scaleFactor) / 2
    }

    fun addMarker(marker: Marker) {
        markers.add(marker)
        invalidate()
    }

    fun addRoute(route: Route) {
        routes.add(route)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scaleFactor, scaleFactor)

        svg?.renderToCanvas(canvas)
        drawRoutes(canvas)
        drawMarkers(canvas)

        canvas.restore()
    }

    private fun drawRoutes(canvas: Canvas) {
        svg?.let { svg ->
            paint.color = Color.parseColor("#1E88E5")
            paint.strokeWidth = 8f / scaleFactor
            paint.strokeCap = Paint.Cap.ROUND
            paint.pathEffect = CornerPathEffect(10f)
            paint.alpha = 220

            routes.forEach { route ->
                val start = markers.getOrNull(route.startId)
                val end = markers.getOrNull(route.endId)

                if (start != null && end != null) {
                    val startX = start.getAbsX(svg.documentWidth)
                    val startY = start.getAbsY(svg.documentHeight)
                    val endX = end.getAbsX(svg.documentWidth)
                    val endY = end.getAbsY(svg.documentHeight)

                    canvas.drawLine(startX, startY, endX, endY, paint)
                }
            }
        }
    }

    private fun drawMarkers(canvas: Canvas) {
        svg?.let { svg ->
            markers.forEach { marker ->
                val absX = marker.getAbsX(svg.documentWidth)
                val absY = marker.getAbsY(svg.documentHeight)

                val markerSize = 5f / scaleFactor
                paint.color = Color.RED
                canvas.drawCircle(absX, absY, markerSize, paint)

                paint.color = Color.BLACK
                paint.textSize = 24f / scaleFactor
                canvas.drawText(marker.name, absX + 20f / scaleFactor, absY, paint)
            }
        }
    }

    private fun constrainOffsets() {
        val scaledWidth = svgWidth * scaleFactor
        val scaledHeight = svgHeight * scaleFactor

        val padding = min(scaledWidth, scaledHeight) * 0.25f

        val maxX = max(0f, width - scaledWidth) + padding
        val minX = min(0f, width - scaledWidth) - padding

        val maxY = max(0f, height - scaledHeight) + padding
        val minY = min(0f, height - scaledHeight) - padding

        offsetX = offsetX.coerceIn(minX, maxX)
        offsetY = offsetY.coerceIn(minY, maxY)
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {

            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    if (event.pointerCount == 1) {
                        offsetX += event.x - lastTouchX
                        offsetY += event.y - lastTouchY
                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                    else if (event.pointerCount >= 2) {
                        offsetX += event.getX(0) - lastTouchX
                        offsetY += event.getY(0) - lastTouchY
                        lastTouchX = event.getX(0)
                        lastTouchY = event.getY(0)
                    }
                    constrainOffsets()
                    invalidate()
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val remainingPointerIndex = if (event.actionIndex == 0) 1 else 0
                lastTouchX = event.getX(remainingPointerIndex)
                lastTouchY = event.getY(remainingPointerIndex)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            }
        }

        return true
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val oldScale = scaleFactor
            scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(initialScale * 0.5f, initialScale * 5f)

            offsetX += (detector.focusX - offsetX) * (1 - scaleFactor / oldScale)
            offsetY += (detector.focusY - offsetY) * (1 - scaleFactor / oldScale)

            constrainOffsets()
            invalidate()
            return true
        }
    }
}