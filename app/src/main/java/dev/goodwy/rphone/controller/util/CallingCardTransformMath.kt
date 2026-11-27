package dev.goodwy.rphone.controller.util

import kotlin.math.abs

/**
 * Normalized, container-independent calling card wallpaper transform.
 *
 * [scale] multiplies the cover-fitted image size (`1f` fills the container edge-to-edge).
 * [offsetX] and [offsetY] are horizontal and vertical translations expressed as fractions
 * of the container's width and height respectively (`0f` centers the image).
 */
data class WallpaperTransform(
    val scale: Float = CallingCardStyle.DEFAULT_BG_SCALE,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)

/**
 * Pixel-space geometry for drawing a transformed wallpaper inside a container of size
 * `(containerWidth, containerHeight)` without ever exposing blank edges.
 */
data class ClampedWallpaperDrawMetrics(
    val drawWidth: Float,
    val drawHeight: Float,
    val leftPx: Float,
    val topPx: Float,
    val translationXPx: Float,
    val translationYPx: Float,
    val maxOffsetXFraction: Float,
    val maxOffsetYFraction: Float
)

/**
 * Pure JVM geometry helpers for calling card wallpaper zoom and pan.
 */
object CallingCardTransformMath {

    /**
     * Computes exact draw size and edge-clamped offsets for rendering an image with
     * intrinsic dimensions `(intrinsicWidth, intrinsicHeight)` inside a viewport of
     * `(containerWidth, containerHeight)`.
     */
    fun computeDrawMetrics(
        containerWidth: Float,
        containerHeight: Float,
        intrinsicWidth: Float,
        intrinsicHeight: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float
    ): ClampedWallpaperDrawMetrics {
        if (containerWidth <= 0f || containerHeight <= 0f) {
            return ClampedWallpaperDrawMetrics(
                drawWidth = 0f,
                drawHeight = 0f,
                leftPx = 0f,
                topPx = 0f,
                translationXPx = 0f,
                translationYPx = 0f,
                maxOffsetXFraction = 0f,
                maxOffsetYFraction = 0f
            )
        }

        val clampedScale = sanitizeScale(scale)
        val (baseWidth, baseHeight) = if (
            intrinsicWidth.isFinite() && intrinsicHeight.isFinite() &&
            intrinsicWidth > 0f && intrinsicHeight > 0f
        ) {
            val coverScale = maxOf(
                containerWidth / intrinsicWidth,
                containerHeight / intrinsicHeight
            )
            (intrinsicWidth * coverScale) to (intrinsicHeight * coverScale)
        } else {
            containerWidth to containerHeight
        }

        val drawWidth = baseWidth * clampedScale
        val drawHeight = baseHeight * clampedScale

        val maxTxPx = maxOf(0f, (drawWidth - containerWidth) / 2f)
        val maxTyPx = maxOf(0f, (drawHeight - containerHeight) / 2f)

        val maxOffsetXFraction = maxTxPx / containerWidth
        val maxOffsetYFraction = maxTyPx / containerHeight

        val safeOffsetX = sanitizeOffset(offsetX).coerceIn(-maxOffsetXFraction, maxOffsetXFraction)
        val safeOffsetY = sanitizeOffset(offsetY).coerceIn(-maxOffsetYFraction, maxOffsetYFraction)

        val translationXPx = safeOffsetX * containerWidth
        val translationYPx = safeOffsetY * containerHeight

        val leftPx = (containerWidth - drawWidth) / 2f + translationXPx
        val topPx = (containerHeight - drawHeight) / 2f + translationYPx

        return ClampedWallpaperDrawMetrics(
            drawWidth = drawWidth,
            drawHeight = drawHeight,
            leftPx = leftPx,
            topPx = topPx,
            translationXPx = translationXPx,
            translationYPx = translationYPx,
            maxOffsetXFraction = maxOffsetXFraction,
            maxOffsetYFraction = maxOffsetYFraction
        )
    }

