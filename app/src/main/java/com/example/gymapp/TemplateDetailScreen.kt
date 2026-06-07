package com.example.gymapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymapp.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplateDetailScreen(
    template: WorkoutTemplate,
    onBack: () -> Unit,
    onStart: (WorkoutTemplate) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxSize().background(BgColor)) {
        DetailTopBar(
            title = template.name,
            subtitle = template.subtitle,
            onBack = onBack,
            trailing = if (onDelete != null) {
                {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(CardColor)
                            .clickable { onDelete() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, "Delete routine", tint = MuscleChest, modifier = Modifier.size(19.dp))
                    }
                }
            } else null,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 16.dp),
        ) {
            // Muscle groups
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                template.groups.forEach { MuscleTag(it) }
            }

            Spacer(Modifier.height(16.dp))

            // Overview card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, LineColor, RoundedCornerShape(16.dp))
                    .background(CardColor)
                    .padding(16.dp),
            ) {
                Text("Overview", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MutedColor, letterSpacing = 0.4.sp)
                Spacer(Modifier.height(8.dp))
                Text(template.description, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = SubTextColor, lineHeight = 21.sp)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    MetaStat("${template.count}", "Exercises")
                    MetaStat("${template.groups.size}", "Muscle groups")
                    MetaStat("~${template.count * 9} min", "Est. time")
                }
            }

            Spacer(Modifier.height(22.dp))
            Text("Exercises", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextColor)
            Spacer(Modifier.height(12.dp))

            template.exercises.forEachIndexed { i, ex ->
                val col = muscleColor(ex.group)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, LineColor, RoundedCornerShape(14.dp))
                        .background(CardColor)
                        .padding(horizontal = 13.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    Box(
                        modifier = Modifier.size(34.dp).background(SubtleFillColor, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${i + 1}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SubTextColor)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ex.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextColor)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(ex.group, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = col)
                            Box(modifier = Modifier.size(3.dp).background(MutedColor, CircleShape))
                            EquipTag(ex.equip)
                        }
                    }
                }
            }
        }

        // Sticky start button
        HorizontalDivider(color = LineColor, thickness = 1.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgColor)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Button(
                onClick = { onStart(template) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                contentPadding = PaddingValues(vertical = 15.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start ${template.name} Workout", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun MetaStat(value: String, label: String) {
    Column {
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextColor)
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)
    }
}
