package dev.goodwy.rphone.modal.data

enum class CallLogFilter {
    All,
    Missed,
    Contacts,
    Incoming,
    Outgoing,
    Rejected;

    companion object {
        public fun filter(logs: List<CallLogEntry>, type: CallLogFilter): List<List<CallLogEntry>> {
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

        public fun getNames(): List<String> {
            return entries.map { it.name }
        }
    }
};