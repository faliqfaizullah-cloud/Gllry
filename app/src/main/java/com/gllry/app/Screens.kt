@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.gllry.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

// =====================================================================================
// Root
// =====================================================================================
@Composable
fun GllryApp(vm: GalleryVM, hasPermission: Boolean, onRequest: () -> Unit, onDelete: (Photo) -> Unit) {
    var albumOpen by remember { mutableStateOf(false) }
    var albumKey by remember { mutableStateOf(ALL) }
    var viewerOpen by remember { mutableStateOf(false) }
    var viewerKey by remember { mutableStateOf(ALL) }
    var viewerStart by remember { mutableIntStateOf(0) }
    var detailsOpen by remember { mutableStateOf(false) }
    var detailsPhoto by remember { mutableStateOf<Photo?>(null) }
    var editOpen by remember { mutableStateOf(false) }
    var editPhoto by remember { mutableStateOf<Photo?>(null) }

    BackHandler(viewerOpen) { viewerOpen = false }
    BackHandler(!viewerOpen && albumOpen) { albumOpen = false }
    BackHandler(detailsOpen) { detailsOpen = false }
    BackHandler(editOpen) { editOpen = false }

    Box(Modifier.fillMaxSize()) {
        Speckle()
        if (!hasPermission) {
            PermissionGate(onRequest)
        } else {
            val albums = remember(vm.all, vm.archived) { vm.albums() }
            Home(vm, albums,
                onAlbum = { albumKey = it; albumOpen = true },
                onPhoto = { k, i -> viewerKey = k; viewerStart = i; viewerOpen = true })

            AnimatedVisibility(
                albumOpen,
                enter = slideInVertically(spring(0.8f, Spring.StiffnessLow)) { it / 2 } + fadeIn() + scaleIn(initialScale = 0.9f),
                exit = slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeOut() + scaleOut(targetScale = 0.9f)
            ) {
                AlbumScreen(albums.firstOrNull { it.key == albumKey } ?: Album(albumKey, "", emptyList()),
                    onBack = { albumOpen = false },
                    onPhoto = { i -> viewerKey = albumKey; viewerStart = i; viewerOpen = true })
            }
        }

        AnimatedVisibility(
            viewerOpen,
            enter = scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), initialScale = 0.78f) + fadeIn(),
            exit = scaleOut(targetScale = 0.9f) + fadeOut()
        ) {
            Viewer(
                photos = vm.photosFor(viewerKey), start = viewerStart, isArchive = viewerKey == ARCHIVE,
                onArchive = { vm.setArchived(it, viewerKey != ARCHIVE) },
                onDelete = onDelete, onClose = { viewerOpen = false },
                onInfo = { detailsPhoto = it; detailsOpen = true },
                onEdit = { editPhoto = it; editOpen = true }
            )
        }

        // photo details sheet
        if (detailsOpen) {
            detailsPhoto?.let { DetailsSheet(it) { detailsOpen = false } }
        }

        // in-app editor (saves a copy, original untouched)
        AnimatedVisibility(
            editOpen,
            enter = slideInVertically(spring(0.85f, Spring.StiffnessLow)) { it } + fadeIn(),
            exit = slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeOut()
        ) {
            editPhoto?.let { Editor(it, onClose = { editOpen = false }, onSaved = { vm.refresh(); editOpen = false }) }
        }
    }
}

@Composable
fun PermissionGate(onRequest: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("Gllry", fontFamily = Display, fontSize = 56.sp, color = PoolBlue, letterSpacing = (-2).sp)
        Spacer(Modifier.height(8.dp))
        Text("Organize what matters", fontFamily = UiSans, fontSize = 16.sp, color = Color.Gray)
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier.bounceClick(onRequest).clip(RoundedCornerShape(50)).background(Color(0xFF111111))
                .padding(horizontal = 28.dp, vertical = 14.dp)
        ) { Text("Allow photo access", color = Color.White, fontFamily = UiSans, fontWeight = FontWeight.Medium) }
    }
}

// =====================================================================================
// Home  (Pool-style header + fanned stack + rounded album tiles)
// =====================================================================================
@Composable
fun Home(vm: GalleryVM, albums: List<Album>, onAlbum: (String) -> Unit, onPhoto: (String, Int) -> Unit) {
    val live = albums.firstOrNull { it.key == ALL }?.photos ?: emptyList()
    LazyVerticalGrid(
        GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 44.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { Header(live.size) }
        item(span = { GridItemSpan(maxLineSpan) }) { FanStack(live.take(5)) { onPhoto(ALL, 0) } }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Organize what\nmatters", fontFamily = Display, fontSize = 38.sp, lineHeight = 40.sp,
                letterSpacing = (-1.8).sp, color = PoolBlue, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
            )
        }
        items(albums, key = { it.key }) { a -> AlbumCard(a) { onAlbum(a.key) } }
    }
}

