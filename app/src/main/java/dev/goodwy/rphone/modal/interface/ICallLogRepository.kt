package dev.goodwy.rphone.modal.`interface`

import dev.goodwy.rphone.modal.data.CallLogEntry

interface ICallLogRepository {
    fun getCallLogs(): List<CallLogEntry>
    fun saveCallLog(entry: CallLogEntry)
    fun deleteCallLog(number: String)
    fun deleteCallLogsByIds(ids: List<Long>)
    fun clearCallLogs()
}