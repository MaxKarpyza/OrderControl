package ru.ordercontrol

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.ordercontrol.ui.form.OrderFormScreen
import ru.ordercontrol.ui.theme.OrderControlTheme

class FormActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val recordId = intent.getLongExtra(EXTRA_RECORD_ID, 0L)
        val returnToList = intent.getBooleanExtra(EXTRA_RETURN_TO_LIST, false)
        setContent {
            OrderControlTheme {
                OrderFormScreen(
                    recordId = recordId,
                    onClose = {
                        if (returnToList) finish() else closeToHome()
                    },
                )
            }
        }
    }

    /**
     * «Сохранить и закрыть» возвращает на рабочий стол: форма открывается
     * из виджета с главного экрана, и после сохранения оператору нужен
     * именно он, а не список приложения.
     */
    private fun closeToHome() {
        finishAndRemoveTask()
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    companion object {
        const val EXTRA_RECORD_ID = "record_id"

        /** Форма открыта из списка: закрытие возвращает в список, а не на рабочий стол. */
        const val EXTRA_RETURN_TO_LIST = "return_to_list"
    }
}
