package dev.goodwy.rphone.controller

import dev.goodwy.rphone.modal.data.CallerMetadata
import dev.goodwy.rphone.modal.`interface`.ICallerRepository

class GetCallerNameUseCase(private val repository: ICallerRepository) {
    suspend operator fun invoke(incomingNumber: String, cnamName: String?): CallerMetadata {
        val localContact = repository.getContactByNumber(incomingNumber)
        val cleanCnam = cnamName?.takeIf { it.isNotBlank() }
        
        val hasLocalName = localContact?.hasLocalName() == true

        return when {
            hasLocalName -> {
                CallerMetadata(
                    number = incomingNumber,
                    name = localContact.displayName,
                    isLocalContact = true,
                    photoUri = localContact.photoUri
                )
            }
            cleanCnam != null -> {
                CallerMetadata(
                    number = incomingNumber,
                    name = cleanCnam,
                    isLocalContact = false,
                    photoUri = localContact?.photoUri
                )
            }
            localContact != null -> {
                // We have a contact but no name, use its display name
                CallerMetadata(
                    number = incomingNumber,
                    name = localContact.displayName,
                    isLocalContact = true,
                    photoUri = localContact.photoUri
                )
            }
            else -> {
                CallerMetadata(
                    number = incomingNumber,
                    name = incomingNumber.ifBlank { "Unknown Number" },
                    isLocalContact = false
                )
            }
        }
    }
}
