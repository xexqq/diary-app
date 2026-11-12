package com.xexqq.diary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class Book(val name: String, val uri: Uri, val hay: String)

class Rendered(val bmp: Bitmap, val wPts: Int, val hPts: Int)

class Match(val page: Int, val rects: List<RectF>)

/** По каким словам в имени файла (или папки) узнаём учебник предмета. */
val KEYS: Map<String, List<String>> = mapOf(
    "География" to listOf("географ"),
    "История" to listOf("истор"),
    "Алгебра" to listOf("алгебр"),
    "Русский язык" to listOf("русск"),
    "Физика" to listOf("физик"),
    "Труд (технология)" to listOf("технолог", "труд"),
    "Физическая культура" to listOf("физическ", "физкульт", "физра"),
    "Биология" to listOf("биолог"),
    "Ин.яз. (англ.)" to listOf("англ", "english"),
    "Математика" to listOf("математик", "алгебр", "геометр"),
    "Химия" to listOf("хими"),
    "Информатика" to listOf("информат"),
    "Геометрия" to listOf("геометр"),
    "Вероятность и статистика" to listOf("вероятн", "статист"),
    "Литература" to listOf("литератур"),
    "ОБЗР" to listOf("обзр", "обж", "безопасн"),
    "Музыка" to listOf("музык")
)

fun noBook(subject: String) = subject.startsWith("Разговоры")

fun booksFor(subject: String, all: List<Book>, ov: Map<String, String>): List<Book> {
    val o = ov[subject]
    if (o != null) {
        val u = Uri.parse(o)
        return listOf(Book(Uri.decode(u.lastPathSegment ?: "PDF").substringAfterLast('/'), u, ""))
    }
    val keys = KEYS[subject] ?: listOf(subject.lowercase().take(5))
    return all.filter { b -> keys.any { k -> b.hay.contains(k) } }
}

fun missingSubjects(all: List<Book>, ov: Map<String, String>): List<String> =
    SCHEDULE.flatten().map { it.name }.distinct().filter { !noBook(it) && booksFor(it, all, ov).isEmpty() }

/** Рекурсивно собирает PDF из выбранной папки (до 3 уровней вложенности). */
fun scanPdfs(ctx: Context, tree: Uri): List<Book> {
    val out = ArrayList<Book>()
    fun walk(d: DocumentFile, path: String, depth: Int) {
        for (f in d.listFiles()) {
            val n = f.name ?: continue
            if (f.isDirectory) {
                if (depth < 3) walk(f, "$path $n", depth + 1)
            } else if (n.endsWith(".pdf", ignoreCase = true)) {
                out.add(Book(n, f.uri, "$path $n".lowercase()))
            }
        }
    }
    val root = DocumentFile.fromTreeUri(ctx, tree) ?: return out
    walk(root, "", 0)
    out.sortBy { it.name }
    return out
}

/** Поиск по тексту есть в системном PdfRenderer на Android 15 и новее. */
val SEARCH_OK: Boolean = try {
    PdfRenderer.Page::class.java.getMethod("searchText", String::class.java)
    true
} catch (e: Throwable) {
    false
}

class PdfDoc private constructor(private val pfd: ParcelFileDescriptor, private val r: PdfRenderer) {
    val pages: Int = r.pageCount
    private val lock = Mutex()
    private val cache = LruCache<Int, Rendered>(4)

    suspend fun render(i: Int, width: Int): Rendered? = withContext(Dispatchers.Default) {
        lock.withLock {
            val hit = cache.get(i)
            if (hit != null && hit.bmp.width == width) {
                hit
            } else {
                try {
                    val p = r.openPage(i)
                    try {
                        val h = (width.toLong() * p.height / p.width).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val out = Rendered(bmp, p.width, p.height)
                        cache.put(i, out)
                        out
                    } finally {
                        p.close()
                    }
                } catch (e: Throwable) {
                    null
                }
            }
        }
    }

    /** Ищет слово или фразу по всем страницам. Возвращает совпадения с рамками на странице. */
    suspend fun search(q: String, onProgress: (Int) -> Unit): List<Match> = withContext(Dispatchers.Default) {
        val out = ArrayList<Match>()
        val method = try {
            PdfRenderer.Page::class.java.getMethod("searchText", String::class.java)
        } catch (e: Throwable) {
            null
        }
        if (method != null) {
            for (i in 0 until pages) {
                ensureActive()
                lock.withLock {
                    try {
                        val p = r.openPage(i)
                        try {
                            val res = method.invoke(p, q) as? List<*>
                            if (res != null) {
                                for (m in res) {
                                    if (m == null) continue
                                    val rs = ArrayList<RectF>()
                                    val bounds = m.javaClass.getMethod("getBounds").invoke(m) as? List<*>
                                    if (bounds != null) {
                                        for (rf in bounds) if (rf is RectF) rs.add(rf)
                                    }
                                    if (rs.isNotEmpty()) out.add(Match(i, rs))
                                }
                            }
                        } finally {
                            p.close()
                        }
                    } catch (e: Throwable) {
                        // страница без текста или ошибка поиска: пропускаем
                    }
                }
                onProgress(i + 1)
            }
        }
        out
    }

    fun close() {
        try { r.close() } catch (e: Throwable) { }
        try { pfd.close() } catch (e: Throwable) { }
    }

    companion object {
        suspend fun open(ctx: Context, uri: Uri): PdfDoc? = withContext(Dispatchers.IO) {
            try {
                val pfd = ctx.contentResolver.openFileDescriptor(uri, "r")
                if (pfd == null) {
                    null
                } else {
                    try {
                        PdfDoc(pfd, PdfRenderer(pfd))
                    } catch (e: Throwable) {
                        pfd.close()
                        null
                    }
                }
            } catch (e: Throwable) {
                null
            }
        }
    }
}
