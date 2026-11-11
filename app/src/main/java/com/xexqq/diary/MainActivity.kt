package com.xexqq.diary

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(this)
        setContent { App(store) }
    }
}

// ---------- стиль ----------

val GlassEdge = Brush.linearGradient(
    listOf(Color.White.copy(alpha = .85f), Color.White.copy(alpha = .08f), Color.White.copy(alpha = .45f))
)

fun Modifier.backdrop(t: AppTheme, glass: Boolean): Modifier = this
    .background(Brush.verticalGradient(listOf(t.bg, t.bg2)))
    .drawBehind {
        if (glass) {
            fun blob(c: Color, cx: Float, cy: Float, r: Float) {
                val o = Offset(size.width * cx, size.height * cy)
                drawCircle(
                    Brush.radialGradient(listOf(c.copy(alpha = .75f), Color.Transparent), center = o, radius = r),
                    radius = r,
                    center = o
                )
            }
            blob(t.blob1, .1f, .15f, size.width * .9f)
            blob(t.blob2, .95f, .5f, size.width * .8f)
            blob(t.accent, .15f, .95f, size.width * .7f)
        } else if (t.grid) {
            val step = 22.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(t.line, Offset(x, 0f), Offset(x, size.height))
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(t.line, Offset(0f, y), Offset(size.width, y))
                y += step
            }
        }
    }

fun Modifier.panel(t: AppTheme, glass: Boolean, shape: Shape): Modifier =
    if (glass) {
        this.clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (t.dark) .16f else .42f),
                        Color.White.copy(alpha = if (t.dark) .05f else .16f)
                    )
                )
            )
            .border(1.dp, GlassEdge, shape)
    } else {
        this.clip(shape).background(t.card).border(1.dp, t.line, shape)
    }

@Composable
fun Tx(s: String, c: Color, size: TextUnit = 16.sp, w: FontWeight? = null, m: Modifier = Modifier) =
    Text(s, m, color = c, fontSize = size, fontWeight = w)

fun hueColor(s: String) = Color.hsv((((s.hashCode() % 360) + 360) % 360).toFloat(), .55f, .62f)

@Composable
fun fieldColors(t: AppTheme) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = t.ink,
    unfocusedTextColor = t.ink,
    focusedBorderColor = t.accent,
    unfocusedBorderColor = t.line,
    cursorColor = t.accent
)

@Composable
fun checkColors(t: AppTheme) = CheckboxDefaults.colors(
    checkedColor = t.accent,
    checkmarkColor = t.onAccent,
    uncheckedColor = t.muted
)

// ---------- корень ----------

@Composable
fun App(store: Store) {
    var themeId by remember { mutableStateOf(store.theme) }
    var len by remember { mutableIntStateOf(store.len) }
    var glass by remember { mutableStateOf(store.glass) }
    var tab by remember { mutableIntStateOf(0) }
    val hw = remember { mutableStateMapOf<String, Hw>().apply { putAll(store.loadHw()) } }
    val now by produceState(Calendar.getInstance()) {
        while (true) {
            delay(15_000)
            value = Calendar.getInstance()
        }
    }
    val t = THEMES.firstOrNull { it.id == themeId } ?: THEMES[0]

    val view = LocalView.current
    SideEffect {
        val w = (view.context as Activity).window
        WindowCompat.getInsetsController(w, view).apply {
            isAppearanceLightStatusBars = !t.dark
            isAppearanceLightNavigationBars = !t.dark
        }
    }

    val save = { store.saveHw(hw.toMap()) }

    Column(Modifier.fillMaxSize().backdrop(t, glass).imePadding()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> ScheduleScreen(t, glass, len, now, hw, save)
                1 -> CalendarScreen(t, glass, len, now)
                2 -> TasksScreen(t, glass, hw, save)
                else -> SettingsScreen(
                    t, glass, len,
                    onTheme = { themeId = it; store.theme = it },
                    onLen = { len = it; store.len = it },
                    onGlass = { glass = it; store.glass = it }
                )
            }
        }
        BottomBar(t, glass, tab) { tab = it }
    }
}

