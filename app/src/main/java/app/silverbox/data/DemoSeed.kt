package app.silverbox.data

import android.content.Context
import app.silverbox.AppContainer
import app.silverbox.domain.Format
import app.silverbox.domain.Rules
import app.silverbox.domain.Stock
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * A made-up family year on film for the demo build (screenshots): a summer at Gran's, an autumn at
 * home, Grandpa in black and white, New Year's Eve, a seaside trip a year ago, an envelope waiting, a
 * roll at the lab and one in the camera. Photos come from the demo assets, which only the demo build carries.
 */
object DemoSeed {
    private class R(
        val stock: Stock, val en: String, val ru: String, val daysAgo: Int, val photos: List<String>,
        /** 0: on the shelf, 1: envelope waiting, 2: at the lab, 3: in the camera. */
        val state: Int = 0, val capacity: Int = 24,
        val album: Set<Int> = emptySet(), val notes: Map<Int, Pair<String, String>> = emptyMap(),
    )

    private val rolls = listOf(
        R(
            Stock.SLIDE, "By the sea", "У моря", 365, listOf("1001", "656", "54", "176", "1011", "839", "199"),
            album = setOf(0, 1, 4),
            notes = mapOf(0 to ("Ten years married, and still holding hands" to "Десять лет вместе, и всё ещё за руку"), 1 to ("Masha ran to the sea first" to "Маша первая побежала к морю")),
        ),
        R(
            Stock.SUNNY, "Summer at Gran's", "Лето у бабушки", 110,
            listOf("102", "129", "146", "646", "64", "65", "429", "1080", "103", "674", "75", "76"),
            album = setOf(1, 2, 3, 6, 9),
            notes = mapOf(
                1 to ("Gran and Grandpa on their bench. Forty-six years." to "Бабушка с дедушкой на своей лавочке. 46 лет вместе."),
                2 to ("Dad's old tricycle, found in the shed" to "Папин старый велосипед, нашёлся в сарае"),
                6 to ("Raspberries straight from the bush" to "Малина прямо с куста"),
                9 to ("Blueberry hands" to "Черничные руки"),
            ),
        ),
        R(
            Stock.SILVER, "Grandpa", "Дедушка", 85, listOf("175", "250", "91", "99", "785"), capacity = 24,
            album = setOf(1, 4),
            notes = mapOf(1 to ("His Zorki, still working" to "Его «Зоркий», всё ещё снимает"), 4 to ("He plants something every spring" to "Каждую весну он что-то сажает")),
        ),
        R(
            Stock.EVERY, "Autumn at home", "Осень дома", 40,
            listOf("225", "24", "1010", "691", "755", "167", "301", "1008", "237", "169", "1062", "1082"),
            album = setOf(2, 4, 8, 11),
            notes = mapOf(2 to ("Reading till late again" to "Опять читает до ночи"), 8 to ("Our Ugolyok, first week home" to "Наш Уголёк, первая неделя дома"), 11 to ("Sunday music" to "Воскресная музыка")),
        ),
        R(Stock.NIGHT, "New Year's Eve", "Новый год", 290, listOf("407", "195", "158", "815", "56", "63"), album = setOf(0, 3)),
        R(Stock.EVERY, "October", "Октябрь", 6, listOf("822", "838", "777", "30", "152", "1025"), state = 1),
        R(Stock.SUNNY, "Masha's birthday", "День рождения Маши", 2, listOf("493", "431", "1080", "102", "65", "429", "225", "103"), state = 2),
        R(Stock.SUNNY, "Our autumn", "Наша осень", 12, List(17) { listOf("167", "301", "237", "30", "152")[it % 5] }, state = 3),
    )

    /** [open] reads a demo file by its asset path (the screen renders read them from disk instead). */
    suspend fun seedIfEmpty(context: Context, c: AppContainer, open: (String) -> java.io.InputStream = { context.assets.open(it) }) {
        val dao = c.db.dao()
        if (dao.rolls().isNotEmpty()) return
        // The in-app language, if one is set (AppCompat applies it to activities, not to the application).
        val tags = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        val lang = if (!tags.isEmpty) tags[0]!!.language else context.resources.configuration.locales[0].language
        val ru = lang == "ru"
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone)
        var order = 412
        fun at(d: LocalDate, h: Int, m: Int) = d.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

