package com.xexqq.diary

import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun clampOff(o: Offset, s: Float, sz: IntSize): Offset {
    val mx = sz.width * (s - 1f) / 2f
    val my = sz.height * (s - 1f) / 2f
    return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
}

@Composable
fun PdfPageView(doc: PdfDoc, index: Int, widthPx: Int, hits: List<RectF>, currentHit: List<RectF>) {
    val r by produceState<Rendered?>(null, doc, index, widthPx) { value = doc.render(index, widthPx) }
    val a by animateFloatAsState(if (r != null) 1f else 0f, tween(250), label = "pg")
    val img = remember(r) { r?.bmp?.asImageBitmap() }
    Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = a }) {
        val rr = r
        if (rr != null && img != null) {
            val bw = rr.bmp.width.toFloat()
            val bh = rr.bmp.height.toFloat()
            val s = minOf(size.width / bw, size.height / bh)
            val dw = bw * s
            val dh = bh * s
            val left = (size.width - dw) / 2f
            val top = (size.height - dh) / 2f
            drawImage(
                img,
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                filterQuality = FilterQuality.Medium
            )
            fun box(rf: RectF) = Pair(
                Offset(left + rf.left / rr.wPts * dw, top + rf.top / rr.hPts * dh),
                Size(rf.width() / rr.wPts * dw, rf.height() / rr.hPts * dh)
            )
            for (rf in hits) {
                val (o, sz) = box(rf)
                drawRect(Color(0x66FFD400), topLeft = o, size = sz)
            }
            for (rf in currentHit) {
                val (o, sz) = box(rf)
                drawRect(Color(0x99FF8A00), topLeft = o, size = sz)
            }
        }
    }
}

@Composable
fun CtrlBtn(text: String, onClick: () -> Unit) {
    val t = LocalT.current
    Tx(
        text, t.ink, 22.sp, FontWeight.Bold,
        Modifier.bounceClick(onClick = onClick).clip(CircleShape).padding(horizontal = 14.dp, vertical = 4.dp)
    )
}

