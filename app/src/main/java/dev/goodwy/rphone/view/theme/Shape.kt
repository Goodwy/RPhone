package dev.goodwy.rphone.view.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

object RillShapeDefaults {
    const val DefaultRoundness: Int = 28
    val RoundnessOptions: List<Int> = listOf(32, 28, 20, 16, 12, 8)

    const val BaseExtraSmall: Int = 4
    const val BaseSmall: Int = 8
    const val BaseMedium: Int = 12
    const val BaseLarge: Int = 16
    const val BaseLargeIncreased: Int = 20
    const val BaseExtraLarge: Int = 28
    const val BaseExtraLargeIncreased: Int = 32
    const val BaseExtraExtraLarge: Int = 38 //48

    val None: Shape = RectangleShape
    val Full: Shape = CircleShape
}

fun rillRoundnessScale(roundness: Int): Float =
    roundness.coerceIn(4, RillShapeDefaults.BaseExtraExtraLarge) /
            RillShapeDefaults.DefaultRoundness.toFloat()

fun rillCornerDp(baseDp: Int, roundness: Int): Dp =
    (baseDp * rillRoundnessScale(roundness)).coerceAtLeast(1f).dp

fun rillCornerShape(baseDp: Int, roundness: Int): CornerBasedShape =
    RoundedCornerShape(rillCornerDp(baseDp, roundness))

fun rillShapes(roundness: Int = RillShapeDefaults.DefaultRoundness): Shapes {
    val scale = rillRoundnessScale(roundness)
    fun corner(baseDp: Int): CornerBasedShape {
        val cornerDp = (baseDp * scale).coerceAtLeast(1f).dp
        return RoundedCornerShape(cornerDp)
    }
    return Shapes(
        extraSmall = corner(RillShapeDefaults.BaseExtraSmall),
        small = corner(RillShapeDefaults.BaseSmall),
        medium = corner(RillShapeDefaults.BaseMedium),
        large = corner(RillShapeDefaults.BaseLarge),
        extraLarge = corner(RillShapeDefaults.BaseExtraLarge)
    ).copy(
        largeIncreased = corner(RillShapeDefaults.BaseLargeIncreased),
        extraLargeIncreased = corner(RillShapeDefaults.BaseExtraLargeIncreased),
        extraExtraLarge = corner(RillShapeDefaults.BaseExtraExtraLarge)
    )
}

val LocalCardRoundness: ProvidableCompositionLocal<Int> =
    staticCompositionLocalOf { RillShapeDefaults.DefaultRoundness }

object RillMaterialShapes {
    val Circle: RoundedPolygon get() = MaterialShapes.Circle
    val Square: RoundedPolygon get() = MaterialShapes.Square
    val Slanted: RoundedPolygon get() = MaterialShapes.Slanted
    val Arch: RoundedPolygon get() = MaterialShapes.Arch
    val Fan: RoundedPolygon get() = MaterialShapes.Fan
    val Arrow: RoundedPolygon get() = MaterialShapes.Arrow
    val SemiCircle: RoundedPolygon get() = MaterialShapes.SemiCircle
    val Oval: RoundedPolygon get() = MaterialShapes.Oval
    val Pill: RoundedPolygon get() = MaterialShapes.Pill
    val Triangle: RoundedPolygon get() = MaterialShapes.Triangle
    val Diamond: RoundedPolygon get() = MaterialShapes.Diamond
    val ClamShell: RoundedPolygon get() = MaterialShapes.ClamShell
    val Pentagon: RoundedPolygon get() = MaterialShapes.Pentagon
    val Gem: RoundedPolygon get() = MaterialShapes.Gem
    val Sunny: RoundedPolygon get() = MaterialShapes.Sunny
    val VerySunny: RoundedPolygon get() = MaterialShapes.VerySunny
    val Cookie4Sided: RoundedPolygon get() = MaterialShapes.Cookie4Sided
    val Cookie6Sided: RoundedPolygon get() = MaterialShapes.Cookie6Sided
    val Cookie7Sided: RoundedPolygon get() = MaterialShapes.Cookie7Sided
    val Cookie9Sided: RoundedPolygon get() = MaterialShapes.Cookie9Sided
    val Cookie12Sided: RoundedPolygon get() = MaterialShapes.Cookie12Sided
    val Clover4Leaf: RoundedPolygon get() = MaterialShapes.Clover4Leaf
    val Clover8Leaf: RoundedPolygon get() = MaterialShapes.Clover8Leaf
    val Burst: RoundedPolygon get() = MaterialShapes.Burst
    val SoftBurst: RoundedPolygon get() = MaterialShapes.SoftBurst
    val Boom: RoundedPolygon get() = MaterialShapes.Boom
    val SoftBoom: RoundedPolygon get() = MaterialShapes.SoftBoom
    val Flower: RoundedPolygon get() = MaterialShapes.Flower
    val Puffy: RoundedPolygon get() = MaterialShapes.Puffy
    val PuffyDiamond: RoundedPolygon get() = MaterialShapes.PuffyDiamond
    val PixelCircle: RoundedPolygon get() = MaterialShapes.PixelCircle
    val PixelTriangle: RoundedPolygon get() = MaterialShapes.PixelTriangle
    val Ghostish: RoundedPolygon get() = MaterialShapes.Ghostish
    val Heart: RoundedPolygon get() = MaterialShapes.Heart
    val Bun: RoundedPolygon get() = MaterialShapes.Bun

