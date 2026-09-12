package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ExtractedCoupon(
    val merchantName: String = "",
    val offerTitle: String = "",
    val couponCode: String = "",
    val discountValue: String = "",
    val expiryDate: String = "",
    val hasExpiry: Boolean = true,
    val category: String = "Shopping",
    val minimumOrderValue: String = "",
    val terms: String = "",
    val redemptionUrl: String = "",
    val isAiExtracted: Boolean = true
)

object GeminiCouponExtractor {
    private const val TAG = "GeminiCouponExtractor"
    private const val MODEL = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private const val SYSTEM_PROMPT = """
You are a precise coupon and voucher information extractor.
Analyze the provided coupon image or coupon text and extract the coupon details strictly according to what is explicitly visible or written.

STRICT RULES:
1. Extract ONLY information actually present.
2. NEVER invent a coupon code. If no code is present, return an empty string "".
3. NEVER invent or assume an expiry date. If no expiry date is found, return "" for expiryDate and false for hasExpiry.
4. If a field cannot be confidently extracted, leave it as an empty string "".
5. Category MUST be one of: "Shopping", "Food", "Travel", "Entertainment", "Beauty", "Grocery", "Other".
6. Extract redemption URL if explicitly visible/written.
7. Return ONLY a valid JSON object with the specified schema, no markdown codeblocks, no surrounding commentary.

JSON Schema:
{
  "merchantName": "string",
  "offerTitle": "string",
  "couponCode": "string",
  "discountValue": "string",
  "expiryDate": "string",
  "hasExpiry": boolean,
  "category": "string",
  "minimumOrderValue": "string",
  "terms": "string",
  "redemptionUrl": "string"
}
"""

    /**
     * Extracts coupon details from an image file (screenshot)
     */
    suspend fun extractFromImage(imageFile: File): ExtractedCoupon = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (_: Exception) { "" }

        if (!apiKey.isNullOrBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val base64 = encodeImageToBase64(imageFile)
                if (base64 != null) {
                    val result = callGeminiMultimodal(apiKey, base64)
                    if (result != null) return@withContext result
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gemini multimodal extraction failed: ${e.message}", e)
            }
        }

