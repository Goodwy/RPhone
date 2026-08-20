package dev.goodwy.rphone.data.repository

import android.telephony.PhoneNumberUtils
import dev.goodwy.rphone.domain.repository.ICallerRepository
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.`interface`.IContactsRepository

class CallerRepositoryImpl(
    private val contactsRepository: IContactsRepository
) : ICallerRepository {
    override suspend fun getContactByNumber(number: String): Contact? {
        val normalizedNumber = PhoneNumberUtils.normalizeNumber(number)
        return contactsRepository.getContactByNumber(normalizedNumber.ifBlank { number })
    }
}