    /**
     * Applies a multi-touch pinch + pan delta anchored at `(centroidX, centroidY)` in
     * container coordinates and clamps the result so the image never pulls away from
     * any container edge.
     */
    fun applyTransformGesture(
        current: WallpaperTransform,
        containerWidth: Float,
        containerHeight: Float,
        intrinsicWidth: Float,
        intrinsicHeight: Float,
        centroidX: Float,
        centroidY: Float,
        panX: Float,
        panY: Float,
        zoomChange: Float
    ): WallpaperTransform {
        if (containerWidth <= 0f || containerHeight <= 0f) return current

        val oldScale = sanitizeScale(current.scale)
        val safeZoom = if (zoomChange.isFinite() && zoomChange > 0f) zoomChange else 1f
        val newScale = sanitizeScale(oldScale * safeZoom)
        val zoomRatio = newScale / oldScale

        val oldMetrics = computeDrawMetrics(
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            intrinsicWidth = intrinsicWidth,
            intrinsicHeight = intrinsicHeight,
            scale = oldScale,
            offsetX = current.offsetX,
            offsetY = current.offsetY
        )

        val centerX = containerWidth / 2f
        val centerY = containerHeight / 2f
        val safePanX = if (panX.isFinite()) panX else 0f
        val safePanY = if (panY.isFinite()) panY else 0f
        val safeCentroidX = if (centroidX.isFinite()) centroidX else centerX
        val safeCentroidY = if (centroidY.isFinite()) centroidY else centerY

        // Keep the image point currently beneath the pinch centroid stationary while scaling.
        val rawTxPx = oldMetrics.translationXPx * zoomRatio +
                (safeCentroidX - centerX) * (1f - zoomRatio) +
                safePanX
        val rawTyPx = oldMetrics.translationYPx * zoomRatio +
                (safeCentroidY - centerY) * (1f - zoomRatio) +
                safePanY

        val newMetrics = computeDrawMetrics(
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            intrinsicWidth = intrinsicWidth,
            intrinsicHeight = intrinsicHeight,
            scale = newScale,
            offsetX = rawTxPx / containerWidth,
            offsetY = rawTyPx / containerHeight
        )

        return WallpaperTransform(
            scale = normalizeZero(newScale),
            offsetX = normalizeZero(newMetrics.translationXPx / containerWidth),
            offsetY = normalizeZero(newMetrics.translationYPx / containerHeight)
        )
    }

    /**
     * Computes a double-tap target transform: resets to default when already transformed,
     * or zooms smoothly to [doubleTapZoom] anchored around `(tapX, tapY)`.
     */
    fun toggleDoubleTapTransform(
        current: WallpaperTransform,
        containerWidth: Float,
        containerHeight: Float,
        intrinsicWidth: Float,
        intrinsicHeight: Float,
        tapX: Float,
        tapY: Float,
        doubleTapZoom: Float = 2f
    ): WallpaperTransform {
        val isTransformed = abs(current.scale - CallingCardStyle.DEFAULT_BG_SCALE) > CallingCardStyle.TRANSFORM_EPSILON ||
                abs(current.offsetX) > CallingCardStyle.TRANSFORM_EPSILON ||
                abs(current.offsetY) > CallingCardStyle.TRANSFORM_EPSILON
        if (isTransformed) {
            return WallpaperTransform()
        }
        return applyTransformGesture(
            current = WallpaperTransform(),
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            intrinsicWidth = intrinsicWidth,
            intrinsicHeight = intrinsicHeight,
            centroidX = tapX,
            centroidY = tapY,
            panX = 0f,
            panY = 0f,
            zoomChange = doubleTapZoom
        )
    }

    fun sanitizeScale(scale: Float): Float {
        if (!scale.isFinite()) return CallingCardStyle.DEFAULT_BG_SCALE
        return scale.coerceIn(CallingCardStyle.MIN_BG_SCALE, CallingCardStyle.MAX_BG_SCALE)
    }

    fun sanitizeOffset(offset: Float): Float {
        if (!offset.isFinite()) return 0f
        return offset.coerceIn(-CallingCardStyle.MAX_BG_OFFSET, CallingCardStyle.MAX_BG_OFFSET)
    }

    fun normalizeZero(value: Float): Float = if (value == 0f) 0f else value
}
