package dev.goodwy.rphone.domain.repository

import dev.goodwy.rphone.modal.data.Contact

interface ICallerRepository {
    suspend fun getContactByNumber(number: String): Contact?
}
