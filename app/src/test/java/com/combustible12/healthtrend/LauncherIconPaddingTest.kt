package com.combustible12.healthtrend

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherIconPaddingTest {
    @Test fun artworkKeepsProportionalPaddingAtEveryRenderSize() {
        val context = RuntimeEnvironment.getApplication()
        val fractions = mutableListOf<Double>()
        for (size in listOf(108, 432, 1296)) {
            val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
            val foreground = icon.foreground
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            foreground.setBounds(0, 0, size, size)
            foreground.draw(Canvas(bitmap))
            var left = size; var top = size; var right = -1; var bottom = -1
            for (y in 0 until size) for (x in 0 until size) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) > 200 && Color.red(pixel) > 170 &&
                    Color.green(pixel) > 190 && Color.blue(pixel) > 210) {
                    left = minOf(left, x); top = minOf(top, y)
                    right = maxOf(right, x); bottom = maxOf(bottom, y)
                }
            }
            assertTrue("Artwork must render at size $size", right > left && bottom > top)
            val padding = minOf(left, top, size - 1 - right, size - 1 - bottom).toDouble() / size
            val height = (bottom - top + 1).toDouble() / size
            println("LAUNCHER_QA size=$size padding=$padding artworkHeight=$height")
            assertTrue("Visible padding at every scale: $size, $padding", padding > 0.20)
            assertTrue("Full illustration has balanced size: $size, $height", height in 0.40..0.52)
            fractions += height
            bitmap.recycle()
        }
        assertTrue("Artwork scale must be independent of raster size", fractions.max() - fractions.min() < 0.025)
    }
}
