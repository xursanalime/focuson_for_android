package uz.focuson

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import uz.focuson.ui.AppIcons
import uz.focuson.ui.drawAppIcon
import java.io.File

/** Renders the app icon at every size the phone and the computer need (into build/icons). */
class IconGen {
    private fun png(file: File, px: Int, small: Boolean, tile: Boolean) {
        val scene = ImageComposeScene(px, px, Density(1f)) {
            Canvas(Modifier.fillMaxSize()) { drawAppIcon(small = small, tile = tile) }
        }
        file.parentFile.mkdirs()
        file.writeBytes(scene.render(0).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }

    @Test fun icons() {
        val out = File("build/icons")
        listOf(16, 24, 32, 48, 64, 128, 256, 512).forEach { px -> png(File(out, "desktop/$px.png"), px, px <= 32, true) }
        mapOf("mdpi" to 108, "hdpi" to 162, "xhdpi" to 216, "xxhdpi" to 324, "xxxhdpi" to 432).forEach { (d, px) ->
            png(File(out, "android/mipmap-$d/ic_launcher_foreground.png"), px, false, false)
        }
        // preview: big icon, small icon, and the menu icons
        val scene = ImageComposeScene(900, 300, Density(2f)) {
            Row(Modifier.fillMaxSize().background(Color(0xFFF1F3EF)).padding(12.dp)) {
                Canvas(Modifier.size(120.dp)) { drawAppIcon() }
                Canvas(Modifier.size(24.dp)) { drawAppIcon(small = true) }
                Column(Modifier.padding(start = 16.dp)) {
                    Row { listOf(AppIcons.Focus, AppIcons.Garden, AppIcons.Limits, AppIcons.Shop, AppIcons.Settings).forEach {
                        Icon(it, null, Modifier.padding(6.dp).size(36.dp), tint = Color(0xFF2B5C43))
                    } }
                    Row { listOf(AppIcons.Focus, AppIcons.Garden, AppIcons.Limits, AppIcons.Shop, AppIcons.Settings).forEach {
                        Icon(it, null, Modifier.padding(6.dp).size(24.dp), tint = Color(0xFF6B7C72))
                    } }
                }
            }
        }
        File(out, "preview.png").writeBytes(scene.render(0).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }
}
