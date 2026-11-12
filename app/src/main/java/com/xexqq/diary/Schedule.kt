package com.xexqq.diary

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit as EditIcon
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.LocalDate

data class Hero(val kicker: String, val title: String, val sub: String, val cur: Int, val pos: Float?)

fun heroState(date: LocalDate, clock: Clock, len: Int, info: DayInfo, ls: List<Lesson>, cx: List<Boolean>): Hero {
    val dateText = dateLabel(date)
    if (info.kind != Kind.SCHOOL) return Hero(dateText, info.title, info.note, -1, null)
    val n = ls.size
    val a = slot(0, len).first
    val z = slot(n - 1, len).second
    if (date != clock.date) {
        val c = cx.count { it }
        return Hero(dateText, "$n уроков", "с ${fmt(a)} до ${fmt(z)}" + (if (c > 0) ", отменено: $c" else ""), -1, null)
    }
    val m = clock.minutes
    val cur = (0 until n).firstOrNull { m >= slot(it, len).first && m < slot(it, len).second } ?: -1
    val nx = (0 until n).firstOrNull { m < slot(it, len).first } ?: -1
    val pos = if (m in a..z) (m - a).toFloat() / (z - a) else null
    return when {
        cur >= 0 -> Hero(
            if (cx[cur]) "Урок отменён" else "Идёт урок", ls[cur].name,
            "Ещё ${slot(cur, len).second - m} мин" +
                (if (cur < n - 1) ". Дальше: ${ls[cur + 1].name}" else ". Это последний урок"),
            cur, pos
        )
        nx >= 0 && m >= a -> Hero(
            "Перемена", ls[nx].name + (if (cx[nx]) " (отменён)" else ""),
            "Через ${slot(nx, len).first - m} мин" + (if (ls[nx].room.isNotEmpty()) ", ${room(ls[nx].room)}" else ""),
            -1, pos
        )
        m < a -> Hero(
            "Уроки ещё не начались", ls[0].name,
            "Первый урок в ${fmt(a)}" + (if (ls[0].room.isNotEmpty()) ", ${room(ls[0].room)}" else ""),
            -1, pos
        )
        else -> Hero("Уроки закончились", "Можно отдыхать", "Загляни во вкладку «Задания»", -1, null)
    }
}

