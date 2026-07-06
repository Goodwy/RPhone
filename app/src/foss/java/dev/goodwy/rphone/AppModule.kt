package dev.goodwy.rphone

import dev.goodwy.rphone.controller.CallLogViewModel
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.DonateViewModel
import dev.goodwy.rphone.controller.PurchaseHelper
import dev.goodwy.rphone.modal.`interface`.ICallLogRepository
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.modal.repository.CallLogRepository
import dev.goodwy.rphone.modal.repository.ContactsRepository
import dev.goodwy.rphone.controller.util.PreferenceManager
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<IContactsRepository> {
        ContactsRepository(androidContext())
    }
    single<ICallLogRepository> {
        CallLogRepository(androidContext().contentResolver, androidContext(), get())
    }
    single {
        PreferenceManager(androidContext())
    }
    viewModel { ContactsViewModel(androidApplication(), get(), get()) }
    viewModel { CallLogViewModel(androidApplication(), get(), androidContext().contentResolver) }
    single<PurchaseHelper> {
        DonateViewModel(get())
    }
}