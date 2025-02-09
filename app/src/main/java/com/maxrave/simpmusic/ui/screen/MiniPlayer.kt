package com.maxrave.simpmusic.ui.screen

import android.util.Log
import androidx.compose.animation.Animatable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.toBitmap
import com.kmpalette.rememberPaletteState
import com.maxrave.simpmusic.R
import com.maxrave.simpmusic.data.db.entities.SongEntity
import com.maxrave.simpmusic.extension.connectArtists
import com.maxrave.simpmusic.extension.getColorFromPalette
import com.maxrave.simpmusic.ui.component.ExplicitBadge
import com.maxrave.simpmusic.ui.component.HeartCheckBox
import com.maxrave.simpmusic.ui.component.PlayPauseButton
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.SharedViewModel
import com.maxrave.simpmusic.viewModel.UIEvent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt


@UnstableApi
fun MiniPlayer(
    sharedViewModel: SharedViewModel,
    onClose: () -> Unit,
    onClick: () -> Unit,
    position: Long,
    duration: Long,
    modifier: Modifier = Modifier,
) {
    val (songEntity, setSongEntity) = remember { mutableStateOf<SongEntity?>(null) }
    val (liked, setLiked) = remember { mutableStateOf(false) }
    val (isPlaying, setIsPlaying) = remember { mutableStateOf(false) }
    val (progress, setProgress) = remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = ""
    )

    val playerConnection = LocalPlayerConnection.current ?: return
    val playbackState by playerConnection.playbackState.collectAsState()
    val error by playerConnection.error.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()

    // To manage dragging and background color
    val offsetY = remember { Animatable(0f) }
    val background = remember { Animatable(Color.DarkGray) }

    // Observe sharedViewModel state
    LaunchedEffect(Unit) {
        launch {
            sharedViewModel.nowPlayingState.collect { item ->
                if (item != null) {
                    setSongEntity(item.songEntity)
                }
            }
        }
        launch {
            sharedViewModel.controllerState.collect { state ->
                setLiked(state.isLiked)
                setIsPlaying(state.isPlaying)
            }
        }
        launch {
            sharedViewModel.timeline.collect { timeline ->
                val prog = if (timeline.total > 0L && timeline.current >= 0L) {
                    timeline.current.toFloat() / timeline.total
                } else {
                    0f
                }
                setProgress(prog)
            }
        }
    }

    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(10.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = background.value),
        modifier = modifier
            .fillMaxHeight()
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { },
                    onVerticalDrag = { change, dragAmount ->
                        coroutineScope.launch {
                            change.consume()
                            offsetY.animateTo(offsetY.value + dragAmount)
                        }
                    },
                    onDragEnd = {
                        coroutineScope.launch {
                            if (offsetY.value > 70) {
                                onClose()
                            }
                            offsetY.animateTo(0f)
                        }
                    }
                )
            }
    ) {
        Box(modifier = Modifier.fillMaxHeight()) {
            LinearProgressIndicator(
                progress = (position.toFloat() / duration).coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomCenter)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize().padding(end = 6.dp)
            ) {
                Box(Modifier.weight(1f)) {
                    mediaMetadata?.let {
                        MiniMediaInfo(
                            mediaMetadata = it,
                            error = error,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                }
                IconButton(
                    onClick = {
                        if (playbackState == Player.STATE_ENDED) {
                            playerConnection.player.seekTo(0, 0)
                            playerConnection.player.playWhenReady = true
                        } else {
                            playerConnection.player.togglePlayPause()
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(if (playbackState == Player.STATE_ENDED) R.drawable.replay else if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = null
                    )
                }
                IconButton(
                    enabled = canSkipNext,
                    onClick = playerConnection::seekToNext
                ) {
                    Icon(
                        painter = painterResource(R.drawable.skip_next),
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@Composable
fun MiniMediaInfo(
    mediaMetadata: MediaMetadata,
    error: PlaybackException?,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(modifier = Modifier.padding(6.dp)) {
            AsyncImage(
                model = mediaMetadata.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
            )
            AnimatedVisibility(
                visible = error != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(ThumbnailCornerRadius)
                        )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.info),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
        ) {
            Text(
                text = mediaMetadata.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = mediaMetadata.artists.joinToString { it.name },
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
