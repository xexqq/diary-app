package com.xexqq.diary

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

// ---------- календарь ----------

@Composable
fun CalendarScreen(len: Int, clock: Clock) {
    val today = clock.date
    val wide = LocalWide.current
    val first = YEAR_START.withDayOfMonth(1)
    val last = YEAR_END.withDayOfMonth(1)
    val start = today.withDayOfMonth(1).let { if (it.isBefore(first)) first else if (it.isAfter(last)) last else it }
    var month by remember { mutableStateOf(start) }
    var sel by remember { mutableStateOf(today) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (wide) {
            Row(
                Modifier.widthIn(max = 1100.dp).fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) { CalendarGrid(month, { month = it }, sel, { sel = it }, today) }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) { CalendarDetails(sel, today, len) }
            }
        } else {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CalendarGrid(month, { month = it }, sel, { sel = it }, today)
                CalendarDetails(sel, today, len)
            }
        }
    }
}

@Composable
fun CalendarGrid(month: LocalDate, onMonth: (LocalDate) -> Unit, sel: LocalDate, onSel: (LocalDate) -> Unit, today: LocalDate) {
    val t = LocalT.current
    val prevOk = month.isAfter(YEAR_START.withDayOfMonth(1))
    val nextOk = month.isBefore(YEAR_END.withDayOfMonth(1))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ArrowBtn("‹", prevOk) { onMonth(month.minusMonths(1)) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            val name = month.month.getDisplayName(TextStyle.FULL_STANDALONE, RU).replaceFirstChar { it.uppercase() }
            Tx("$name ${month.year}", t.ink, 18.sp, FontWeight.SemiBold)
        }
        ArrowBtn("›", nextOk) { onMonth(month.plusMonths(1)) }
    }
    Row(Modifier.fillMaxWidth()) {
        listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { w ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Tx(w, t.muted, 13.sp, FontWeight.Medium) }
        }
    }
    AnimatedContent(
        targetState = month,
        transitionSpec = {
            val dir = if (targetState.isAfter(initialState)) 1 else -1
            (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 6 * dir }) togetherWith
                (fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { -it / 6 * dir })
        },
        label = "month"
    ) { m -> MonthCells(m, sel, onSel, today) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(
            t.accent.copy(alpha = .35f) to "Уроки",
            VAC_COLOR.copy(alpha = .6f) to "Каникулы",
            OFF_COLOR.copy(alpha = .6f) to "Праздник"
        ).forEach { (c, s) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(c))
                Spacer(Modifier.width(6.dp))
                Tx(s, t.muted, 13.sp)
            }
        }
    }
}