@Composable
fun BottomBar(t: AppTheme, glass: Boolean, tab: Int, onTab: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().panel(t, glass, RectangleShape).navigationBarsPadding().padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val items = listOf(
            "Уроки" to Icons.Default.Home,
            "Календарь" to Icons.Default.DateRange,
            "Задания" to Icons.Default.CheckCircle,
            "Настройки" to Icons.Default.Settings
        )
        items.forEachIndexed { i, (label, icon) ->
            val sel = tab == i
            val c = if (sel) t.onAccent else t.muted
            Column(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) t.accent else Color.Transparent)
                    .clickable { onTab(i) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, contentDescription = label, tint = c)
                Tx(label, c, 12.sp)
            }
        }
    }
}

// ---------- расписание ----------

class Hero(val kicker: String, val title: String, val sub: String, val cur: Int, val pos: Float?)

fun heroState(day: Int, today: Int, len: Int, m: Int, off: String): Hero {
    val ls = SCHEDULE[day]
    val a = slot(0, len).first
    val z = slot(ls.size - 1, len).second
    if (day != today) {
        val k = if (today < 0) off else "Выбранный день"
        return Hero(k, DAYS[day], "${ls.size} уроков, с ${fmt(a)} до ${fmt(z)}", -1, null)
    }
    val cur = ls.indices.firstOrNull { m >= slot(it, len).first && m < slot(it, len).second } ?: -1
    val nx = ls.indices.firstOrNull { m < slot(it, len).first } ?: -1
    val pos = if (m in a..z) (m - a).toFloat() / (z - a) else null
    return when {
        cur >= 0 -> Hero(
            "Идёт урок", ls[cur].name,
            "Ещё ${slot(cur, len).second - m} мин" +
                if (cur < ls.size - 1) ". Дальше: ${ls[cur + 1].name}" else ". Это последний урок",
            cur, pos
        )
        nx >= 0 && m >= a -> Hero(
            "Перемена", ls[nx].name,
            "Через ${slot(nx, len).first - m} мин" + if (ls[nx].room.isNotEmpty()) ", ${room(ls[nx].room)}" else "",
            -1, pos
        )
        m < a -> Hero(
            "Уроки ещё не начались", ls[0].name,
            "Первый урок в ${fmt(a)}" + if (ls[0].room.isNotEmpty()) ", ${room(ls[0].room)}" else "",
            -1, pos
        )
        else -> Hero("Уроки закончились", "Можно отдыхать", "Загляни во вкладку «Задания»", -1, null)
    }
}

@Composable
fun ScheduleScreen(
    t: AppTheme, glass: Boolean, len: Int, now: Calendar,
    hw: SnapshotStateMap<String, Hw>, save: () -> Unit
) {
    val ld = LocalDate.of(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
    val info = dayInfo(ld)
    val today = if (info.kind == Kind.SCHOOL) ld.dayOfWeek.value - 1 else -1
    val off = when (info.kind) {
        Kind.VACATION -> info.title
        Kind.OFF -> "Выходной: ${info.title}"
        Kind.WEEKEND -> "Сегодня выходной"
        else -> "Вне учебного года"
    }
    var day by remember { mutableIntStateOf(if (today < 0) 0 else today) }
    var opened by remember { mutableStateOf<String?>(null) }
    val m = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val date = SimpleDateFormat("EEEE, d MMMM", Locale.forLanguageTag("ru"))
        .format(now.time).replaceFirstChar { it.uppercase() }
    val h = heroState(day, today, len, m, off)

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(Modifier.padding(bottom = 4.dp)) {
                Tx(date, t.muted, 14.sp)
                Tx("Дневник", t.ink, 28.sp, FontWeight.Bold)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SHORT.forEachIndexed { i, s ->
                    val sel = i == day
                    Column(
                        Modifier.weight(1f)
                            .then(
                                if (sel) Modifier.clip(RoundedCornerShape(50)).background(t.accent)
                                else Modifier.panel(t, glass, RoundedCornerShape(50))
                            )
                            .clickable { day = i; opened = null }
                            .padding(top = 10.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Tx(s, if (sel) t.onAccent else t.ink, 15.sp, FontWeight.SemiBold)
                        val dot = if (i == today) (if (sel) t.onAccent else t.accent) else Color.Transparent
                        Box(Modifier.padding(top = 3.dp).size(5.dp).clip(CircleShape).background(dot))
                    }
                }
            }
        }
        item { HeroCard(t, glass, len, h, SCHEDULE[day].size) }
        itemsIndexed(SCHEDULE[day]) { i, lsn ->
            val key = "$day-$i"
            LessonCard(
                t, glass, i, lsn, slot(i, len), h.cur == i && day == today, hw, key,
                expanded = opened == key,
                onToggle = { opened = if (opened == key) null else key },
                save = save
            )
        }
    }
}

