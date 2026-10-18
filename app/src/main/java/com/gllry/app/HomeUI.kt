package com.gllry.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.time.format.TextStyle as DayStyle

// =====================================================================================
// models + helpers
// =====================================================================================
data class DayGroup(val title: String, val caption: String, val photos: List<Photo>)
data class ViewerCtx(val title: String, val keys: List<String>, val start: Int, val isArchive: Boolean = false)

private val fmtDate: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

fun relTime(ms: Long, prefix: String): String {
    val m = (System.currentTimeMillis() - ms) / 60000
    return when {
        m < 1 -> "$prefix just now"
        m < 60 -> "$prefix $m minute${if (m == 1L) "" else "s"} ago"
        m < 1440 -> "$prefix ${m / 60} hour${if (m / 60 == 1L) "" else "s"} ago"
        else -> "$prefix " + DateTimeFormatter.ofPattern("dd/MM/yyyy").format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
    }
}

fun groupByDay(photos: List<Photo>): List<DayGroup> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val loc = Locale.getDefault()
    return photos.groupBy { Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() }
        .entries.sortedByDescending { it.key }
        .map { (d, list) ->
            val gap = ChronoUnit.DAYS.between(d, today)
            val title = when {
                gap == 0L -> "Today"
                gap == 1L -> "Yesterday"
                gap in 2..6 -> d.dayOfWeek.getDisplayName(DayStyle.FULL, loc)
                d.year == today.year -> "${d.dayOfMonth} ${d.month.getDisplayName(DayStyle.FULL, loc)}"
                else -> "${d.dayOfMonth} ${d.month.getDisplayName(DayStyle.FULL, loc)} ${d.year}"
            }
            DayGroup(title, d.format(fmtDate), list.sortedByDescending { it.time })
        }
}

fun buildAlbums(vm: GalleryVM): List<Album> {
    val live = vm.photosFor(ALL)
    val out = ArrayList<Album>()
    live.groupBy { it.album }.forEach { (k, v) -> out += Album(k, k, v.sortedByDescending { it.time }) }
    val vids = live.filter { it.isVideo }
    if (vids.isNotEmpty()) out += Album(VIDEOS, "Videos", vids.sortedByDescending { it.time })
    val arch = vm.photosFor(ARCHIVE)
    if (arch.isNotEmpty()) out += Album(ARCHIVE, "Archive", arch.sortedByDescending { it.time })
    return out.sortedByDescending { it.photos.first().time }
}

private fun matches(p: Photo, q: String): Boolean {
    val zone = ZoneId.systemDefault(); val loc = Locale.getDefault()
    val d = Instant.ofEpochMilli(p.time).atZone(zone).toLocalDate()
    val hay = listOf(
        p.album, p.name, d.format(fmtDate), d.month.getDisplayName(DayStyle.FULL, loc),
        d.dayOfWeek.getDisplayName(DayStyle.FULL, loc), d.year.toString(), if (p.isVideo) "video" else "photo"
    ).joinToString(" ").lowercase()
    return q.trim().lowercase().split(' ').filter { it.isNotBlank() }.all { hay.contains(it) }
}

/** the vertical ruler on the left edge (tick marks) */
fun Modifier.rail(): Modifier = this.drawBehind {
    val step = 7.dp.toPx()
    var y = 0f
    var i = 0
    while (y < size.height) {
        val major = i % 4 == 0
        drawLine(
            Color.White.copy(alpha = if (major) 0.40f else 0.22f),
            Offset(0f, y), Offset((if (major) 11.dp else 6.dp).toPx(), y), strokeWidth = 1f
        )
        y += step; i++
    }
}

/** Material 3 Expressive round button: morphs from circle to squircle while pressed, springs back. */
@Composable
fun GlassButton(size: Dp = 52.dp, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val haptic = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val radius by animateDpAsState(if (pressed) size * 0.32f else size / 2, Motion.spatialFast<Dp>(), label = "morph")
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, Motion.spatialFast<Float>(), label = "press")
    Box(
        modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }
            .glass(RoundedCornerShape(radius), Color(0xFF1B1B1E), 0.55f, 6.dp)
            .clickable(src, null) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        Alignment.Center, content = content
    )
}

// =====================================================================================
// building blocks
// =====================================================================================
@Composable
fun SectionHeading(title: String, caption: String?) {
    Column(Modifier.fillMaxWidth().rail().padding(top = 26.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(start = 4.dp).width(26.dp).height(1.dp).background(Color.White.copy(alpha = 0.9f)))
            Spacer(Modifier.width(8.dp))
            Text(title, color = Ink, fontFamily = UiSans, fontWeight = FontWeight.Medium, fontSize = 15.sp)
        }
        if (caption != null) {
            Text(
                "$caption ⁝", Modifier.padding(start = 38.dp, top = 2.dp),
                color = Color.White.copy(alpha = 0.35f), fontFamily = AppFonts.pixel, fontSize = 11.sp
            )
        }
    }
}