@Composable
fun Header(count: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Box(
            Modifier.size(42.dp)
                .shadow(22.dp, CircleShape, ambientColor = PoolBlue, spotColor = PoolBlue)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFFFE066), Color(0xFFFFB300))))
        )
        Text("Gllry", fontFamily = Display, fontSize = 30.sp, color = Color(0xFF2B3340), letterSpacing = (-0.5).sp)
        Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFF2D2D)).padding(horizontal = 20.dp, vertical = 9.dp)) {
            Text("$count", color = Color.White, fontFamily = UiSans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
    }
}

@Composable
fun FanStack(photos: List<Photo>, onTopClick: () -> Unit) {
    if (photos.isEmpty()) return
    val rot = listOf(0f, -9f, 8f, -15f, 14f)
    val dx = listOf(0f, -64f, 64f, -96f, 96f)
    val dy = listOf(0f, 14f, 10f, 26f, 22f)
    Box(Modifier.fillMaxWidth().height(330.dp), Alignment.Center) {
        for (i in photos.indices.reversed()) {
            val p = photos[i]
            val prog = remember(p.id) { Animatable(0f) }
            LaunchedEffect(p.id) {
                delay(i * 70L)
                prog.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
            }
            val mod = Modifier
                .graphicsLayer {
                    rotationZ = rot[i] * prog.value
                    translationX = dx[i] * density * prog.value
                    translationY = (dy[i] * density * prog.value) + (1f - prog.value) * 500f
                    alpha = prog.value.coerceIn(0f, 1f)
                }
                .size(172.dp, 232.dp)
                .shadow(16.dp, RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp))
            AsyncImage(p.uri, null, if (i == 0) mod.bounceClick(onTopClick) else mod, contentScale = ContentScale.Crop)
        }
    }
    Box(Modifier.fillMaxWidth(), Alignment.Center) {
        Box(Modifier.size(44.dp, 5.dp).clip(RoundedCornerShape(50)).background(Color(0xFFDEDEDB)))
    }
}

@Composable
fun AlbumCard(a: Album, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().popIn().bounceClick(onClick)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFF5F3F1), Color(0xFFEEECEA))))
            .padding(16.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(112.dp)) {
            MiniGrid(a.photos, Modifier.align(Alignment.TopStart))
            Canvas(Modifier.align(Alignment.TopEnd).size(4.dp, 18.dp)) {
                for (k in 0..2) drawCircle(Color(0xFF555555), 2.dp.toPx(), Offset(size.width / 2, 2.dp.toPx() + k * 7.dp.toPx()))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(a.title, fontFamily = UiSans, fontWeight = FontWeight.Medium, fontSize = 19.sp, color = Color(0xFF111111), maxLines = 1)
        Text(
            if (a.photos.size == 1) "1 memory" else "${a.photos.size} memories",
            fontFamily = UiSans, fontSize = 13.sp, color = Color(0xFF9A9A9A)
        )
    }
}

@Composable
fun MiniGrid(photos: List<Photo>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (r in 0..1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (c in 0..1) {
                val idx = r * 2 + c
                val shape = RoundedCornerShape(16.dp)
                when {
                    idx == 3 && photos.size > 4 -> Box(
                        Modifier.size(52.dp).clip(shape).background(Color.White), Alignment.Center
                    ) { Text("+${photos.size - 3}", fontSize = 13.sp, color = Color(0xFF9A9A9A), fontFamily = UiSans) }
                    idx < photos.size -> AsyncImage(photos[idx].uri, null, Modifier.size(52.dp).clip(shape), contentScale = ContentScale.Crop)
                    else -> Spacer(Modifier.size(52.dp))
                }
            }
        }
    }
}

