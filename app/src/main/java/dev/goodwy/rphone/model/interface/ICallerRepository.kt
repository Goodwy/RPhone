package dev.goodwy.rphone.model.`interface`

import dev.goodwy.rphone.model.data.Contact

interface ICallerRepository {
    suspend fun getContactByNumber(number: String): Contact?
}
