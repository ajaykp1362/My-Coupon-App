package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.CouponViewModel
import com.example.ui.Screen
import com.example.ui.screens.AddEditCouponScreen
import com.example.ui.screens.CouponDetailsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsDialog
import com.example.ui.theme.CouponWalletTheme

class MainActivity : ComponentActivity() {

    private val viewModel: CouponViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (intent != null) {
            viewModel.processSharedContent(intent)
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val isDark = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            CouponWalletTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val currentScreen by viewModel.currentScreen.collectAsState()
                    val showSettings by viewModel.showSettingsDialog.collectAsState()

                    when (val screen = currentScreen) {
                        is Screen.Home -> {
                            HomeScreen(viewModel = viewModel)
                        }
                        is Screen.AddEdit -> {
                            AddEditCouponScreen(
                                couponToEdit = screen.couponToEdit,
                                isAiExtracted = screen.isAiExtracted,
                                viewModel = viewModel
                            )
                        }
                        is Screen.Details -> {
                            CouponDetailsScreen(
                                couponId = screen.couponId,
                                viewModel = viewModel
                            )
                        }
                    }

                    if (showSettings) {
                        SettingsDialog(
                            viewModel = viewModel,
                            onDismiss = { viewModel.setShowSettingsDialog(false) }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.processSharedContent(intent)
    }
}

// Retained for test compatibility
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