@Composable
fun ReaderScreen(book: Book, startPage: Int, onPage: (Int) -> Unit, onClose: () -> Unit) {
    val t = LocalT.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var failed by remember(book.uri) { mutableStateOf(false) }
    val doc by produceState<PdfDoc?>(null, book.uri) {
        val d = PdfDoc.open(ctx, book.uri)
        value = d
        if (d == null) failed = true
        awaitDispose { d?.close() }
    }
    val count = doc?.pages ?: 0
    val pager = rememberPagerState { doc?.pages ?: 0 }

    var scale by remember { mutableStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }

    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<Match>>(emptyList()) }
    var cur by remember { mutableStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var searched by remember { mutableStateOf(false) }
    var gotoOpen by remember { mutableStateOf(false) }
    val job = remember { arrayOfNulls<Job>(1) }
    val tok = remember { IntArray(1) }

    val cfg = LocalConfiguration.current
    val dens = LocalDensity.current.density
    val widthPx = (cfg.screenWidthDp * dens * 2f).toInt().coerceIn(900, 2000)

    LaunchedEffect(doc) {
        if (count > 0) pager.scrollToPage(startPage.coerceIn(0, count - 1))
    }
    LaunchedEffect(pager.currentPage, count) {
        scale = 1f
        off = Offset.Zero
        if (count > 0) onPage(pager.currentPage)
    }

    val zoomBy = { f: Float ->
        val ns = (scale * f).coerceIn(1f, 5f)
        scale = ns
        off = if (ns <= 1f) Offset.Zero else clampOff(off, ns, box)
    }

    fun runSearch() {
        val d = doc
        val q = query.trim()
        if (d == null || q.isEmpty() || !SEARCH_OK) return
        job[0]?.cancel()
        tok[0] = tok[0] + 1
        val my = tok[0]
        searching = true
        searched = false
        matches = emptyList()
        progress = 0
        job[0] = scope.launch {
            val res = d.search(q) { if (tok[0] == my) progress = it }
            if (tok[0] == my) {
                matches = res
                cur = 0
                searching = false
                searched = true
                if (res.isNotEmpty()) pager.scrollToPage(res[0].page)
            }
        }
    }

    fun goMatch(delta: Int) {
        if (matches.isEmpty()) return
        cur = (cur + delta + matches.size) % matches.size
        scope.launch { pager.scrollToPage(matches[cur].page) }
    }

    Column(
        Modifier.fillMaxSize().backdrop()
            .pointerInput(Unit) { detectTapGestures(onTap = { }) }
            .statusBarsPadding().navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArrowBtn("‹", true, onClose)
            Column(Modifier.weight(1f)) {
                Tx(book.name.removeSuffix(".pdf").removeSuffix(".PDF"), t.ink, 16.sp, FontWeight.SemiBold)
                if (count > 0) Tx("$count стр.", t.muted, 12.sp)
            }
            IconBtn(Icons.Default.Search, searchOpen, 0f, "Поиск") { searchOpen = !searchOpen }
        }

        AnimatedVisibility(
            visible = searchOpen,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Tx("Слово или предложение", t.muted, 14.sp) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                    colors = fieldColors()
                )
                val msg = when {
                    !SEARCH_OK -> "Поиск по тексту работает на Android 15 и новее"
                    searching -> "Поиск… $progress из $count"
                    searched && matches.isEmpty() -> "Ничего не найдено. Если учебник — скан без текста, поиск не сработает"
                    matches.isNotEmpty() -> "${cur + 1} из ${matches.size}"
                    else -> ""
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tx(msg, t.muted, 13.sp, m = Modifier.weight(1f))
                    if (matches.isNotEmpty()) {
                        ArrowBtn("‹", true) { goMatch(-1) }
                        ArrowBtn("›", true) { goMatch(1) }
                    }
                    DlgBtn("Найти", t.onAccent, t.accent) { runSearch() }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val d = doc
            if (d != null && count > 0) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = scale <= 1.01f,
                    beyondViewportPageCount = 1,
                    pageSpacing = 8.dp
                ) { p ->
                    val isCur = p == pager.currentPage
                    val hits = remember(matches, p) { matches.filter { it.page == p }.flatMap { it.rects } }
                    val safeCur = if (matches.isEmpty()) 0 else cur.coerceIn(0, matches.size - 1)
                    val curHit = if (matches.isNotEmpty() && matches[safeCur].page == p) matches[safeCur].rects else emptyList()
                    Box(
                        Modifier.fillMaxSize().onSizeChanged { box = it }
                            .then(
                                if (isCur) {
                                    Modifier
                                        .pointerInput(Unit) {
                                            detectTapGestures(onDoubleTap = {
                                                scale = if (scale > 1.01f) 1f else 2.5f
                                                off = Offset.Zero
                                            })
                                        }
                                        .pointerInput(Unit) {
                                            awaitEachGesture {
                                                awaitFirstDown(requireUnconsumed = false)
                                                do {
                                                    val ev = awaitPointerEvent()
                                                    val zoom = ev.calculateZoom()
                                                    val pan = ev.calculatePan()
                                                    if (ev.changes.size > 1 || scale > 1.01f) {
                                                        if (zoom != 1f || pan != Offset.Zero) {
                                                            val ns = (scale * zoom).coerceIn(1f, 5f)
                                                            scale = ns
                                                            off = if (ns <= 1f) Offset.Zero else clampOff(off + pan, ns, box)
                                                            ev.changes.forEach { if (it.positionChanged()) it.consume() }
                                                        }
                                                    }
                                                } while (ev.changes.any { it.pressed })
                                            }
                                        }
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            translationX = off.x
                                            translationY = off.y
                                        }
                                } else {
                                    Modifier
                                }
                            )
                    ) { PdfPageView(d, p, widthPx, hits, curHit) }
                }
            } else {
                Tx(
                    if (failed) "Не удалось открыть PDF" else "Загрузка…", t.muted,
                    m = Modifier.align(Alignment.Center).padding(20.dp)
                )
            }
        }

        if (count > 0) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
                    .panel(RoundedCornerShape(24.dp)).padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tx(
                        "${pager.currentPage + 1} / $count", t.ink, 15.sp, FontWeight.SemiBold,
                        Modifier.bounceClick { gotoOpen = true }.padding(end = 12.dp)
                    )
                    Slider(
                        value = pager.currentPage.toFloat(),
                        onValueChange = { v -> scope.launch { pager.scrollToPage(v.roundToInt()) } },
                        valueRange = 0f..maxOf(1f, (count - 1).toFloat()),
                        enabled = count > 1,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = t.accent,
                            activeTrackColor = t.accent,
                            inactiveTrackColor = t.line
                        )
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CtrlBtn("«") { scope.launch { pager.scrollToPage(maxOf(0, pager.currentPage - 10)) } }
                    CtrlBtn("‹") { scope.launch { pager.scrollToPage(maxOf(0, pager.currentPage - 1)) } }
                    CtrlBtn("›") { scope.launch { pager.scrollToPage(minOf(count - 1, pager.currentPage + 1)) } }
                    CtrlBtn("»") { scope.launch { pager.scrollToPage(minOf(count - 1, pager.currentPage + 10)) } }
                    Spacer(Modifier.weight(1f))
                    CtrlBtn("−") { zoomBy(1f / 1.4f) }
                    Tx(
                        "${(scale * 100).roundToInt()}%", t.muted, 14.sp, FontWeight.Medium,
                        Modifier.bounceClick {
                            scale = 1f
                            off = Offset.Zero
                        }.padding(horizontal = 6.dp)
                    )
                    CtrlBtn("+") { zoomBy(1.4f) }
                }
            }
        }
    }

    if (gotoOpen) {
        var txt by remember { mutableStateOf("") }
        val shape = RoundedCornerShape(28.dp)
        val go = {
            val n = txt.toIntOrNull()
            if (n != null && count > 0) {
                scope.launch { pager.scrollToPage((n - 1).coerceIn(0, count - 1)) }
                gotoOpen = false
            }
        }
        Dialog(onDismissRequest = { gotoOpen = false }) {
            Column(
                Modifier.fillMaxWidth().clip(shape).background(t.bg).panel(shape).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Tx("Перейти на страницу", t.ink, 20.sp, FontWeight.Bold)
                OutlinedTextField(
                    value = txt,
                    onValueChange = { v -> txt = v.filter { it.isDigit() }.take(5) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Tx("от 1 до $count", t.muted, 14.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { go() }),
                    colors = fieldColors()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.weight(1f))
                    DlgBtn("Отмена", t.ink, t.accent.copy(alpha = .12f)) { gotoOpen = false }
                    DlgBtn("Перейти", t.onAccent, t.accent) { go() }
                }
            }
        }
    }
}

