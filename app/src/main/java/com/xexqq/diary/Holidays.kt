package com.xexqq.diary

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class Span(
    val from: LocalDate,
    val to: LocalDate,
    val title: String,
    val note: String = "",
    val prelim: Boolean = false
) {
    fun has(d: LocalDate) = !d.isBefore(from) && !d.isAfter(to)
}

private fun ymd(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d)
private fun span(a: LocalDate, b: LocalDate, title: String, note: String = "", prelim: Boolean = false) =
    Span(a, b, title, note, prelim)

val YEAR_START: LocalDate = ymd(2026, 9, 1)
val YEAR_END: LocalDate = ymd(2027, 8, 31)

/**
 * Школьные каникулы 2026/27 по рекомендациям Минпросвещения РФ (письмо от 07.07.2026 № ОК-1948/03),
 * которых придерживается Республика Крым. Школа может сдвинуть даты.
 */
val VACATIONS = listOf(
    span(
        ymd(2026, 10, 26), ymd(2026, 11, 4), "Осенние каникулы",
        "К каникулам присоединяется 4 ноября (День народного единства). 5 ноября началась 2-я четверть"
    ),
    span(
        ymd(2026, 12, 31), ymd(2027, 1, 10), "Зимние каникулы",
        "Включая новогодние праздники и Рождество. 11 января начнётся 3-я четверть"
    ),
    span(ymd(2027, 3, 27), ymd(2027, 4, 4), "Весенние каникулы", "5 апреля начнётся 4-я четверть"),
    span(
        ymd(2027, 5, 27), ymd(2027, 8, 31), "Летние каникулы",
        "Последний звонок 26 мая. Новый учебный год начнётся 1 сентября 2027"
    )
)

/**
 * Общероссийские нерабочие дни (постановление Правительства РФ от 17.09.2026 № 1187)
 * и региональные выходные Республики Крым. Региональные на 2027 год помечены как предварительные:
 * указ Главы Крыма на 2027 год на момент сборки ещё не найден.
 */
val OFF_DAYS = listOf(
    span(ymd(2026, 11, 4), ymd(2026, 11, 4), "День народного единства"),
    span(
        ymd(2027, 2, 21), ymd(2027, 2, 23), "День защитника Отечества",
        "Суббота 20 февраля по производственному календарю рабочая"
    ),
    span(ymd(2027, 3, 6), ymd(2027, 3, 8), "Международный женский день"),
    span(ymd(2027, 3, 9), ymd(2027, 3, 9), "Ураза-байрам", "Региональный выходной в Крыму", prelim = true),
    span(
        ymd(2027, 3, 18), ymd(2027, 3, 18), "День воссоединения Крыма с Россией",
        "Региональный выходной в Крыму", prelim = true
    ),
    span(
        ymd(2027, 5, 1), ymd(2027, 5, 3), "Праздник Весны и Труда",
        "2 мая ещё и Пасха (региональный выходной в Крыму)"
    ),
    span(ymd(2027, 5, 8), ymd(2027, 5, 10), "День Победы"),
    span(
        ymd(2027, 5, 16), ymd(2027, 5, 16), "Курбан-байрам",
        "Региональный выходной в Крыму, воскресенье", prelim = true
    )
)

val QUARTERS = listOf(
    span(ymd(2026, 9, 1), ymd(2026, 10, 25), "1-я четверть"),
    span(ymd(2026, 11, 5), ymd(2026, 12, 30), "2-я четверть"),
    span(ymd(2027, 1, 11), ymd(2027, 3, 26), "3-я четверть"),
    span(ymd(2027, 4, 5), ymd(2027, 5, 26), "4-я четверть")
)

enum class Kind { SCHOOL, WEEKEND, OFF, VACATION, OUT }

class DayInfo(val kind: Kind, val title: String, val note: String)

fun dayInfo(day: LocalDate): DayInfo {
    if (day.isBefore(YEAR_START) || day.isAfter(YEAR_END)) return DayInfo(Kind.OUT, "Вне учебного года", "")
    val off = OFF_DAYS.firstOrNull { it.has(day) }
    if (off != null) {
        val note = if (off.prelim) off.note + ". Предварительно: указ Главы Крыма на 2027 год ещё не опубликован" else off.note
        return DayInfo(Kind.OFF, off.title, note)
    }
    val vac = VACATIONS.firstOrNull { it.has(day) }
    if (vac != null) return DayInfo(Kind.VACATION, vac.title, vac.note)
    val w = day.dayOfWeek
    if (w == DayOfWeek.SATURDAY) return DayInfo(Kind.WEEKEND, "Суббота", "Выходной")
    if (w == DayOfWeek.SUNDAY) return DayInfo(Kind.WEEKEND, "Воскресенье", "Выходной")
    val q = QUARTERS.firstOrNull { it.has(day) }?.title ?: ""
    return DayInfo(Kind.SCHOOL, "Учебный день", q)
}

val RU: Locale = Locale.forLanguageTag("ru")
val FULL_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", RU)

fun dateLabel(d: LocalDate) = d.format(FULL_DAY).replaceFirstChar { it.uppercase() }

private val MON = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

fun dm(d: LocalDate) = "${d.dayOfMonth} ${MON[d.monthValue - 1]}"

fun rng(a: LocalDate, b: LocalDate) = if (a == b) dm(a) else "${dm(a)} – ${dm(b)}"
