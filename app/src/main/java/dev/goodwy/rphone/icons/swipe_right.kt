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
public val swipe_right: ImageVector
    get() {
        if (_swipe_right != null) {
            return _swipe_right!!
        }
        _swipe_right =
            ImageVector.Builder(
                name = "swipe_right",
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
                        moveTo(19.9f, 5.5f)
                        quadTo(18.25f, 4.05f, 16.23f, 3.27f)
                        reflectiveQuadTo(12f, 2.5f)
                        quadTo(9.35f, 2.5f, 7.25f, 3.49f)
                        reflectiveQuadTo(4.03f, 6.2f)
                        quadTo(3.83f, 6.55f, 3.51f, 6.77f)
                        reflectiveQuadTo(2.8f, 7f)
                        quadTo(2.5f, 7f, 2.34f, 6.74f)
                        quadTo(2.18f, 6.47f, 2.3f, 6.18f)
                        quadTo(3.35f, 3.85f, 6f, 2.42f)
                        reflectiveQuadTo(12f, 1f)
                        quadToRelative(2.35f, 0f, 4.53f, 0.77f)
                        reflectiveQuadTo(20.5f, 4.02f)
                        verticalLineTo(2.75f)
                        quadToRelative(0f, -0.33f, 0.21f, -0.54f)
                        reflectiveQuadTo(21.25f, 2f)
                        reflectiveQuadToRelative(0.54f, 0.21f)
                        reflectiveQuadTo(22f, 2.75f)
                        verticalLineTo(6f)
                        quadToRelative(0f, 0.43f, -0.29f, 0.71f)
                        reflectiveQuadTo(21f, 7f)
                        horizontalLineTo(17.75f)
                        quadTo(17.43f, 7f, 17.21f, 6.79f)
                        reflectiveQuadTo(17f, 6.25f)
                        reflectiveQuadTo(17.21f, 5.71f)
                        reflectiveQuadTo(17.75f, 5.5f)
                        horizontalLineTo(19.9f)
                        close()
                        moveTo(14.28f, 17f)
                        close()
                    }
                }
                .build()
        return _swipe_right!!
    }

private var _swipe_right: ImageVector? = null