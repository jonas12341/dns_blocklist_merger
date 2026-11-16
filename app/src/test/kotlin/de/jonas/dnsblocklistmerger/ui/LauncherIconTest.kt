package de.jonas.dnsblocklistmerger.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import de.jonas.dnsblocklistmerger.R
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = android.app.Application::class)
class LauncherIconTest {
    @Test
    fun adaptiveIconHasAllLayersAndRenders() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        assertNotNull(icon.foreground)
        assertNotNull(icon.background)
        assertNotNull(icon.monochrome)
        // Look by eye: app/build/screenshots/launcher-icon.png
        val bitmap = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 432, 432)
        icon.draw(Canvas(bitmap))
        File("build/screenshots").mkdirs()
        File("build/screenshots/launcher-icon.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
