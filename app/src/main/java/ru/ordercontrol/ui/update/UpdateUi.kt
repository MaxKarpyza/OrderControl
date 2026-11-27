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
import androidx.compose.runtime.collectAsState
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
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ru.ordercontrol.AppGraph
import ru.ordercontrol.BuildConfig
import ru.ordercontrol.R
import ru.ordercontrol.update.ApkInstaller
import ru.ordercontrol.update.UpdateCheckResult
import ru.ordercontrol.update.UpdateDownloadState
import ru.ordercontrol.update.UpdateInfo

/**
 * APK, для которых установщик уже открывали сами. Чтобы возврат на экран
 * не открывал его повторно, если пользователь отменил установку.
 */
private val autoInstallShown = mutableSetOf<String>()

private fun claimAutoInstall(apk: File): Boolean =
    synchronized(autoInstallShown) { autoInstallShown.add(apk.absolutePath) }

/**
 * Плашка обновления внизу экрана. Ничего не перекрывает и не блокирует:
 * занимает своё место в разметке, проверка и скачивание идут в фоне.
 */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloads = AppGraph.updateDownloads
    val downloadState by downloads.state.collectAsState()
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var hiddenVersion by remember { mutableStateOf<Int?>(null) }
    var canInstall by remember { mutableStateOf(ApkInstaller.canInstall(context)) }
    var installError by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canInstall = ApkInstaller.canInstall(context)
    }

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

    // Если скачивание уже идёт, плашка показывает его даже без проверки:
    // так докачка после перезапуска видна сразу.
    val available = info ?: downloadState.updateInfo ?: return
    // Скрытие привязано к версии: когда выйдет следующая, плашка покажется снова.
    if (hiddenVersion == available.versionCode) return

    // Обрыв загрузки лечится сам: если на диске остался недокачанный файл,
    // продолжаем его без нажатий.
    LaunchedEffect(available.versionCode) {
        downloads.resumeIfStarted(available)
    }

    val readyApk = (downloadState as? UpdateDownloadState.Ready)?.apk
    // Скачалось — один раз сами открываем системный установщик, если доступ
    // разрешён. Если пользователь отменил установку, кнопка «Установить»
    // останется на плашке.
    LaunchedEffect(readyApk) {
        if (readyApk == null || !canInstall) return@LaunchedEffect
        if (!claimAutoInstall(readyApk)) return@LaunchedEffect
        installError = !startInstaller(context, readyApk)
        if (!installError) hiddenVersion = available.versionCode
    }

    val downloading = downloadState as? UpdateDownloadState.Downloading
    val failed = downloadState is UpdateDownloadState.Failed

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
                    downloading != null -> {
                        LinearProgressIndicator(
                            progress = { downloading.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        downloading.status?.let { status ->
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    installError -> Text(
                        text = stringResource(R.string.update_install_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    failed -> Text(
                        text = stringResource(R.string.update_download_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    !canInstall -> Text(
                        text = stringResource(R.string.update_allow_install),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(
                onClick = {
                    installError = false
                    when {
                        !canInstall -> ApkInstaller.openInstallPermission(context)
                        readyApk != null -> {
                            installError = !startInstaller(context, readyApk)
                            if (!installError) hiddenVersion = available.versionCode
                        }
                        else -> downloads.start(available)
                    }
                },
                enabled = downloading == null,
            ) {
                Text(
                    stringResource(
                        when {
                            readyApk != null && canInstall -> R.string.update_install
                            !canInstall -> R.string.update_allow_install_action
                            else -> R.string.update_action
                        },
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
    val downloadState by AppGraph.updateDownloads.state.collectAsState()
    var state by remember { mutableStateOf<CheckState>(CheckState.Idle) }
    var canInstall by remember { mutableStateOf(ApkInstaller.canInstall(context)) }
    var installError by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canInstall = ApkInstaller.canInstall(context)
    }

    val noNetwork = stringResource(R.string.update_no_network)
    val failed = stringResource(R.string.update_failed)

    val readyApk = (downloadState as? UpdateDownloadState.Ready)?.apk
    LaunchedEffect(readyApk) {
        if (readyApk == null || !canInstall) return@LaunchedEffect
        if (!claimAutoInstall(readyApk)) return@LaunchedEffect
        installError = !startInstaller(context, readyApk)
    }

    val available = (state as? CheckState.Available)?.info ?: downloadState.updateInfo

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
                        val result = AppGraph.updates.check()
                        if (result is UpdateCheckResult.Available) {
                            AppGraph.updateDownloads.resumeIfStarted(result.info)
                        }
                        when (result) {
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
        if (available != null) {
            Text(
                text = stringResource(R.string.update_available, available.versionName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            TextButton(
                onClick = {
                    installError = false
                    when {
                        !canInstall -> ApkInstaller.openInstallPermission(context)
                        readyApk != null -> installError = !startInstaller(context, readyApk)
                        else -> AppGraph.updateDownloads.start(available)
                    }
                },
                enabled = downloadState !is UpdateDownloadState.Downloading,
            ) {
                Text(
                    stringResource(
                        when {
                            readyApk != null && canInstall -> R.string.update_install
                            !canInstall -> R.string.update_allow_install_action
                            else -> R.string.update_download
                        },
                    ),
                )
            }
        }
        when (val current = state) {
            CheckState.UpToDate -> Text(
                text = stringResource(R.string.update_up_to_date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is CheckState.Error -> Text(
                text = current.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            CheckState.Idle, CheckState.Checking, is CheckState.Available -> Unit
        }
        (downloadState as? UpdateDownloadState.Downloading)?.let { downloading ->
            LinearProgressIndicator(
                progress = { downloading.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            downloading.status?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (downloadState is UpdateDownloadState.Failed) Text(
            text = stringResource(R.string.update_download_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        if (installError) Text(
            text = stringResource(R.string.update_install_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** Запускает системный установщик; `false` — открыть его не удалось. */
private fun startInstaller(context: Context, apk: File): Boolean = try {
    ApkInstaller.install(context, apk)
    true
} catch (_: Exception) {
    false
}
