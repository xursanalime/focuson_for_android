package uz.focuson.block

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import uz.focuson.MainActivity
import uz.focuson.data.Catalog
import uz.focuson.data.Store
import uz.focuson.focus.FocusController
import uz.focuson.limits.AppLimits
import androidx.compose.runtime.mutableIntStateOf
import uz.focuson.ui.C
import uz.focuson.ui.FocusTheme
import uz.focuson.ui.PlantView
import uz.focuson.ui.formatClock

/** Shown instead of a blocked app while a focus session runs. */
class BlockActivity : ComponentActivity() {
    private var pkg by mutableStateOf<String?>(null)
    private var mode by mutableStateOf(MODE_FOCUS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pkg = intent.getStringExtra(EXTRA_PKG)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_FOCUS
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = if (mode == MODE_LIMIT) goHome() else goBack()
        })
        setContent { FocusTheme { if (mode == MODE_LIMIT) LimitContent() else BlockContent() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pkg = intent.getStringExtra(EXTRA_PKG)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_FOCUS
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    /** Shown when an app used up its daily limit. */
    @Composable
    private fun LimitContent() {
        val p = pkg ?: return
        val limits by Store.limits.collectAsStateWithLifecycle()
        val limitMin = limits[p] ?: 0
        var used by remember { mutableLongStateOf(0L) }
        var left by remember { mutableIntStateOf(AppLimits.passesLeft(p)) }
        LaunchedEffect(p) { used = uz.focuson.limits.UsageTracker.usedToday(applicationContext)[p] ?: 0L }
        val info = remember(p) {
            runCatching {
                val ai = packageManager.getApplicationInfo(p, 0)
                packageManager.getApplicationLabel(ai).toString() to
                    packageManager.getApplicationIcon(ai).toBitmap(120, 120).asImageBitmap()
            }.getOrNull()
        }
        val label = info?.first ?: "Bu ilova"
        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF2A2440), Color(0xFF3B3357))))
                .systemBarsPadding()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            info?.let { Image(it.second, null, Modifier.size(64.dp)) }
            Spacer(Modifier.height(14.dp))
            Text("$label uchun bugungi vaqt tugadi", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                "Limit: ${uz.focuson.ui.formatShort(limitMin * 60_000L)} · bugun ishlatildi: ${uz.focuson.ui.formatShort(used)}",
                color = Color(0xFFD9D2F0), fontSize = 15.sp, textAlign = TextAlign.Center,
            )
            Text("Ertaga yarim tundan keyin yana ochiladi.", color = Color(0xFFB9B0D6), fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = ::goHome,
                modifier = Modifier.width(240.dp).height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF2A2440)),
            ) { Text("Yopish", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(10.dp))
            if (left > 0) {
                TextButton(onClick = {
                    if (AppLimits.grant(p)) {
                        left = AppLimits.passesLeft(p)
                        packageManager.getLaunchIntentForPackage(p)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        finish()
                    }
                }) {
                    Text("5 daqiqa ochish (bugun yana $left marta)", color = Color(0xFFE6DFFF), fontSize = 15.sp)
                }
            } else {
                Text("Bugungi 5 daqiqalik ruxsatlar tugagan.", color = Color(0xFFB9B0D6), fontSize = 13.sp)
            }
        }
    }

    private fun goBack() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    @Composable
    private fun BlockContent() {
        val active by Store.active.collectAsStateWithLifecycle()
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var confirm by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            while (true) {
                now = System.currentTimeMillis()
                FocusController.check(applicationContext)
                delay(250)
            }
        }
        val s = active
        LaunchedEffect(s == null) { if (s == null) finish() }
        if (s == null) return

        val appLabel = remember(pkg) {
            pkg?.let { p ->
                runCatching {
                    val ai = packageManager.getApplicationInfo(p, 0)
                    packageManager.getApplicationLabel(ai).toString() to
                        packageManager.getApplicationIcon(ai).toBitmap(120, 120).asImageBitmap()
                }.getOrNull()
            }
        }
        val progress = ((now - s.startAt).toFloat() / s.durationMs).coerceIn(0f, 1f)
        val warnLeft = if (s.warnDeadline > 0) ((s.warnDeadline - now + 999) / 1000).coerceAtLeast(0) else -1

        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(C.night, Color(0xFF2E5546))))
                .systemBarsPadding()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            appLabel?.let { (label, icon) ->
                Image(icon, null, Modifier.size(56.dp))
                Spacer(Modifier.height(10.dp))
                Text("$label hozir yopiq", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            } ?: Text("Bu ilova hozir yopiq", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(8.dp))
            Text(
                "${Catalog.byId(s.speciesId).name} o'smoqda. Fokus tugashiga ${formatClock(s.endAt - now)} qoldi.",
                color = Color(0xFFCFE3D6), fontSize = 16.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            PlantView(s.speciesId, progress, sway = true, modifier = Modifier.size(200.dp))
            Spacer(Modifier.height(20.dp))

            if (warnLeft >= 0) {
                Text(
                    "Qattiq rejim: $warnLeft soniya ichida qayting,\naks holda o'simlik quriydi.",
                    color = Color(0xFFFFC9A8), fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
            }

            Button(
                onClick = ::goBack,
                modifier = Modifier.width(240.dp).height(54.dp),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = C.primaryDark),
            ) { Text("Focus On'ga qaytish", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { confirm = true }) {
                Text("Taslim bo'lish", color = Color(0xFFB9CFC2))
            }
        }

        if (confirm) AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Taslim bo'lasizmi?") },
            text = { Text("O'simlik qurib qoladi va bloklash to'xtaydi.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    FocusController.giveUp(applicationContext)
                    goBack()
                }) { Text("Ha, taslim", color = C.danger) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Davom etaman") } },
        )
    }

    companion object {
        const val EXTRA_PKG = "pkg"
        const val EXTRA_MODE = "mode"
        const val MODE_FOCUS = "focus"
        const val MODE_LIMIT = "limit"
    }
}