@Composable
fun HeroCard(h: Hero, len: Int, n: Int, modifier: Modifier = Modifier) {
    val t = LocalT.current
    val g = LocalG.current
    val shape = RoundedCornerShape(28.dp)
    val fill = remember(t.accent, t.blob2, g) {
        Brush.linearGradient(
            listOf(t.accent.copy(alpha = 1f - .08f * g), lerp(t.accent, t.blob2.copy(alpha = .78f), g))
        )
    }
    val edge = remember(g) {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = .85f * g),
                Color.White.copy(alpha = .08f * g),
                Color.White.copy(alpha = .45f * g)
            )
        )
    }
    val pos by animateFloatAsState(h.pos ?: 0f, tween(700, easing = FastOutSlowInEasing), label = "pos")
    Column(modifier.fillMaxWidth().clip(shape).background(fill).border(1.dp, edge, shape).padding(22.dp)) {
        Tx(h.kicker, t.onAccent.copy(alpha = .85f), 14.sp)
        Tx(h.title, t.onAccent, 24.sp, FontWeight.Bold, Modifier.padding(top = 8.dp, bottom = 6.dp))
        if (h.sub.isNotEmpty()) Tx(h.sub, t.onAccent.copy(alpha = .92f))
        if (n > 0) {
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
                if (h.pos != null) {
                    drawRoundRect(
                        t.onAccent,
                        Offset(pos * size.width - 2.dp.toPx(), 0f),
                        Size(4.dp.toPx(), size.height),
                        CornerRadius(2.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
fun HeroSection(date: LocalDate, clock: Clock, len: Int, edits: SnapshotStateMap<String, Edit>) {
    AnimatedContent(
        targetState = date,
        transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
        label = "hero"
    ) { d ->
        val inf = dayInfo(d)
        val base = if (inf.kind == Kind.SCHOOL) SCHEDULE[d.dayOfWeek.value - 1] else emptyList()
        val eff = base.mapIndexed { i, b -> effective(b, edits[ekey(d, i)]) }
        val cx = base.indices.map { edits[ekey(d, it)]?.cancel == true }
        HeroCard(heroState(d, clock, len, inf, eff, cx), len, base.size)
    }
}

@Composable
fun WeekBar(monday: LocalDate, weekOffset: Int, onWeek: (Int) -> Unit) {
    val t = LocalT.current
    val prevOk = !monday.minusWeeks(1).plusDays(6).isBefore(YEAR_START)
    val nextOk = !monday.plusWeeks(1).isAfter(YEAR_END)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ArrowBtn("‹", prevOk) { onWeek(weekOffset - 1) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Tx(rng(monday, monday.plusDays(4)), t.ink, 17.sp, FontWeight.SemiBold)
                if (weekOffset == 0) Tx("эта неделя", t.muted, 12.sp)
            }
        }
        ArrowBtn("›", nextOk) { onWeek(weekOffset + 1) }
    }
}

@Composable
fun DayTabs(monday: LocalDate, day: Int, today: LocalDate, onDay: (Int) -> Unit) {
    val t = LocalT.current
    val shape = RoundedCornerShape(22.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until 5) {
            val date = monday.plusDays(i.toLong())
            val info = remember(date) { dayInfo(date) }
            val sel = i == day
            val a by animateFloatAsState(if (sel) 1f else 0f, tween(260), label = "day")
            Column(
                Modifier.weight(1f).bounceClick { onDay(i) }.panel(shape)
                    .background(t.accent.copy(alpha = a))
                    .then(if (date == today && !sel) Modifier.border(1.5.dp, t.accent, shape) else Modifier)
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Tx(SHORT[i], lerp(t.muted, t.onAccent, a), 13.sp)
                Tx("${date.dayOfMonth}", lerp(t.ink, t.onAccent, a), 18.sp, FontWeight.Bold)
                val dot = when (info.kind) {
                    Kind.OFF -> OFF_COLOR
                    Kind.VACATION -> VAC_COLOR
                    else -> Color.Transparent
                }
                Box(Modifier.padding(top = 4.dp).size(6.dp).clip(CircleShape).background(dot))
            }
        }
    }
}

@Composable
fun EditBanner(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalT.current
    Row(
        modifier.fillMaxWidth().panel(RoundedCornerShape(18.dp))
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Tx("Режим правок", t.ink, 15.sp, FontWeight.SemiBold)
            Tx("Нажми на урок: замена, отмена, задание, заметка", t.muted, 13.sp)
        }
        Tx(
            "Готово", t.onAccent, 14.sp, FontWeight.SemiBold,
            Modifier.bounceClick(onClick = onDone).clip(RoundedCornerShape(50)).background(t.accent)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun NoLessons(info: DayInfo, modifier: Modifier = Modifier) {
    val t = LocalT.current
    Column(modifier.fillMaxWidth().panel(RoundedCornerShape(18.dp)).padding(20.dp)) {
        Tx("Уроков нет", t.muted, 14.sp)
        Tx(info.title, t.ink, 20.sp, FontWeight.Bold, Modifier.padding(top = 4.dp))
        if (info.note.isNotEmpty()) Tx(info.note, t.muted, 14.sp, m = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun Tag(text: String, c: Color) = Tx(
    text, c, 12.sp, FontWeight.Medium,
    Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = .14f)).padding(horizontal = 8.dp, vertical = 2.dp)
)

@Composable
fun LessonCard(
    i: Int, base: Lesson, eff: Lesson, e: Edit?, start: Int, end: Int, isNow: Boolean,
    editMode: Boolean, expanded: Boolean, onClick: () -> Unit, onCheck: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalT.current
    val shape = RoundedCornerShape(18.dp)
    val cancelled = e?.cancel == true
    val replaced = eff.name != base.name
    val hw = e?.hw ?: ""
    val note = e?.note ?: ""
    val done = e?.done == true
    Column(
        modifier.fillMaxWidth()
            .alpha(if (cancelled) .6f else 1f)
            .bounceClick(onClick = onClick)
            .panel(shape)
            .then(if (isNow) Modifier.border(2.dp, t.accent, shape) else Modifier)
            .then(if (editMode) Modifier.border(1.5.dp, t.accent.copy(alpha = .55f), shape) else Modifier)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (cancelled) t.muted else hueColor(eff.name)),
                contentAlignment = Alignment.Center
            ) { Tx("${i + 1}", Color.White, 15.sp, FontWeight.SemiBold) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Tx(
                    eff.name, t.ink, 16.sp, FontWeight.SemiBold,
                    td = if (cancelled) TextDecoration.LineThrough else null
                )
                if (replaced) Tx("вместо ${base.name}", t.muted, 12.sp)
                if (eff.teacher.isNotEmpty()) Tx(eff.teacher, t.muted, 13.sp)
                if (cancelled || replaced || hw.isNotEmpty() || note.isNotEmpty()) {
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (cancelled) Tag("Отменён", OFF_COLOR)
                        if (replaced) Tag("Замена", t.accent)
                        if (hw.isNotEmpty()) Tag(if (done) "ДЗ сделано" else "Есть ДЗ", if (done) VAC_COLOR else t.accent)
                        if (note.isNotEmpty()) Tag("Заметка", t.muted)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Tx("${fmt(start)}–${fmt(end)}", t.ink, 15.sp, FontWeight.SemiBold)
                Tx(room(eff.room), t.muted, 13.sp)
            }
            if (editMode) {
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Default.EditIcon, "Изменить", tint = t.accent, modifier = Modifier.size(20.dp))
            }
        }
        AnimatedVisibility(
            visible = expanded && !editMode,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (hw.isNotEmpty()) {
                    Tx("Задание", t.muted, 13.sp)
                    Tx(hw, t.ink)
                    Row(Modifier.bounceClick { onCheck(!done) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = done, onCheckedChange = null, colors = checkColors())
                        Tx("Сделано", t.muted)
                    }
                }
                if (note.isNotEmpty()) {
                    Tx("Заметка", t.muted, 13.sp)
                    Tx(note, t.ink)
                }
                if (hw.isEmpty() && note.isEmpty()) {
                    Tx("Заданий и заметок нет. Чтобы добавить, нажми на карандаш вверху.", t.muted, 14.sp)
                }
            }
        }
    }
}

fun LazyListScope.lessonItems(
    date: LocalDate, info: DayInfo, len: Int, clock: Clock, edits: SnapshotStateMap<String, Edit>,
    editMode: Boolean, opened: Int?, onOpen: (Int?) -> Unit, onEdit: (Int) -> Unit,
    onCheck: (String, Edit) -> Unit
) {
    if (info.kind != Kind.SCHOOL) {
        item(key = "none") { NoLessons(info, Modifier.appear(0, date)) }
        return
    }
    val base = SCHEDULE[date.dayOfWeek.value - 1]
    itemsIndexed(base, key = { i, _ -> "l$i" }) { i, b ->
        val key = ekey(date, i)
        val e = edits[key]
        val eff = effective(b, e)
        val (s, en) = slot(i, len)
        val isNow = date == clock.date && clock.minutes >= s && clock.minutes < en && e?.cancel != true
        LessonCard(
            i, b, eff, e, s, en, isNow, editMode, opened == i,
            onClick = { if (editMode) onEdit(i) else onOpen(if (opened == i) null else i) },
            onCheck = { if (e != null) onCheck(key, e.copy(done = it)) },
            modifier = Modifier.appear(i, date)
        )
    }
}

@Composable
fun DlgBtn(text: String, c: Color, bg: Color, onClick: () -> Unit) = Tx(
    text, c, 15.sp, FontWeight.SemiBold,
    Modifier.bounceClick(onClick = onClick).clip(RoundedCornerShape(50)).background(bg)
        .padding(horizontal = 16.dp, vertical = 10.dp)
)

@Composable
fun EditDialog(
    date: LocalDate, i: Int, base: Lesson, start: Int, end: Int, cur: Edit?,
    onSave: (Edit?) -> Unit, onDismiss: () -> Unit
) {
    val t = LocalT.current
    var cancel by remember { mutableStateOf(cur?.cancel == true) }
    var subject by remember { mutableStateOf(cur?.subject ?: "") }
    var teacher by remember { mutableStateOf(cur?.teacher ?: "") }
    var roomT by remember { mutableStateOf(cur?.room ?: "") }
    var hw by remember { mutableStateOf(cur?.hw ?: "") }
    var note by remember { mutableStateOf(cur?.note ?: "") }
    val shape = RoundedCornerShape(28.dp)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 620.dp).clip(shape).background(t.bg).panel(shape)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Tx("Урок ${i + 1}: ${base.name}", t.ink, 20.sp, FontWeight.Bold)
            Tx("${dateLabel(date)}, ${fmt(start)}–${fmt(end)}", t.muted, 14.sp)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Tx("Отменить урок", t.ink, 16.sp, FontWeight.SemiBold)
                    Tx("Урок не состоится", t.muted, 13.sp)
                }
                Switch(checked = cancel, onCheckedChange = { cancel = it }, colors = switchColors())
            }

            Tx("Замена", t.muted, 14.sp, FontWeight.SemiBold, Modifier.padding(top = 4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TEACHERS.keys.forEach { name ->
                    val sel = subject == name
                    Tx(
                        name, if (sel) t.onAccent else t.ink, 13.sp, FontWeight.Medium,
                        Modifier.bounceClick {
                            subject = if (sel) "" else name
                            teacher = if (sel) "" else (TEACHERS[name] ?: "")
                        }.clip(RoundedCornerShape(50))
                            .background(if (sel) t.accent else t.accent.copy(alpha = .12f))
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
            OutlinedTextField(
                subject, { subject = it }, Modifier.fillMaxWidth(),
                label = { Tx("Предмет вместо «${base.name}»", t.muted, 13.sp) },
                singleLine = true, colors = fieldColors()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    teacher, { teacher = it }, Modifier.weight(1f),
                    label = { Tx("Учитель", t.muted, 13.sp) }, singleLine = true, colors = fieldColors()
                )
                OutlinedTextField(
                    roomT, { roomT = it }, Modifier.weight(1f),
                    label = { Tx("Кабинет", t.muted, 13.sp) }, singleLine = true, colors = fieldColors()
                )
            }

            Tx("Задание", t.muted, 14.sp, FontWeight.SemiBold, Modifier.padding(top = 4.dp))
            OutlinedTextField(hw, { hw = it }, Modifier.fillMaxWidth(), minLines = 2, colors = fieldColors())
            Tx("Заметка", t.muted, 14.sp, FontWeight.SemiBold, Modifier.padding(top = 4.dp))
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), colors = fieldColors())

            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DlgBtn("Сбросить", t.muted, Color.Transparent) { onSave(null) }
                Spacer(Modifier.weight(1f))
                DlgBtn("Отмена", t.ink, t.accent.copy(alpha = .12f)) { onDismiss() }
                DlgBtn("Готово", t.onAccent, t.accent) {
                    val hwT = hw.trim()
                    val sT = subject.trim().let { if (it == base.name) "" else it }
                    val tT = teacher.trim().let { if (sT.isEmpty() && it == base.teacher) "" else it }
                    val rT = roomT.trim().let { if (it == base.room) "" else it }
                    val ed = Edit(
                        cancel, sT, tT, rT, hwT,
                        cur != null && cur.done && cur.hw == hwT && hwT.isNotEmpty(), note.trim()
                    )
                    onSave(if (ed.empty) null else ed)
                }
            }
        }
    }
}