@Composable
fun PhotoTile(p: Photo, w: Dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.width(w).aspectRatio(0.82f).bounceClick(onClick).clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
    ) {
        AsyncImage(p.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (p.isVideo) DurationTag(p.duration, Modifier.align(Alignment.BottomStart).padding(6.dp))
    }
}

@Composable
private fun PhotoRow(row: List<Photo>, tile: Dp, onClick: (Photo) -> Unit) {
    Row(
        Modifier.fillMaxWidth().rail().padding(start = 38.dp, end = 18.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) { row.forEach { p -> PhotoTile(p, tile) { onClick(p) } } }
}

@Composable
private fun FolderPhotos(photos: List<Photo>, modifier: Modifier) {
    Box(modifier) {
        photos.getOrNull(1)?.let {
            AsyncImage(
                it.uri, null,
                Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 8.dp).size(78.dp, 90.dp)
                    .rotate(-7f).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
        }
        photos.getOrNull(0)?.let {
            AsyncImage(
                it.uri, null,
                Modifier.align(Alignment.TopEnd).padding(end = 10.dp, top = 2.dp).size(78.dp, 90.dp)
                    .rotate(6f).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/** album = two tilted photos peeking out of a frosted-glass pocket */
@Composable
fun AlbumFolder(a: Album, onClick: () -> Unit) {
    val w = 124.dp; val h = 122.dp; val pocketH = 76.dp
    val pocket = RoundedCornerShape(26.dp)
    Box(Modifier.width(w).height(h).popIn().bounceClick(onClick)) {
        FolderPhotos(a.photos, Modifier.fillMaxSize())
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(pocketH).clip(pocket)) {
            // the same photos again, blurred, = frosted glass
            Box(Modifier.align(Alignment.BottomCenter).requiredSize(w, h).blur(18.dp)) {
                FolderPhotos(a.photos, Modifier.fillMaxSize())
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x990B0B0D), Color(0xE6000000)))))
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(a.title, color = Color.White, fontFamily = UiSans, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, maxLines = 1)
                Text(
                    relTime(a.photos.first().time, "Updated"), color = Color.White.copy(alpha = 0.55f),
                    fontFamily = AppFonts.script, fontSize = 10.sp, maxLines = 1
                )
            }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(pocketH).border(
                1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.05f))), pocket
            )
        )
    }
}

@Composable
private fun AlbumsRow(albums: List<Album>, onAlbum: (Album) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().rail().padding(bottom = 6.dp),
        contentPadding = PaddingValues(start = 38.dp, end = 18.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) { items(albums, key = { it.key }) { a -> AlbumFolder(a) { onAlbum(a) } } }
}

@Composable
private fun SelfiesRow(list: List<Photo>, onClick: (Int) -> Unit) {
    val fmt = remember { DateTimeFormatter.ofPattern("d MMM") }
    LazyRow(
        Modifier.fillMaxWidth().rail(),
        contentPadding = PaddingValues(start = 38.dp, end = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        itemsIndexed(list, key = { _, p -> p.key }) { i, p ->
            Column(Modifier.popIn().bounceClick { onClick(i) }, horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    p.uri, null,
                    Modifier.size(66.dp, 94.dp).clip(RoundedCornerShape(topStart = 33.dp, topEnd = 33.dp, bottomStart = 14.dp, bottomEnd = 14.dp)),
                    contentScale = ContentScale.Crop, alignment = Alignment.TopCenter
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    fmt.format(Instant.ofEpochMilli(p.time).atZone(ZoneId.systemDefault())),
                    color = Color.White.copy(alpha = 0.5f), fontFamily = AppFonts.script, fontSize = 13.sp
                )
            }
        }
    }
}

