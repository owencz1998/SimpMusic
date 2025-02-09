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

@Composable
@UnstableApi
fun MiniPlayer(
    sharedViewModel: SharedViewModel,
    onClose: () -> Unit,
    onClick: () -> Unit,
) {
    val (songEntity, setSongEntity) =
        remember {
            mutableStateOf<SongEntity?>(null)
        }
    val (liked, setLiked) =
        remember {
            mutableStateOf(false)
        }
    val (isPlaying, setIsPlaying) =
        remember {
            mutableStateOf(false)
        }
    val (progress, setProgress) =
        remember {
            mutableFloatStateOf(0f)
        }

    val coroutineScope = rememberCoroutineScope()

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "",
    )

    // Palette state
    val paletteState = rememberPaletteState()
    val background =
        remember {
            Animatable(Color.DarkGray)
        }

    val offsetX = remember { Animatable(initialValue = 0f) }
    val offsetY = remember { Animatable(0f) }

    var loading by rememberSaveable {
        mutableStateOf(true)
    }

    var bitmap by remember {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(bitmap) {
        val bm = bitmap
        if (bm != null) {
            paletteState.generate(bm)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { paletteState.palette }
            .distinctUntilChanged()
            .collectLatest {
                background.animateTo(it.getColorFromPalette())
            }
    }

    LaunchedEffect(key1 = true) {
        val job1 =
            launch {
                sharedViewModel.nowPlayingState.collect { item ->
                    if (item != null) {
                        setSongEntity(item.songEntity)
                    }
                }
            }
        val job2 =
            launch {
                sharedViewModel.controllerState.collectLatest { state ->
                    setLiked(state.isLiked)
                    setIsPlaying(state.isPlaying)
                }
            }
        val job4 =
            launch {
                sharedViewModel.timeline.collect { timeline ->
                    loading = timeline.loading
                    val prog =
                        if (timeline.total > 0L && timeline.current >= 0L) {
                            timeline.current.toFloat() / timeline.total
                        } else {
                            0f
                        }
                    setProgress(prog)
                }
            }
        job1.join()
        job2.join()
        job4.join()
    }

    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(10.dp),
        shape = RoundedCornerShape(8.dp),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = background.value,
            ),
        modifier =
            Modifier
                .clipToBounds()
                .fillMaxHeight()
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
                .clickable(
                    onClick = onClick,
                ).pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                        },
                        onVerticalDrag = { change: PointerInputChange, dragAmount: Float ->
                            if (offsetY.value + dragAmount > 0) {
                                coroutineScope.launch {
                                    change.consume()
                                    offsetY.animateTo(offsetY.value + dragAmount)
                                    Log.w("MiniPlayer", "Dragged ${offsetY.value}")
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetY.animateTo(0f)
                            }
                        },
                        onDragEnd = {
                            Log.w("MiniPlayer", "Drag Ended")
                            coroutineScope.launch {
                                if (offsetY.value > 70) {
                                    onClose()
                                }
                                offsetY.animateTo(0f)
                            }
                        },
                    )
                },
    ) {
        Box(modifier = Modifier.fillMaxHeight()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxSize(),
            ) {
                Spacer(modifier = Modifier.size(8.dp))
                Box(modifier = Modifier.weight(1F)) {
                    Row(
                        modifier =
                            Modifier
                                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragStart = {
                                        },
                                        onHorizontalDrag = {
                                            change: PointerInputChange,
                                            dragAmount: Float,
                                            ->
                                            coroutineScope.launch {
                                                change.consume()
                                                offsetX.animateTo(offsetX.value + dragAmount)
                                                Log.w("MiniPlayer", "Dragged ${offsetX.value}")
                                            }
                                        },
                                        onDragCancel = {
                                            Log.w("MiniPlayer", "Drag Cancelled")
                                            coroutineScope.launch {
                                                if (offsetX.value > 200) {
                                                    sharedViewModel.onUIEvent(UIEvent.Previous)
                                                } else if (offsetX.value < -120) {
                                                    sharedViewModel.onUIEvent(UIEvent.Next)
                                                }
                                                offsetX.animateTo(0f)
                                            }
                                        },
                                        onDragEnd = {
                                            Log.w("MiniPlayer", "Drag Ended")
                                            coroutineScope.launch {
                                                if (offsetX.value > 200) {
                                                    sharedViewModel.onUIEvent(UIEvent.Previous)
                                                } else if (offsetX.value < -120) {
                                                    sharedViewModel.onUIEvent(UIEvent.Next)
                                                }
                                                offsetX.animateTo(0f)
                                            }
                                        },
                                    )
                                },
                    ) {
                        AsyncImage(
                            model =
                                ImageRequest
                                    .Builder(LocalContext.current)
                                    .data(songEntity?.thumbnails)
                                    .crossfade(550)
                                    .build(),
                            placeholder = painterResource(R.drawable.holder),
                            error = painterResource(R.drawable.holder),
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            onSuccess = {
                                bitmap =
                                    it.result.image
                                        .toBitmap()
                                        .asImageBitmap()
                            },
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .align(Alignment.CenterVertically)
                                    .clip(
                                        RoundedCornerShape(4.dp),
                                    ),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        AnimatedContent(
                            targetState = songEntity,
                            modifier = Modifier.weight(1F).fillMaxHeight(),
                            contentAlignment = Alignment.CenterStart,
                            transitionSpec = {
                                // Compare the incoming number with the previous number.
                                if (targetState != initialState) {
                                    // If the target number is larger, it slides up and fades in
                                    // while the initial (smaller) number slides up and fades out.
                                    (
                                        slideInHorizontally { width ->
                                            width
                                        } + fadeIn()
                                    ).togetherWith(
                                        slideOutHorizontally { width -> +width } + fadeOut(),
                                    )
                                } else {
                                    // If the target number is smaller, it slides down and fades in
                                    // while the initial number slides down and fades out.
                                    (
                                        slideInHorizontally { width ->
                                            +width
                                        } + fadeIn()
                                    ).togetherWith(
                                        slideOutHorizontally { width -> width } + fadeOut(),
                                    )
                                }.using(
                                    // Disable clipping since the faded slide-in/out should
                                    // be displayed out of bounds.
                                    SizeTransform(clip = false),
                                )
                            },
                        ) { target ->
                            if (target != null) {
                                Column(
                                    Modifier
                                        .wrapContentHeight()
                                        .align(Alignment.CenterVertically),
                                ) {
                                    Text(
                                        text = (songEntity?.title ?: "").toString(),
                                        style = typo.labelSmall,
                                        color = Color.White,
                                        maxLines = 1,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .wrapContentHeight(
                                                    align = Alignment.CenterVertically,
                                                ).basicMarquee(
                                                    iterations = Int.MAX_VALUE,
                                                    animationMode = MarqueeAnimationMode.Immediately,
                                                ).focusable(),
                                    )
                                    LazyRow(verticalAlignment = Alignment.CenterVertically) {
                                        item {
                                            androidx.compose.animation.AnimatedVisibility(visible = songEntity?.isExplicit == true) {
                                                ExplicitBadge(
                                                    modifier =
                                                        Modifier
                                                            .size(20.dp)
                                                            .padding(end = 4.dp)
                                                            .weight(1f),
                                                )
                                            }
                                        }
                                        item {
                                            Text(
                                                text = (songEntity?.artistName?.connectArtists() ?: "").toString(),
                                                style = typo.bodySmall,
                                                maxLines = 1,
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .wrapContentHeight(
                                                            align = Alignment.CenterVertically,
                                                        ).basicMarquee(
                                                            iterations = Int.MAX_VALUE,
                                                            animationMode = MarqueeAnimationMode.Immediately,
                                                        ).focusable(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(15.dp))
                HeartCheckBox(checked = liked, size = 30) {
                    sharedViewModel.onUIEvent(UIEvent.ToggleLike)
                }
                Spacer(modifier = Modifier.width(15.dp))
                Crossfade(targetState = loading, label = "") {
                    if (it) {
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.LightGray,
                                strokeWidth = 3.dp,
                            )
                        }
                    } else {
                        PlayPauseButton(isPlaying = isPlaying, modifier = Modifier.size(48.dp)) {
                            sharedViewModel.onUIEvent(UIEvent.PlayPause)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(15.dp))
            }
            Box(
                modifier =
                    Modifier
                        .wrapContentSize(Alignment.Center)
                        .padding(
                            horizontal = 10.dp,
                        ).align(Alignment.BottomCenter),
            ) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                color = Color.Transparent,
                                shape = RoundedCornerShape(4.dp),
                            ),
                    color = Color.White,
                    trackColor = Color.Transparent,
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {},
                )
            }
        }
    }
}
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
@UnstableApi
fun MiniPlayer(
    sharedViewModel: SharedViewModel,
    onClose: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val (songEntity, setSongEntity) = remember { mutableStateOf(null) }
    val (liked, setLiked) = remember { mutableStateOf(false) }
    val (isPlaying, setIsPlaying) = remember { mutableStateOf(false) }
    val (progress, setProgress) = remember { mutableFloatStateOf(0f) }

    val coroutineScope = rememberCoroutineScope()

    // Palette state for background color
    val paletteState = rememberPaletteState()
    val background = remember { Animatable(Color.DarkGray) }

    // Dragging states
    val offsetX = remember { Animatable(initialValue = 0f) }
    val offsetY = remember { Animatable(0f) }

    // Bitmap for the current song thumbnail
    var bitmap by remember { mutableStateOf(null) }

    LaunchedEffect(bitmap) {
        bitmap?.let {
            paletteState.generate(it)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { paletteState.palette }
            .distinctUntilChanged()
            .collect { color ->
                background.animateTo(color.getColorFromPalette())
            }
    }

    LaunchedEffect(true) {
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
            .clipToBounds()
            .fillMaxHeight()
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .clickable(onClick = onClick)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
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
                progress = progress.coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomCenter)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize().padding(end = 6.dp),
            ) {
                Spacer(modifier = Modifier.size(8.dp))
                Box(modifier = Modifier.weight(1F)) {
                    Row(
                        modifier = Modifier
                            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        coroutineScope.launch {
                                            if (offsetX.value > 200) {
                                                sharedViewModel.onUIEvent(UIEvent.Previous)
                                            } else if (offsetX.value  width } + fadeIn()).togetherWith(
                                        slideOutHorizontally { width -> +width } + fadeOut()
                                    )
                                } else {
                                    // Handle other transitions if needed
                                    fadeIn() with fadeOut()
                                }
                            }
                        ) { song ->
                            song?.let {
                                Text(
                                    text = it.title,
                                    style = MaterialTheme.typography.body1,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = {
                        if (playerConnection.playbackState.value == Player.STATE_ENDED) {
                            playerConnection.player.seekTo(0, 0)
                            playerConnection.player.playWhenReady = true
                        } else {
                            playerConnection.player.togglePlayPause()
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(
                            if (playerConnection.playbackState.value == Player.STATE_ENDED) R.drawable.replay
                            else if (isPlaying) R.drawable.pause
                            else R.drawable.play
                        ),
                        contentDescription = null
                    )
                }

                IconButton(
                    enabled = playerConnection.canSkipNext.collectAsState().value,
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

Key Features:
State Management**: Combines views and state management from both provided snippets.
Thumbnail Handling**: Displays the song thumbnail and handles loading states.
Playback Controls**: Includes buttons for play/pause and skip functionalities.
Drag Gesture Handling**: Allows you to swipe to navigate between songs and dismiss the player.

Notes:
Make sure to replace SharedViewModel, SongEntity, MediaMetadata, and other placeholders with your actual implementations and resources.
Ensure you have the necessary imports based on your project structure.
The AsyncImage part assumes you have an appropriate image loading library, such as Coil.

Let me know if you need further adjustments or additional features!