package uz.focuson.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.time.LocalDate

/**
 * Once a week, writes a backup to Download/FocusOn on the phone, so the data
 * survives even if the app is removed. Keeps the newest [KEEP] files.
 */
object AutoBackup {
    private const val WEEK_MS = 7 * 24 * 3600 * 1000L
    private const val KEEP = 5
    private const val PREFIX = "focuson-auto-"
    private val folder = Environment.DIRECTORY_DOWNLOADS + "/FocusOn/"

    fun maybeRun(context: Context) {
        if (System.currentTimeMillis() - Store.lastAutoBackup() < WEEK_MS) return
        runCatching { write(context) }
    }

    /** Writes a backup now. Returns the file name, or null if this Android version can't. */
    fun write(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val name = "$PREFIX${LocalDate.now()}.json"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, folder)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)!!.use { it.write(Store.exportJson().toByteArray()) }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        Store.setLastAutoBackup(System.currentTimeMillis())
        prune(context)
        return name
    }

    private fun prune(context: Context) {
        val resolver = context.contentResolver
        val cols = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME)
        val sel = "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
        val ids = ArrayList<Long>()
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, cols, sel, arrayOf(folder, "$PREFIX%"),
            "${MediaStore.Downloads.DATE_ADDED} DESC",
        )?.use { c ->
            while (c.moveToNext()) ids += c.getLong(0)
        }
        ids.drop(KEEP).forEach { id ->
            runCatching {
                resolver.delete(
                    android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id), null, null,
                )
            }
        }
    }
}
