package dev.goodwy.rphone.model.repository

import dev.goodwy.rphone.model.data.Contact
import dev.goodwy.rphone.model.`interface`.ICallerRepository
import dev.goodwy.rphone.model.`interface`.IContactsRepository

class CallerRepositoryImpl(
    private val contactsRepository: IContactsRepository
) : ICallerRepository {
    override suspend fun getContactByNumber(number: String): Contact? {
        return contactsRepository.getContactByNumber(number)
    }
}
