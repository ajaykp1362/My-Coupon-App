package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Entity(tableName = "coupons")
data class Coupon(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val merchantName: String,
    val offerTitle: String,
    val offerDescription: String = "",
    val couponCode: String = "",
    val discountValue: String = "",
    val expiryDate: String = "",
    val hasExpiry: Boolean = true,
    val category: String = "Shopping",
    val minimumOrderValue: String = "",
    val terms: String = "",
    val redemptionUrl: String = "",
    val screenshotPath: String? = null,
    val status: String = "ACTIVE", // ACTIVE, USED, EXPIRED
    val isUsed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class CouponStatus {
    ACTIVE,
    EXPIRES_TODAY,
    EXPIRES_SOON,
    EXPIRED,
    NO_EXPIRY,
    USED
}

fun Coupon.getEffectiveStatus(): CouponStatus {
    if (isUsed || status == "USED") {
        return CouponStatus.USED
    }
    if (!hasExpiry || expiryDate.isBlank()) {
        return CouponStatus.NO_EXPIRY
    }
    val parsedDate = parseExpiryDate(expiryDate) ?: return CouponStatus.ACTIVE

    val now = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val expCal = Calendar.getInstance().apply {
        time = parsedDate
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    val diffDays = ((expCal - now) / (1000 * 60 * 60 * 24)).toInt()

    return when {
        diffDays < 0 -> CouponStatus.EXPIRED
        diffDays == 0 -> CouponStatus.EXPIRES_TODAY
        diffDays in 1..3 -> CouponStatus.EXPIRES_SOON
        else -> CouponStatus.ACTIVE
    }
}

fun Coupon.getStatusBadgeText(): String {
    if (isUsed || status == "USED") return "Used"
    if (!hasExpiry || expiryDate.isBlank()) return "No expiry"

    val parsedDate = parseExpiryDate(expiryDate) ?: return "Expires: $expiryDate"

    val now = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val expCal = Calendar.getInstance().apply {
        time = parsedDate
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    val diffDays = ((expCal - now) / (1000 * 60 * 60 * 24)).toInt()

    return when {
        diffDays < 0 -> "Expired"
        diffDays == 0 -> "Expires today"
        diffDays == 1 -> "Expires tomorrow"
        diffDays in 2..30 -> "Expires in $diffDays days"
        else -> "Expires: $expiryDate"
    }
}

private val dateFormats = listOf(
    SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH),
    SimpleDateFormat("d MMM yyyy", Locale.ENGLISH),
    SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH),
    SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH),
    SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH),
    SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH),
    SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH),
    SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH),
    SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH)
)

fun parseExpiryDate(text: String): Date? {
    if (text.isBlank()) return null
    val clean = text.trim()
    for (fmt in dateFormats) {
        try {
            fmt.isLenient = true
            val date = fmt.parse(clean)
            if (date != null) return date
        } catch (_: Exception) {}
    }
    return null
}
