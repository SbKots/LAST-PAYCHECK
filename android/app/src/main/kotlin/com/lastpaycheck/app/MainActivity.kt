package com.lastpaycheck.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.lastpaycheck.presentation.account.AccountViewModel
import com.lastpaycheck.presentation.game.FinanceViewModel
import com.lastpaycheck.presentation.game.GameNavigation
import com.lastpaycheck.presentation.game.MainViewModel
import com.lastpaycheck.presentation.startup.StartupViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = ViewModelProvider(this)[StartupViewModel::class.java]
        val gameViewModel = ViewModelProvider(this)[MainViewModel::class.java]
        val accountViewModel = ViewModelProvider(this)[AccountViewModel::class.java]
        val financeViewModel = ViewModelProvider(this)[FinanceViewModel::class.java]
        setContent {
            GameNavigation(
                gameViewModel,
                viewModel,
                accountViewModel,
                financeViewModel,
                onThemeChanged = ::updateSystemBars
            )
        }
    }

    private fun updateSystemBars(darkTheme: Boolean) {
        val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
