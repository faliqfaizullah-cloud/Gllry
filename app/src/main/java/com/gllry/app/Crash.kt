package com.gllry.app

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Saves the last crash so the app can show it on the next launch (and never crash-loops on the Stack default). */
object CrashReporter {
    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                File(app.filesDir, "last_crash.txt").writeText("Thread: ${t.name}\n$sw")
                // safe mode: if the Stack style is the default and it is what crashed, switch it off
                app.getSharedPreferences("gllry", Context.MODE_PRIVATE).edit().putBoolean("stack_default", false).commit()
            }
            previous?.uncaughtException(t, e)
        }
    }

    fun read(ctx: Context): String? =
        runCatching { File(ctx.filesDir, "last_crash.txt").takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(ctx: Context) {
        runCatching { File(ctx.filesDir, "last_crash.txt").delete() }
    }
}

@Composable
fun CrashScreen(report: String, onContinue: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().background(Color(0xFF0B0B12)).padding(20.dp).padding(top = 28.dp)) {
        Text("Gllry crashed last time", color = Color.White, fontSize = 22.sp, fontFamily = Display)
        Spacer(Modifier.height(6.dp))
        Text("Take a screenshot of this screen and send it to Claude.", color = Color(0xFF9A9AAB), fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .verticalScroll(rememberScrollState()).padding(12.dp)
        ) {
            Text(
                report.lineSequence().take(70).joinToString("\n"),
                color = Color(0xFFFFB4AB), fontSize = 10.sp, lineHeight = 13.sp, fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.bounceClick {
                runCatching {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("Gllry crash", report))
                }
            }.glass(RoundedCornerShape(50), Color.White, 0.14f, 0.dp).padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text("Copy", color = Color.White, fontWeight = FontWeight.Medium)
            }
            Box(Modifier.bounceClick(onContinue).glass(RoundedCornerShape(50), Color.White, 0.14f, 0.dp)
                .padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text("Continue", color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
