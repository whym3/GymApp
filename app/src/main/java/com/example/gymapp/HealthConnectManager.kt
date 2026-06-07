package com.example.gymapp

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Thin wrapper around [HealthConnectClient]. Every call guards availability and
 * permissions and fails soft (returns null) so the UI degrades gracefully when
 * Health Connect is missing or access has not been granted.
 */
class HealthConnectManager(private val context: Context) {

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
    )

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    val isAvailable: Boolean
        get() = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    val isUpdateRequired: Boolean
        get() = sdkStatus() == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    private val client: HealthConnectClient?
        get() = if (isAvailable) runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull() else null

    suspend fun grantedPermissions(): Set<String> {
        val c = client ?: return emptySet()
        return runCatching { c.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
    }

    suspend fun hasAllPermissions(): Boolean {
        val granted = grantedPermissions()
        return granted.containsAll(permissions)
    }

    /** True if at least one of our permissions is granted (partial access still useful). */
    suspend fun hasAnyPermission(): Boolean = grantedPermissions().any { it in permissions }

    private fun todayRange(): TimeRangeFilter {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now().atStartOfDay(zone).toInstant()
        return TimeRangeFilter.between(start, Instant.now())
    }

    suspend fun readTodaySteps(): Long? {
        val c = client ?: return null
        return runCatching {
            val resp = c.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = todayRange(),
                )
            )
            resp[StepsRecord.COUNT_TOTAL]
        }.getOrNull()
    }

    /** Steps per day (oldest → newest) for the last [days] days. */
    suspend fun readDailySteps(days: Int = 7): List<Pair<LocalDate, Long>> {
        val c = client ?: return emptyList()
        return runCatching {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now()
            (0 until days).map { offset ->
                val day = today.minusDays((days - 1 - offset).toLong())
                val start = day.atStartOfDay(zone).toInstant()
                val endRaw = day.plusDays(1).atStartOfDay(zone).toInstant()
                val end = if (endRaw.isAfter(Instant.now())) Instant.now() else endRaw
                val resp = c.aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(start, end),
                    )
                )
                day to (resp[StepsRecord.COUNT_TOTAL] ?: 0L)
            }
        }.getOrDefault(emptyList())
    }

    suspend fun readTodayActiveCalories(): Double? {
        val c = client ?: return null
        return runCatching {
            val resp = c.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = todayRange(),
                )
            )
            resp[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
        }.getOrNull()
    }

    suspend fun readLatestWeightKg(): Double? {
        val c = client ?: return null
        return runCatching {
            val resp = c.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.before(Instant.now()),
                    ascendingOrder = false,
                    pageSize = 1,
                )
            )
            resp.records.firstOrNull()?.weight?.inKilograms
        }.getOrNull()
    }

    /** Most recent heart-rate reading available (for live display). */
    suspend fun readLatestHeartRate(): Long? {
        val c = client ?: return null
        return runCatching {
            val resp = c.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.before(Instant.now()),
                    ascendingOrder = false,
                    pageSize = 10,
                )
            )
            resp.records.flatMap { it.samples }.maxByOrNull { it.time }?.beatsPerMinute
        }.getOrNull()
    }

    /** All heart-rate bpm samples recorded between [start] and [end]. */
    suspend fun readHeartRateBetween(start: Instant, end: Instant): List<Long> {
        val c = client ?: return emptyList()
        return runCatching {
            val resp = c.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    ascendingOrder = true,
                )
            )
            resp.records.flatMap { rec ->
                rec.samples.filter { !it.time.isBefore(start) && !it.time.isAfter(end) }.map { it.beatsPerMinute }
            }
        }.getOrDefault(emptyList())
    }

    /** Heart-rate samples (oldest → newest) over the last [days] days, as (time, bpm). */
    suspend fun readHeartRateSamples(days: Long = 7): List<Pair<Instant, Long>> {
        val c = client ?: return emptyList()
        return runCatching {
            val start = Instant.now().minusSeconds(days * 24 * 60 * 60)
            val resp = c.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, Instant.now()),
                    ascendingOrder = true,
                )
            )
            resp.records.flatMap { rec -> rec.samples.map { it.time to it.beatsPerMinute } }.sortedBy { it.first }
        }.getOrDefault(emptyList())
    }

    /** Today's heart-rate samples (oldest → newest) as (time, bpm). */
    suspend fun readTodayHeartRate(): List<Pair<Instant, Long>> {
        val c = client ?: return emptyList()
        return runCatching {
            val resp = c.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = todayRange(),
                    ascendingOrder = true,
                )
            )
            resp.records
                .flatMap { rec -> rec.samples.map { it.time to it.beatsPerMinute } }
                .sortedBy { it.first }
        }.getOrDefault(emptyList())
    }

    /** Bodyweight history (oldest → newest) over the last [days] days. */
    suspend fun readWeightHistoryKg(days: Long = 56): List<Pair<Instant, Double>> {
        val c = client ?: return emptyList()
        return runCatching {
            val start = Instant.now().minusSeconds(days * 24 * 60 * 60)
            val resp = c.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, Instant.now()),
                    ascendingOrder = true,
                )
            )
            resp.records.map { it.time to it.weight.inKilograms }
        }.getOrDefault(emptyList())
    }
}
