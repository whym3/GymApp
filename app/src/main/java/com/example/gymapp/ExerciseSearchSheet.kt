package com.example.gymapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseSearchSheet(
    onClose: () -> Unit,
    onAdd: (ExerciseLibraryItem) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query  by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<String?>(null) }
    val added  = remember { mutableStateMapOf<String, Boolean>() }

    val filtered = exerciseLibrary.filter { ex ->
        (filter == null || ex.group == filter) &&
        (query.isBlank() || ex.name.contains(query, ignoreCase = true))
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
        Column(modifier = Modifier.fillMaxHeight(0.88f)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Add Exercise", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextColor)
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

            // Search bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(CardColor)
                    .border(1.dp, LineColor, RoundedCornerShape(13.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Search, null, tint = MutedColor, modifier = Modifier.size(18.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    cursorBrush = SolidColor(AccentColor),
                    textStyle = TextStyle(
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextColor,
                    ),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                "Search exercises",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MutedColor,
                            )
                        }
                        inner()
                    },
                )
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Close,
                        null,
                        tint = MutedColor,
                        modifier = Modifier.size(16.dp).clickable { query = "" },
                    )
                }
            }

            // Filter chips
            val groups = listOf("Chest", "Back", "Legs", "Shoulders", "Arms", "Core")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groups.forEach { group ->
                    val on  = filter == group
                    val col = muscleColor(group)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (on) col.copy(alpha = 0.13f) else Color.Transparent)
                            .border(1.dp, if (on) col else LineColor, RoundedCornerShape(99.dp))
                            .clickable { filter = if (on) null else group }
                            .padding(horizontal = 13.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(modifier = Modifier.size(7.dp).background(col, CircleShape))
                        Text(
                            group,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (on) col else SubTextColor,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Exercise list
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No exercises found", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    items(filtered) { ex ->
                        val col       = muscleColor(ex.group)
                        val isAdded   = added[ex.name] == true

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onAdd(ex)
                                    added[ex.name] = true
                                }
                                .padding(horizontal = 10.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(13.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(col.copy(alpha = 0.11f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.FitnessCenter, null, tint = col, modifier = Modifier.size(20.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ex.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextColor)
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    Text(ex.group, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = col)
                                    Box(modifier = Modifier.size(3.dp).background(MutedColor, CircleShape))
                                    EquipTag(ex.equip)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        if (isAdded) GoodColor else AccentSoftColor,
                                        RoundedCornerShape(10.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (isAdded) Icons.Rounded.Check else Icons.Rounded.Add,
                                    null,
                                    tint = if (isAdded) BgColor else AccentColor,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