// =====================================================================================
// the gallery screen: header + feeds + floating bar
// =====================================================================================
@Composable
fun GalleryScreen(
    vm: GalleryVM, tab: Int, onTab: (Int) -> Unit,
    searchOpen: Boolean, onSearchOpen: (Boolean) -> Unit, query: String, onQuery: (String) -> Unit,
    onOpen: (ViewerCtx) -> Unit, onSecret: () -> Unit, onImport: () -> Unit, onRecord: () -> Unit
) {
    val photos = remember(vm.all, vm.archived) { vm.photosFor(ALL).sortedByDescending { it.time } }
    val albums = remember(vm.all, vm.archived) { buildAlbums(vm) }
    val groups = remember(photos) { groupByDay(photos) }
    val sw = LocalConfiguration.current.screenWidthDp.dp
    val tile = (sw - 38.dp - 18.dp - 24.dp) / 4
    var menuOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        val searching = query.isNotBlank()
        when {
            searching -> {
                val res = remember(photos, query) { photos.filter { matches(it, query) } }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 128.dp, bottom = 160.dp)) {
                    item(key = "rh") { SectionHeading("Results", "${res.size} found") }
                    val rows = res.chunked(4)
                    items(rows.size, key = { "r$it" }) { r ->
                        PhotoRow(rows[r], tile) { p -> onOpen(ViewerCtx("Results", res.map { it.key }, res.indexOf(p))) }
                    }
                    if (res.isEmpty()) item(key = "none") {
                        Text("Nothing found", Modifier.rail().padding(start = 38.dp, top = 8.dp), color = Color.White.copy(0.5f), fontFamily = AppFonts.script, fontSize = 16.sp)
                    }
                }
            }
            tab == 1 -> CalendarFeed(photos, sw, onOpen)
            else -> {
                val selfieList = remember(photos) { photos.filter { it.album.contains("selfie", true) } }
                val portraits = remember(photos) { photos.filter { !it.isVideo && it.h > it.w && it.w > 0 }.take(12) }
                val people = if (selfieList.isNotEmpty()) selfieList else portraits
                val peopleTitle = if (selfieList.isNotEmpty()) "Selfies" else "Portraits"
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 128.dp, bottom = 160.dp)) {
                    item(key = "albums") { AlbumsRow(albums) { a -> onOpen(ViewerCtx(a.title, a.photos.map { it.key }, 0, a.key == ARCHIVE)) } }
                    if (people.isNotEmpty()) {
                        item(key = "people_h") { SectionHeading(peopleTitle, null) }
                        item(key = "people") { SelfiesRow(people.take(14)) { i -> onOpen(ViewerCtx(peopleTitle, people.map { it.key }, i)) } }
                    }
                    groups.forEachIndexed { gi, g ->
                        item(key = "h$gi") { SectionHeading(g.title, g.caption) }
                        val rows = g.photos.chunked(4)
                        items(rows.size, key = { "g${gi}_$it" }) { r ->
                            PhotoRow(rows[r], tile) { p -> onOpen(ViewerCtx(g.title, g.photos.map { it.key }, g.photos.indexOf(p))) }
                        }
                    }
                }
            }
        }

        // fixed header (title + date + add)
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.88f), Color.Transparent)))
                .rail().padding(top = 44.dp, start = 34.dp, end = 18.dp, bottom = 26.dp)
        ) {
            Column(Modifier.align(Alignment.CenterStart)) {
                HoldTitle(onSecret, AppFonts.ui, 21.sp, Alignment.Start)
                Text(
                    LocalDate.now().format(fmtDate) + " ⁝", color = Color.White.copy(alpha = 0.85f),
                    fontFamily = AppFonts.pixel, fontSize = 12.sp
                )
            }
            GlassButton(52.dp, { menuOpen = !menuOpen }, Modifier.align(Alignment.CenterEnd)) {
                if (menuOpen) GlyphClose() else GlyphPlus()
            }
        }
        AnimatedVisibility(
            menuOpen, Modifier.align(Alignment.TopEnd).padding(top = 104.dp, end = 18.dp),
            enter = fadeIn(Motion.effects<Float>()) + scaleIn(Motion.spatialFast<Float>(), initialScale = 0.7f),
            exit = fadeOut(Motion.effects<Float>()) + scaleOut(targetScale = 0.7f)
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassPill("Import photos & videos") { menuOpen = false; onImport() }
                GlassPill("Record video") { menuOpen = false; onRecord() }
            }
        }

        // frosted fade + floating bar
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))))
        )
        BottomBar(
            tab, { menuOpen = false; onTab(it) }, searchOpen, onSearchOpen, query, onQuery,
            Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun BottomBar(
    tab: Int, onTab: (Int) -> Unit, searchOpen: Boolean, onSearchOpen: (Boolean) -> Unit,
    query: String, onQuery: (String) -> Unit, modifier: Modifier
) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(searchOpen) {
        if (searchOpen) { delay(180); runCatching { focus.requestFocus() } } else focusManager.clearFocus()
    }
    BoxWithConstraints(modifier.fillMaxWidth().imePadding().padding(start = 18.dp, end = 18.dp, bottom = 28.dp)) {
        val navW = 150.dp
        val full = maxWidth - navW - 10.dp
        val searchW by animateDpAsState(if (searchOpen) full else 56.dp, Motion.spatial<Dp>(), label = "searchW")
        val indicatorX by animateDpAsState(if (tab == 0) 6.dp else 76.dp, Motion.spatialFast<Dp>(), label = "indicator")
        val barShape = RoundedCornerShape(30.dp)

        // navigation pill
        Box(Modifier.align(Alignment.CenterStart).width(navW).height(56.dp).glass(barShape, Color(0xFF17171A), 0.62f, 10.dp)) {
            Box(
                Modifier.offset(x = indicatorX, y = 5.dp).size(68.dp, 46.dp)
                    .clip(RoundedCornerShape(23.dp)).background(Color.Black.copy(alpha = 0.78f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(23.dp))
            )
            Row(Modifier.fillMaxSize()) {
                NavCell(0, tab) { onTab(0) }
                NavCell(1, tab) { onTab(1) }
            }
        }
        // search button -> expands into a field
        Box(
            Modifier.align(Alignment.CenterEnd).width(searchW).height(56.dp)
                .glass(barShape, Color(0xFF17171A), 0.62f, 10.dp)
                .clickable(remember { MutableInteractionSource() }, null) { if (!searchOpen) onSearchOpen(true) }
        ) {
            Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                GlyphSearch(24.dp, Color.White)
                if (searchW > 110.dp) {
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("Search Here", color = Color.White.copy(alpha = 0.55f), fontFamily = UiSans, fontSize = 14.sp, maxLines = 1)
                        }
                        BasicTextField(
                            value = query, onValueChange = onQuery, singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = UiSans),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavCell(index: Int, tab: Int, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.width(75.dp).fillMaxHeight()
            .clickable(remember { MutableInteractionSource() }, null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick()
            },
        Alignment.Center
    ) {
        val c = if (tab == index) Color.White else Color.White.copy(alpha = 0.55f)
        if (index == 0) GlyphHome(24.dp, c) else GlyphCalendar(24.dp, c)
    }
}

// =====================================================================================
// calendar tab
// =====================================================================================
@Composable
private fun CalendarFeed(photos: List<Photo>, sw: Dp, onOpen: (ViewerCtx) -> Unit) {
    val zone = ZoneId.systemDefault()
    val byDay = remember(photos) { photos.groupBy { Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() } }
    val months = remember(byDay) { byDay.keys.map { YearMonth.from(it) }.distinct().sortedDescending() }
    val cell = (sw - 38.dp - 18.dp - 36.dp) / 7
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 128.dp, bottom = 160.dp)) {
        items(months, key = { it.toString() }) { ym ->
          Column {
            val count = byDay.filterKeys { YearMonth.from(it) == ym }.values.sumOf { it.size }
            SectionHeading(
                ym.month.getDisplayName(DayStyle.FULL, Locale.getDefault()) + " " + ym.year, "$count memories"
            )
            Row(Modifier.fillMaxWidth().rail().padding(start = 38.dp, end = 18.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                    Text(it, Modifier.width(cell), color = Color.White.copy(alpha = 0.4f), fontFamily = AppFonts.pixel, fontSize = 11.sp)
                }
            }
            val offset = ym.atDay(1).dayOfWeek.value - 1
            val days = ym.lengthOfMonth()
            val rows = (offset + days + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth().rail().padding(start = 38.dp, end = 18.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in 0..6) {
                        val day = r * 7 + c - offset + 1
                        if (day in 1..days) {
                            val date = ym.atDay(day)
                            DayCell(day, byDay[date].orEmpty().sortedByDescending { it.time }, cell) { list ->
                                onOpen(ViewerCtx(date.format(DateTimeFormatter.ofPattern("d MMMM")), list.map { it.key }, 0))
                            }
                        } else Spacer(Modifier.size(cell))
                    }
                }
            }
          }
        }
    }
}

