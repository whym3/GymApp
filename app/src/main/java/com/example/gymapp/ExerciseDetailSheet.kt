package com.example.gymapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.gif.AnimatedImageDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.example.gymapp.network.ExerciseDbClient
import com.example.gymapp.network.ExerciseDbExercise
import com.example.gymapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailSheet(exercise: ExerciseLibraryItem, onClose: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalPlatformContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory())
                add(AnimatedImageDecoder.Factory())
            }
            .build()
    }

    var demo by remember(exercise.name) { mutableStateOf<ExerciseDbExercise?>(null) }
    var loading by remember(exercise.name) { mutableStateOf(true) }

    LaunchedEffect(exercise.name) {
        loading = true
        demo = ExerciseDbClient.findDemo(exercise.name, exercise.group, exercise.equip)
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = BgElevColor,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .background(LineStrongColor, RoundedCornerShape(99.dp)),
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight(0.88f),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(exercise.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextColor)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SubtleFillColor)
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Close, null, tint = SubTextColor, modifier = Modifier.size(17.dp))
                    }
                }

                Spacer(Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MuscleTag(exercise.group)
                    EquipTag(exercise.equip)
                }

                Spacer(Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardColor),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        loading -> CircularProgressIndicator(color = AccentColor, modifier = Modifier.size(28.dp))
                        demo != null -> AsyncImage(
                            model = demo!!.gifUrl,
                            contentDescription = exercise.name,
                            imageLoader = imageLoader,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.FitnessCenter, null, tint = MutedColor, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("No demo available", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
            }

            demo?.let { d ->
                val muscles = (d.targetMuscles + d.secondaryMuscles).distinct()
                if (muscles.isNotEmpty()) {
                    item {
                        Text("Target Muscles", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextColor)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            muscles.forEach { muscle ->
                                Box(
                                    modifier = Modifier
                                        .background(SubtleFillColor, RoundedCornerShape(99.dp))
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                ) {
                                    Text(muscle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SubTextColor)
                                }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                }

                if (d.instructions.isNotEmpty()) {
                    item {
                        Text("Instructions", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextColor)
                        Spacer(Modifier.height(8.dp))
                    }
                    items(d.instructions) { step ->
                        Text(
                            step,
                            fontSize = 13.sp,
                            color = SubTextColor,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}
