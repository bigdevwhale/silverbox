package app.silverbox.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Everything in one zip: a JSON index of rolls and frames, the camera's originals, the prints and the
 * films. Nothing leaves the phone unless the keeper exports it.
 */
object Backup {
    private const val VERSION = 1
    private const val INDEX = "silverbox.json"

    fun encode(rolls: List<Roll>, frames: List<Frame>): String = JSONObject()
        .put("app", "silverbox")
        .put("version", VERSION)
        .put("rolls", JSONArray(rolls.map {
            JSONObject().put("id", it.id).put("format", it.format).put("stock", it.stock).put("capacity", it.capacity).put("name", it.name)
                .put("loadedAt", it.loadedAt).put("sentAt", it.sentAt ?: JSONObject.NULL).put("readyAt", it.readyAt ?: JSONObject.NULL)
                .put("express", it.express).put("order", it.order).put("developed", it.developed).put("opened", it.opened)
                .put("expired", it.expired.toDouble()).put("film", it.film)
        }))
        .put("frames", JSONArray(frames.map {
            JSONObject().put("id", it.id).put("rollId", it.rollId).put("number", it.number).put("original", it.original).put("video", it.video)
                .put("durationMs", it.durationMs).put("clipStartMs", it.clipStartMs).put("takenAt", it.takenAt).put("source", it.source)
                .put("print", it.print).put("defect", it.defect).put("album", it.album).put("note", it.note).put("by", it.by)
        }))
        .toString(2)

    /** Throws [IllegalArgumentException] when the text is not a Silverbox index. */
    fun decode(text: String): Pair<List<Roll>, List<Frame>> {
        val root = runCatching { JSONObject(text) }.getOrNull()
        require(root != null && root.optString("app") == "silverbox") { "Not a Silverbox backup" }
        val r = root.optJSONArray("rolls") ?: JSONArray()
        val f = root.optJSONArray("frames") ?: JSONArray()
        val rolls = (0 until r.length()).map { i ->
            r.getJSONObject(i).run {
                Roll(
                    id = getLong("id"), format = getString("format"), stock = getString("stock"), capacity = getInt("capacity"), name = optString("name"),
                    loadedAt = getLong("loadedAt"), sentAt = optL("sentAt"), readyAt = optL("readyAt"), express = optBoolean("express"),
                    order = optInt("order"), developed = optBoolean("developed"), opened = optBoolean("opened"),
                    expired = optDouble("expired", 0.0).toFloat(), film = optString("film"),
                )
            }
        }
        val frames = (0 until f.length()).map { i ->
            f.getJSONObject(i).run {
                Frame(
                    id = getLong("id"), rollId = getLong("rollId"), number = getInt("number"), original = getString("original"),
                    video = optBoolean("video"), durationMs = optLong("durationMs"), clipStartMs = optLong("clipStartMs"), takenAt = getLong("takenAt"),
                    source = optString("source"), print = optString("print"), defect = optString("defect"), album = optBoolean("album"),
                    note = optString("note"), by = optString("by"),
                )
            }
        }
        return rolls to frames
    }

    private fun JSONObject.optL(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)

    fun write(context: Context, uri: Uri, rolls: List<Roll>, frames: List<Frame>, vault: Vault) {
        context.contentResolver.openOutputStream(uri)?.use { os ->
            ZipOutputStream(os.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(INDEX))
                zip.write(encode(rolls, frames).toByteArray())
                zip.closeEntry()
                val names = frames.flatMap { listOf(it.original, it.print) } + rolls.map { it.film }
                names.filter { it.isNotEmpty() }.distinct().forEach { name ->
                    val file = vault.file(name)
                    if (!file.exists()) return@forEach
                    zip.putNextEntry(ZipEntry("media/$name"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: error("Can't write there")
    }

    /** Reads a backup into a fresh folder first, so a broken file never wipes what is here. */
    fun read(context: Context, uri: Uri): Triple<List<Roll>, List<Frame>, File> {
        val tmp = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var index: String? = null
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    when {
                        e.name == INDEX -> index = zip.readBytes().decodeToString()
                        e.name.startsWith("media/") && !e.isDirectory -> {
                            val name = e.name.removePrefix("media/")
                            require(!name.contains('/') && !name.contains("..")) { "Bad entry" }
                            File(tmp, name).outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
            }
        } ?: error("Can't read that")
        val (rolls, frames) = decode(index ?: throw IllegalArgumentException("Not a Silverbox backup"))
        return Triple(rolls, frames, tmp)
    }
}
