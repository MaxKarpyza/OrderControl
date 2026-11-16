package ru.ordercontrol.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.ordercontrol.AppGraph
import ru.ordercontrol.ui.base.AddressBaseBanner
import ru.ordercontrol.ui.base.AddressBaseViewModel
import ru.ordercontrol.ui.list.UnprocessedScreen
import ru.ordercontrol.ui.onboarding.OnboardingScreen
import ru.ordercontrol.ui.update.UpdateBanner

@Composable
fun RootScreen() {
    var onboardingDone by remember { mutableStateOf<Boolean?>(null) }
    val baseViewModel: AddressBaseViewModel = viewModel()

    LaunchedEffect(Unit) {
        onboardingDone = AppGraph.settings.isOnboardingDone()
    }

    // База адресов нужна для подсказок при ручном вводе. Пока её нет,
    // приложение собирает её с таблицы в фоне: работать можно и без неё,
    // о состоянии сборки говорит плашка внизу экрана.
    LaunchedEffect(Unit) {
        baseViewModel.start()
    }

    // Surface задаёт цвет текста по умолчанию. Без него экраны, нарисованные
    // вне Scaffold (настройки, онбординг), в тёмной теме получали чёрный
    // текст на тёмном фоне.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                when (onboardingDone) {
                    null -> Box(modifier = Modifier.fillMaxSize())
                    false -> OnboardingScreen(onDone = { onboardingDone = true })
                    true -> UnprocessedScreen()
                }
            }

            AddressBaseBanner(
                state = baseViewModel.state,
                onRetry = baseViewModel::retry,
            )
            UpdateBanner()
        }
    }
}
