@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.gllry.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.abs

// =====================================================================================
// Root
// =====================================================================================
@Composable
fun GllryApp(
    vm: GalleryVM, hasPermission: Boolean, onRequest: () -> Unit, onDelete: (Photo) -> Unit,
    onImport: () -> Unit, onRecord: () -> Unit
) {
    val ctx = LocalContext.current
    var stackMode by remember { mutableStateOf(Store.stackDefault(ctx)) }
    var stackDefault by remember { mutableStateOf(Store.stackDefault(ctx)) }
    var tab by remember { mutableIntStateOf(0) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var viewer by remember { mutableStateOf<ViewerCtx?>(null) }
    var viewerOpen by remember { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }
    var detailsPhoto by remember { mutableStateOf<Photo?>(null) }
    var editOpen by remember { mutableStateOf(false) }
    var editPhoto by remember { mutableStateOf<Photo?>(null) }

    // later handlers win: search < viewer < details < editor
    BackHandler(searchOpen && !viewerOpen) { searchOpen = false; query = "" }
    BackHandler(viewerOpen) { viewerOpen = false }
    BackHandler(detailsOpen) { detailsOpen = false }
    BackHandler(editOpen) { editOpen = false }

    val index = remember(vm.all) { vm.all.associateBy { it.key } }
    val vphotos = remember(index, vm.archived, viewer) {
        viewer?.let { c -> c.keys.mapNotNull { index[it] }.filter { c.isArchive || it.archiveKey !in vm.archived } } ?: emptyList()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Backdrop()
        if (!hasPermission) {
            PermissionGate(onRequest)
        } else {
            val blurR by animateDpAsState(if (viewerOpen) 22.dp else 0.dp, Motion.spatial<Dp>(), label = "bg-blur")
            Box(Modifier.fillMaxSize().blur(blurR)) {
                GalleryScreen(
                    vm = vm, tab = tab, onTab = { tab = it; searchOpen = false; query = "" },
                    searchOpen = searchOpen, onSearchOpen = { searchOpen = it }, query = query, onQuery = { query = it },
                    onOpen = { c -> viewer = c; viewerOpen = true },
                    onSecret = { stackMode = true }, onImport = onImport, onRecord = onRecord
                )
            }
        }

        // Stack style (hold the "Gllry" title for 3 s, or set it as default)
        AnimatedVisibility(
            stackMode && hasPermission,
            enter = fadeIn(tween(450)) + scaleIn(tween(500), initialScale = 1.08f),
            exit = fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 1.05f)
        ) {
            StackGallery(
                vm, stackDefault,
                onToggleDefault = { stackDefault = !stackDefault; Store.setStackDefault(ctx, stackDefault) },
                onExit = { stackMode = false }
            )
        }

        AnimatedVisibility(
            viewerOpen,
            enter = fadeIn(Motion.effects<Float>()) + scaleIn(Motion.spatial<Float>(), initialScale = 0.92f),
            exit = fadeOut(Motion.effects<Float>()) + scaleOut(targetScale = 0.95f)
        ) {
            viewer?.let { c ->
                AlbumViewer(
                    ctx = c, photos = vphotos,
                    onArchive = { vm.setArchived(it, !c.isArchive) },
                    onDelete = onDelete, onClose = { viewerOpen = false },
                    onInfo = { detailsPhoto = it; detailsOpen = true },
                    onEdit = { editPhoto = it; editOpen = true }
                )
            }
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
        Text("Gllry", fontFamily = AppFonts.ui, fontWeight = FontWeight.Medium, fontSize = 44.sp, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text("allow photo access to begin", fontFamily = AppFonts.pixel, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier.bounceClick(onRequest).glass(RoundedCornerShape(50), Color(0xFF1B1B1E), 0.6f, 8.dp)
                .padding(horizontal = 28.dp, vertical = 14.dp)
        ) { Text("Allow photo access", color = Color.White, fontFamily = UiSans, fontWeight = FontWeight.Medium) }
    }
}

// =====================================================================================
// One page of the viewer: swipe UP = archive, swipe DOWN = delete (system confirm)
// =====================================================================================
@Composable
fun SwipePage(
    photo: Photo, active: Boolean, isArchive: Boolean,
    onArchive: (Photo) -> Unit, onDelete: (Photo) -> Unit, fit: Boolean = true
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val off = remember { Animatable(0f) }
    val th = with(LocalDensity.current) { 110.dp.toPx() }
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
        val lay = Modifier.fillMaxSize().graphicsLayer {
            val p = (abs(off.value) / (th * 3)).coerceIn(0f, 1f)
            translationY = off.value
            scaleX = 1f - 0.22f * p; scaleY = scaleX
            alpha = 1f - 0.4f * p
        }
        if (photo.isVideo) VideoPlayer(photo, active, lay)
        else AsyncImage(photo.uri, null, contentScale = if (fit) ContentScale.Fit else ContentScale.Crop, modifier = lay)
        HintPill(if (isArchive) "↑  Restore" else "↑  Archive", Color.White, Modifier.align(Alignment.TopCenter).padding(top = 40.dp)) {
            (-off.value / th).coerceIn(0f, 1f)
        }
        HintPill("↓  Delete", Color(0xFFFF3B30), Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp)) {
            (off.value / th).coerceIn(0f, 1f)
        }
    }
}

@Composable
fun HintPill(label: String, tint: Color, modifier: Modifier, progress: () -> Float) {
    Box(
        modifier.graphicsLayer { val p = progress(); alpha = p; scaleX = 0.7f + 0.3f * p; scaleY = scaleX }
            .glass(RoundedCornerShape(50), tint, 0.22f, 0.dp)
            .padding(horizontal = 22.dp, vertical = 12.dp)
    ) { Text(label, color = tint, fontFamily = UiSans, fontWeight = FontWeight.Medium, fontSize = 16.sp) }
}
