package com.pandyabhavik.blackicons

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

/** The hand-drawn icons bundled in this app, as launchers see them through res/xml/appfilter.xml. */
class IconPack(private val context: Context) {
    private val mutex = Mutex()
    private var byComponent: Map<String, String>? = null
    private var byPackage: Map<String, String> = emptyMap()

    var iconCount = 0
        private set

    suspend fun load(): Unit = mutex.withLock {
        if (byComponent != null) return@withLock
        withContext(Dispatchers.IO) {
            val components = HashMap<String, String>(60_000)
            val packages = HashMap<String, String>(30_000)
            val names = HashSet<String>()
            val parser = context.resources.getXml(R.xml.appfilter)
            try {
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType != XmlPullParser.START_TAG || parser.name != "item") continue
                    val component = parser.getAttributeValue(null, "component") ?: continue
                    val drawable = parser.getAttributeValue(null, "drawable") ?: continue
                    val flat = component.removePrefix("ComponentInfo{").removeSuffix("}")
                    val pkg = flat.substringBefore('/', "")
                    if (pkg.isEmpty()) continue
                    components.putIfAbsent(flat, drawable)
                    packages.putIfAbsent(pkg, drawable)
                    names += drawable
                }
            } finally {
                parser.close()
            }
            byPackage = packages
            iconCount = names.size
            byComponent = components
        }
    }

    fun drawableFor(component: ComponentName): String? =
        byComponent?.get("${component.packageName}/${component.className}")

    fun drawableForPackage(pkg: String): String? = byPackage[pkg]

    fun resourceId(name: String): Int =
        context.resources.getIdentifier(name, "drawable", context.packageName)

    fun loadDrawable(name: String): Bitmap? {
        val id = resourceId(name)
        if (id == 0) return null
        val d = context.getDrawable(id) ?: return null
        return (d as? BitmapDrawable)?.bitmap ?: d.toBitmap(GlyphMaker.SIZE, GlyphMaker.SIZE)
    }
}
