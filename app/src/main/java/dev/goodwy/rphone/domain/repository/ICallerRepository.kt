package dev.goodwy.rphone.domain.repository

import dev.goodwy.rphone.modal.data.Contact

interface ICallerRepository {
    fun getContactByNumber(number: String): Contact?
}
