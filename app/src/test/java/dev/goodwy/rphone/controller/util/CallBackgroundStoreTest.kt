package dev.goodwy.rphone.controller.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallBackgroundStoreTest {

    private val defaultUri = "file:///data/user/0/app/files/call_backgrounds/bg_default.jpg"
    private val customUri = "file:///data/user/0/app/files/call_backgrounds/bg_custom.jpg"

    @Test
    fun avatarShownWhenNoBackground() {
        assertTrue(
            CallBackgroundStore.shouldShowAvatar(
                showCallScreenAvatar = true,
                hideAvatarWithBg = false,
                backgroundUri = null,
                defaultBackgroundUri = defaultUri
            )
        )
        assertTrue(
            CallBackgroundStore.shouldShowAvatar(
                showCallScreenAvatar = true,
                hideAvatarWithBg = false,
                backgroundUri = "",
                defaultBackgroundUri = defaultUri
            )
        )
        assertTrue(
            CallBackgroundStore.shouldShowAvatar(
                showCallScreenAvatar = true,
                hideAvatarWithBg = true,
                backgroundUri = null,
                defaultBackgroundUri = defaultUri
            )
        )
    }

    @Test
    fun avatarFollowsToggleForGlobalDefaultBackground() {
        assertTrue(
            CallBackgroundStore.shouldShowAvatar(
                showCallScreenAvatar = true,
                hideAvatarWithBg = false,
                backgroundUri = defaultUri,
                defaultBackgroundUri = defaultUri
            )
        )
        assertFalse(
            CallBackgroundStore.shouldShowAvatar(
                showCallScreenAvatar = true,
                hideAvatarWithBg = true,
                backgroundUri = defaultUri,
                defaultBackgroundUri = defaultUri
            )
        )
    }

    @Test
    fun customBackgroundAlwaysHidesAvatar() {
        assertFalse(
            CallBackgroundStore.shouldShowAvatar(
                showCallScreenAvatar = true,
                hideAvatarWithBg = false,
                backgroundUri = customUri,
                defaultBackgroundUri = defaultUri
            )
        )
        assertFalse(
            CallBackgroundStore.shouldShowAvatar(
                showCallScreenAvatar = true,
                hideAvatarWithBg = true,
                backgroundUri = customUri,
                defaultBackgroundUri = defaultUri
            )
        )
        assertFalse(
            CallBackgroundStore.shouldShowAvatar(
                showCallScreenAvatar = true,
                hideAvatarWithBg = false,
                backgroundUri = customUri,
                defaultBackgroundUri = null
            )
        )
    }

    @Test
    fun avatarHiddenWhenUserDisabledIt() {
        assertFalse(
            CallBackgroundStore.shouldShowAvatar(
                showCallScreenAvatar = false,
                hideAvatarWithBg = false,
                backgroundUri = null,
                defaultBackgroundUri = defaultUri
            )
        )
        assertFalse(
            CallBackgroundStore.shouldShowAvatar(
                showCallScreenAvatar = false,
                hideAvatarWithBg = false,
                backgroundUri = defaultUri,
                defaultBackgroundUri = defaultUri
            )
        )
    }
}
