package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.ExtractedCoupon
import com.example.ai.GeminiCouponExtractor
import com.example.data.Coupon
import com.example.data.CouponDatabase
import com.example.data.CouponRepository
import com.example.data.CouponStatus
import com.example.data.getEffectiveStatus
import com.example.data.parseExpiryDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class SortOption(val displayName: String) {
    RECENTLY_ADDED("Recently added"),
    EXPIRING_SOON("Expiring soon"),
    ALPHABETICAL("Alphabetical"),
    CATEGORY("Category")
}

enum class StatusTab(val displayName: String) {
    ACTIVE("Active"),
    EXPIRING_SOON("Expiring Soon"),
    USED("Used"),
    ALL("All")
}

sealed class Screen {
    object Home : Screen()
    data class AddEdit(val couponToEdit: Coupon? = null, val isAiExtracted: Boolean = false) : Screen()
    data class Details(val couponId: Long) : Screen()
}

class CouponViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CouponRepository
    init {
        val db = CouponDatabase.getInstance(application)
        repository = CouponRepository(db.couponDao(), application)
    }

    // UI state
    val allCoupons = repository.allCoupons.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.RECENTLY_ADDED)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _selectedStatusTab = MutableStateFlow(StatusTab.ACTIVE)
    val selectedStatusTab: StateFlow<StatusTab> = _selectedStatusTab.asStateFlow()

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Home)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisMessage = MutableStateFlow<String?>(null)
    val analysisMessage: StateFlow<String?> = _analysisMessage.asStateFlow()

    private val _previewScreenshotPath = MutableStateFlow<String?>(null)
    val previewScreenshotPath: StateFlow<String?> = _previewScreenshotPath.asStateFlow()

    private val _themeMode = MutableStateFlow("SYSTEM") // SYSTEM, LIGHT, DARK
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    // Filtered & Sorted coupons stream
    val displayedCoupons: StateFlow<List<Coupon>> = combine(
        allCoupons,
        _searchQuery,
        _selectedCategory,
        _sortOption,
        _selectedStatusTab
    ) { coupons, query, category, sort, statusTab ->
        var list = coupons

        // 1. Status Filter
        list = when (statusTab) {
            StatusTab.ACTIVE -> list.filter { !it.isUsed && it.status != "USED" }
            StatusTab.EXPIRING_SOON -> list.filter {
                !it.isUsed && (it.getEffectiveStatus() == CouponStatus.EXPIRES_SOON || it.getEffectiveStatus() == CouponStatus.EXPIRES_TODAY)
            }
            StatusTab.USED -> list.filter { it.isUsed || it.status == "USED" }
            StatusTab.ALL -> list
        }

        // 2. Category Filter
        if (category != "All") {
            list = list.filter { it.category.equals(category, ignoreCase = true) }
        }

        // 3. Search Query Filter
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter { c ->
                c.merchantName.lowercase().contains(q) ||
                c.offerTitle.lowercase().contains(q) ||
                c.couponCode.lowercase().contains(q) ||
                c.category.lowercase().contains(q) ||
                c.offerDescription.lowercase().contains(q) ||
                c.terms.lowercase().contains(q) ||
                c.discountValue.lowercase().contains(q)
            }
        }

        // 4. Sorting
        when (sort) {
            SortOption.RECENTLY_ADDED -> list.sortedByDescending { it.createdAt }
            SortOption.EXPIRING_SOON -> list.sortedWith(
                compareBy<Coupon> {
                    when (it.getEffectiveStatus()) {
                        CouponStatus.EXPIRES_TODAY -> 0
                        CouponStatus.EXPIRES_SOON -> 1
                        CouponStatus.ACTIVE -> 2
                        CouponStatus.NO_EXPIRY -> 3
                        CouponStatus.EXPIRED -> 4
                        CouponStatus.USED -> 5
                    }
                }.thenBy { parseExpiryDate(it.expiryDate)?.time ?: Long.MAX_VALUE }
            )
            SortOption.ALPHABETICAL -> list.sortedBy { it.merchantName.lowercase() }
            SortOption.CATEGORY -> list.sortedWith(compareBy({ it.category.lowercase() }, { it.merchantName.lowercase() }))
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(cat: String) {
        _selectedCategory.value = cat
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun setSelectedStatusTab(tab: StatusTab) {
        _selectedStatusTab.value = tab
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun setShowSettingsDialog(show: Boolean) {
        _showSettingsDialog.value = show
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
    }

    fun setPreviewScreenshot(path: String?) {
        _previewScreenshotPath.value = path
    }

    // AI & Screenshot extraction
    fun processScreenshot(uri: Uri) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisMessage.value = "Analyzing screenshot with AI..."

            val localPath = repository.saveScreenshotLocally(uri)
            val extracted = if (localPath != null) {
                GeminiCouponExtractor.extractFromImage(File(localPath))
            } else {
                ExtractedCoupon(isAiExtracted = false)
            }

            val newCoupon = Coupon(
                merchantName = extracted.merchantName,
                offerTitle = extracted.offerTitle,
                couponCode = extracted.couponCode,
                discountValue = extracted.discountValue,
                expiryDate = extracted.expiryDate,
                hasExpiry = extracted.hasExpiry,
                category = extracted.category,
                minimumOrderValue = extracted.minimumOrderValue,
                terms = extracted.terms,
                redemptionUrl = extracted.redemptionUrl,
                screenshotPath = localPath
            )

            _isAnalyzing.value = false
            _analysisMessage.value = null
            navigateTo(Screen.AddEdit(couponToEdit = newCoupon, isAiExtracted = true))
        }
    }

    // Android Share Sheet handler: text or image
    fun processSharedContent(intent: Intent) {
        viewModelScope.launch {
            val action = intent.action
            val type = intent.type

            if (Intent.ACTION_SEND == action && type != null) {
                if (type.startsWith("image/")) {
                    @Suppress("DEPRECATION")
                    val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    if (imageUri != null) {
                        processScreenshot(imageUri)
                    }
                } else if (type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                    if (sharedText.isNotBlank()) {
                        _isAnalyzing.value = true
                        _analysisMessage.value = "Extracting coupon from shared text..."
                        val extracted = GeminiCouponExtractor.extractFromText(sharedText)
                        val newCoupon = Coupon(
                            merchantName = extracted.merchantName,
                            offerTitle = extracted.offerTitle,
                            couponCode = extracted.couponCode,
                            discountValue = extracted.discountValue,
                            expiryDate = extracted.expiryDate,
                            hasExpiry = extracted.hasExpiry,
                            category = extracted.category,
                            minimumOrderValue = extracted.minimumOrderValue,
                            terms = extracted.terms,
                            redemptionUrl = extracted.redemptionUrl
                        )
                        _isAnalyzing.value = false
                        _analysisMessage.value = null
                        navigateTo(Screen.AddEdit(couponToEdit = newCoupon, isAiExtracted = true))
                    }
                }
            }
        }
    }

    fun saveCoupon(coupon: Coupon) {
        viewModelScope.launch {
            repository.saveCoupon(coupon)
            navigateTo(Screen.Home)
        }
    }

    fun toggleUsedStatus(id: Long) {
        viewModelScope.launch {
            repository.toggleUsedStatus(id)
        }
    }

    fun deleteCoupon(id: Long) {
        viewModelScope.launch {
            repository.deleteCouponById(id)
            if (_currentScreen.value is Screen.Details) {
                navigateTo(Screen.Home)
            }
        }
    }

    fun deleteAllCoupons() {
        viewModelScope.launch {
            repository.deleteAllCoupons()
            navigateTo(Screen.Home)
        }
    }

    fun copyCouponCode(context: Context, code: String) {
        if (code.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Coupon Code", code)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Coupon code '$code' copied", Toast.LENGTH_SHORT).show()
    }

    fun redeemCoupon(context: Context, coupon: Coupon) {
        // Copy code first
        if (coupon.couponCode.isNotBlank()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Coupon Code", coupon.couponCode)
            clipboard.setPrimaryClip(clip)
        }

        if (coupon.redemptionUrl.isNotBlank()) {
            try {
                var url = coupon.redemptionUrl.trim()
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://$url"
                }
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                val msg = if (coupon.couponCode.isNotBlank()) "Code copied & opening link..." else "Opening link..."
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open URL: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            if (coupon.couponCode.isNotBlank()) {
                Toast.makeText(context, "Coupon code copied!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "No code or redemption URL available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun exportCoupons(context: Context) {
        viewModelScope.launch {
            val json = repository.exportCouponsToJson()
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, json)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Export Coupons JSON").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(shareIntent)
        }
    }

    fun importCoupons(jsonText: String, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.importCouponsFromJson(jsonText)
            onComplete(count)
        }
    }

    fun addSampleCoupons() {
        viewModelScope.launch {
            val sample1 = Coupon(
                merchantName = "District",
                offerTitle = "₹200 OFF on Perfume",
                couponCode = "PERF200",
                discountValue = "₹200",
                expiryDate = "30 Nov 2026",
                hasExpiry = true,
                category = "Beauty",
                minimumOrderValue = "₹999",
                terms = "Valid on selected perfume orders above ₹999 on District app/web.",
                redemptionUrl = "https://district.in"
            )
            val sample2 = Coupon(
                merchantName = "PhonePe",
                offerTitle = "₹500 OFF first flight booking",
                couponCode = "FLIGHT500",
                discountValue = "₹500",
                expiryDate = "30 Oct 2026",
                hasExpiry = true,
                category = "Travel",
                minimumOrderValue = "₹3500",
                terms = "Valid on domestic flights for new bookings."
            )
            val sample3 = Coupon(
                merchantName = "Swiggy Gourmet",
                offerTitle = "Flat 25% OFF on dining",
                couponCode = "GOURMET25",
                discountValue = "25% OFF",
                expiryDate = "",
                hasExpiry = false,
                category = "Food",
                minimumOrderValue = "₹500",
                terms = "No expiry date. Valid on Dineout partner outlets."
            )
            repository.saveCoupon(sample1)
            repository.saveCoupon(sample2)
            repository.saveCoupon(sample3)
        }
    }
}