// =====================================================================================
// Album grid
// =====================================================================================
@Composable
fun AlbumScreen(album: Album, onBack: () -> Unit, onPhoto: (Int) -> Unit) {
    Box(Modifier.fillMaxSize().background(Paper)) {
        Speckle()
        LazyVerticalGrid(
            GridCells.Fixed(3),
            contentPadding = PaddingValues(14.dp, 44.dp, 14.dp, 40.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.bounceClick(onBack).size(44.dp).clip(CircleShape).background(Color(0xFFEEECEA)),
                        Alignment.Center
                    ) { Text("‹", fontSize = 28.sp, color = Color(0xFF111111)) }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(album.title, fontFamily = Display, fontSize = 32.sp, letterSpacing = (-1).sp, color = Color(0xFF2B3340))
                        Text("${album.photos.size} memories", fontFamily = UiSans, fontSize = 13.sp, color = Color(0xFF9A9A9A))
                    }
                }
            }
            itemsIndexed(album.photos, key = { _, p -> p.id }) { i, p ->
                AsyncImage(
                    p.uri, null,
                    Modifier.aspectRatio(1f).popIn((i % 9) * 30L).bounceClick { onPhoto(i) }.clip(RoundedCornerShape(22.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

// =====================================================================================
// Full-screen viewer: fluid horizontal slide, swipe UP = archive, swipe DOWN = delete
// =====================================================================================
@Composable
fun Viewer(
    photos: List<Photo>, start: Int, isArchive: Boolean,
    onArchive: (Photo) -> Unit, onDelete: (Photo) -> Unit, onClose: () -> Unit,
    onInfo: (Photo) -> Unit, onEdit: (Photo) -> Unit
) {
    if (photos.isEmpty()) { LaunchedEffect(Unit) { onClose() }; return }
    val state = rememberPagerState(initialPage = start.coerceIn(0, photos.lastIndex)) { photos.size }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state) {
        snapshotFlow { state.currentPage }.collect { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state, Modifier.fillMaxSize(), pageSpacing = 16.dp,
            key = { photos.getOrNull(it)?.id ?: it },
            flingBehavior = PagerDefaults.flingBehavior(state, snapAnimationSpec = spring(0.72f, Spring.StiffnessMediumLow))
        ) { page ->
            val off = (state.currentPage - page) + state.currentPageOffsetFraction
            Box(Modifier.fillMaxSize().graphicsLayer {
                val a = abs(off).coerceIn(0f, 1f)
                scaleX = 1f - 0.12f * a; scaleY = scaleX
                alpha = 1f - 0.45f * a
                rotationY = off * -14f
                cameraDistance = 14f * density
            }) {
                SwipePage(photos[page], isArchive, onArchive, onDelete)
            }
        }
        photos.getOrNull(state.currentPage)?.let { cur ->
            val d = Instant.ofEpochSecond(cur.added).atZone(ZoneId.systemDefault()).toLocalDate()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 40.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically
            ) {
                Box(
                    Modifier.bounceClick(onClose).size(42.dp).clip(CircleShape).background(Color.White.copy(0.18f)),
                    Alignment.Center
                ) { Text("✕", color = Color.White, fontSize = 15.sp) }
                // Date in the same "sans + italic serif" pairing as the reference typography
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), color = Color.White,
                        fontFamily = UiSans, fontWeight = FontWeight.Light, fontSize = 24.sp)
                    Text(" ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}.", color = Color.White,
                        fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 24.sp)
                }
                Spacer(Modifier.size(42.dp))
            }
        }
        photos.getOrNull(state.currentPage)?.let { cur ->
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassPill("Details") { onInfo(cur) }
                GlassPill("Edit") { onEdit(cur) }
            }
        }
    }
}

@Composable
fun SwipePage(photo: Photo, isArchive: Boolean, onArchive: (Photo) -> Unit, onDelete: (Photo) -> Unit) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val off = remember { Animatable(0f) }
    val th = with(LocalDensity.current) { 150.dp.toPx() }
    val bouncy = spring<Float>(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)

    // haptic tick when crossing the commit threshold
    LaunchedEffect(Unit) {
        snapshotFlow { when { off.value < -th -> -1; off.value > th -> 1; else -> 0 } }
            .collect { if (it != 0) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }

    Box(
        Modifier.fillMaxSize().draggable(
            rememberDraggableState { d -> scope.launch { off.snapTo(off.value + d * 0.85f) } },
            Orientation.Vertical,
            onDragStopped = { v ->
                scope.launch {
                    when {
                        off.value < -th || v < -2800f -> {           // swipe UP -> archive
                            off.animateTo(-3000f, spring(0.9f, Spring.StiffnessLow))
                            onArchive(photo)
                        }
                        off.value > th || v > 2800f -> {             // swipe DOWN -> delete (system confirm)
                            onDelete(photo)
                            off.animateTo(0f, bouncy)
                        }
                        else -> off.animateTo(0f, bouncy)
                    }
                }
            }
        )
    ) {
        AsyncImage(
            photo.uri, null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val p = (abs(off.value) / (th * 3)).coerceIn(0f, 1f)
                translationY = off.value
                scaleX = 1f - 0.22f * p; scaleY = scaleX
                alpha = 1f - 0.4f * p
            }
        )
        HintPill(if (isArchive) "↑  Restore" else "↑  Archive", Color.White, Modifier.align(Alignment.TopCenter).padding(top = 110.dp)) {
            (-off.value / th).coerceIn(0f, 1f)
        }
        HintPill("↓  Delete", Color(0xFFFF3B30), Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp)) {
            (off.value / th).coerceIn(0f, 1f)
        }
    }
}

@Composable
fun HintPill(label: String, tint: Color, modifier: Modifier, progress: () -> Float) {
    Box(
        modifier.graphicsLayer { val p = progress(); alpha = p; scaleX = 0.7f + 0.3f * p; scaleY = scaleX }
            .clip(RoundedCornerShape(50)).background(tint.copy(alpha = 0.22f))
            .padding(horizontal = 22.dp, vertical = 12.dp)
    ) { Text(label, color = tint, fontFamily = UiSans, fontWeight = FontWeight.Medium, fontSize = 16.sp) }
}
