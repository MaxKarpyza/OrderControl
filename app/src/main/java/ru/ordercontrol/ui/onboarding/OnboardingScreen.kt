package ru.ordercontrol.ui.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import ru.ordercontrol.AppGraph
import ru.ordercontrol.R
import ru.ordercontrol.data.ContactAccount
import ru.ordercontrol.widget.WidgetRefresh

private data class PermissionState(
    val phoneState: Boolean,
    val callLog: Boolean,
    val contacts: Boolean,
    val contactsWrite: Boolean,
) {
    val canContinue: Boolean get() = phoneState && callLog
}

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var permissionsVersion by remember { mutableIntStateOf(0) }
    var batteryOptimized by remember { mutableStateOf(isBatteryOptimized(context)) }
    var accounts by remember { mutableStateOf<List<ContactAccount>>(emptyList()) }
    var selectedAccountName by rememberSaveable { mutableStateOf<String?>(null) }
    var localSelected by rememberSaveable { mutableStateOf(false) }

    val selectedAccount: ContactAccount? = when {
        localSelected -> null
        selectedAccountName != null -> accounts.firstOrNull { it.name == selectedAccountName }
        else -> accounts.firstOrNull()
    }

    val permissions = remember(permissionsVersion) { readPermissions(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionsVersion++
        batteryOptimized = isBatteryOptimized(context)
    }

    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        batteryOptimized = isBatteryOptimized(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionsVersion++
                batteryOptimized = isBatteryOptimized(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(permissionsVersion) {
        accounts = AppGraph.contacts.googleAccounts()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.onboarding_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()

        PermissionRow(
            title = stringResource(R.string.permission_phone_state),
            granted = permissions.phoneState,
        )
        PermissionRow(
            title = stringResource(R.string.permission_call_log),
            granted = permissions.callLog,
        )
        PermissionRow(
            title = stringResource(R.string.permission_contacts),
            granted = permissions.contacts,
        )
        PermissionRow(
            title = stringResource(R.string.permission_contacts_write),
            granted = permissions.contactsWrite,
        )

        if (!permissions.canContinue) {
            Text(
                text = stringResource(R.string.onboarding_required_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        OutlinedButton(
            onClick = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.READ_CALL_LOG,
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.WRITE_CONTACTS,
                        Manifest.permission.GET_ACCOUNTS,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.permission_grant))
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.battery_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.battery_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (batteryOptimized) {
            OutlinedButton(
                onClick = { requestIgnoreBatteryOptimizations(context, batteryLauncher::launch) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.battery_grant))
            }
        } else {
            Text(
                text = stringResource(R.string.battery_granted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.account_title),
            style = MaterialTheme.typography.titleMedium,
        )
        if (accounts.isEmpty()) {
            Text(
                text = stringResource(R.string.account_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        accounts.forEach { account ->
            AccountRow(
                title = account.name,
                subtitle = stringResource(R.string.account_google),
                selected = selectedAccount == account,
                onClick = {
                    selectedAccountName = account.name
                    localSelected = false
                },
            )
        }
        AccountRow(
            title = stringResource(R.string.account_local),
            subtitle = null,
            selected = selectedAccount == null,
            onClick = {
                selectedAccountName = null
                localSelected = true
            },
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                scope.launch {
                    AppGraph.settings.setContactAccount(selectedAccount)
                    AppGraph.settings.setOnboardingDone()
                    WidgetRefresh.updateAll(context)
                    onDone()
                }
            },
            enabled = permissions.canContinue,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_start))
        }
    }
}

@Composable
private fun PermissionRow(title: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(
                if (granted) R.string.permission_granted else R.string.permission_denied,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = if (granted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}

@Composable
private fun AccountRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun readPermissions(context: Context): PermissionState = PermissionState(
    phoneState = context.hasPermission(Manifest.permission.READ_PHONE_STATE),
    callLog = context.hasPermission(Manifest.permission.READ_CALL_LOG),
    contacts = context.hasPermission(Manifest.permission.READ_CONTACTS),
    contactsWrite = context.hasPermission(Manifest.permission.WRITE_CONTACTS),
)

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun isBatteryOptimized(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        ?: return false
    return !powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

@SuppressLint("BatteryLife")
private fun requestIgnoreBatteryOptimizations(
    context: Context,
    launch: (Intent) -> Unit,
) {
    val request = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        "package:${context.packageName}".toUri(),
    )
    try {
        launch(request)
    } catch (_: ActivityNotFoundException) {
        launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}
