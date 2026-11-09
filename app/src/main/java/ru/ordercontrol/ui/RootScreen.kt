package ru.ordercontrol.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.ordercontrol.AppGraph
import ru.ordercontrol.ui.list.UnprocessedScreen
import ru.ordercontrol.ui.onboarding.OnboardingScreen
import ru.ordercontrol.ui.update.UpdateBanner

@Composable
fun RootScreen() {
    var onboardingDone by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        onboardingDone = AppGraph.settings.isOnboardingDone()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (onboardingDone) {
                null -> Box(modifier = Modifier.fillMaxSize())
                false -> OnboardingScreen(onDone = { onboardingDone = true })
                true -> UnprocessedScreen()
            }
        }

        UpdateBanner()
    }
}