        rolls.forEachIndexed { ri, r ->
            val start = today.minusDays(r.daysAgo.toLong())
            // Shot over a couple of weeks (the camera's roll is still being shot, up to today).
            val span = if (r.state == 3) r.daysAgo else minOf(14, r.daysAgo)
            val loadedAt = at(start, 9, 0)
            val sent = when (r.state) {
                3 -> null
                2 -> now - 2 * Rules.HOUR
                1 -> now - 26 * Rules.HOUR
                else -> at(start.plusDays(span.toLong() + 1), 18, 30)
            }
            val roll = Roll(
                format = Format.PHOTO.name, stock = r.stock.name, capacity = r.capacity, name = if (ru) r.ru else r.en, loadedAt = loadedAt,
                sentAt = sent, readyAt = sent?.let { it + Rules.STANDARD }, order = if (sent != null) order++ else 0, opened = r.state == 0,
            )
            val id = dao.insert(roll)
            r.photos.forEachIndexed { i, p ->
                val tmp = File(context.cacheDir, "demo-$ri-$i.jpg")
                val ok = runCatching { open("demo/p$p.jpg").use { a -> tmp.outputStream().use { a.copyTo(it) } } }.isSuccess
                if (!ok) return@forEachIndexed
                val name = tmp.inputStream().use { c.vault.take(it, "jpg") }
                tmp.delete()
                // The seaside roll's second frame was shot a year ago today, for "on this day".
                val day = if (r.daysAgo == 365 && i == 1) today.minusYears(1) else start.plusDays((i * span / r.photos.size.coerceAtLeast(1)).toLong())
                val note = r.notes[i]
                dao.insert(
                    Frame(
                        rollId = id, number = i + 1, original = name, takenAt = at(day, 10 + (i * 3) % 9, (i * 17) % 60),
                        album = i in r.album, note = note?.let { if (ru) it.second else it.first } ?: "",
                    )
                )
            }
        }
        // A summer cassette already on the shelf (the lab cuts it on first start), and one in the camera.
        val reel = dao.insert(
            Roll(
                format = Format.MOVIE.name, stock = Stock.HOME8.name, capacity = Rules.CASSETTE, name = if (ru) "Лето у бабушки" else "Summer at Gran's",
                loadedAt = at(today.minusDays(110), 9, 0), sentAt = at(today.minusDays(95), 18, 0), readyAt = at(today.minusDays(94), 18, 0),
                order = order++, opened = true,
            )
        )
        listOf("clip_bruno", "clip_rain", "clip_bruno").forEachIndexed { i, clip ->
            val tmp = File(context.cacheDir, "demo-reel-$i.mp4")
            val ok = runCatching { open("demo/$clip.mp4").use { a -> tmp.outputStream().use { a.copyTo(it) } } }.isSuccess
            if (!ok) return@forEachIndexed
            val name = tmp.inputStream().use { c.vault.take(it, "mp4") }
            tmp.delete()
            val d = c.vault.durationMs(c.vault.file(name))
            dao.insert(Frame(rollId = reel, number = i + 1, original = name, video = true, durationMs = d, takenAt = at(today.minusDays(108L - i * 4), 11, 0)))
        }
        // A cassette in the camera with two clips on it.
        val cassette = dao.insert(Roll(format = Format.MOVIE.name, stock = Stock.HOME8.name, capacity = Rules.CASSETTE, name = if (ru) "Наша осень" else "Our autumn", loadedAt = at(today.minusDays(9), 10, 0)))
        listOf("clip_bruno", "clip_rain").forEachIndexed { i, clip ->
            val tmp = File(context.cacheDir, "demo-clip-$i.mp4")
            val ok = runCatching { open("demo/$clip.mp4").use { a -> tmp.outputStream().use { a.copyTo(it) } } }.isSuccess
            if (!ok) return@forEachIndexed
            val name = tmp.inputStream().use { c.vault.take(it, "mp4") }
            tmp.delete()
            val d = c.vault.durationMs(c.vault.file(name))
            dao.insert(Frame(rollId = cassette, number = i + 1, original = name, video = true, durationMs = d, takenAt = at(today.minusDays(5L - i * 3), 17, 10)))
        }
        c.settings.update { it.copy(onboarded = true, keeper = if (ru) "Аня" else "Anna") }
    }
}
