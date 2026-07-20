package dev.goodwy.rphone.modal.data

import android.content.Context
import dev.goodwy.rphone.R

enum class CallLogFilter(val stringRes: Int) {
    All(R.string.filter_all),
    Missed(R.string.filter_missed),
    Contacts(R.string.contacts),
    Incoming(R.string.filter_incoming),
    Outgoing(R.string.filter_outgoing),
    Rejected(R.string.filter_rejected);

    companion object {
        fun filter(logs: List<CallLogEntry>, type: CallLogFilter): List<List<CallLogEntry>> {
            val filteredList = when (type) {
                All -> logs
                Missed -> logs.filter { it.type == android.provider.CallLog.Calls.MISSED_TYPE }
                Contacts -> logs.filter { it.contactId != null }
                Incoming -> logs.filter { it.type == android.provider.CallLog.Calls.INCOMING_TYPE }
                Outgoing -> logs.filter { it.type == android.provider.CallLog.Calls.OUTGOING_TYPE }
                Rejected -> logs.filter { it.type == android.provider.CallLog.Calls.REJECTED_TYPE }
            }
//            return filteredList.groupBy { formatDateHeader(it.date) }.values.toList()
            return filteredList.groupBy { it.date }.values.toList()
        }

        fun getNames(context: Context): List<String> {
            return entries.map { context.getString(it.stringRes) }
        }
    }
}