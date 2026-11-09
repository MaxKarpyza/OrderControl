package ru.ordercontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.ordercontrol.ui.RootScreen
import ru.ordercontrol.ui.theme.OrderControlTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Окно не ресайзится под клавиатуру: инсеты IME анимируются Compose,
        // поэтому поиск в истории выезжает вместе с клавиатурой без рывка.
        enableEdgeToEdge()
        setContent {
            OrderControlTheme {
                RootScreen()
            }
        }
    }
}