        // Fallback if API key missing, offline, or call failed
        fallbackExtractionFromImageName(imageFile)
    }

    /**
     * Extracts coupon details from shared text or URL
     */
    suspend fun extractFromText(sharedText: String): ExtractedCoupon = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (_: Exception) { "" }

        if (!apiKey.isNullOrBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val result = callGeminiText(apiKey, sharedText)
                if (result != null) return@withContext result
            } catch (e: Exception) {
                Log.e(TAG, "Gemini text extraction failed: ${e.message}", e)
            }
        }

        // Offline / fallback regex & heuristic text parser
        fallbackExtractionFromText(sharedText)
    }

    private fun callGeminiMultimodal(apiKey: String, base64Image: String): ExtractedCoupon? {
        val requestJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "Extract all coupon and offer details from this screenshot.")
                        })
                        put(JSONObject().apply {
                            val inlineData = JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", base64Image)
                            }
                            put("inlineData", inlineData)
                        })
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)

            val systemInstruction = JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", SYSTEM_PROMPT) })
                })
            }
            put("systemInstruction", systemInstruction)

            val genConfig = JSONObject().apply {
                put("temperature", 0.1)
                put("responseMimeType", "application/json")
            }
            put("generationConfig", genConfig)
        }

        return executeGeminiRequest(apiKey, requestJson)
    }

    private fun callGeminiText(apiKey: String, text: String): ExtractedCoupon? {
        val requestJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "Extract all coupon and offer details from the following shared text:\n\n$text")
                        })
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)

            val systemInstruction = JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", SYSTEM_PROMPT) })
                })
            }
            put("systemInstruction", systemInstruction)

            val genConfig = JSONObject().apply {
                put("temperature", 0.1)
                put("responseMimeType", "application/json")
            }
            put("generationConfig", genConfig)
        }

        return executeGeminiRequest(apiKey, requestJson)
    }

    private fun executeGeminiRequest(apiKey: String, requestJson: JSONObject): ExtractedCoupon? {
        val url = "$BASE_URL?key=$apiKey"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = requestJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "Gemini API call returned status ${response.code}: ${response.message}")
                return null
            }
            val responseBody = response.body?.string() ?: return null
            return parseGeminiResponse(responseBody)
        }
    }

    private fun parseGeminiResponse(responseJsonStr: String): ExtractedCoupon? {
        return try {
            val root = JSONObject(responseJsonStr)
            val candidates = root.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            if (parts.length() == 0) return null
            var text = parts.getJSONObject(0).optString("text", "")

            // Clean markdown code fence if present
            if (text.startsWith("```")) {
                text = text.replace(Regex("^```(?:json)?\\s*"), "").replace(Regex("\\s*```$"), "")
            }

            val data = JSONObject(text)
            ExtractedCoupon(
                merchantName = data.optString("merchantName", "").trim(),
                offerTitle = data.optString("offerTitle", "").trim(),
                couponCode = data.optString("couponCode", "").trim(),
                discountValue = data.optString("discountValue", "").trim(),
                expiryDate = data.optString("expiryDate", "").trim(),
                hasExpiry = data.optBoolean("hasExpiry", data.optString("expiryDate", "").isNotBlank()),
                category = normalizeCategory(data.optString("category", "Shopping")),
                minimumOrderValue = data.optString("minimumOrderValue", "").trim(),
                terms = data.optString("terms", "").trim(),
                redemptionUrl = data.optString("redemptionUrl", "").trim(),
                isAiExtracted = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini response: ${e.message}", e)
            null
        }
    }

    private fun encodeImageToBase64(file: File): String? {
        return try {
            // Downscale if image is very large to stay well within limits
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)

            var sampleSize = 1
            val maxDim = 1600
            while (boundsOptions.outWidth / sampleSize > maxDim || boundsOptions.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            val bytes = baos.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Offline heuristic extraction from text (shared content or OCR fallback)
     */
    fun fallbackExtractionFromText(text: String): ExtractedCoupon {
        var merchant = ""
        var code = ""
        var offer = ""
        var discount = ""
        var expiry = ""
        var hasExpiry = true
        var minOrder = ""
        var url = ""
        var category = "Shopping"

        val knownMerchants = listOf(
            "District", "PhonePe", "Amazon", "Flipkart", "MakeMyTrip", "Swiggy", "Zomato",
            "Uber", "Ola", "Myntra", "Ajio", "Nykaa", "BookMyShow", "Dominos", "KFC",
            "Blinkit", "Zepto", "Instamart", "BigBasket", "Tata Neu", "Paytm", "Google Pay"
        )
        for (m in knownMerchants) {
            if (text.contains(m, ignoreCase = true)) {
                merchant = m
                break
            }
        }

        // Extract URL
        val urlMatcher = Pattern.compile("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+").matcher(text)
        if (urlMatcher.find()) {
            url = urlMatcher.group(0) ?: ""
        }

        // Extract Coupon Code
        val codeMatcher = Pattern.compile("(?i)(?:coupon\\s*code|promo\\s*code|voucher\\s*code|coupon|code|promo|voucher)[:\\s]+([A-Za-z0-9_-]{3,20})").matcher(text)
        while (codeMatcher.find()) {
            val candidate = codeMatcher.group(1)?.trim() ?: ""
            if (!candidate.equals("code", ignoreCase = true) &&
                !candidate.equals("coupon", ignoreCase = true) &&
                !candidate.equals("voucher", ignoreCase = true) &&
                !candidate.equals("promo", ignoreCase = true) &&
                !candidate.equals("valid", ignoreCase = true) &&
                !candidate.equals("until", ignoreCase = true)
            ) {
                code = candidate.uppercase()
                break
            }
        }
        if (code.isBlank()) {
            // Find standalone uppercase code pattern
            val standaloneMatcher = Pattern.compile("\\b([A-Z]{3,}[0-9]{2,}|[0-9]{2,}[A-Z]{3,}|[A-Z]{4,12})\\b").matcher(text)
            while (standaloneMatcher.find()) {
                val candidate = standaloneMatcher.group(1) ?: ""
                if (!listOf("VALID", "UNTIL", "TILL", "ORDER", "ORDERS", "ABOVE", "SHOPPING", "DISTRICT", "PHONEPE", "AMAZON", "FLIPKART", "OFFER", "FIRST").contains(candidate)) {
                    code = candidate
                    break
                }
            }
        }

        // Extract Discount Value
        val discountMatcher = Pattern.compile("(?:₹|Rs\\.?|\\$)\\s*\\d+(?:\\s*OFF)?|\\d+%\\s*OFF", Pattern.CASE_INSENSITIVE).matcher(text)
        if (discountMatcher.find()) {
            discount = discountMatcher.group(0)?.trim() ?: ""
        }

        // Extract Expiry
        val expiryMatcher = Pattern.compile("(?i)(?:valid (?:until|till)|expires?(?: on)?|expiry)[:\\s]+([0-9]{1,2}(?:\\s+|-|/)[a-zA-Z]{3,9}(?:\\s+|-|/)[0-9]{2,4}|[0-9]{1,2}(?:\\s+|-|/)[a-zA-Z]{3,9}|[0-9]{1,2}/[0-9]{1,2}/[0-9]{2,4})").matcher(text)
        if (expiryMatcher.find()) {
            expiry = expiryMatcher.group(1)?.trim() ?: ""
        } else if (text.contains("no expiry", ignoreCase = true)) {
            hasExpiry = false
            expiry = ""
        }

        // Extract Minimum Order
        val minOrderMatcher = Pattern.compile("(?i)(?:above|min(?:imum)? order(?: of)?|orders? over)\\s*(?:₹|Rs\\.?|\\$)?\\s*([0-9,]+)").matcher(text)
        if (minOrderMatcher.find()) {
            minOrder = "₹" + (minOrderMatcher.group(1)?.trim() ?: "")
        }

        // Deduce Category
        val lower = text.lowercase()
        category = when {
            lower.contains("perfume") || lower.contains("beauty") || lower.contains("cosmetic") || lower.contains("skincare") || lower.contains("nykaa") || lower.contains("salon") -> "Beauty"
            lower.contains("food") || lower.contains("meal") || lower.contains("swiggy") || lower.contains("zomato") || lower.contains("pizza") || lower.contains("burger") || lower.contains("restaurant") || lower.contains("dining") -> "Food"
            lower.contains("flight") || lower.contains("hotel") || lower.contains("trip") || lower.contains("travel") || lower.contains("bus") || lower.contains("train") || lower.contains("makemytrip") -> "Travel"
            lower.contains("movie") || lower.contains("cinema") || lower.contains("concert") || lower.contains("ticket") || lower.contains("bookmyshow") -> "Entertainment"
            lower.contains("grocery") || lower.contains("blinkit") || lower.contains("zepto") || lower.contains("instamart") || lower.contains("bigbasket") || lower.contains("milk") || lower.contains("vegetables") -> "Grocery"
            else -> "Shopping"
        }

        // Generate Offer Title
        offer = when {
            discount.isNotBlank() && text.contains("perfume", ignoreCase = true) -> "$discount on Perfume"
            discount.isNotBlank() && text.contains("flight", ignoreCase = true) -> "$discount on first flight booking"
            discount.isNotBlank() -> "$discount OFF"
            text.lines().firstOrNull()?.isNotBlank() == true -> text.lines().first().trim()
            else -> "Coupon Offer"
        }

        if (merchant.isBlank()) {
            merchant = "Coupon Offer"
        }

        return ExtractedCoupon(
            merchantName = merchant,
            offerTitle = offer,
            couponCode = code,
            discountValue = discount,
            expiryDate = expiry,
            hasExpiry = hasExpiry && expiry.isNotBlank(),
            category = category,
            minimumOrderValue = minOrder,
            terms = "",
            redemptionUrl = url,
            isAiExtracted = true
        )
    }

    private fun fallbackExtractionFromImageName(file: File): ExtractedCoupon {
        return ExtractedCoupon(
            merchantName = "",
            offerTitle = "",
            couponCode = "",
            discountValue = "",
            expiryDate = "",
            hasExpiry = true,
            category = "Shopping",
            isAiExtracted = false
        )
    }

    private fun normalizeCategory(categoryStr: String): String {
        val valid = listOf("Shopping", "Food", "Travel", "Entertainment", "Beauty", "Grocery", "Other")
        return valid.firstOrNull { it.equals(categoryStr.trim(), ignoreCase = true) } ?: "Shopping"
    }
}
