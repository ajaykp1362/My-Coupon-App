package com.example.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class CouponRepository(
    private val couponDao: CouponDao,
    private val context: Context
) {
    val allCoupons: Flow<List<Coupon>> = couponDao.getAllCoupons()

    fun getCouponById(id: Long): Flow<Coupon?> = couponDao.getCouponById(id)

    suspend fun getCouponByIdOnce(id: Long): Coupon? = couponDao.getCouponByIdOnce(id)

    suspend fun saveCoupon(coupon: Coupon): Long = withContext(Dispatchers.IO) {
        if (coupon.id == 0L) {
            couponDao.insertCoupon(coupon.copy(createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
        } else {
            couponDao.updateCoupon(coupon.copy(updatedAt = System.currentTimeMillis()))
            coupon.id
        }
    }

    suspend fun updateCoupon(coupon: Coupon) = withContext(Dispatchers.IO) {
        couponDao.updateCoupon(coupon.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun toggleUsedStatus(id: Long) = withContext(Dispatchers.IO) {
        val coupon = couponDao.getCouponByIdOnce(id) ?: return@withContext
        val newUsed = !coupon.isUsed
        val newStatus = if (newUsed) "USED" else "ACTIVE"
        couponDao.updateCoupon(coupon.copy(isUsed = newUsed, status = newStatus, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteCouponById(id: Long) = withContext(Dispatchers.IO) {
        val coupon = couponDao.getCouponByIdOnce(id)
        if (coupon?.screenshotPath != null) {
            try {
                File(coupon.screenshotPath).delete()
            } catch (_: Exception) {}
        }
        couponDao.deleteCouponById(id)
    }

    suspend fun deleteAllCoupons() = withContext(Dispatchers.IO) {
        val coupons = couponDao.getAllCouponsOnce()
        for (c in coupons) {
            if (c.screenshotPath != null) {
                try {
                    File(c.screenshotPath).delete()
                } catch (_: Exception) {}
            }
        }
        couponDao.deleteAllCoupons()
    }

    suspend fun saveScreenshotLocally(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val screenshotsDir = File(context.filesDir, "coupon_screenshots").apply {
                if (!exists()) mkdirs()
            }
            val destinationFile = File(screenshotsDir, "screenshot_${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            destinationFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun exportCouponsToJson(): String = withContext(Dispatchers.IO) {
        val coupons = couponDao.getAllCouponsOnce()
        val jsonArray = JSONArray()
        for (c in coupons) {
            val obj = JSONObject().apply {
                put("merchantName", c.merchantName)
                put("offerTitle", c.offerTitle)
                put("offerDescription", c.offerDescription)
                put("couponCode", c.couponCode)
                put("discountValue", c.discountValue)
                put("expiryDate", c.expiryDate)
                put("hasExpiry", c.hasExpiry)
                put("category", c.category)
                put("minimumOrderValue", c.minimumOrderValue)
                put("terms", c.terms)
                put("redemptionUrl", c.redemptionUrl)
                put("status", c.status)
                put("isUsed", c.isUsed)
                put("createdAt", c.createdAt)
            }
            jsonArray.put(obj)
        }
        jsonArray.toString(2)
    }

    suspend fun importCouponsFromJson(jsonText: String): Int = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray(jsonText)
            val coupons = mutableListOf<Coupon>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val coupon = Coupon(
                    merchantName = obj.optString("merchantName", "Merchant"),
                    offerTitle = obj.optString("offerTitle", "Offer"),
                    offerDescription = obj.optString("offerDescription", ""),
                    couponCode = obj.optString("couponCode", ""),
                    discountValue = obj.optString("discountValue", ""),
                    expiryDate = obj.optString("expiryDate", ""),
                    hasExpiry = obj.optBoolean("hasExpiry", true),
                    category = obj.optString("category", "Shopping"),
                    minimumOrderValue = obj.optString("minimumOrderValue", ""),
                    terms = obj.optString("terms", ""),
                    redemptionUrl = obj.optString("redemptionUrl", ""),
                    status = obj.optString("status", "ACTIVE"),
                    isUsed = obj.optBoolean("isUsed", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = System.currentTimeMillis()
                )
                coupons.add(coupon)
            }
            if (coupons.isNotEmpty()) {
                couponDao.insertCoupons(coupons)
            }
            coupons.size
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }
}
