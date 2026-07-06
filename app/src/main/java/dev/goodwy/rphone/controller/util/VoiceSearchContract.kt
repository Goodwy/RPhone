package dev.goodwy.rphone.controller.util

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.result.contract.ActivityResultContract

class VoiceSearchContract : ActivityResultContract<Unit, String?>() {
    override fun createIntent(context: Context, input: Unit): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak contact name or number")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): String? {
        if (resultCode != android.app.Activity.RESULT_OK) return null
        val results = intent?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        return results?.firstOrNull()
    }
}