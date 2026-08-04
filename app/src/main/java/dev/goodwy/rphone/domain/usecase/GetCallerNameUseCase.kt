package dev.goodwy.rphone.domain.usecase

import dev.goodwy.rphone.domain.model.CallerMetadata
import dev.goodwy.rphone.domain.repository.ICallerRepository

class GetCallerNameUseCase(private val repository: ICallerRepository) {
    operator fun invoke(incomingNumber: String, cnamName: String?): CallerMetadata {
        val localContact = repository.getContactByNumber(incomingNumber)
        
        val hasLocalName = localContact != null && (
            localContact.givenName.isNotBlank() || 
            localContact.familyName.isNotBlank() || 
            localContact.nickname.isNotBlank() || 
            localContact.company.isNotBlank()
        )

        return when {
            hasLocalName -> {
                CallerMetadata(
                    number = incomingNumber,
                    name = localContact.displayName,
                    isLocalContact = true,
                    photoUri = localContact.photoUri
                )
            }
            !cnamName.isNullOrBlank() -> {
                CallerMetadata(
                    number = incomingNumber,
                    name = cnamName,
                    isLocalContact = false
                )
            }
            localContact != null -> {
                // We have a contact but no name, maybe just use its display name (which might be the number)
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
