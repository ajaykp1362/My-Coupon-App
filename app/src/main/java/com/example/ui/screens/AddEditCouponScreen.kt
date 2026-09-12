package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Coupon
import com.example.ui.CouponViewModel
import com.example.ui.Screen
import com.example.ui.components.ScreenshotViewerDialog
import com.example.ui.theme.WalletAmberContainer
import com.example.ui.theme.WalletTealAccent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCouponScreen(
    couponToEdit: Coupon?,
    isAiExtracted: Boolean,
    viewModel: CouponViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var merchantName by remember { mutableStateOf(couponToEdit?.merchantName ?: "") }
    var offerTitle by remember { mutableStateOf(couponToEdit?.offerTitle ?: "") }
    var couponCode by remember { mutableStateOf(couponToEdit?.couponCode ?: "") }
    var discountValue by remember { mutableStateOf(couponToEdit?.discountValue ?: "") }
    var expiryDate by remember { mutableStateOf(couponToEdit?.expiryDate ?: "") }
    var hasNoExpiry by remember { mutableStateOf(couponToEdit?.hasExpiry == false) }
    var category by remember { mutableStateOf(couponToEdit?.category ?: "Shopping") }
    var minimumOrderValue by remember { mutableStateOf(couponToEdit?.minimumOrderValue ?: "") }
    var redemptionUrl by remember { mutableStateOf(couponToEdit?.redemptionUrl ?: "") }
    var terms by remember { mutableStateOf(couponToEdit?.terms ?: "") }
    var screenshotPath by remember { mutableStateOf(couponToEdit?.screenshotPath) }

    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showFullScreenshot by remember { mutableStateOf(false) }
    var showValidationError by remember { mutableStateOf(false) }

    val categoriesList = listOf("Shopping", "Food", "Travel", "Entertainment", "Beauty", "Grocery", "Other")

    // Photo picker to attach or replace screenshot
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            // Save locally
            val localPath = com.example.data.CouponRepository(
                com.example.data.CouponDatabase.getInstance(context).couponDao(),
                context
            )
            // Launch coroutine to copy
            kotlinx.coroutines.GlobalScope.let {
                // In Compose we can use LaunchedEffect or ViewModel
            }
        }
    }

    val isEditingExisting = couponToEdit != null && couponToEdit.id != 0L
    val screenTitle = when {
        isAiExtracted -> "Review Coupon"
        isEditingExisting -> "Edit Coupon"
        else -> "Add Coupon"
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = screenTitle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.Home) },
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.navigateTo(Screen.Home) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (merchantName.isBlank() || offerTitle.isBlank()) {
                                showValidationError = true
                            } else {
                                val coupon = Coupon(
                                    id = couponToEdit?.id ?: 0L,
                                    merchantName = merchantName.trim(),
                                    offerTitle = offerTitle.trim(),
                                    offerDescription = "",
                                    couponCode = couponCode.trim(),
                                    discountValue = discountValue.trim(),
                                    expiryDate = if (hasNoExpiry) "" else expiryDate.trim(),
                                    hasExpiry = !hasNoExpiry,
                                    category = category,
                                    minimumOrderValue = minimumOrderValue.trim(),
                                    terms = terms.trim(),
                                    redemptionUrl = redemptionUrl.trim(),
                                    screenshotPath = screenshotPath,
                                    status = couponToEdit?.status ?: "ACTIVE",
                                    isUsed = couponToEdit?.isUsed ?: false,
                                    createdAt = couponToEdit?.createdAt ?: System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis()
                                )
                                viewModel.saveCoupon(coupon)
                            }
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .height(50.dp)
                            .testTag("save_coupon_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Save Coupon",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // AI Extracted notice banner
            if (isAiExtracted) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "AI extracted these details. Please review before saving.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Attached screenshot preview card if present
            if (screenshotPath != null && File(screenshotPath!!).exists()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showFullScreenshot = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AsyncImage(
                            model = File(screenshotPath!!),
                            contentDescription = "Coupon Screenshot",
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Attached Screenshot",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Tap to view full image",
                                style = MaterialTheme.typography.bodySmall,
                                color = WalletTealAccent
                            )
                        }
                        IconButton(onClick = { screenshotPath = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove screenshot")
                        }
                    }
                }
            }

            // Validation error prompt
            if (showValidationError) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Please enter Merchant name and Offer title.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Field: Merchant / App (Mandatory)
            OutlinedTextField(
                value = merchantName,
                onValueChange = {
                    merchantName = it
                    if (it.isNotBlank()) showValidationError = false
                },
                label = { Text("Merchant / App *") },
                placeholder = { Text("e.g. District, Amazon, Swiggy") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_merchant_name"),
                singleLine = true,
                isError = showValidationError && merchantName.isBlank(),
                shape = RoundedCornerShape(10.dp)
            )

            // Field: Offer Title (Mandatory)
            OutlinedTextField(
                value = offerTitle,
                onValueChange = {
                    offerTitle = it
                    if (it.isNotBlank()) showValidationError = false
                },
                label = { Text("Offer Title *") },
                placeholder = { Text("e.g. ₹200 OFF on Perfume") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_offer_title"),
                singleLine = true,
                isError = showValidationError && offerTitle.isBlank(),
                shape = RoundedCornerShape(10.dp)
            )

            // Field: Coupon Code (Optional)
            OutlinedTextField(
                value = couponCode,
                onValueChange = { couponCode = it.uppercase() },
                label = { Text("Coupon Code (Optional)") },
                placeholder = { Text("e.g. PERF200") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_coupon_code"),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            // Field: Category Dropdown
            ExposedDropdownMenuBox(
                expanded = categoryDropdownExpanded,
                onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .testTag("category_dropdown"),
                    shape = RoundedCornerShape(10.dp)
                )
                ExposedDropdownMenu(
                    expanded = categoryDropdownExpanded,
                    onDismissRequest = { categoryDropdownExpanded = false }
                ) {
                    categoriesList.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                category = cat
                                categoryDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Field: Value / Discount (Optional)
            OutlinedTextField(
                value = discountValue,
                onValueChange = { discountValue = it },
                label = { Text("Value / Discount (Optional)") },
                placeholder = { Text("e.g. ₹200 or 20% OFF") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_discount_value"),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            // Expiry Section
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "No Expiry",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Check if voucher has no expiration date",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = hasNoExpiry,
                            onCheckedChange = { hasNoExpiry = it },
                            modifier = Modifier.testTag("switch_no_expiry")
                        )
                    }

                    if (!hasNoExpiry) {
                        OutlinedTextField(
                            value = expiryDate,
                            onValueChange = { expiryDate = it },
                            label = { Text("Expiry Date") },
                            placeholder = { Text("e.g. 30 Nov 2026") },
                            trailingIcon = {
                                IconButton(onClick = { showDatePicker = true }) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_expiry_date"),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            // Field: Minimum Order (Optional)
            OutlinedTextField(
                value = minimumOrderValue,
                onValueChange = { minimumOrderValue = it },
                label = { Text("Minimum Order (Optional)") },
                placeholder = { Text("e.g. ₹999") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_minimum_order"),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            // Field: Redemption URL (Optional)
            OutlinedTextField(
                value = redemptionUrl,
                onValueChange = { redemptionUrl = it },
                label = { Text("Redemption URL (Optional)") },
                placeholder = { Text("e.g. https://district.in") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_redemption_url"),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            // Field: Terms / Notes (Optional)
            OutlinedTextField(
                value = terms,
                onValueChange = { terms = it },
                label = { Text("Terms / Notes (Optional)") },
                placeholder = { Text("Valid on orders above ₹999. Applicable on perfume category.") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_terms"),
                minLines = 3,
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selectedMillis = datePickerState.selectedDateMillis
                    if (selectedMillis != null) {
                        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
                        expiryDate = sdf.format(Date(selectedMillis))
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Fullscreen Screenshot Viewer if clicked
    if (showFullScreenshot && screenshotPath != null) {
        ScreenshotViewerDialog(
            imagePath = screenshotPath!!,
            onDismiss = { showFullScreenshot = false }
        )
    }
}
