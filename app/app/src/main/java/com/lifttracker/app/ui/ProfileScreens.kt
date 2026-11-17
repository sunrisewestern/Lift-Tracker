package com.lifttracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fittribe.musclemap.BodyGender
import com.fittribe.musclemap.BodySide
import com.fittribe.musclemap.BodyView
import com.fittribe.musclemap.BodyViewStyle
import com.fittribe.musclemap.Muscle
import com.fittribe.musclemap.MuscleHighlight
import com.lifttracker.app.domain.BodyMuscle
import com.lifttracker.app.domain.MuscleGroup
import com.lifttracker.app.domain.StatBucket
import com.lifttracker.app.domain.StatMetric
import com.lifttracker.app.domain.StatPeriod
import com.lifttracker.app.domain.WeightUnit
import com.lifttracker.app.domain.WorkoutSession
import com.lifttracker.app.domain.WorkoutStats
import com.lifttracker.app.domain.WorkoutTotals
import com.lifttracker.app.viewmodel.LiftTrackerViewModel
import com.lifttracker.app.viewmodel.ProfileLayer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ============================================================================
// Profile: workout dashboard, body heatmap, statistics, monthly report, calendar
// ============================================================================

private val Primary: Color @Composable get() = MaterialTheme.colorScheme.primary
private val StreakOrange = Color(0xFFFF6B35)
private val UpGreen = Color(0xFF27AE60)

private fun firstDayOfWeek(): DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

// ---------------------------------------------------------------- formatting

internal fun formatStatDuration(seconds: Long): String {
    val minutes = (seconds / 60).coerceAtLeast(0)
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}min"
        h > 0 -> "${h}h"
        else -> "${m}min"
    }
}

/** "85k lbs", "1.2k kg", "640 kg". */
internal fun formatStatVolume(volumeKg: Double, unit: WeightUnit): String {
    val v = abs(volumeKg) * unit.factorFromKg
    val number = when {
        v >= 10_000 -> "${(v / 1000).roundToInt()}k"
        v >= 1_000 -> String.format(Locale.US, "%.1fk", v / 1000)
        else -> "${v.roundToInt()}"
    }
    return "$number ${unit.label}"
}

internal fun formatStatValue(totals: WorkoutTotals, metric: StatMetric, unit: WeightUnit): String = when (metric) {
    StatMetric.WORKOUTS -> "${totals.workouts}"
    StatMetric.DURATION -> formatStatDuration(totals.durationSeconds)
    StatMetric.VOLUME -> formatStatVolume(totals.volumeKg, unit)
    StatMetric.SETS -> "${totals.sets}"
    StatMetric.REPS -> "${totals.reps}"
}

/** Change from [previous] to [current], e.g. "↑ 2", "↓ 40k lbs"; null when there is none. */
internal fun formatStatDelta(current: WorkoutTotals, previous: WorkoutTotals, metric: StatMetric, unit: WeightUnit): String? {
    val diff = current.value(metric) - previous.value(metric)
    val amount = when (metric) {
        StatMetric.WORKOUTS, StatMetric.SETS, StatMetric.REPS -> "${abs(diff).roundToInt()}".takeIf { abs(diff) >= 0.5 }
        StatMetric.DURATION -> formatStatDuration(abs(diff).toLong()).takeIf { abs(diff) >= 60 }
        StatMetric.VOLUME -> formatStatVolume(abs(diff), unit).takeIf { abs(diff) * unit.factorFromKg >= 0.5 }
    } ?: return null
    return (if (diff > 0) "↑ " else "↓ ") + amount
}

private fun bucketLabel(bucket: StatBucket, period: StatPeriod): String = when (period) {
    StatPeriod.WEEK -> "${bucket.start.monthValue}/${bucket.start.dayOfMonth}"
    StatPeriod.MONTH -> bucket.start.month.getDisplayName(DateTextStyle.NARROW, Locale.getDefault())
    StatPeriod.QUARTER -> "Q${(bucket.start.monthValue - 1) / 3 + 1}"
    StatPeriod.YEAR -> "'${bucket.start.year % 100}"
}

