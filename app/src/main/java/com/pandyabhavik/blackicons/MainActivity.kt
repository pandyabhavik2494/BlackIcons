package com.pandyabhavik.blackicons

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val NOVA = "com.teslacoilsw.launcher"
private val LAWNCHAIR = listOf("app.lawnchair", "app.lawnchair.play")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BlackTheme { Home() } }
    }
}

@Composable
private fun Home() {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppIcon>?>(null) }
    var onlyAuto by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<AppIcon?>(null) }
    LaunchedEffect(Unit) { apps = AppRepo.apps(context) }

    val all = apps
    if (all == null) {
        Loading()
        return
    }
    val covered = all.count { it.covered }
    AppGrid(
        apps = if (onlyAuto) all.filterNot { it.covered } else all,
        onClick = { selected = it },
        modifier = Modifier.safeDrawingPadding(),
    ) {
        fullWidth {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("BlackIcons", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(
                    "$covered of your ${all.size} apps have a hand-drawn black icon. " +
                        "The rest get an automatic one you can set by hand.",
                )
                ApplyCard(context)
                SaveAllButton(all)
                ShareMissingButton(all.filterNot { it.covered })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !onlyAuto, onClick = { onlyAuto = false }, label = { Text("All") })
                    FilterChip(
                        selected = onlyAuto,
                        onClick = { onlyAuto = true },
                        label = { Text("Need manual icon (${all.size - covered})") },
                    )
                }
            }
        }
    }
    selected?.let { app -> IconDialog(app) { selected = null } }
}

@Composable
private fun SaveAllButton(apps: List<AppIcon>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    OutlinedButton(
        enabled = !saving,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            saving = true
            scope.launch {
                val saved = withContext(Dispatchers.IO) { apps.count { saveToPhotos(context, it.label, it.bitmap) } }
                saving = false
                Toast.makeText(context, "Saved $saved icons to Pictures/BlackIcons", Toast.LENGTH_LONG).show()
            }
        },
    ) { Text(if (saving) "Saving…" else "Save all to Photos (for Good Lock Theme Park)") }
}

@Composable
private fun ShareMissingButton(missing: List<AppIcon>) {
    val context = LocalContext.current
    OutlinedButton(
        enabled = missing.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            val pack = AppRepo.pack(context)
            val text = missing.joinToString("\n", prefix = "BlackIcons missing apps (${missing.size}):\n") {
                "${it.label} | ${it.component.flattenToString()} | ${pack.drawableForPackage(it.component.packageName) ?: "-"}"
            }
            context.startActivity(
                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null),
            )
        },
    ) { Text("Share missing apps (${missing.size})") }
}

private fun Context.isInstalled(pkg: String) =
    runCatching { packageManager.getPackageInfo(pkg, 0) }.isSuccess

@Composable
private fun ApplyCard(context: Context) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F2F2)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Apply", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            val nova = context.isInstalled(NOVA)
            val lawnchair = LAWNCHAIR.firstOrNull { context.isInstalled(it) }
            if (nova) {
                Button(onClick = { applyInNova(context) }) { Text("Apply in Nova") }
            }
            if (lawnchair != null) {
                Text("Lawnchair: Settings > Home screen > Icon style > Icon pack > BlackIcons")
                Button(onClick = {
                    context.packageManager.getLaunchIntentForPackage(lawnchair)?.let(context::startActivity)
                }) { Text("Open Lawnchair") }
            }
            if (!nova && lawnchair == null) {
                Text("Install Nova Launcher from the Play Store, then come back.")
            }
        }
    }
}

private fun applyInNova(context: Context) {
    try {
        context.startActivity(
            Intent("com.teslacoilsw.launcher.APPLY_ICON_THEME")
                .setPackage(NOVA)
                .putExtra("com.teslacoilsw.launcher.extra.ICON_THEME_TYPE", "GO")
                .putExtra("com.teslacoilsw.launcher.extra.ICON_THEME_PACKAGE", context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: Exception) {
        Toast.makeText(context, "Open Nova Settings > Look & feel > Icon style > Icon theme", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun IconDialog(app: AppIcon, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(app.label) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Image(app.bitmap.asImageBitmap(), contentDescription = app.label, modifier = Modifier.size(160.dp))
                Text(
                    if (app.covered) "Hand-drawn icon. Your launcher uses it automatically."
                    else "Made automatically from the app's own icon. Set it in your launcher by editing " +
                        "this app's icon and picking BlackIcons, or save it to Photos.",
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ok = saveToPhotos(context, app.label, app.bitmap)
                Toast.makeText(context, if (ok) "Saved to Pictures/BlackIcons" else "Couldn't save", Toast.LENGTH_SHORT).show()
            }) { Text("Save to Photos") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun saveToPhotos(context: Context, label: String, bitmap: Bitmap): Boolean = runCatching {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, label.replace(Regex("[^A-Za-z0-9 _-]"), "_") + ".png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BlackIcons")
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
}.getOrDefault(false)
