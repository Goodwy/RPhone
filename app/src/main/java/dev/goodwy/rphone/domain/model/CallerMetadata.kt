package dev.goodwy.rphone.domain.model

data class CallerMetadata(
    val number: String,
    val name: String,
    val isLocalContact: Boolean,
    val photoUri: String? = null
)
