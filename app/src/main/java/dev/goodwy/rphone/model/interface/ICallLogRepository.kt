package dev.goodwy.rphone.model.`interface`

import dev.goodwy.rphone.model.data.CallLogEntry

interface ICallLogRepository {
    suspend fun getCallLogs(): List<CallLogEntry>
    suspend fun saveCallLog(entry: CallLogEntry)
    suspend fun deleteCallLog(number: String)
    suspend fun deleteCallLogsByIds(ids: List<Long>)
    suspend fun clearCallLogs()
}