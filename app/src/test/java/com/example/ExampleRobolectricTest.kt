package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.GeminiCouponExtractor
import com.example.data.Coupon
import com.example.data.CouponStatus
import com.example.data.getEffectiveStatus
import com.example.data.parseExpiryDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Coupon Wallet", appName)
  }

  @Test
  fun `coupon status calculation for active and no expiry`() {
    val activeCoupon = Coupon(
      merchantName = "District",
      offerTitle = "₹200 OFF",
      expiryDate = "30 Dec 2030",
      hasExpiry = true
    )
    assertEquals(CouponStatus.ACTIVE, activeCoupon.getEffectiveStatus())

    val noExpiryCoupon = Coupon(
      merchantName = "Swiggy",
      offerTitle = "Flat 20% OFF",
      hasExpiry = false
    )
    assertEquals(CouponStatus.NO_EXPIRY, noExpiryCoupon.getEffectiveStatus())

    val usedCoupon = Coupon(
      merchantName = "Amazon",
      offerTitle = "₹50 cash",
      isUsed = true
    )
    assertEquals(CouponStatus.USED, usedCoupon.getEffectiveStatus())

    val expiredCoupon = Coupon(
      merchantName = "Flipkart",
      offerTitle = "₹100 OFF",
      expiryDate = "01 Jan 2020",
      hasExpiry = true
    )
    assertEquals(CouponStatus.EXPIRED, expiredCoupon.getEffectiveStatus())
  }

  @Test
  fun `fallback extraction from text extracts code and merchant`() {
    val sampleText = "Get ₹200 OFF on perfume orders above ₹999. Coupon code: PERF200. Valid until 30 November 2026. Valid on District."
    val extracted = GeminiCouponExtractor.fallbackExtractionFromText(sampleText)

    assertEquals("District", extracted.merchantName)
    assertEquals("PERF200", extracted.couponCode)
    assertEquals("Beauty", extracted.category)
    assertEquals("₹999", extracted.minimumOrderValue)
    assertTrue(extracted.offerTitle.contains("₹200"))
  }
}
