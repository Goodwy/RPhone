package dev.goodwy.rphone.modal.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.theme.customColors

enum class SwipeActionType(
    val id: Int,
    val titleRes: Int
) {
    NONE(
        id = PreferenceManager.SWIPE_ACTION_NONE,
        titleRes = R.string.swipe_action_none,
    ),
    CALL(
        id = PreferenceManager.SWIPE_ACTION_CALL,
        titleRes = R.string.call,
    ),
    MESSAGE(
        id = PreferenceManager.SWIPE_ACTION_MESSAGE,
        titleRes = R.string.message,
    ),
    VIDEO_CALL(
        id = PreferenceManager.SWIPE_ACTION_VIDEO_CALL,
        titleRes = R.string.video_call,
    ),
    WHATSAPP(
        id = PreferenceManager.SWIPE_ACTION_WHATSAPP,
        titleRes = R.string.whatsapp,
    ),
    TELEGRAM(
        id = PreferenceManager.SWIPE_ACTION_TELEGRAM,
        titleRes = R.string.telegram,
    ),
    COPY_NUMBER(
        id = PreferenceManager.SWIPE_ACTION_COPY_NUMBER,
        titleRes = R.string.swipe_action_copy_number,
    ),
    DELETE(
        id = PreferenceManager.SWIPE_ACTION_DELETE,
        titleRes = R.string.delete,
    );

    @Composable
    fun icon(): ImageVector = when (this) {
        NONE -> Icons.Default.DoNotDisturb
        CALL -> Icons.Outlined.Call
        MESSAGE -> ImageVector.vectorResource(id = R.drawable.ic_message_outline)
        VIDEO_CALL -> ImageVector.vectorResource(id = R.drawable.ic_video_camera)
        WHATSAPP -> ImageVector.vectorResource(id = R.drawable.ic_whatsapp)
        TELEGRAM -> ImageVector.vectorResource(id = R.drawable.ic_telegram_icon)
        COPY_NUMBER -> Icons.Rounded.ContentCopy
        DELETE -> ImageVector.vectorResource(id = R.drawable.ic_delete)
    }

    @Composable
    fun containerColor(): Color = when (this) {
        NONE -> MaterialTheme.colorScheme.surfaceContainerHigh
        CALL -> MaterialTheme.colorScheme.customColors.colorGreen
        MESSAGE -> MaterialTheme.colorScheme.customColors.colorBlue
        VIDEO_CALL -> MaterialTheme.colorScheme.customColors.colorPurple
        WHATSAPP -> Color(0xFF27D366)
        TELEGRAM -> Color(0xFF2FA3DD)
        COPY_NUMBER -> MaterialTheme.colorScheme.secondaryContainer
        DELETE -> MaterialTheme.colorScheme.customColors.colorRed
    }

    @Composable
    fun contentColor(): Color = when (this) {
        NONE -> MaterialTheme.colorScheme.onSurfaceVariant
        CALL -> MaterialTheme.colorScheme.customColors.colorDarkGreen
        MESSAGE -> MaterialTheme.colorScheme.customColors.colorDarkBlue
        VIDEO_CALL -> MaterialTheme.colorScheme.customColors.colorDarkPurple
        WHATSAPP -> Color.White
        TELEGRAM -> Color.White
        COPY_NUMBER -> MaterialTheme.colorScheme.onSecondaryContainer
        DELETE -> MaterialTheme.colorScheme.customColors.colorDarkRed
    }

    companion object {
        fun fromId(id: Int): SwipeActionType {
            return entries.firstOrNull { it.id == id } ?: NONE
        }
    }
}