@Composable
private fun DayCell(day: Int, list: List<Photo>, size: Dp, onClick: (List<Photo>) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val base = Modifier.size(size).clip(shape).background(Color.White.copy(alpha = 0.06f))
    if (list.isEmpty()) {
        Box(base, Alignment.Center) {
            Text("$day", color = Color.White.copy(alpha = 0.3f), fontFamily = AppFonts.pixel, fontSize = 11.sp)
        }
    } else {
        Box(base.bounceClick { onClick(list) }.border(1.dp, Color.White.copy(alpha = 0.2f), shape)) {
            AsyncImage(list.first().uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))))
            Text("$day", Modifier.align(Alignment.BottomStart).padding(start = 5.dp, bottom = 3.dp), color = Color.White, fontFamily = AppFonts.pixel, fontSize = 11.sp)
        }
    }
}

// =====================================================================================
// album / photo viewer: big rounded card + thumbnail strip
// =====================================================================================
@Composable
fun AlbumViewer(
    ctx: ViewerCtx, photos: List<Photo>,
    onArchive: (Photo) -> Unit, onDelete: (Photo) -> Unit, onClose: () -> Unit,
    onInfo: (Photo) -> Unit, onEdit: (Photo) -> Unit
) {
    if (photos.isEmpty()) { LaunchedEffect(Unit) { onClose() }; return }
    val state = rememberPagerState(initialPage = ctx.start.coerceIn(0, photos.lastIndex)) { photos.size }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val thumbs = rememberLazyListState()
    LaunchedEffect(state) {
        snapshotFlow { state.currentPage }.drop(1).collect { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
    LaunchedEffect(state.currentPage) {
        runCatching { thumbs.animateScrollToItem((state.currentPage - 1).coerceIn(0, photos.lastIndex)) }
    }
    val cur = photos.getOrNull(state.currentPage)
    val cardShape = RoundedCornerShape(44.dp)

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xCC000000), Color(0xF0000000))))) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().rail().padding(top = 44.dp, start = 34.dp, end = 18.dp, bottom = 14.dp)) {
                Column(Modifier.align(Alignment.CenterStart)) {
                    Text("Gllry", color = Color.White, fontFamily = AppFonts.ui, fontWeight = FontWeight.Medium, fontSize = 21.sp)
                    Text(LocalDate.now().format(fmtDate) + " ⁝", color = Color.White.copy(alpha = 0.85f), fontFamily = AppFonts.pixel, fontSize = 12.sp)
                }
                GlassButton(52.dp, onClose, Modifier.align(Alignment.CenterEnd)) { GlyphClose() }
            }

            Box(Modifier.weight(1f).fillMaxWidth().rail().padding(start = 38.dp, end = 18.dp, top = 8.dp, bottom = 8.dp)) {
                Box(
                    Modifier.fillMaxSize().clip(cardShape)
                        .border(1.5.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f))), cardShape)
                        .background(Color(0xFF0B0B0D))
                ) {
                    HorizontalPager(
                        state, Modifier.fillMaxSize(), pageSpacing = 12.dp,
                        key = { photos.getOrNull(it)?.key ?: it },
                        flingBehavior = PagerDefaults.flingBehavior(state, snapAnimationSpec = spring(0.8f, 380f))
                    ) { page ->
                        SwipePage(photos[page], page == state.currentPage, ctx.isArchive, onArchive, onDelete, false)
                    }
                    if (cur != null) {
                        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassPill("Details") { onInfo(cur) }
                            if (!cur.isVideo) GlassPill("Edit") { onEdit(cur) }
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().rail().padding(start = 38.dp, end = 18.dp, top = 10.dp)) {
                Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    GlyphFolder(20.dp, Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(ctx.title, color = Color.White, fontFamily = AppFonts.ui, fontWeight = FontWeight.Medium, fontSize = 19.sp, maxLines = 1)
                }
                if (cur != null) {
                    Text(
                        relTime(cur.time, "Added"), Modifier.align(Alignment.CenterHorizontally),
                        color = Color.White.copy(alpha = 0.55f), fontFamily = AppFonts.script, fontSize = 15.sp
                    )
                }
                Text(
                    "+${(photos.size - 5).coerceAtLeast(0)} more", Modifier.align(Alignment.End).padding(bottom = 6.dp),
                    color = Color.White.copy(alpha = 0.6f), fontFamily = AppFonts.script, fontSize = 11.sp
                )
            }

            LazyRow(
                Modifier.fillMaxWidth().rail().padding(bottom = 30.dp), state = thumbs,
                contentPadding = PaddingValues(start = 38.dp, end = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(photos, key = { _, p -> p.key }) { i, p ->
                    val selected = i == state.currentPage
                    val ring by animateDpAsState(if (selected) 2.5.dp else 1.dp, Motion.spatialFast<Dp>(), label = "ring")
                    val sc by animateFloatAsState(if (selected) 1.06f else 1f, Motion.spatialFast<Float>(), label = "thumb")
                    val shape = RoundedCornerShape(20.dp)
                    AsyncImage(
                        p.uri, null,
                        Modifier.size(62.dp).graphicsLayer { scaleX = sc; scaleY = sc }.bounceClick { scope.launch { state.animateScrollToPage(i, animationSpec = spring(0.8f, 380f)) } }
                            .clip(shape).border(ring, Color.White.copy(alpha = if (selected) 0.95f else 0.3f), shape),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}
