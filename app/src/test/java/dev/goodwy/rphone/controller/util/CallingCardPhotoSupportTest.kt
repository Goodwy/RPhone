package dev.goodwy.rphone.controller.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CallingCardPhotoSupportTest {

    private fun status(
        supported: Boolean = true,
        installed: Boolean,
        enabled: Boolean
    ) = CallingCardPhotoSupport.CloudPhotoStatus(
        cloudPickerSupported = supported,
        googlePhotosInstalled = installed,
        googlePhotosEnabled = enabled
    )

    @Test
    fun enabledGooglePhotosOpensThePicker() {
        assertEquals(
            CallingCardPhotoSupport.CloudPhotoAction.OPEN_PICKER,
            CallingCardPhotoSupport.cloudPhotoAction(status(installed = true, enabled = true))
        )
    }

    @Test
    fun installedButNotSelectedSendsUserToSettings() {
        assertEquals(
            CallingCardPhotoSupport.CloudPhotoAction.OPEN_SETTINGS,
            CallingCardPhotoSupport.cloudPhotoAction(status(installed = true, enabled = false))
        )
    }

    @Test
    fun missingGooglePhotosFallsBackToGallery() {
        assertEquals(
            CallingCardPhotoSupport.CloudPhotoAction.FALLBACK,
            CallingCardPhotoSupport.cloudPhotoAction(status(installed = false, enabled = false))
        )
    }

    @Test
    fun oldAndroidWithoutCloudPickerFallsBackEvenIfPhotosIsInstalled() {
        assertEquals(
            CallingCardPhotoSupport.CloudPhotoAction.FALLBACK,
            CallingCardPhotoSupport.cloudPhotoAction(
                status(supported = false, installed = true, enabled = false)
            )
        )
    }
}
