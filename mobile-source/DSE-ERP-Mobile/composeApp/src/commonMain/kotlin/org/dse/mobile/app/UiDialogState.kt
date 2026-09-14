package org.dse.mobile.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * One application-level contract for acknowledgement dialogs.
 * Field validation remains local to its field; business/server outcomes are routed here.
 */
internal sealed interface UiDialogState {
    val title: String
    val message: String

    data class Error(
        override val title: String = "Unable to Complete Action",
        override val message: String,
        val onDismiss: () -> Unit = {},
    ) : UiDialogState

    data class Warning(
        override val title: String = "Attention Required",
        override val message: String,
        val onDismiss: () -> Unit = {},
    ) : UiDialogState

    data class Information(
        override val title: String = "Information",
        override val message: String,
        val onDismiss: () -> Unit = {},
    ) : UiDialogState

    data class Confirmation(
        override val title: String,
        override val message: String,
        val confirmLabel: String = "Confirm",
        val destructive: Boolean = false,
        val requiredPhrase: String? = null,
        val requiredPhraseLabel: String? = null,
        val onConfirm: () -> Unit,
        val onDismiss: () -> Unit,
    ) : UiDialogState
}

@Stable
internal class UiDialogController {
    var state: UiDialogState? by mutableStateOf(null)
        private set

    fun error(message: String, title: String = "Unable to Complete Action", onDismiss: () -> Unit = {}) {
        if (message.isNotBlank()) state = UiDialogState.Error(title, message.trim(), onDismiss)
    }

    fun warning(message: String, title: String = "Attention Required", onDismiss: () -> Unit = {}) {
        if (message.isNotBlank()) state = UiDialogState.Warning(title, message.trim(), onDismiss)
    }

    fun information(message: String, title: String = "Information", onDismiss: () -> Unit = {}) {
        if (message.isNotBlank()) state = UiDialogState.Information(title, message.trim(), onDismiss)
    }

    fun confirmation(
        title: String,
        message: String,
        confirmLabel: String = "Confirm",
        destructive: Boolean = false,
        requiredPhrase: String? = null,
        requiredPhraseLabel: String? = null,
        onDismiss: () -> Unit,
        onConfirm: () -> Unit,
    ) {
        state = UiDialogState.Confirmation(title, message, confirmLabel, destructive, requiredPhrase, requiredPhraseLabel, onConfirm, onDismiss)
    }

    fun dismiss() {
        state = null
    }

    /** Returns true when the message is an acknowledgement-level business/server outcome. */
    fun showBusinessMessage(message: String, onDismiss: () -> Unit = {}): Boolean {
        return when (noticeKindFor(message)) {
            DseNoticeKind.ERROR -> { error(message, onDismiss = onDismiss); true }
            DseNoticeKind.WARNING -> { warning(message, onDismiss = onDismiss); true }
            DseNoticeKind.SUCCESS, DseNoticeKind.INFO -> { information(message, onDismiss = onDismiss); true }
            null -> false
        }
    }
}

internal val LocalUiDialogController = staticCompositionLocalOf<UiDialogController?> { null }

@Composable
internal fun UiDialogRenderer(controller: UiDialogController) {
    when (val dialog = controller.state) {
        is UiDialogState.Error -> UiMessageDialog(
            title = dialog.title,
            message = dialog.message,
            accent = DseDanger,
            icon = { Icon(Icons.Rounded.ErrorOutline, null, tint = DseDanger) },
            onDismiss = { controller.dismiss(); dialog.onDismiss() },
        )
        is UiDialogState.Warning -> UiMessageDialog(
            title = dialog.title,
            message = dialog.message,
            accent = DseWarning,
            icon = { Icon(Icons.Rounded.WarningAmber, null, tint = DseWarning) },
            onDismiss = { controller.dismiss(); dialog.onDismiss() },
        )
        is UiDialogState.Information -> UiMessageDialog(
            title = dialog.title,
            message = dialog.message,
            accent = DseInfo,
            icon = { Icon(Icons.Rounded.Info, null, tint = DseInfo) },
            onDismiss = { controller.dismiss(); dialog.onDismiss() },
        )
        is UiDialogState.Confirmation -> {
            val accent = if (dialog.destructive) DseDanger else DseWarning
            var typedPhrase by remember(dialog) { mutableStateOf("") }
            val phraseAccepted = dialog.requiredPhrase == null || typedPhrase == dialog.requiredPhrase
            PremiumAlertDialog(
                onDismissRequest = {
                    controller.dismiss()
                    dialog.onDismiss()
                },
                title = {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            if (dialog.destructive) Icons.Rounded.WarningAmber else Icons.Rounded.HelpOutline,
                            null,
                            tint = accent,
                        )
                        Text(dialog.title, fontWeight = FontWeight.ExtraBold, color = accent)
                    }
                },
                text = {
                    androidx.compose.foundation.layout.Column(
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        Text(dialog.message)
                        dialog.requiredPhrase?.let { phrase ->
                            DseField(
                                dialog.requiredPhraseLabel ?: "Type $phrase",
                                typedPhrase,
                                singleLine = true,
                                required = true,
                                onValue = { typedPhrase = it },
                            )
                        }
                    }
                },
                confirmButton = {
                    if (dialog.destructive) {
                        PremiumDangerButton(dialog.confirmLabel, {
                            controller.dismiss()
                            dialog.onConfirm()
                        }, enabled = phraseAccepted)
                    } else {
                        PremiumPrimaryButton(dialog.confirmLabel, {
                            controller.dismiss()
                            dialog.onConfirm()
                        }, enabled = phraseAccepted, leadingIcon = Icons.Rounded.Check)
                    }
                },
                dismissButton = {
                    PremiumSecondaryButton("Cancel", {
                        controller.dismiss()
                        dialog.onDismiss()
                    })
                },
            )
        }
        null -> Unit
    }
}

@Composable
private fun UiMessageDialog(
    title: String,
    message: String,
    accent: Color,
    icon: @Composable () -> Unit,
    onDismiss: () -> Unit,
) {
    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                icon()
                Text(title, fontWeight = FontWeight.ExtraBold, color = accent)
            }
        },
        text = { Text(message) },
        confirmButton = { PremiumPrimaryButton("Close", onDismiss, leadingIcon = Icons.Rounded.Check) },
    )
}
