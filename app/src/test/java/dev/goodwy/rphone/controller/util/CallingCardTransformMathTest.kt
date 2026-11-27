package dev.goodwy.rphone.controller.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallingCardTransformMathTest {

    @Test
    fun landscapePhotoInPortraitFrameAllowsHorizontalPanAtOneXWithoutBlankEdges() {
        // Container: 400x800 (portrait 1:2), Image: 1600x800 (landscape 2:1)
        // Cover scale = 800 / 800 = 1.0 -> drawWidth = 1600, drawHeight = 800
        // Max horizontal pan = (1600 - 400) / 2 = 600px -> maxOffsetX = 600 / 400 = 1.5
        val metrics = CallingCardTransformMath.computeDrawMetrics(
            containerWidth = 400f,
            containerHeight = 800f,
            intrinsicWidth = 1600f,
            intrinsicHeight = 800f,
            scale = 1f,
            offsetX = 10f, // intentionally beyond edge
            offsetY = 10f
        )

        assertEquals(1600f, metrics.drawWidth, 0.001f)
        assertEquals(800f, metrics.drawHeight, 0.001f)
        assertEquals(1.5f, metrics.maxOffsetXFraction, 0.001f)
        assertEquals(0f, metrics.maxOffsetYFraction, 0.001f)
        // Flush with left edge (leftPx == 0) and vertical edges (topPx == 0)
        assertEquals(0f, metrics.leftPx, 0.001f)
        assertEquals(0f, metrics.topPx, 0.001f)
    }

    @Test
    fun zoomedImageClampsToExactBoundsOnAllSides() {
        // Container: 400x800, matching aspect image: 400x800 at 2x -> 800x1600
        // Max pan: X = (800 - 400) / 2 = 200px (0.5), Y = (1600 - 800) / 2 = 400px (0.5)
        val topLeftClamp = CallingCardTransformMath.computeDrawMetrics(
            containerWidth = 400f,
            containerHeight = 800f,
            intrinsicWidth = 400f,
            intrinsicHeight = 800f,
            scale = 2f,
            offsetX = 5f,
            offsetY = 5f
        )
        assertEquals(0f, topLeftClamp.leftPx, 0.001f)
        assertEquals(0f, topLeftClamp.topPx, 0.001f)

        val bottomRightClamp = CallingCardTransformMath.computeDrawMetrics(
            containerWidth = 400f,
            containerHeight = 800f,
            intrinsicWidth = 400f,
            intrinsicHeight = 800f,
            scale = 2f,
            offsetX = -5f,
            offsetY = -5f
        )
        assertEquals(400f, bottomRightClamp.leftPx + bottomRightClamp.drawWidth, 0.001f)
        assertEquals(800f, bottomRightClamp.topPx + bottomRightClamp.drawHeight, 0.001f)
    }

    @Test
    fun pinchZoomKeepsFocalPointUnderCentroidStationary() {
        // Container: 400x800 (center = 200, 400), image matches container
        // Zoom from 1x to 2x anchored at centroid (300, 500) -> +100x, +100y from center
        // Expected translation = (-100px, -100px) -> (-0.25, -0.125)
        val updated = CallingCardTransformMath.applyTransformGesture(
            current = WallpaperTransform(scale = 1f, offsetX = 0f, offsetY = 0f),
            containerWidth = 400f,
            containerHeight = 800f,
            intrinsicWidth = 400f,
            intrinsicHeight = 800f,
            centroidX = 300f,
            centroidY = 500f,
            panX = 0f,
            panY = 0f,
            zoomChange = 2f
        )

        assertEquals(2f, updated.scale, 0.001f)
        assertEquals(-0.25f, updated.offsetX, 0.001f)
        assertEquals(-0.125f, updated.offsetY, 0.001f)
    }

    @Test
    fun zoomIsStrictlyClampedBetweenOneXAndFourX() {
        val zoomedOut = CallingCardTransformMath.applyTransformGesture(
            current = WallpaperTransform(scale = 1.2f, offsetX = 0.05f, offsetY = -0.05f),
            containerWidth = 400f,
            containerHeight = 800f,
            intrinsicWidth = 400f,
            intrinsicHeight = 800f,
            centroidX = 200f,
            centroidY = 400f,
            panX = 0f,
            panY = 0f,
            zoomChange = 0.5f
        )
        assertEquals(CallingCardStyle.MIN_BG_SCALE, zoomedOut.scale, 0.001f)
        assertEquals(0f, zoomedOut.offsetX, 0.001f)
        assertEquals(0f, zoomedOut.offsetY, 0.001f)

        val zoomedIn = CallingCardTransformMath.applyTransformGesture(
            current = WallpaperTransform(scale = 3f, offsetX = 0f, offsetY = 0f),
            containerWidth = 400f,
            containerHeight = 800f,
            intrinsicWidth = 400f,
            intrinsicHeight = 800f,
            centroidX = 200f,
            centroidY = 400f,
            panX = 0f,
            panY = 0f,
            zoomChange = 3f
        )
        assertEquals(CallingCardStyle.MAX_BG_SCALE, zoomedIn.scale, 0.001f)
    }

    @Test
    fun doubleTapTogglesBetweenZoomedAndReset() {
        val zoomed = CallingCardTransformMath.toggleDoubleTapTransform(
            current = WallpaperTransform(),
            containerWidth = 400f,
            containerHeight = 800f,
            intrinsicWidth = 400f,
            intrinsicHeight = 800f,
            tapX = 250f,
            tapY = 450f
        )
        assertEquals(2f, zoomed.scale, 0.001f)
        assertTrue(zoomed.offsetX < 0f)
        assertTrue(zoomed.offsetY < 0f)

        val reset = CallingCardTransformMath.toggleDoubleTapTransform(
            current = zoomed,
            containerWidth = 400f,
            containerHeight = 800f,
            intrinsicWidth = 400f,
            intrinsicHeight = 800f,
            tapX = 250f,
            tapY = 450f
        )
        assertEquals(WallpaperTransform(), reset)
    }
}
