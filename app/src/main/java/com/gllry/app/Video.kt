package com.gllry.app

import android.view.LayoutInflater
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

fun fmtDuration(ms: Long): String {
    val s = (ms / 1000).toInt()
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
fun PlayBadge(modifier: Modifier = Modifier, size: Int = 46) {
    Box(modifier.size(size.dp).glass(CircleShape, Color.White, 0.22f, 0.dp), Alignment.Center) {
        Text("▶", color = Color.White, fontSize = (size * 0.34f).sp)
    }
}

@Composable
fun DurationTag(ms: Long, modifier: Modifier = Modifier) {
    Box(modifier.glass(RoundedCornerShape(50), Color.Black, 0.35f, 0.dp).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text("▶ " + fmtDuration(ms), color = Color.White, fontSize = 11.sp, fontFamily = UiSans)
    }
}

/** In-app video player (ExoPlayer on a TextureView so it follows the viewer's swipe animations). */
@Composable
fun VideoPlayer(photo: Photo, active: Boolean, modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val player = remember(photo.key) {
        ExoPlayer.Builder(ctx).build().apply { setMediaItem(MediaItem.fromUri(photo.uri)); prepare() }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    var playing by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(photo.duration) }
    var muted by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                ended = state == Player.STATE_ENDED
                if (state == Player.STATE_READY && player.duration > 0) dur = player.duration
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    LaunchedEffect(active) { if (active) player.play() else player.pause() }
    LaunchedEffect(player) { while (true) { pos = player.currentPosition; delay(200) } }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(playing, controls) { if (playing && controls) { delay(2500); controls = false } }

    fun toggle() {
        if (ended) { player.seekTo(0); player.play() }
        else if (player.isPlaying) player.pause() else player.play()
    }

    Box(modifier.clickable(remember { MutableInteractionSource() }, null) { controls = !controls }) {
        AndroidView(
            factory = { c ->
                (LayoutInflater.from(c).inflate(R.layout.player_view, null) as PlayerView).also { it.player = player }
            },
            modifier = Modifier.fillMaxSize()
        )
        AnimatedVisibility(controls || !playing, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.size(76.dp).bounceClick { toggle() }.glass(CircleShape, Color.White, 0.22f, 0.dp),
                Alignment.Center
            ) {
                Text(if (ended) "↻" else if (playing) "❚❚" else "▶", color = Color.White, fontSize = 24.sp)
            }
        }
        AnimatedVisibility(controls || !playing, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 104.dp)
                    .glass(RoundedCornerShape(50), Color.White, 0.14f, 0.dp)
                    .padding(horizontal = 18.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(fmtDuration(pos), color = Color.White, fontSize = 12.sp, fontFamily = UiSans)
                Slider(
                    value = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                    onValueChange = { v -> pos = (v * dur).toLong(); player.seekTo(pos) },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White, activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                Text(fmtDuration(dur), color = Color.White, fontSize = 12.sp, fontFamily = UiSans)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (muted) "Sound" else "Mute", color = Color.White, fontSize = 12.sp,
                    fontWeight = FontWeight.Medium, fontFamily = UiSans,
                    modifier = Modifier.bounceClick { muted = !muted }.padding(4.dp)
                )
            }
        }
    }
}
