package com.pandyabhavik.blackicons

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import java.io.File

/** Lets a launcher (or any app asking for a picture) take one of the black icons. */
class PickIconActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlackTheme {
                var apps by remember { mutableStateOf<List<AppIcon>?>(null) }
                var query by remember { mutableStateOf("") }
                LaunchedEffect(Unit) { apps = AppRepo.apps(this@PickIconActivity) }
                val all = apps
                if (all == null) {
                    Loading()
                } else {
                    AppGrid(
                        apps = all.filter { it.label.contains(query.trim(), ignoreCase = true) },
                        onClick = { pick(it) },
                        modifier = Modifier.safeDrawingPadding(),
                    ) {
                        fullWidth {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text("Search apps") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun pick(app: AppIcon) {
        val result = when (intent?.action) {
            Intent.ACTION_GET_CONTENT, Intent.ACTION_PICK -> {
                val dir = File(cacheDir, "picked").apply { mkdirs() }
                val file = File(dir, "${app.component.packageName}.png")
                file.outputStream().use { app.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
                Intent().setDataAndType(uri, "image/png")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .also { it.clipData = ClipData.newRawUri(app.label, uri) }
            }
            else -> Intent().apply {
                putExtra("icon", app.bitmap)
                app.packDrawable?.let { name ->
                    val id = AppRepo.pack(this@PickIconActivity).resourceId(name)
                    if (id != 0) {
                        @Suppress("DEPRECATION")
                        putExtra(
                            Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                            Intent.ShortcutIconResource.fromContext(this@PickIconActivity, id),
                        )
                    }
                }
            }
        }
        setResult(RESULT_OK, result)
        finish()
    }
}
