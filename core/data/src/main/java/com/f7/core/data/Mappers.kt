package com.f7.core.data

import com.f7.core.data.db.DownloadEntity
import com.f7.core.data.db.ScheduleEntity
import com.f7.core.data.db.SegmentEntity
import com.f7.core.model.DownloadItem
import com.f7.core.model.DownloadKind
import com.f7.core.model.DownloadStatus
import com.f7.core.model.FileCategory
import com.f7.core.model.RepeatRule
import com.f7.core.model.ScheduleItem
import com.f7.core.model.SegmentInfo

fun DownloadEntity.toItem(
    liveSpeed: Long = 0,
    liveConnections: Int = activeConnections,
    liveEta: Long = 0,
): DownloadItem = DownloadItem(
    id = id,
    url = url,
    fileName = fileName,
    mimeType = mimeType,
    category = runCatching { FileCategory.valueOf(category) }.getOrDefault(FileCategory.OTHER),
    kind = runCatching { DownloadKind.valueOf(kind) }.getOrDefault(DownloadKind.HTTP),
    status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.QUEUED),
    totalBytes = totalBytes,
    downloadedBytes = downloadedBytes,
    connections = requestedConnections,
    activeConnections = liveConnections,
    speedBytesPerSec = liveSpeed,
    etaSeconds = liveEta,
    error = error,
    etag = etag,
    lastModified = lastModified,
    supportsResume = supportsResume,
    createdAt = createdAt,
    startedAt = startedAt,
    completedAt = completedAt,
    checksum = checksum,
    checksumType = checksumType,
    savedUri = savedUri,
    displayPath = displayPath,
    sizeIsExact = sizeIsExact,
    storageKind = storageKind,
    priority = priority,
)

fun SegmentEntity.toInfo(): SegmentInfo = SegmentInfo(index, start, end, downloaded)

fun ScheduleEntity.toItem(): ScheduleItem = ScheduleItem(
    id = id,
    url = url,
    fileName = fileName,
    category = category?.let { runCatching { FileCategory.valueOf(it) }.getOrNull() },
    triggerAt = triggerAt,
    repeatRule = runCatching { RepeatRule.valueOf(repeatRule) }.getOrDefault(RepeatRule.ONCE),
    enabled = enabled,
    lastRunAt = lastRunAt,
    nextRunAt = nextRunAt,
)

data class LiveStats(
    val speed: Long,
    val connections: Int,
    val eta: Long,
)


