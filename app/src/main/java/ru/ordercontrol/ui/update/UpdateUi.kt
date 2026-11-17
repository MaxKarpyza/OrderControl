package ru.ordercontrol.ui.update

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.ordercontrol.AppGraph
import ru.ordercontrol.BuildConfig
import ru.ordercontrol.R
import ru.ordercontrol.update.ApkInstaller
import ru.ordercontrol.update.UpdateCheckResult
import ru.ordercontrol.update.UpdateInfo

/**
 * Плашка обновления внизу экрана. Ничего не перекрывает и не блокирует:
 * занимает своё место в разметке, проверка и скачивание идут в фоне.
 */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var hiddenVersion by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        if (!AppGraph.settings.isUpdateCheckDue()) return@LaunchedEffect
        // Проверка обновления не должна ронять приложение: сеть может
        // отдавать мусор вместо ответа.
        val result = try {
            AppGraph.updates.check()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return@LaunchedEffect
        }
        if (result is UpdateCheckResult.Available &&
            !AppGraph.settings.isUpdateDismissed(result.info.versionCode)
        ) {
            info = result.info
        }
    }

    val available = info ?: return
    // Скрытие привязано к версии: когда выйдет следующая, плашка покажется снова.
    if (hiddenVersion == available.versionCode) return

    val install = remember { UpdateInstallState(context, scope) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { install.refreshPermission() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.update_available, available.versionName),
                    style = MaterialTheme.typography.bodyMedium,
                )
                when {
                    install.downloading -> {
                        LinearProgressIndicator(
                            progress = { install.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        install.status?.let { status ->
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    install.error != null -> Text(
                        text = install.error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    !install.canInstall -> Text(
                        text = stringResource(R.string.update_allow_install),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(
                onClick = { install.start(available) { hiddenVersion = available.versionCode } },
                enabled = !install.downloading,
            ) {
                Text(
                    stringResource(
                        if (install.canInstall) R.string.update_action
                        else R.string.update_allow_install_action,
                    ),
                )
            }
            IconButton(
                onClick = {
                    hiddenVersion = available.versionCode
                    scope.launch { AppGraph.settings.dismissUpdate(available.versionCode) }
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_close),
                )
            }
        }
    }
}

private sealed interface CheckState {
    data object Idle : CheckState

    data object Checking : CheckState

    data object UpToDate : CheckState

    data class Available(val info: UpdateInfo) : CheckState

    data class Error(val text: String) : CheckState
}

/** Блок «Обновления» на экране настроек: проверка и запуск установки без окон. */
@Composable
fun UpdateSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<CheckState>(CheckState.Idle) }
    val install = remember { UpdateInstallState(context, scope) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { install.refreshPermission() }

    val noNetwork = stringResource(R.string.update_no_network)
    val failed = stringResource(R.string.update_failed)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.update_section_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                state = CheckState.Checking
                scope.launch {
                    state = try {
                        when (val result = AppGraph.updates.check()) {
                            is UpdateCheckResult.Available -> CheckState.Available(result.info)
                            UpdateCheckResult.UpToDate -> CheckState.UpToDate
                            UpdateCheckResult.NoNetwork -> CheckState.Error(noNetwork)
                            is UpdateCheckResult.Failure -> CheckState.Error(failed)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        CheckState.Error(failed)
                    }
                }
            },
            enabled = state !is CheckState.Checking,
        ) {
            Text(
                stringResource(
                    if (state is CheckState.Checking) R.string.update_checking
                    else R.string.update_check,
                ),
            )
        }
        when (val current = state) {
            CheckState.UpToDate -> Text(
                text = stringResource(R.string.update_up_to_date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is CheckState.Available -> {
                Text(
                    text = stringResource(R.string.update_available, current.info.versionName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(
                    onClick = { install.start(current.info) {} },
                    enabled = !install.downloading,
                ) {
                    Text(
                        stringResource(
                            if (install.canInstall) R.string.update_download
                            else R.string.update_allow_install_action,
                        ),
                    )
                }
            }
            is CheckState.Error -> Text(
                text = current.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            CheckState.Idle, CheckState.Checking -> Unit
        }
        if (install.downloading) {
            LinearProgressIndicator(
                progress = { install.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            install.status?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        install.error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Скачивание APK и запуск системного установщика. */
private class UpdateInstallState(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    var percent by mutableStateOf(0)
        private set

    var downloading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)

    /** Сообщение о ходе загрузки: повтор запроса или докачка. */
    var status by mutableStateOf<String?>(null)
        private set

    var canInstall by mutableStateOf(ApkInstaller.canInstall(context))
        private set

    /** Пользователь мог вернуться из системных настроек с выданным доступом. */
    fun refreshPermission() {
        canInstall = ApkInstaller.canInstall(context)
    }

    fun start(info: UpdateInfo, onInstallerOpened: () -> Unit) {
        if (downloading) return
        if (!canInstall) {
            ApkInstaller.openInstallPermission(context)
            return
        }
        error = null
        scope.launch {
            downloading = true
            percent = 0
            status = null
            val apk = try {
                AppGraph.updates.download(
                    info,
                    onProgress = { percent = it },
                    onStatus = { status = it },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = context.getString(R.string.update_download_failed)
                downloading = false
                return@launch
            } finally {
                status = null
            }
            try {
                ApkInstaller.install(context, apk)
                onInstallerOpened()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = context.getString(R.string.update_install_failed)
            } finally {
                downloading = false
            }
        }
    }
}
