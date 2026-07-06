package dev.goodwy.rphone.modal.data

data class CallLogEntry(
    val id: Long,
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long,
    val duration: Long,
    val photoUri: String?,
    val contactId: String?,
    val simLabel: String? = null,
    val isBlocked: Boolean = false,
    val types: List<Int> = emptyList(),
    val ids: List<Long> = emptyList(),
    val isCallerIdName: Boolean = false,
    val phoneType: Int? = null,         // Mobile, Home, Work
    val phoneLabel: String? = null      // Custom type
) {
    val count: Int get() = types.size.coerceAtLeast(1)
}