@Composable
fun ScheduleScreen(
    clock: Clock, len: Int, weekOffset: Int, onWeek: (Int) -> Unit, day: Int, onDay: (Int) -> Unit,
    edits: SnapshotStateMap<String, Edit>, editMode: Boolean, onExitEdit: () -> Unit,
    commit: (String, Edit?) -> Unit
) {
    val wide = LocalWide.current
    val monday = remember(clock.date, weekOffset) {
        clock.date.minusDays((clock.date.dayOfWeek.value - 1).toLong()).plusWeeks(weekOffset.toLong())
    }
    val date = monday.plusDays(day.toLong())
    val info = remember(date) { dayInfo(date) }
    var opened by remember(date) { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf<Int?>(null) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (wide) {
            Row(
                Modifier.widthIn(max = 1100.dp).fillMaxSize().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(
                    Modifier.width(380.dp).fillMaxHeight().verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WeekBar(monday, weekOffset, onWeek)
                    DayTabs(monday, day, clock.date, onDay)
                    HeroSection(date, clock, len, edits)
                    if (editMode) EditBanner(onExitEdit)
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    lessonItems(
                        date, info, len, clock, edits, editMode, opened,
                        { opened = it }, { editing = it }
                    ) { k, e -> commit(k, e) }
                }
            }
        } else {
            LazyColumn(
                Modifier.widthIn(max = 640.dp).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "week") { WeekBar(monday, weekOffset, onWeek) }
                item(key = "tabs") { DayTabs(monday, day, clock.date, onDay) }
                item(key = "hero") { HeroSection(date, clock, len, edits) }
                if (editMode) item(key = "banner") { EditBanner(onExitEdit, Modifier.animateItem()) }
                lessonItems(
                    date, info, len, clock, edits, editMode, opened,
                    { opened = it }, { editing = it }
                ) { k, e -> commit(k, e) }
            }
        }
    }

    val ei = editing
    if (ei != null && info.kind == Kind.SCHOOL) {
        val key = ekey(date, ei)
        val (s, en) = slot(ei, len)
        EditDialog(
            date, ei, SCHEDULE[date.dayOfWeek.value - 1][ei], s, en, edits[key],
            onSave = { commit(key, it); editing = null },
            onDismiss = { editing = null }
        )
    }
}
