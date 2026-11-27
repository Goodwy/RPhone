package dev.goodwy.rphone.modal.data

import androidx.compose.runtime.Immutable

@Immutable
data class CallerMetadata(
    val number: String,
    val name: String,
    val isLocalContact: Boolean,
    val photoUri: String? = null,
    val hasLocalName: Boolean = false,
    val spamEvaluation: SpamEvaluationResult? = null
)
