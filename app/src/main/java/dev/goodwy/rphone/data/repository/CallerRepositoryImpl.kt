package dev.goodwy.rphone.data.repository

import dev.goodwy.rphone.domain.repository.ICallerRepository
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.`interface`.IContactsRepository

class CallerRepositoryImpl(
    private val contactsRepository: IContactsRepository
) : ICallerRepository {
    override fun getContactByNumber(number: String): Contact? {
        return contactsRepository.getContactByNumber(number)
    }
}
