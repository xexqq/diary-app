package com.xexqq.diary

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONObject

data class Lesson(val name: String, val room: String, val teacher: String = "")
data class Hw(val text: String, val done: Boolean)

val DAYS = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница")
val SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт")

val TEACHERS = mapOf(
    "География" to "Анна Николаевна",
    "История" to "Дмитрий Владимирович",
    "Алгебра" to "Екатерина Дмитриевна",
    "Русский язык" to "Наталья Борисовна",
    "Физика" to "Татьяна Леонидовна",
    "Труд (технология)" to "Татьяна Алексеевна",
    "Физическая культура" to "Денис Вячеславович",
    "Биология" to "Анна Игоревна",
    "Ин.яз. (англ.)" to "Анастасия Юрьевна",
    "Математика" to "Екатерина Дмитриевна",
    "Химия" to "Наталья Николаевна",
    "Информатика" to "Ирина Тодосевна",
    "Геометрия" to "Екатерина Дмитриевна",
    "Вероятность и статистика" to "Екатерина Дмитриевна",
    "Литература" to "Наталья Борисовна",
    "ОБЗР" to "Дмитрий Владимирович",
    "Музыка" to "Мария Анатольевна"
)

private fun l(name: String, room: String = "") = Lesson(name, room, TEACHERS[name] ?: "")

val SCHEDULE: List<List<Lesson>> = listOf(
    listOf(
        l("Разговоры о важном", "207"), l("География", "204"), l("История", "107"),
        l("Алгебра", "207"), l("Русский язык", "111"), l("Физика", "207"), l("Труд (технология)", "102")
    ),
    listOf(
        l("Физическая культура", "207"), l("Алгебра", "207"), l("История", "107"),
        l("Русский язык", "103"), l("Биология", "103"), l("Ин.яз. (англ.)", "207/118"), l("Математика")
    ),
    listOf(
        l("Химия", "111"), l("Ин.яз. (англ.)", "205/117"), l("Информатика", "205/117"),
        l("Физика", "207"), l("Геометрия", "216"), l("Вероятность и статистика", "104"), l("Русский язык")
    ),
    listOf(
        l("Химия", "111"), l("Алгебра", "207"), l("Биология", "207"),
        l("География", "207"), l("Литература", "207"), l("История", "207"), l("Разговоры о важном 2")
    ),
    listOf(
        l("Геометрия", "204"), l("ОБЗР", "ОБЗР"), l("Ин.яз. (англ.)"),
        l("Биология", "103"), l("Музыка", "104"), l("Литература", "103"), l("Физическая культура")
    )
)

/** Первый урок начинается в 08:30, перемены после каждого урока фиксированные. */
const val START = 8 * 60 + 30
val BREAKS = listOf(10, 10, 20, 10, 20, 10)

/** Начало и конец урока i (в минутах от полуночи) при длительности урока len. */
fun slot(i: Int, len: Int): Pair<Int, Int> {
    val s = START + i * len + BREAKS.take(i).sum()
    return s to s + len
}

fun fmt(m: Int) = "%02d:%02d".format(m / 60, m % 60)

fun room(r: String) = when {
    r.isEmpty() -> ""
    r[0].isDigit() -> "каб. $r"
    else -> r
}

class AppTheme(
    val id: String,
    val name: String,
    val bg: Color,
    val bg2: Color,
    val card: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val line: Color,
    val dark: Boolean,
    val blob1: Color,
    val blob2: Color,
    val grid: Boolean = false
)

val THEMES = listOf(
    AppTheme(
        "kletka", "Клетка", Color(0xFFF4F8FF), Color(0xFFF4F8FF), Color.White, Color(0xFF17254A),
        Color(0xFF667599), Color(0xFF2F5BEA), Color.White, Color(0xFFDBE4F7), false,
        Color(0xFF6EA8FF), Color(0xFFB38CFF), grid = true
    ),
    AppTheme(
        "noch", "Ночь", Color(0xFF15132E), Color(0xFF15132E), Color(0xFF221F47), Color(0xFFEEEAFF),
        Color(0xFFA29CD0), Color(0xFFFFB454), Color(0xFF2A1B00), Color(0xFF35306A), true,
        Color(0xFF7B5CFF), Color(0xFFFFB454)
    ),
    AppTheme(
        "zakat", "Закат", Color(0xFFFFD8C4), Color(0xFFCDB8FF), Color(0xA3FFFFFF), Color(0xFF3B1C4F),
        Color(0xFF7D5B8F), Color(0xFFD63F7B), Color.White, Color(0x243B1C4F), false,
        Color(0xFFFF8FB1), Color(0xFF9C7BFF)
    ),
    AppTheme(
        "les", "Лес", Color(0xFFE4EEE1), Color(0xFFE4EEE1), Color(0xFFF8FBF5), Color(0xFF1C3427),
        Color(0xFF587263), Color(0xFF2D6A4A), Color.White, Color(0xFFCFDFCB), false,
        Color(0xFF6FCF97), Color(0xFFF2D675)
    ),
    AppTheme(
        "grafit", "Графит", Color(0xFFE9E9EB), Color(0xFFE9E9EB), Color.White, Color(0xFF1A1A1C),
        Color(0xFF6F6F76), Color(0xFF1A1A1C), Color.White, Color(0xFFD9D9DD), false,
        Color(0xFF9AA0FF), Color(0xFFFF9ACB)
    )
)

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("diary", Context.MODE_PRIVATE)

    var theme: String
        get() = p.getString("theme", "kletka") ?: "kletka"
        set(v) { p.edit().putString("theme", v).apply() }

    var len: Int
        get() = p.getInt("len", 45)
        set(v) { p.edit().putInt("len", v).apply() }

    var glass: Boolean
        get() = p.getBoolean("glass", false)
        set(v) { p.edit().putBoolean("glass", v).apply() }

    fun loadHw(): Map<String, Hw> {
        val o = JSONObject(p.getString("hw", "{}") ?: "{}")
        val out = HashMap<String, Hw>()
        for (k in o.keys()) {
            val j = o.getJSONObject(k)
            out[k] = Hw(j.getString("t"), j.getBoolean("d"))
        }
        return out
    }

    fun saveHw(m: Map<String, Hw>) {
        val o = JSONObject()
        for ((k, v) in m) o.put(k, JSONObject().put("t", v.text).put("d", v.done))
        p.edit().putString("hw", o.toString()).apply()
    }
}
