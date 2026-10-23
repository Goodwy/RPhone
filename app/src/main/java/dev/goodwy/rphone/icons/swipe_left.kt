package dev.goodwy.rphone.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val swipe_left: ImageVector
    get() {
        if (_swipe_left != null) {
            return _swipe_left!!
        }
        _swipe_left =
            ImageVector.Builder(
                name = "swipe_left",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            )
                .apply {
                    path(
                        fill = SolidColor(Color.Black),
                        fillAlpha = 1f,
                        stroke = null,
                        strokeAlpha = 1f,
                        strokeLineWidth = 1f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Bevel,
                        strokeLineMiter = 1f,
                        pathFillType = PathFillType.Companion.NonZero,
                    ) {
                        moveTo(4.1f, 5.5f)
                        horizontalLineTo(6.25f)
                        quadToRelative(0.33f, 0f, 0.54f, 0.21f)
                        quadTo(7f, 5.93f, 7f, 6.25f)
                        reflectiveQuadTo(6.79f, 6.79f)
                        reflectiveQuadTo(6.25f, 7f)
                        horizontalLineTo(3f)
                        quadTo(2.58f, 7f, 2.29f, 6.71f)
                        quadTo(2f, 6.43f, 2f, 6f)
                        verticalLineTo(2.75f)
                        quadTo(2f, 2.42f, 2.21f, 2.21f)
                        quadTo(2.43f, 2f, 2.75f, 2f)
                        reflectiveQuadTo(3.29f, 2.21f)
                        reflectiveQuadTo(3.5f, 2.75f)
                        verticalLineTo(4.02f)
                        quadTo(5.3f, 2.55f, 7.48f, 1.77f)
                        reflectiveQuadTo(12f, 1f)
                        quadToRelative(3.35f, 0f, 6f, 1.42f)
                        reflectiveQuadToRelative(3.7f, 3.75f)
                        quadToRelative(0.13f, 0.3f, -0.03f, 0.56f)
                        reflectiveQuadTo(21.23f, 7f)
                        quadTo(20.85f, 7f, 20.54f, 6.8f)
                        reflectiveQuadTo(20.03f, 6.27f)
                        quadTo(18.9f, 4.5f, 16.8f, 3.5f)
                        reflectiveQuadTo(12f, 2.5f)
                        quadTo(9.8f, 2.5f, 7.78f, 3.27f)
                        reflectiveQuadTo(4.1f, 5.5f)
                        close()
                        moveTo(11.83f, 22f)
                        quadToRelative(-0.6f, 0f, -1.15f, -0.23f)
                        reflectiveQuadTo(9.7f, 21.13f)
                        lineTo(5.3f, 16.7f)
                        quadTo(5.03f, 16.43f, 5.01f, 16.01f)
                        reflectiveQuadTo(5.28f, 15.3f)
                        lineTo(5.35f, 15.23f)
                        quadToRelative(0.4f, -0.4f, 0.94f, -0.54f)
                        reflectiveQuadTo(7.35f, 14.7f)
                        lineTo(9f, 15.18f)
                        verticalLineTo(7f)
                        quadTo(9f, 6.57f, 9.29f, 6.29f)
                        quadTo(9.58f, 6f, 10f, 6f)
                        reflectiveQuadToRelative(0.71f, 0.29f)
                        reflectiveQuadTo(11f, 7f)
                        verticalLineToRelative(9.5f)
                        quadToRelative(0f, 0.5f, -0.4f, 0.8f)
                        reflectiveQuadTo(9.73f, 17.48f)
                        lineTo(8.58f, 17.15f)
                        lineToRelative(2.55f, 2.55f)
                        quadToRelative(0.13f, 0.13f, 0.31f, 0.21f)
                        reflectiveQuadTo(11.83f, 20f)
                        horizontalLineTo(16f)
                        quadToRelative(0.82f, 0f, 1.41f, -0.59f)
                        reflectiveQuadTo(18f, 18f)
                        verticalLineTo(14f)
                        quadToRelative(0f, -0.43f, 0.29f, -0.71f)
                        reflectiveQuadTo(19f, 13f)
                        reflectiveQuadToRelative(0.71f, 0.29f)
                        reflectiveQuadTo(20f, 14f)
                        verticalLineToRelative(4f)
                        quadToRelative(0f, 1.65f, -1.18f, 2.82f)
                        reflectiveQuadTo(16f, 22f)
                        horizontalLineTo(11.83f)
                        close()
                        moveTo(13.71f, 10.29f)
                        quadTo(14f, 10.58f, 14f, 11f)
                        verticalLineToRelative(3f)
                        quadToRelative(0f, 0.42f, -0.29f, 0.71f)
                        reflectiveQuadTo(13f, 15f)
                        quadToRelative(-0.42f, 0f, -0.71f, -0.29f)
                        reflectiveQuadTo(12f, 14f)
                        verticalLineTo(11f)
                        quadToRelative(0f, -0.43f, 0.29f, -0.71f)
                        reflectiveQuadTo(13f, 10f)
                        reflectiveQuadToRelative(0.71f, 0.29f)
                        close()
                        moveToRelative(3f, 1f)
                        quadTo(17f, 11.58f, 17f, 12f)
                        verticalLineToRelative(2f)
                        quadToRelative(0f, 0.42f, -0.29f, 0.71f)
                        reflectiveQuadTo(16f, 15f)
                        quadToRelative(-0.42f, 0f, -0.71f, -0.29f)
                        reflectiveQuadTo(15f, 14f)
                        verticalLineTo(12f)
                        quadToRelative(0f, -0.43f, 0.29f, -0.71f)
                        reflectiveQuadTo(16f, 11f)
                        quadToRelative(0.43f, 0f, 0.71f, 0.29f)
                        close()
                        moveTo(14.28f, 17f)
                        close()
                    }
                }
                .build()
        return _swipe_left!!
    }

private var _swipe_left: ImageVector? = null