@Composable
fun HeroCard(t: AppTheme, glass: Boolean, len: Int, h: Hero, n: Int) {
    val shape = RoundedCornerShape(28.dp)
    val fill: Brush = if (glass) {
        Brush.linearGradient(listOf(t.accent.copy(alpha = .92f), t.blob2.copy(alpha = .78f)))
    } else SolidColor(t.accent)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(fill)
            .then(if (glass) Modifier.border(1.dp, GlassEdge, shape) else Modifier)
            .padding(22.dp)
    ) {
        Tx(h.kicker, t.onAccent.copy(alpha = .85f), 14.sp)
        Tx(h.title, t.onAccent, 24.sp, FontWeight.Bold, Modifier.padding(top = 8.dp, bottom = 6.dp))
        Tx(h.sub, t.onAccent.copy(alpha = .92f))
        Canvas(Modifier.padding(top = 22.dp).fillMaxWidth().height(20.dp)) {
            val a = slot(0, len).first.toFloat()
            val z = slot(n - 1, len).second.toFloat()
            val tot = z - a
            for (i in 0 until n) {
                val (s, e) = slot(i, len)
                drawRoundRect(
                    color = t.onAccent.copy(alpha = if (i == h.cur) 1f else .3f),
                    topLeft = Offset((s - a) / tot * size.width, 5.dp.toPx()),
                    size = Size((e - s) / tot * size.width, 10.dp.toPx()),
                    cornerRadius = CornerRadius(5.dp.toPx())
                )
            }
            h.pos?.let { p ->
                drawRoundRect(
                    t.onAccent,
                    Offset(p * size.width - 2.dp.toPx(), 0f),
                    Size(4.dp.toPx(), size.height),
                    CornerRadius(2.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun LessonCard(
    t: AppTheme, glass: Boolean, i: Int, lsn: Lesson, sl: Pair<Int, Int>, isNow: Boolean,
    hw: SnapshotStateMap<String, Hw>, key: String, expanded: Boolean,
    onToggle: () -> Unit, save: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val v = hw[key]
    Column(
        Modifier.fillMaxWidth().panel(t, glass, shape)
            .then(if (isNow) Modifier.border(2.dp, t.accent, shape) else Modifier)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { onToggle() }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(hueColor(lsn.name)),
                contentAlignment = Alignment.Center
            ) { Tx("${i + 1}", Color.White, 15.sp, FontWeight.SemiBold) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Tx(lsn.name, t.ink, 16.sp, FontWeight.SemiBold)
                if (lsn.teacher.isNotEmpty()) Tx(lsn.teacher, t.muted, 13.sp)
                if (v != null && v.text.isNotBlank()) {
                    Tx(if (v.done) "ДЗ сделано" else "Есть ДЗ", t.accent, 13.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Tx("${fmt(sl.first)}–${fmt(sl.second)}", t.ink, 15.sp, FontWeight.SemiBold)
                Tx(room(lsn.room), t.muted, 13.sp)
            }
        }
        if (expanded) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                OutlinedTextField(
                    value = v?.text ?: "",
                    onValueChange = { s ->
                        val d = v?.done == true
                        if (s.isEmpty() && !d) {
                            hw.remove(key)
                        } else {
                            hw[key] = Hw(s, d)
                        }
                        save()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Tx("Что задали?", t.muted) },
                    colors = fieldColors(t)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = v?.done == true,
                        onCheckedChange = { c ->
                            hw[key] = Hw(v?.text ?: "", c)
                            save()
                        },
                        colors = checkColors(t)
                    )
                    Tx("Сделано", t.muted)
                }
            }
        }
    }
}

// ---------- задания ----------

class Task(val key: String, val d: Int, val i: Int, val hw: Hw)

@Composable
fun TasksScreen(t: AppTheme, glass: Boolean, hw: SnapshotStateMap<String, Hw>, save: () -> Unit) {
    val tasks = hw.entries
        .filter { it.value.text.isNotBlank() }
        .map { e ->
            val p = e.key.split("-")
            Task(e.key, p[0].toInt(), p[1].toInt(), e.value)
        }
        .sortedWith(compareBy<Task>({ it.d }, { it.i }))
    val todo = tasks.filter { !it.hw.done }
    val done = tasks.filter { it.hw.done }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Tx("Задания", t.ink, 28.sp, FontWeight.Bold) }
        if (tasks.isEmpty()) {
            item {
                Tx(
                    "Заданий пока нет. Открой урок в расписании и запиши, что задали.",
                    t.muted, m = Modifier.padding(top = 12.dp)
                )
            }
        }
        if (todo.isEmpty() && done.isNotEmpty()) {
            item { Tx("Всё сделано. Отдыхай.", t.muted) }
        }
        if (todo.isNotEmpty()) {
            item { Tx("Нужно сделать", t.muted, 14.sp, FontWeight.SemiBold) }
            items(todo, key = { it.key }) { TaskRow(t, glass, it, hw, save) }
        }
        if (done.isNotEmpty()) {
            item { Tx("Сделано", t.muted, 14.sp, FontWeight.SemiBold) }
            items(done, key = { it.key }) { TaskRow(t, glass, it, hw, save) }
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Tx(
                        "Убрать сделанное", t.ink, 15.sp,
                        m = Modifier.panel(t, glass, RoundedCornerShape(50))
                            .clickable {
                                done.forEach { hw.remove(it.key) }
                                save()
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TaskRow(t: AppTheme, glass: Boolean, k: Task, hw: SnapshotStateMap<String, Hw>, save: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().panel(t, glass, RoundedCornerShape(18.dp)).padding(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = k.hw.done,
            onCheckedChange = { c ->
                hw[k.key] = Hw(k.hw.text, c)
                save()
            },
            colors = checkColors(t)
        )
        Column(Modifier.weight(1f).padding(top = 12.dp, end = 8.dp)) {
            Tx(k.hw.text, if (k.hw.done) t.muted else t.ink)
            Tx("${SCHEDULE[k.d][k.i].name}, ${SHORT[k.d]}, урок ${k.i + 1}", t.muted, 13.sp)
        }
    }
}

// ---------- настройки ----------

@Composable
fun SettingsScreen(
    t: AppTheme, glass: Boolean, len: Int,
    onTheme: (String) -> Unit, onLen: (Int) -> Unit, onGlass: (Boolean) -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Tx("Настройки", t.ink, 28.sp, FontWeight.Bold)

        Tx("Тема", t.muted, 14.sp, FontWeight.SemiBold, Modifier.padding(top = 8.dp))
        THEMES.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { th ->
                    val sel = th.id == t.id
                    Row(
                        Modifier.weight(1f).panel(t, glass, shape)
                            .then(if (sel) Modifier.border(2.dp, t.accent, shape) else Modifier)
                            .clickable { onTheme(th.id) }
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
        Column(Modifier.fillMaxWidth().panel(t, glass, shape).padding(16.dp)) {
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
            Modifier.fillMaxWidth().panel(t, glass, shape).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Tx("Жидкое стекло", t.ink, 16.sp, FontWeight.SemiBold)
                Tx("Стеклянные панели, блики и цветной фон", t.muted, 13.sp)
            }
            Switch(
                checked = glass,
                onCheckedChange = onGlass,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = t.onAccent,
                    checkedTrackColor = t.accent,
                    uncheckedThumbColor = t.muted,
                    uncheckedTrackColor = t.line,
                    uncheckedBorderColor = t.muted
                )
            )
        }
    }
}


// ---------- календарь ----------

private val MON = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
private val RU = Locale.forLanguageTag("ru")
private val OFF_COLOR = Color(0xFFE5484D)
private val VAC_COLOR = Color(0xFF2FB872)

private fun dm(d: LocalDate) = "${d.dayOfMonth} ${MON[d.monthValue - 1]}"
private fun rng(a: LocalDate, b: LocalDate) = if (a == b) dm(a) else "${dm(a)} – ${dm(b)}"

@Composable
fun CalendarScreen(t: AppTheme, glass: Boolean, len: Int, now: Calendar) {
    val today = LocalDate.of(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
    val firstMonth = YEAR_START.withDayOfMonth(1)
    val lastMonth = YEAR_END.withDayOfMonth(1)
    val startMonth = today.withDayOfMonth(1).let {
        if (it.isBefore(firstMonth)) firstMonth else if (it.isAfter(lastMonth)) lastMonth else it
    }
    var month by remember { mutableStateOf(startMonth) }
    var sel by remember { mutableStateOf(today) }
    val shape = RoundedCornerShape(18.dp)

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Tx("Календарь", t.ink, 28.sp, FontWeight.Bold)

        val prevOk = month.isAfter(firstMonth)
        val nextOk = month.isBefore(lastMonth)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Tx(
                "‹", if (prevOk) t.ink else t.muted.copy(alpha = .35f), 30.sp, FontWeight.Bold,
                Modifier.clip(CircleShape).clickable(enabled = prevOk) { month = month.minusMonths(1) }
                    .padding(horizontal = 18.dp, vertical = 2.dp)
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val name = month.month.getDisplayName(TextStyle.FULL_STANDALONE, RU)
                    .replaceFirstChar { it.uppercase() }
                Tx("$name ${month.year}", t.ink, 18.sp, FontWeight.SemiBold)
            }
            Tx(
                "›", if (nextOk) t.ink else t.muted.copy(alpha = .35f), 30.sp, FontWeight.Bold,
                Modifier.clip(CircleShape).clickable(enabled = nextOk) { month = month.plusMonths(1) }
                    .padding(horizontal = 18.dp, vertical = 2.dp)
            )
        }

        Row(Modifier.fillMaxWidth()) {
            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { w ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Tx(w, t.muted, 13.sp, FontWeight.Medium)
                }
            }
        }

        val lead = month.dayOfWeek.value - 1
        val dim = month.lengthOfMonth()
        val rows = (lead + dim + 6) / 7
        Column(Modifier.fillMaxWidth().panel(t, glass, shape).padding(6.dp)) {
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val n = r * 7 + c - lead + 1
                        if (n < 1 || n > dim) {
                            Spacer(Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val date = month.withDayOfMonth(n)
                            val info = dayInfo(date)
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
                                Modifier.weight(1f).aspectRatio(1f).padding(2.dp)
                                    .clip(cell).background(bg).border(2.dp, ring, cell)
                                    .clickable { sel = date },
                                contentAlignment = Alignment.Center
                            ) {
                                val dim2 = info.kind == Kind.WEEKEND || info.kind == Kind.OUT
                                Tx(
                                    "$n", if (dim2) t.muted else t.ink, 15.sp,
                                    if (date == today) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

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

        val info = dayInfo(sel)
        val dateText = sel.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", RU))
            .replaceFirstChar { it.uppercase() }
        Column(Modifier.fillMaxWidth().panel(t, glass, shape).padding(16.dp)) {
            Tx(dateText, t.muted, 14.sp)
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
        val upcoming = (VACATIONS + OFF_DAYS).filter { !it.to.isBefore(today) }.sortedBy { it.from }.take(6)
        upcoming.forEach { e ->
            val lbl = if (e.has(today)) "идёт сейчас" else "через ${ChronoUnit.DAYS.between(today, e.from)} дн."
            Column(Modifier.fillMaxWidth().panel(t, glass, shape).padding(14.dp)) {
                Tx(e.title + if (e.prelim) " (предв.)" else "", t.ink, 16.sp, FontWeight.SemiBold)
                Tx("${rng(e.from, e.to)}, $lbl", t.muted, 14.sp)
            }
        }

        Tx(
            "Каникулы даны по рекомендациям Минпросвещения РФ, которым следует Крым, школа может сдвинуть даты. " +
                "Региональные выходные Крыма на 2027 год пока предварительные.",
            t.muted, 12.sp, m = Modifier.padding(top = 4.dp)
        )
    }
}