private fun bucketTitle(bucket: StatBucket, period: StatPeriod): String = when (period) {
    StatPeriod.WEEK -> {
        val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        "${fmt.format(bucket.start)} – ${fmt.format(bucket.endExclusive.minusDays(1))}"
    }
    StatPeriod.MONTH -> DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()).format(bucket.start)
    StatPeriod.QUARTER -> "Q${(bucket.start.monthValue - 1) / 3 + 1} ${bucket.start.year}"
    StatPeriod.YEAR -> "${bucket.start.year}"
}

private fun bucketCount(period: StatPeriod): Int = when (period) {
    StatPeriod.WEEK -> 12
    StatPeriod.MONTH -> 12
    StatPeriod.QUARTER -> 8
    StatPeriod.YEAR -> 5
}

// ---------------------------------------------------------------- shared pieces

@Composable
private fun ProfileCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, Outline),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = OnSurface, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
}

@Composable
private fun ChipRow(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Surface(
                onClick = { onSelect(i) },
                shape = RoundedCornerShape(16.dp),
                color = if (selected) Primary else CardHigh,
            ) {
                Text(
                    text = label,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else OnSurface,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, delta: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, color = Muted, fontSize = 12.sp)
        Text(value, color = OnSurface, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = delta ?: "no change",
            color = when {
                delta == null -> Muted.copy(alpha = 0.6f)
                delta.startsWith("↑") -> UpGreen
                else -> Muted
            },
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

/** Workouts / Duration / Volume / Sets / Reps tiles with the change from [previous]. */
@Composable
private fun TotalsGrid(current: WorkoutTotals, previous: WorkoutTotals, unit: WeightUnit, metrics: List<StatMetric> = StatMetric.entries) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { metric ->
                    StatTile(
                        label = metric.label,
                        value = formatStatValue(current, metric, unit),
                        delta = formatStatDelta(current, previous, metric, unit),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Bars for [buckets], oldest first. Tapping a bar selects it. */
@Composable
private fun BucketBarChart(
    buckets: List<StatBucket>,
    period: StatPeriod,
    metric: StatMetric,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val max = buckets.maxOfOrNull { it.totals.value(metric) }?.takeIf { it > 0.0 } ?: 1.0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        buckets.forEachIndexed { i, bucket ->
            val fraction = (bucket.totals.value(metric) / max).toFloat()
            val selected = i == selectedIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.72f)
                            .fillMaxHeight(fraction.coerceIn(0.02f, 1f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (selected) Primary else Muted.copy(alpha = 0.3f)),
                    )
                }
                Text(
                    text = bucketLabel(bucket, period),
                    color = if (selected) Primary else Muted,
                    fontSize = 9.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- radar chart

private val RADAR_AXES = listOf(
    MuscleGroup.CHEST, MuscleGroup.CORE, MuscleGroup.SHOULDERS, MuscleGroup.ARMS, MuscleGroup.LEGS, MuscleGroup.BACK,
)

/** Hexagon radar of working sets per muscle group, [current] over [previous]. */
@Composable
private fun MuscleRadarChart(
    current: Map<MuscleGroup, Double>,
    previous: Map<MuscleGroup, Double>,
    currentLabel: String,
    previousLabel: String,
) {
    val measurer = rememberTextMeasurer()
    val grid = Muted.copy(alpha = 0.28f)
    val labelColor = Muted
    val currentColor = Primary
    val previousColor = Muted
    val max = (current.values + previous.values).maxOrNull()?.takeIf { it > 0.0 } ?: 1.0

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width * 0.34f, size.height * 0.40f)
        // First axis points up-right, then clockwise, so the hexagon has a flat top like the labels expect.
        fun vertex(i: Int, r: Float): Offset {
            val a = Math.toRadians(-60.0 + 60.0 * i)
            return Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
        }
        fun polygon(r: (Int) -> Float) = Path().apply {
            RADAR_AXES.indices.forEach { i -> vertex(i, r(i)).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
            close()
        }

        for (ring in 1..5) drawPath(polygon { radius * ring / 5f }, grid, style = Stroke(1.dp.toPx()))
        RADAR_AXES.indices.forEach { i -> drawLine(grid, center, vertex(i, radius), strokeWidth = 1.dp.toPx()) }

        fun series(values: Map<MuscleGroup, Double>, color: Color) {
            if (values.values.none { it > 0.0 }) return
            val path = polygon { i -> radius * ((values[RADAR_AXES[i]] ?: 0.0) / max).toFloat() }
            drawPath(path, color.copy(alpha = 0.22f))
            drawPath(path, color, style = Stroke(2.dp.toPx()))
        }
        series(previous, previousColor)
        series(current, currentColor)

        RADAR_AXES.forEachIndexed { i, group ->
            val layout = measurer.measure(group.displayName, TextStyle(color = labelColor, fontSize = 11.sp))
            val p = vertex(i, radius + 14.dp.toPx())
            val x = when (i) {
                0, 1, 2 -> p.x
                else -> p.x - layout.size.width
            }
            drawText(layout, topLeft = Offset(x.coerceIn(0f, size.width - layout.size.width), p.y - layout.size.height / 2f))
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(currentColor, currentLabel)
        LegendDot(previousColor, previousLabel)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

// ---------------------------------------------------------------- body heatmap

/**
 * The figure's region for each tracked muscle. The figure (github.com/binarybeastt/musclemap, vendored in
 * com.fittribe.musclemap) draws the lats and the mid back as one region, so both map to it.
 */
private fun BodyMuscle.region(): Muscle = when (this) {
    BodyMuscle.CHEST -> Muscle.Chest
    BodyMuscle.SHOULDERS -> Muscle.Deltoids
    BodyMuscle.BICEPS -> Muscle.Biceps
    BodyMuscle.TRICEPS -> Muscle.Triceps
    BodyMuscle.FOREARMS -> Muscle.Forearm
    BodyMuscle.ABS -> Muscle.Abs
    BodyMuscle.OBLIQUES -> Muscle.Obliques
    BodyMuscle.LATS, BodyMuscle.UPPER_BACK -> Muscle.UpperBack
    BodyMuscle.TRAPS -> Muscle.Trapezius
    BodyMuscle.LOWER_BACK -> Muscle.LowerBack
    BodyMuscle.GLUTES -> Muscle.Gluteal
    BodyMuscle.QUADS -> Muscle.Quadriceps
    BodyMuscle.HAMSTRINGS -> Muscle.Hamstring
    BodyMuscle.ADDUCTORS -> Muscle.Adductors
    BodyMuscle.CALVES -> Muscle.Calves
}

/** Each figure region's share (0..1) of the most-trained region's sets; every tracked region is present. */
internal fun bodyRegionShares(load: Map<BodyMuscle, Double>): Map<Muscle, Float> {
    val sets = BodyMuscle.entries.groupBy { it.region() }.mapValues { (_, muscles) -> muscles.maxOf { load[it] ?: 0.0 } }
    val max = sets.values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    return sets.mapValues { (it.value / max).toFloat().coerceIn(0f, 1f) }
}

/** Front and back figures; a muscle is coloured by its share of the most-trained muscle's sets. */
@Composable
internal fun BodyHeatmap(load: Map<BodyMuscle, Double>) {
    val skin = lerp(CardBg, Muted, 0.16f)
    val idle = lerp(CardBg, Muted, 0.32f)
    val hot = Primary
    // Every tracked region gets an explicit colour, so the adductors don't inherit the hamstrings' fill.
    val highlights = remember(load, idle, hot) {
        bodyRegionShares(load).mapValues { (region, share) ->
            MuscleHighlight.solid(region, if (share <= 0f) idle else lerp(idle, hot, 0.25f + 0.75f * share))
        }
    }
    val outline = CardBg
    val style = remember(skin, idle, outline) {
        BodyViewStyle(defaultFillColor = skin, strokeColor = outline, strokeWidth = 1f, headColor = skin, hairColor = idle)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
    ) {
        listOf(BodySide.Front, BodySide.Back).forEach { side ->
            BodyView(
                gender = BodyGender.Male,
                side = side,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                highlights = highlights,
                style = style,
            )
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Text("Front", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Text("Back", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Less", color = Muted, fontSize = 10.sp)
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { share ->
            Box(
                Modifier
                    .size(width = 22.dp, height = 8.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (share == 0f) idle else lerp(idle, hot, 0.25f + 0.75f * share)),
            )
        }
        Text("More sets", color = Muted, fontSize = 10.sp)
    }
}

/** Muscles by working sets, most trained first. */
@Composable
private fun MuscleSetsList(load: Map<BodyMuscle, Double>, limit: Int = Int.MAX_VALUE) {
    val rows = load.entries.filter { it.value > 0.0 }.sortedByDescending { it.value }.take(limit)
    if (rows.isEmpty()) {
        Text("No working sets logged in this period.", color = Muted, fontSize = 12.sp)
        return
    }
    val max = rows.first().value
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Muscle", color = Muted, fontSize = 12.sp)
        Text("Sets", color = Muted, fontSize = 12.sp)
    }
    rows.forEach { (muscle, sets) ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(muscle.displayName, color = OnSurface, fontSize = 13.sp, modifier = Modifier.width(92.dp), maxLines = 1)
            Box(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .fillMaxWidth((sets / max).toFloat().coerceIn(0.03f, 1f))
                        .height(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Primary),
                )
            }
            Text(
                text = if (sets % 1.0 == 0.0) "${sets.toInt()}" else String.format(Locale.US, "%.1f", sets),
                color = OnSurface, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(40.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- calendar pieces

@Composable
private fun WeekdayHeader(first: DayOfWeek) {
    Row(Modifier.fillMaxWidth()) {
        (0 until 7).forEach { i ->
            Text(
                text = first.plus(i.toLong()).getDisplayName(DateTextStyle.SHORT, Locale.getDefault()),
                color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One month as a grid of days; days in [days] are filled in. [showTitles] writes the workout's name under its day. */
@Composable
private fun MonthGrid(
    month: YearMonth,
    days: Map<LocalDate, List<WorkoutSession>>,
    today: LocalDate,
    first: DayOfWeek,
    showTitles: Boolean,
    onDayClick: (List<WorkoutSession>) -> Unit,
) {
    val offset = (month.atDay(1).dayOfWeek.value - first.value + 7) % 7
    val cells: List<LocalDate?> = List(offset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column(verticalArrangement = Arrangement.spacedBy(if (showTitles) 2.dp else 6.dp)) {
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i ->
                    val date = week.getOrNull(i)
                    val sessions = date?.let { days[it] }.orEmpty()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .then(if (sessions.isNotEmpty()) Modifier.clip(RoundedCornerShape(8.dp)).clickable { onDayClick(sessions) } else Modifier)
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (sessions.isNotEmpty()) Primary else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (date != null) {
                                Text(
                                    text = "${date.dayOfMonth}",
                                    color = when {
                                        sessions.isNotEmpty() -> MaterialTheme.colorScheme.onPrimary
                                        date == today -> Primary
                                        date.isAfter(today) -> Muted.copy(alpha = 0.55f)
                                        else -> OnSurface
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = if (sessions.isNotEmpty() || date == today) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                        if (showTitles) {
                            Text(
                                text = when {
                                    sessions.isEmpty() -> ""
                                    sessions.size == 1 -> sessions[0].title
                                    else -> "${sessions.size} workouts"
                                },
                                color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Clip,
                                modifier = Modifier.padding(horizontal = 1.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakRow(streak: Int, restDays: Int?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            AppIcons.Flame(modifier = Modifier.size(20.dp), tint = if (streak > 0) StreakOrange else Muted)
            Spacer(Modifier.width(6.dp))
            Text("$streak week streak", color = OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.width(1.dp).height(28.dp).background(Outline))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            AppIcons.Moon(modifier = Modifier.size(18.dp), tint = Primary)
            Spacer(Modifier.width(6.dp))
            Text(
                text = when (restDays) {
                    null -> "No workouts yet"
                    1 -> "1 rest day"
                    else -> "$restDays rest days"
                },
                color = OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ---------------------------------------------------------------- Profile tab

@Composable
internal fun ProfileScreen(viewModel: LiftTrackerViewModel) {
    val zone = remember { ZoneId.systemDefault() }
    val first = remember { firstDayOfWeek() }
    val today = LocalDate.now(zone)
    val unit = viewModel.weightUnit
    val period = viewModel.profilePeriod
    val metric = viewModel.profileMetric

    // Cached in the view model: recomputed only when the history, library, period or day changes.
    val dashboard = viewModel.profileDashboard(today, zone, first, bucketCount(period))
    val buckets = dashboard.buckets
    // The extra, oldest bucket is only there so the first bar has something to compare with.
    val shown = dashboard.shown
    var selected by remember(period) { mutableIntStateOf(shown.lastIndex) }
    val bucket = shown[selected.coerceIn(0, shown.lastIndex)]
    val previous = buckets[selected.coerceIn(0, shown.lastIndex)]
    val muscleLoad = remember(dashboard, bucket) { dashboard.muscleLoad(bucket) }
    val all = dashboard.all

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "overview") {
            ProfileCard {
                Text("Your training", color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (all.workouts == 0) "No workouts logged yet. Finish a workout and your numbers show up here."
                    else "${all.workouts} workouts · ${formatStatDuration(all.durationSeconds)} · ${formatStatVolume(all.volumeKg, unit)} lifted",
                    color = OnSurface, fontSize = 13.sp,
                )
                StreakRow(dashboard.weekStreak, dashboard.restDays)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileNavButton("Statistics", "Muscles & monthly reports", Modifier.weight(1f), { AppIcons.Analytics(Modifier.size(20.dp), tint = Primary) }) {
                        viewModel.openProfileLayer(ProfileLayer.STATISTICS)
                    }
                    ProfileNavButton("Calendar", "Workout days & streak", Modifier.weight(1f), { AppIcons.Calendar(Modifier.size(20.dp), tint = Primary) }) {
                        viewModel.openProfileLayer(ProfileLayer.CALENDAR)
                    }
                }
            }
        }

        item(key = "dashboard") {
            ProfileCard {
                SectionTitle("Workout summary")
                ChipRow(StatPeriod.entries.map { it.adjective }, StatPeriod.entries.indexOf(period)) { viewModel.profilePeriod = StatPeriod.entries[it] }
                Column {
                    Text(bucketTitle(bucket, period), color = Muted, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(formatStatValue(bucket.totals, metric, unit), color = OnSurface, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            text = (formatStatDelta(bucket.totals, previous.totals, metric, unit) ?: "no change") + " vs previous ${period.label.lowercase(Locale.US)}",
                            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
                BucketBarChart(shown, period, metric, selected.coerceIn(0, shown.lastIndex)) { selected = it }
                ChipRow(StatMetric.entries.map { it.label }, StatMetric.entries.indexOf(metric)) { viewModel.profileMetric = StatMetric.entries[it] }
                TotalsGrid(bucket.totals, previous.totals, unit)
            }
        }

        item(key = "heatmap") {
            ProfileCard {
                SectionTitle("Muscles worked")
                Text(bucketTitle(bucket, period) + " · working sets per muscle", color = Muted, fontSize = 12.sp)
                BodyHeatmap(muscleLoad)
                MuscleSetsList(muscleLoad, limit = 6)
            }
        }
    }
}

@Composable
private fun ProfileNavButton(title: String, subtitle: String, modifier: Modifier, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = CardHigh, border = BorderStroke(1.dp, Outline), modifier = modifier) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            icon()
            Column(Modifier.weight(1f)) {
                Text(title, color = OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(subtitle, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AppIcons.ChevronRight(modifier = Modifier.size(14.dp), tint = Muted)
        }
    }
}

// ---------------------------------------------------------------- layers

/** Full-screen page with a back bar, opened from the Profile tab. */
@Composable
private fun ProfileLayerScaffold(
    title: String,
    onBack: () -> Unit,
    fixedHeader: (@Composable () -> Unit)? = null,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    BackHandler(enabled = true) { onBack() }
    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Surface(color = CardBg, tonalElevation = 6.dp) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Chip("← Back", onClick = onBack)
                        Text(title, color = OnSurface, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    fixedHeader?.invoke()
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
internal fun ProfileLayerHost(viewModel: LiftTrackerViewModel) {
    when (viewModel.profileLayer) {
        ProfileLayer.STATISTICS -> StatisticsLayerScreen(viewModel)
        ProfileLayer.MONTHLY_REPORT -> MonthlyReportLayerScreen(viewModel)
        ProfileLayer.CALENDAR -> CalendarLayerScreen(viewModel)
        null -> Unit
    }
}

private val STAT_RANGES = listOf(7L to "Last 7 days", 30L to "Last 30 days", 90L to "Last 3 months", 365L to "Last year")

@Composable
private fun StatisticsLayerScreen(viewModel: LiftTrackerViewModel) {
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone)
    val sessions = viewModel.workoutHistory.toList()
    val unit = viewModel.weightUnit
    var rangeIndex by remember { mutableIntStateOf(1) }
    val rangeDays = STAT_RANGES[rangeIndex].first

    val end = today.plusDays(1)
    val start = end.minusDays(rangeDays)
    val currentSessions = WorkoutStats.between(sessions, start, end, zone)
    val previousSessions = WorkoutStats.between(sessions, start.minusDays(rangeDays), start, zone)
    val current = WorkoutStats.groupLoad(WorkoutStats.muscleLoad(currentSessions, viewModel::muscleLoadOf))
    val previous = WorkoutStats.groupLoad(WorkoutStats.muscleLoad(previousSessions, viewModel::muscleLoadOf))
    val months = WorkoutStats.workoutDays(sessions, zone).keys.map { YearMonth.from(it) }.distinct().sortedDescending()
    val monthFmt = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }

    ProfileLayerScaffold(title = "Statistics", onBack = { viewModel.closeProfileLayer() }) {
        item(key = "distribution") {
            ProfileCard {
                SectionTitle("Muscle distribution")
                ChipRow(STAT_RANGES.map { it.second }, rangeIndex) { rangeIndex = it }
                Text("Working sets per muscle group, against the ${rangeDays} days before.", color = Muted, fontSize = 12.sp)
                MuscleRadarChart(current, previous, "Current", "Previous")
                TotalsGrid(
                    WorkoutStats.totals(currentSessions), WorkoutStats.totals(previousSessions), unit,
                    metrics = listOf(StatMetric.WORKOUTS, StatMetric.DURATION, StatMetric.VOLUME, StatMetric.SETS),
                )
            }
        }
        item(key = "reports_title") { SectionTitle("Monthly reports") }
        if (months.isEmpty()) {
            item(key = "reports_empty") { Text("A report appears for every month with a logged workout.", color = Muted, fontSize = 12.sp) }
        }
        items(months, key = { "report_$it" }) { month ->
            val totals = WorkoutStats.totals(WorkoutStats.between(sessions, month.atDay(1), month.plusMonths(1).atDay(1), zone))
            Surface(
                onClick = { viewModel.openMonthlyReport(month.atDay(1)) },
                shape = RoundedCornerShape(12.dp), color = CardBg, border = BorderStroke(1.dp, Outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(monthFmt.format(month.atDay(1)), color = OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${totals.workouts} workouts · ${formatStatDuration(totals.durationSeconds)} · ${formatStatVolume(totals.volumeKg, unit)}",
                            color = Muted, fontSize = 12.sp,
                        )
                    }
                    AppIcons.ChevronRight(modifier = Modifier.size(16.dp), tint = Muted)
                }
            }
        }
    }
}

@Composable
private fun MonthlyReportLayerScreen(viewModel: LiftTrackerViewModel) {
    val zone = remember { ZoneId.systemDefault() }
    val first = remember { firstDayOfWeek() }
    val today = LocalDate.now(zone)
    val sessions = viewModel.workoutHistory.toList()
    val unit = viewModel.weightUnit
    val monthStart = viewModel.reportMonth
    val month = YearMonth.from(monthStart)
    var metric by remember { mutableStateOf(StatMetric.WORKOUTS) }

    // 12 months ending with the report's month, plus one more before them for the comparison.
    val buckets = WorkoutStats.buckets(sessions, StatPeriod.MONTH, 13, monthStart, zone, first)
    val shown = buckets.drop(1)
    val current = shown.last()
    val previous = buckets[buckets.lastIndex - 1]
    val monthSessions = WorkoutStats.between(sessions, current.start, current.endExclusive, zone)
    val previousSessions = WorkoutStats.between(sessions, previous.start, previous.endExclusive, zone)
    val muscleLoad = WorkoutStats.muscleLoad(monthSessions, viewModel::muscleLoadOf)
    val previousLoad = WorkoutStats.muscleLoad(previousSessions, viewModel::muscleLoadOf)
    val days = WorkoutStats.workoutDays(sessions, zone)
    val monthName = month.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault())
    val titleFmt = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }
    val currentMonthStart = today.withDayOfMonth(1)

    ProfileLayerScaffold(title = "$monthName Report", onBack = { viewModel.closeProfileLayer() }) {
        item(key = "chart") {
            ProfileCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(titleFmt.format(monthStart), color = OnSurface, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(formatStatValue(current.totals, metric, unit), color = OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(formatStatDelta(current.totals, previous.totals, metric, unit) ?: "no change", color = Muted, fontSize = 12.sp)
                        }
                    }
                    Chip("‹") { viewModel.openMonthlyReport(monthStart.minusMonths(1)) }
                    Spacer(Modifier.width(6.dp))
                    Chip("›", enabled = monthStart.isBefore(currentMonthStart)) { viewModel.openMonthlyReport(monthStart.plusMonths(1)) }
                }
                BucketBarChart(shown, StatPeriod.MONTH, metric, shown.lastIndex) { viewModel.openMonthlyReport(shown[it].start) }
                ChipRow(StatMetric.entries.map { it.label }, StatMetric.entries.indexOf(metric)) { metric = StatMetric.entries[it] }
            }
        }
        item(key = "summary") {
            ProfileCard {
                SectionTitle("Summary")
                TotalsGrid(current.totals, previous.totals, unit)
            }
        }
        item(key = "days") {
            ProfileCard {
                SectionTitle("Workout days log")
                val streak = WorkoutStats.weekStreak(days.keys.filter { it.isBefore(current.endExclusive) }, minOf(today, current.endExclusive.minusDays(1)), first)
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIcons.Flame(modifier = Modifier.size(34.dp), tint = if (streak > 0) StreakOrange else Muted)
                    Text("$streak Week Streak", color = OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                WeekdayHeader(first)
                MonthGrid(month, days, today, first, showTitles = false) { viewModel.openWorkoutSessionDetailLayer(it.first().id) }
            }
        }
        item(key = "radar") {
            ProfileCard {
                SectionTitle("Muscle distribution")
                MuscleRadarChart(
                    WorkoutStats.groupLoad(muscleLoad), WorkoutStats.groupLoad(previousLoad),
                    titleFmt.format(monthStart), titleFmt.format(previous.start),
                )
            }
        }
        item(key = "body") {
            ProfileCard {
                SectionTitle("Main muscle groups")
                BodyHeatmap(muscleLoad)
                MuscleSetsList(muscleLoad)
            }
        }
    }
}

@Composable
private fun CalendarLayerScreen(viewModel: LiftTrackerViewModel) {
    val zone = remember { ZoneId.systemDefault() }
    val first = remember { firstDayOfWeek() }
    val today = LocalDate.now(zone)
    val days = WorkoutStats.workoutDays(viewModel.workoutHistory.toList(), zone)
    val thisMonth = YearMonth.from(today)
    val firstMonth = days.keys.minOrNull()?.let { YearMonth.from(it) }?.takeIf { it.isBefore(thisMonth) } ?: thisMonth
    val months = generateSequence(firstMonth) { it.plusMonths(1) }.takeWhile { !it.isAfter(thisMonth) }.toList()
    val titleFmt = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }
    // Opens on the current month, at the end of the list.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = months.lastIndex)

    ProfileLayerScaffold(
        title = "Calendar",
        onBack = { viewModel.closeProfileLayer() },
        listState = listState,
        fixedHeader = {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StreakRow(WorkoutStats.weekStreak(days.keys, today, first), WorkoutStats.restDays(days.keys, today))
                WeekdayHeader(first)
            }
        },
    ) {
        items(months, key = { "month_$it" }) { month ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(titleFmt.format(month.atDay(1)), color = OnSurface, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                MonthGrid(month, days, today, first, showTitles = true) { viewModel.openWorkoutSessionDetailLayer(it.first().id) }
            }
        }
    }
}
