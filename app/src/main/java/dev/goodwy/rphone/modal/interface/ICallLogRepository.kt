package dev.goodwy.rphone.modal.`interface`

import dev.goodwy.rphone.modal.data.CallLogEntry

interface ICallLogRepository {
    suspend fun getCallLogs(): List<CallLogEntry>
    suspend fun saveCallLog(entry: CallLogEntry)
    suspend fun deleteCallLog(number: String)
    suspend fun deleteCallLogsByIds(ids: List<Long>)
    suspend fun clearCallLogs()
}