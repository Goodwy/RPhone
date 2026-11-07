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
public val page_header: ImageVector
    get() {
        if (_page_header != null) {
            return _page_header!!
        }
        _page_header =
            ImageVector.Builder(
                name = "page_header",
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
                        moveTo(4f, 5f)
                        quadTo(3.58f, 5f, 3.29f, 4.71f)
                        reflectiveQuadTo(3f, 4f)
                        quadTo(3f, 3.57f, 3.29f, 3.29f)
                        reflectiveQuadTo(4f, 3f)
                        horizontalLineTo(20f)
                        quadToRelative(0.43f, 0f, 0.71f, 0.29f)
                        reflectiveQuadTo(21f, 4f)
                        quadToRelative(0f, 0.42f, -0.29f, 0.71f)
                        reflectiveQuadTo(20f, 5f)
                        horizontalLineTo(4f)
                        close()
                        moveTo(19f, 7f)
                        quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                        reflectiveQuadTo(21f, 9f)
                        verticalLineTo(19f)
                        quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                        reflectiveQuadTo(19f, 21f)
                        horizontalLineTo(5f)
                        quadTo(4.18f, 21f, 3.59f, 20.41f)
                        reflectiveQuadTo(3f, 19f)
                        verticalLineTo(9f)
                        quadTo(3f, 8.17f, 3.59f, 7.59f)
                        reflectiveQuadTo(5f, 7f)
                        horizontalLineTo(19f)
                        close()
                    }
                }
                .build()
        return _page_header!!
    }

private var _page_header: ImageVector? = null