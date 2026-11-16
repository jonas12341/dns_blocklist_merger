package de.jonas.dnsblocklistmerger.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val GERMAN = Locale.GERMANY
private val DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", GERMAN)

fun formatNumber(value: Number): String = NumberFormat.getIntegerInstance(GERMAN).format(value)

fun formatKb(bytes: Long): String {
    val nf = NumberFormat.getNumberInstance(GERMAN).apply { maximumFractionDigits = 1 }
    return nf.format(bytes / 1024.0) + " KB"
}

fun formatDateTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DATE_TIME.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

fun formatTimeOfDay(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