    val AvatarPolygons: List<RoundedPolygon>
        get() = listOf(Circle, Cookie9Sided, Clover4Leaf, Arch, Pill, Gem, Sunny, PixelCircle)

    val IncomingCallPulse: List<RoundedPolygon>
        get() = listOf(Circle, Cookie9Sided, Clover4Leaf, Circle)

    val AvatarMorphStart: RoundedPolygon get() = Circle
    val AvatarMorphEnd: RoundedPolygon get() = Cookie9Sided
}

const val RILL_AVATAR_SHAPE_SQUIRCLE: Int = 0
const val RILL_AVATAR_SHAPE_CIRCLE: Int = 1
const val RILL_AVATAR_SHAPE_SQUARE: Int = 2
const val RILL_AVATAR_SHAPE_COOKIE: Int = 3
const val RILL_AVATAR_SHAPE_CLOVER: Int = 4
const val RILL_AVATAR_SHAPE_ARCH: Int = 5
const val RILL_AVATAR_SHAPE_PILL: Int = 6
const val RILL_AVATAR_SHAPE_GEM: Int = 7
const val RILL_AVATAR_SHAPE_SUNNY: Int = 8
const val RILL_AVATAR_SHAPE_HEART: Int = 9
const val RILL_AVATAR_SHAPE_BURST: Int = 10

@Composable
fun rillAvatarShape(shapeIndex: Int): Shape = when (shapeIndex) {
    RILL_AVATAR_SHAPE_SQUIRCLE -> MaterialTheme.shapes.large
    RILL_AVATAR_SHAPE_CIRCLE -> CircleShape
    RILL_AVATAR_SHAPE_SQUARE -> RoundedCornerShape(8.dp)
    RILL_AVATAR_SHAPE_COOKIE -> MaterialShapes.Cookie9Sided.toShape()
    RILL_AVATAR_SHAPE_CLOVER -> MaterialShapes.Clover4Leaf.toShape()
    RILL_AVATAR_SHAPE_ARCH -> MaterialShapes.Arch.toShape()
    RILL_AVATAR_SHAPE_PILL -> MaterialShapes.Pill.toShape()
    RILL_AVATAR_SHAPE_GEM -> MaterialShapes.Gem.toShape()
    RILL_AVATAR_SHAPE_SUNNY -> MaterialShapes.Sunny.toShape()
    RILL_AVATAR_SHAPE_HEART -> MaterialShapes.Heart.toShape()
    RILL_AVATAR_SHAPE_BURST -> MaterialShapes.SoftBurst.toShape()
    else -> CircleShape
}

@Composable
fun rillPolygonShape(polygon: RoundedPolygon): Shape = polygon.toShape()

class RillMorphShape(
    private val morph: Morph,
    private val progress: () -> Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) return Outline.Rectangle(Rect.Zero)
        val p = progress()
        if (p.isNaN()) return Outline.Rectangle(Rect.Zero)
        val path = morph.toPath(p.coerceIn(0f, 1f))
        val bounds = path.getBounds()
        if (bounds.width <= 0f || bounds.height <= 0f || bounds.width.isNaN() || bounds.height.isNaN()) {
            return Outline.Rectangle(Rect.Zero)
        }
        val scaleX = size.width / bounds.width
        val scaleY = size.height / bounds.height
        if (scaleX.isNaN() || scaleY.isNaN() || scaleX.isInfinite() || scaleY.isInfinite()) {
            return Outline.Rectangle(Rect.Zero)
        }
        val matrix = Matrix()
        matrix.scale(scaleX, scaleY)
        matrix.translate(-bounds.left, -bounds.top)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

fun rillMorph(start: RoundedPolygon, end: RoundedPolygon): Morph = Morph(start, end)

@Composable
fun rememberRillMorph(start: RoundedPolygon, end: RoundedPolygon): Morph =
    remember(start, end) { Morph(start, end) }

@Composable
fun rememberRillMorphShape(
    start: RoundedPolygon,
    end: RoundedPolygon,
    progress: () -> Float
): Shape {
    val morph = rememberRillMorph(start, end)
    val currentProgress = rememberUpdatedState(progress)
    return remember(morph) { RillMorphShape(morph) { currentProgress.value.invoke() } }
}
