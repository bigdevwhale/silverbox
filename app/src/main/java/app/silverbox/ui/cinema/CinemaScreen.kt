package app.silverbox.ui.cinema

import android.media.MediaPlayer
import android.net.Uri
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.silverbox.R
import app.silverbox.container
import app.silverbox.ui.Go
import app.silverbox.ui.LabData
import app.silverbox.ui.T
import app.silverbox.ui.ctx
import app.silverbox.ui.theme.Hand

/** A dark room, the beam of a projector and the film on the wall; the projector clatters along. */
@OptIn(UnstableApi::class)
@Composable
fun CinemaScreen(id: Long, data: LabData, go: Go) {
    val c = ctx()
    val roll = data.roll(id) ?: return
    val file = c.container.vault.file(roll.film)
    var playing by remember { mutableStateOf(true) }
    val sound = data.settings.projector
    val player = remember(file) {
        ExoPlayer.Builder(c).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) { seekTo(0); pause() }
                }
            })
        }
    }
    val clatter = remember { runCatching { MediaPlayer.create(c, R.raw.projector)?.apply { isLooping = true; setVolume(0.18f, 0.18f) } }.getOrNull() }
    val view = androidx.compose.ui.platform.LocalView.current
    // What the bars go back to: dark icons on the cream theme, light ones in the darkroom.
    val lightIcons = app.silverbox.ui.theme.U.c.dark
    DisposableEffect(player) {
        // The room is dark: light icons in the bars while the film is on.
        val window = (view.context as? android.app.Activity)?.window
        val bars = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = false
        onDispose {
            bars?.isAppearanceLightStatusBars = !lightIcons
            bars?.isAppearanceLightNavigationBars = !lightIcons
            player.release()
            clatter?.release()
        }
    }
    // The projector runs while the film does.
    androidx.compose.runtime.LaunchedEffect(playing, sound) {
        clatter ?: return@LaunchedEffect
        if (playing && sound) clatter.start() else if (clatter.isPlaying) clatter.pause()
    }
    val flicker = rememberInfiniteTransition(label = "lamp").animateFloat(0.85f, 1f, infiniteRepeatable(tween(90), RepeatMode.Reverse), label = "f")

    Box(
        Modifier.fillMaxSize().background(Color(0xFF0B0907)).clickable(remember { MutableInteractionSource() }, null) {
            if (player.isPlaying) player.pause() else player.play()
        },
    ) {
        // The beam from behind the audience.
        Canvas(Modifier.fillMaxSize()) {
            val src = Offset(size.width / 2, size.height * 1.05f)
            val top = size.height * 0.3f
            val beam = Path().apply {
                moveTo(src.x - 6f, src.y)
                lineTo(size.width * 0.02f, top)
                lineTo(size.width * 0.98f, top + size.width * 0.72f)
                lineTo(src.x + 6f, src.y)
                close()
            }
            drawPath(beam, Brush.verticalGradient(listOf(Color(0x14FFF3D6), Color(0x30FFF3D6)), top, src.y), alpha = if (playing) flicker.value else 0.4f)
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { go.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = Color(0xFFE9E1D0)) }
                Text(T.rollName(c, roll), fontFamily = Hand, fontSize = 24.sp, color = Color(0xFFE9E1D0), modifier = Modifier.weight(1f))
                IconButton(onClick = { c.container.write { settings.update { it.copy(projector = !sound) } } }) {
                    Icon(if (sound) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff, stringResource(R.string.film_sound), tint = Color(0xFFE9E1D0))
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).aspectRatio(4f / 3f).background(Color.Black)) {
                    AndroidView(factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } }, modifier = Modifier.fillMaxSize())
                }
            }
            Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
                if (!playing) Icon(Icons.Rounded.PlayArrow, stringResource(R.string.film_play), Modifier.size(48.dp), tint = Color(0xFFE9E1D0))
                else Icon(Icons.Rounded.Pause, null, Modifier.size(28.dp), tint = Color(0x55E9E1D0))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