/** kind: 0 папка не выбрана, 1 учебник не найден, 2 несколько подходящих файлов. */
class BookAsk(val subject: String, val options: List<Book>, val kind: Int)

@Composable
fun BookDialog(
    ask: BookAsk, onPickFolder: () -> Unit, onPickFile: () -> Unit,
    onOpen: (Book) -> Unit, onDismiss: () -> Unit
) {
    val t = LocalT.current
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(shape).background(t.bg).panel(shape).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (ask.kind) {
                0 -> {
                    Tx("Нужна папка с учебниками", t.ink, 20.sp, FontWeight.Bold)
                    Tx(
                        "Распакуй Учебники.zip на телефон и выбери папку с PDF. Приложение само подберёт учебник к каждому предмету.",
                        t.muted, 14.sp
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DlgBtn("Выбрать папку", t.onAccent, t.accent) { onPickFolder() }
                        DlgBtn("Выбрать файл", t.ink, t.accent.copy(alpha = .12f)) { onPickFile() }
                    }
                }
                1 -> {
                    Tx("Учебник не найден", t.ink, 20.sp, FontWeight.Bold)
                    Tx(
                        "Для предмета «${ask.subject}» в выбранной папке нет подходящего PDF. Можно указать файл вручную.",
                        t.muted, 14.sp
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DlgBtn("Выбрать файл", t.onAccent, t.accent) { onPickFile() }
                        DlgBtn("Закрыть", t.ink, t.accent.copy(alpha = .12f)) { onDismiss() }
                    }
                }
                else -> {
                    Tx("Какой учебник открыть?", t.ink, 20.sp, FontWeight.Bold)
                    Tx(ask.subject, t.muted, 14.sp)
                    ask.options.forEach { b ->
                        Tx(
                            b.name.removeSuffix(".pdf").removeSuffix(".PDF"), t.ink, 15.sp, FontWeight.Medium,
                            Modifier.fillMaxWidth().bounceClick { onOpen(b) }.panel(RoundedCornerShape(14.dp)).padding(14.dp)
                        )
                    }
                }
            }
        }
    }
}
