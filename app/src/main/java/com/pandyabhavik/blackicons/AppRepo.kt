package com.pandyabhavik.blackicons

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class AppIcon(
    val label: String,
    val component: ComponentName,
    /** The bundled drawable launchers will use for this app, or null if it needs the automatic one. */
    val packDrawable: String?,
    val bitmap: Bitmap,
) {
    val covered get() = packDrawable != null
}

/** Installed apps with the black icon each one gets. */
object AppRepo {
    private var pack: IconPack? = null
    private val cache = ConcurrentHashMap<ComponentName, AppIcon>()

    fun pack(context: Context): IconPack =
        pack ?: IconPack(context.applicationContext).also { pack = it }

    suspend fun apps(context: Context): List<AppIcon> = withContext(Dispatchers.Default) {
        val app = context.applicationContext
        val iconPack = pack(app).apply { load() }
        val pm = app.packageManager
        val launchable = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
        ).filter { it.activityInfo.packageName != app.packageName }

        coroutineScope {
            launchable.map { info ->
                async {
                    val component = ComponentName(info.activityInfo.packageName, info.activityInfo.name)
                    cache[component] ?: run {
                        val label = info.loadLabel(pm).toString()
                        val packName = iconPack.drawableFor(component)
                        val packBitmap = packName?.let(iconPack::loadDrawable)
                        val bitmap = packBitmap ?: GlyphMaker.make(info.loadIcon(pm))
                        AppIcon(label, component, packName.takeIf { packBitmap != null }, bitmap).also { cache[component] = it }
                    }
                }
            }.awaitAll()
        }.sortedBy { it.label.lowercase() }
    }
}