@Composable
fun MonthCells(m: LocalDate, sel: LocalDate, onSel: (LocalDate) -> Unit, today: LocalDate) {
    val lead = m.dayOfWeek.value - 1
    val dim = m.lengthOfMonth()
    val rows = (lead + dim + 6) / 7
    Column(Modifier.fillMaxWidth().panel(RoundedCornerShape(18.dp)).padding(6.dp)) {
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val n = r * 7 + c - lead + 1
                    if (n < 1 || n > dim) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        DayCell(m.withDayOfMonth(n), n, sel, today, onSel, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun DayCell(date: LocalDate, n: Int, sel: LocalDate, today: LocalDate, onSel: (LocalDate) -> Unit, modifier: Modifier) {
    val t = LocalT.current
    val info = remember(date) { dayInfo(date) }
    val bg = when (info.kind) {
        Kind.SCHOOL -> t.accent.copy(alpha = .14f)
        Kind.VACATION -> VAC_COLOR.copy(alpha = .30f)
        Kind.OFF -> OFF_COLOR.copy(alpha = .30f)
        else -> Color.Transparent
    }
    val ring = when {
        date == today -> t.accent
        date == sel -> t.ink
        else -> Color.Transparent
    }
    val cell = RoundedCornerShape(12.dp)
    Box(
        modifier.aspectRatio(1f).padding(2.dp).clip(cell).background(bg).border(2.dp, ring, cell)
            .clickable { onSel(date) },
        contentAlignment = Alignment.Center
    ) {
        val faded = info.kind == Kind.WEEKEND || info.kind == Kind.OUT
        Tx(
            "$n", if (faded) t.muted else t.ink, 15.sp,
            if (date == today) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun CalendarDetails(sel: LocalDate, today: LocalDate, len: Int) {
    val t = LocalT.current
    val shape = RoundedCornerShape(18.dp)
    val info = dayInfo(sel)
    Column(Modifier.fillMaxWidth().panel(shape).padding(16.dp)) {
        Tx(dateLabel(sel) + " " + sel.year, t.muted, 14.sp)
        Tx(info.title, t.ink, 20.sp, FontWeight.Bold, Modifier.padding(top = 4.dp))
        if (info.kind == Kind.SCHOOL) {
            val n = SCHEDULE[sel.dayOfWeek.value - 1].size
            Tx(
                "$n уроков, с ${fmt(slot(0, len).first)} до ${fmt(slot(n - 1, len).second)}",
                t.ink, m = Modifier.padding(top = 4.dp)
            )
        }
        if (info.note.isNotEmpty()) Tx(info.note, t.muted, 14.sp, m = Modifier.padding(top = 4.dp))
    }
    Tx("Ближайшие каникулы и выходные", t.muted, 14.sp, FontWeight.SemiBold, Modifier.padding(top = 8.dp))
    (VACATIONS + OFF_DAYS).filter { !it.to.isBefore(today) }.sortedBy { it.from }.take(6).forEach { e ->
        val lbl = if (e.has(today)) "идёт сейчас" else "через ${ChronoUnit.DAYS.between(today, e.from)} дн."
        Column(Modifier.fillMaxWidth().panel(shape).padding(14.dp)) {
            Tx(e.title + (if (e.prelim) " (предв.)" else ""), t.ink, 16.sp, FontWeight.SemiBold)
            Tx("${rng(e.from, e.to)}, $lbl", t.muted, 14.sp)
        }
    }
    Tx(
        "Каникулы даны по рекомендациям Минпросвещения РФ, которым следует Крым, школа может сдвинуть даты. " +
            "Региональные выходные Крыма на 2027 год пока предварительные.",
        t.muted, 12.sp, m = Modifier.padding(top = 4.dp)
    )
}

// ---------- задания ----------

class Task(val key: String, val date: LocalDate, val i: Int, val e: Edit)

@Composable
fun TaskRow(k: Task, today: LocalDate, onToggle: (String) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalT.current
    val lsn = effective(SCHEDULE[k.date.dayOfWeek.value - 1][k.i], k.e)
    val tag = when {
        k.e.done -> ""
        k.date.isBefore(today) -> "просрочено"
        k.date == today -> "сегодня"
        k.date == today.plusDays(1) -> "завтра"
        else -> ""
    }
    Row(
        modifier.fillMaxWidth().bounceClick { onToggle(k.key) }.panel(RoundedCornerShape(18.dp)).padding(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(checked = k.e.done, onCheckedChange = null, colors = checkColors(), modifier = Modifier.padding(8.dp))
        Column(Modifier.weight(1f).padding(top = 10.dp, end = 8.dp, bottom = 4.dp)) {
            Tx(k.e.hw, if (k.e.done) t.muted else t.ink)
            Tx("${lsn.name}, ${SHORT[k.date.dayOfWeek.value - 1]} ${dm(k.date)}, урок ${k.i + 1}", t.muted, 13.sp)
            if (tag.isNotEmpty()) {
                Tag(tag, if (tag == "просрочено") OFF_COLOR else t.accent)
            }
        }
    }
}

@Composable
fun TasksScreen(clock: Clock, edits: SnapshotStateMap<String, Edit>, onToggle: (String) -> Unit, onClear: () -> Unit) {
    val t = LocalT.current
    val tasks = edits.entries.mapNotNull { en ->
        if (en.value.hw.isEmpty()) {
            null
        } else {
            val p = en.key.split(":")
            val d = runCatching { LocalDate.parse(p[0]) }.getOrNull()
            val i = p.getOrNull(1)?.toIntOrNull()
            if (d == null || i == null || d.dayOfWeek.value > 5 || i !in 0..6) null else Task(en.key, d, i, en.value)
        }
    }.sortedWith(compareBy<Task>({ it.date }, { it.i }))
    val todo = tasks.filter { !it.e.done }
    val done = tasks.filter { it.e.done }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 640.dp).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (tasks.isEmpty()) {
                item(key = "empty") {
                    Tx(
                        "Заданий пока нет. Нажми на карандаш вверху, выбери урок и впиши, что задали.",
                        t.muted, m = Modifier.padding(top = 12.dp)
                    )
                }
            }
            if (todo.isEmpty() && done.isNotEmpty()) {
                item(key = "alldone") { Tx("Всё сделано. Отдыхай.", t.muted) }
            }
            if (todo.isNotEmpty()) {
                item(key = "h1") { Tx("Нужно сделать", t.muted, 14.sp, FontWeight.SemiBold) }
                items(todo, key = { it.key }) { k -> TaskRow(k, clock.date, onToggle, Modifier.animateItem()) }
            }
            if (done.isNotEmpty()) {
                item(key = "h2") { Tx("Сделано", t.muted, 14.sp, FontWeight.SemiBold) }
                items(done, key = { it.key }) { k -> TaskRow(k, clock.date, onToggle, Modifier.animateItem()) }
                item(key = "clear") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Tx(
                            "Убрать сделанное", t.ink, 15.sp,
                            m = Modifier.bounceClick(onClick = onClear).panel(RoundedCornerShape(50))
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

// ---------- настройки ----------

@Composable
fun SettingsScreen(
    len: Int, glassOn: Boolean, folderName: String?, count: Int, missing: List<String>,
    onPickFolder: () -> Unit,
    onTheme: (String) -> Unit, onLen: (Int) -> Unit, onGlass: (Boolean) -> Unit
) {
    val t = LocalT.current
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Tx("Тема", t.muted, 14.sp, FontWeight.SemiBold)
            THEMES.chunked(2).forEachIndexed { row, pair ->
                Row(
                    Modifier.fillMaxWidth().appear(row),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { th ->
                        val sel = th.id == t.id
                        Row(
                            Modifier.weight(1f).bounceClick { onTheme(th.id) }.panel(shape)
                                .then(if (sel) Modifier.border(2.dp, t.accent, shape) else Modifier)
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(16.dp).clip(CircleShape).background(th.accent))
                            Spacer(Modifier.width(10.dp))
                            Tx(th.name, t.ink, 16.sp, FontWeight.Medium)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Tx("Длительность урока", t.muted, 14.sp, FontWeight.SemiBold, Modifier.padding(top = 8.dp))
            Column(Modifier.fillMaxWidth().appear(3).panel(shape).padding(16.dp)) {
                Tx("$len мин", t.ink, 24.sp, FontWeight.Bold)
                Slider(
                    value = len.toFloat(),
                    onValueChange = { onLen(it.roundToInt()) },
                    valueRange = 10f..45f,
                    steps = 34,
                    colors = SliderDefaults.colors(
                        thumbColor = t.accent,
                        activeTrackColor = t.accent,
                        inactiveTrackColor = t.line,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    )
                )
                Tx(
                    "Уроки идут с ${fmt(START)} до ${fmt(slot(6, len).second)}. Время уроков и блок «сейчас» пересчитываются сразу.",
                    t.muted, 13.sp
                )
            }

            Tx("Оформление", t.muted, 14.sp, FontWeight.SemiBold, Modifier.padding(top = 8.dp))
            Row(
                Modifier.fillMaxWidth().appear(4).panel(shape).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Tx("Жидкое стекло", t.ink, 16.sp, FontWeight.SemiBold)
                    Tx("Стеклянные панели, блики и цветной фон", t.muted, 13.sp)
                }
                Switch(checked = glassOn, onCheckedChange = onGlass, colors = switchColors())
            }

            Tx("Учебники", t.muted, 14.sp, FontWeight.SemiBold, Modifier.padding(top = 8.dp))
            Column(
                Modifier.fillMaxWidth().appear(5).panel(shape).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Tx("Папка с PDF-учебниками", t.ink, 16.sp, FontWeight.SemiBold)
                        Tx(
                            if (folderName == null) "не выбрана" else "$folderName, найдено PDF: $count",
                            t.muted, 13.sp
                        )
                    }
                    DlgBtn("Выбрать", t.onAccent, t.accent, onPickFolder)
                }
                if (folderName != null) {
                    if (missing.isEmpty()) {
                        Tx("Все учебники на месте", t.muted, 14.sp)
                    } else {
                        Tx("Не хватает учебников:", t.ink, 14.sp, FontWeight.SemiBold)
                        Tx(missing.joinToString(", "), t.muted, 14.sp)
                    }
                }
            }
        }
    }
}
