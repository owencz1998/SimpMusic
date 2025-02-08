package com.maxrave.simpmusic.ui.screen

import android.util.Log
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kmpalette.rememberPaletteState
import com.maxrave.simpmusic.data.db.entities.SongEntity
import com.maxrave.simpmusic.viewModel.SharedViewModel
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
    onOpenMainPlayer: () -> Unit // New parameter for opening the main player
) {
    val (songEntity, setSongEntity) = remember { mutableStateOf<SongEntity?>(null) }
    val (liked, setLiked) = remember { mutableStateOf(false) }
    val (isPlaying, setIsPlaying) = remember { mutableStateOf(false) }
    val (progress, setProgress) = remember { mutableFloatStateOf(0f) }

    val coroutineScope = rememberCoroutineScope()

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "",
    )

    // Palette state
    val paletteState = rememberPaletteState()
    val background = remember { Animatable(Color.DarkGray) }

    val offsetX = remember { Animatable(initialValue = 0f) }
    val offsetY = remember { Animatable(0f) }

    var loading by rememberSaveable { mutableStateOf(true) }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }

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
        coroutineScope.launch {
            val job1 = launch {
                sharedViewModel.nowPlayingState.collect { item ->
                    if (item != null) {
                        setSongEntity(item.songEntity)
                    }
                }
            }
            val job2 = launch {
                sharedViewModel.controllerState.collectLatest { state ->
                    setLiked(state.isLiked)
                    setIsPlaying(state.isPlaying)
                }
            }
            val job4 = launch {
                sharedViewModel.timeline.collect { timeline ->
                    loading = timeline.loading
                    val prog = if (timeline.total > 0L && timeline.current >= 0L) {
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
    }

    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(10.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = background.value),
        modifier = Modifier
            .clipToBounds()
            .fillMaxHeight()
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .clickable(onClick = onClick)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {},
                    onVerticalDrag = { change: PointerInputChange, dragAmount: Float ->
                        coroutineScope.launch {
                            change.consume()
                            offsetY.animateTo(offsetY.value + dragAmount)
                            Log.w("MiniPlayer", "Dragged ${offsetY.value}")
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
                            when {
                                offsetY.value < -70 -> {
                                    onOpenMainPlayer() // Open the main player on swipe up
                                }
                                offsetY.value > 70 -> {
                                    onClose() // Close the mini player on swipe down
                                }
                                else -> {
                                    offsetY.animateTo(0f) // Reset position if not swiped enough
                                }
                            }
                        }
                    }
                )
            },
    ) {
        Box(modifier = Modifier.fillMaxHeight()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize(),
            ) {
                Spacer(modifier = Modifier.size(8.dp))
                Box(modifier = Modifier.weight(1F)) {
                    Row(
                        modifier = Modifier
                            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragStart = {},
                                    onHorizontalDrag = { change: PointerInputChange, dragAmount: Float ->
                                        coroutineScope.launch {
                                            change.consume()
                                            offsetX.animateTo(offsetX.value + dragAmount)
                                            Log.w("MiniPlayer", "Dragged ${offsetX.value}")
                                        }
                                    }
                                )
                            }
                    ) {
                        // Your existing UI components (e.g., song title, artist, play/pause button)
                        Text(text = songEntity?.title ?: "No song playing")
                        // Additional UI components can go here
                    }
                }
            }
        }
    }
}
