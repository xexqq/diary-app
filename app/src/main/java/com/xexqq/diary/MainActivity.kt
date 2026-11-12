package com.xexqq.diary

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit as EditIcon
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val resumes = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(this)
        setContent { App(store, resumes.value) }
    }

    override fun onResume() {
        super.onResume()
        resumes.value = resumes.value + 1
    }
}

@Composable
fun App(store: Store, resumeKey: Int) {
    val start = remember { LocalDate.now() }
    val weekend = start.dayOfWeek.value >= 6
    var themeId by rememberSaveable { mutableStateOf(store.theme) }
    var len by rememberSaveable { mutableStateOf(store.len) }
    var glassOn by rememberSaveable { mutableStateOf(store.glass) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var editMode by rememberSaveable { mutableStateOf(false) }
    var weekOffset by rememberSaveable { mutableStateOf(if (weekend) 1 else 0) }
    var day by rememberSaveable { mutableStateOf(if (weekend) 0 else start.dayOfWeek.value - 1) }
    val edits = remember { mutableStateMapOf<String, Edit>().apply { putAll(store.loadEdits()) } }

    // Часы обновляются раз в минуту и при возврате в приложение
    val clock by produceState(nowClock(), resumeKey) {
        while (true) {
            value = nowClock()
            delay(60_000L - System.currentTimeMillis() % 60_000L + 100L)
        }
    }

    val t = animateTheme(THEMES.firstOrNull { it.id == themeId } ?: THEMES[0])
    val g by animateFloatAsState(if (glassOn) 1f else 0f, tween(500), label = "glass")
    val wide = LocalConfiguration.current.screenWidthDp >= 840

    val view = LocalView.current
    SideEffect {
        val w = (view.context as Activity).window
        WindowCompat.getInsetsController(w, view).apply {
            isAppearanceLightStatusBars = !t.dark
            isAppearanceLightNavigationBars = !t.dark
        }
    }

    val commit: (String, Edit?) -> Unit = { k, e ->
        if (e == null || e.empty) edits.remove(k) else edits[k] = e
        store.saveEdits(edits.toMap())
    }

    val screen = if (settings) 3 else tab

    CompositionLocalProvider(LocalT provides t, LocalG provides g, LocalWide provides wide) {
        Box(Modifier.fillMaxSize().backdrop()) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                TopBar(
                    screen, clock.date, editMode,
                    onEdit = {
                        if (editMode && tab == 0 && !settings) {
                            editMode = false
                        } else {
                            settings = false
                            tab = 0
                            editMode = true
                        }
                    },
                    onSettings = { settings = !settings }
                )
                Box(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = screen,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (fadeIn(tween(260, delayMillis = 60)) + slideInHorizontally(tween(320)) { it / 10 * dir }) togetherWith
                                (fadeOut(tween(160)) + slideOutHorizontally(tween(320)) { -it / 10 * dir })
                        },
                        label = "screen"
                    ) { s ->
                        when (s) {
                            0 -> ScheduleScreen(
                                clock, len, weekOffset, { weekOffset = it }, day, { day = it },
                                edits, editMode, { editMode = false }, commit
                            )
                            1 -> CalendarScreen(len, clock)
                            2 -> TasksScreen(
                                clock, edits,
                                onToggle = { k -> edits[k]?.let { commit(k, it.copy(done = !it.done)) } },
                                onClear = {
                                    edits.entries.filter { it.value.done }.map { it.key }.forEach { k ->
                                        edits[k]?.let { commit(k, it.copy(hw = "", done = false)) }
                                    }
                                }
                            )
                            else -> SettingsScreen(
                                len, glassOn,
                                onTheme = { themeId = it; store.theme = it },
                                onLen = { len = it; store.len = it },
                                onGlass = { glassOn = it; store.glass = it }
                            )
                        }
                    }
                }
                BottomBar(if (settings) -1 else tab) { tab = it; settings = false }
            }
        }
    }
}

@Composable
fun IconBtn(icon: ImageVector, active: Boolean, rot: Float, desc: String, onClick: () -> Unit) {
    val t = LocalT.current
    val a by animateFloatAsState(if (active) 1f else 0f, tween(300), label = "ib")
    val r by animateFloatAsState(rot, spring(dampingRatio = .6f), label = "rot")
    Box(
        Modifier.size(46.dp).bounceClick(onClick = onClick).panel(CircleShape).background(t.accent.copy(alpha = a)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = lerp(t.ink, t.onAccent, a), modifier = Modifier.rotate(r))
    }
}

@Composable
fun TopBar(screen: Int, today: LocalDate, editMode: Boolean, onEdit: () -> Unit, onSettings: () -> Unit) {
    val t = LocalT.current
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Crossfade(screen, Modifier.weight(1f), label = "title") { s ->
            Column {
                if (s == 0) Tx(dateLabel(today), t.muted, 14.sp)
                Tx(
                    when (s) {
                        0 -> "Дневник"
                        1 -> "Календарь"
                        2 -> "Задания"
                        else -> "Настройки"
                    },
                    t.ink, 28.sp, FontWeight.Bold
                )
            }
        }
        IconBtn(Icons.Default.EditIcon, editMode && screen == 0, if (editMode && screen == 0) -12f else 0f, "Режим правок", onEdit)
        Spacer(Modifier.width(8.dp))
        IconBtn(Icons.Default.Settings, screen == 3, if (screen == 3) 90f else 0f, "Настройки", onSettings)
    }
}

@Composable
fun BottomBar(tab: Int, onTab: (Int) -> Unit) {
    val t = LocalT.current
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(Modifier.widthIn(max = 520.dp).fillMaxWidth().panel(RoundedCornerShape(30.dp)).padding(6.dp)) {
            val items = listOf(
                "Уроки" to Icons.Default.Home,
                "Календарь" to Icons.Default.DateRange,
                "Задания" to Icons.Default.CheckCircle
            )
            items.forEachIndexed { i, (label, icon) ->
                val a by animateFloatAsState(if (tab == i) 1f else 0f, tween(280), label = "tab")
                val c = lerp(t.muted, t.onAccent, a)
                Column(
                    Modifier.weight(1f).bounceClick { onTab(i) }.clip(RoundedCornerShape(50))
                        .background(t.accent.copy(alpha = a)).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(icon, label, tint = c)
                    Tx(label, c, 12.sp)
                }
            }
        }
    }
}
