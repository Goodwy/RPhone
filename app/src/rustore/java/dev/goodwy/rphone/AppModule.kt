package dev.goodwy.rphone

import androidx.room.Room
import dev.goodwy.rphone.controller.CallLogViewModel
import dev.goodwy.rphone.controller.CallNotificationManager
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.PurchaseHelper
import dev.goodwy.rphone.controller.RuStoreViewModel
import dev.goodwy.rphone.modal.`interface`.ICallLogRepository
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.modal.repository.CallLogRepository
import dev.goodwy.rphone.modal.repository.ContactsRepository
import dev.goodwy.rphone.domain.repository.ICallerRepository
import dev.goodwy.rphone.domain.usecase.GetCallerNameUseCase
import dev.goodwy.rphone.data.repository.CallerRepositoryImpl
import dev.goodwy.rphone.data.manager.CallStateManager
import dev.goodwy.rphone.controller.CallViewModel
import dev.goodwy.rphone.controller.MainViewModel
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.db.RillDatabase
import dev.goodwy.rphone.modal.`interface`.ICallRepository
import dev.goodwy.rphone.modal.repository.CallRepositoryImpl
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            RillDatabase::class.java,
            "rill_database"
        ).addMigrations(RillDatabase.MIGRATION_1_2)
            .build()
    }
    single { get<RillDatabase>().privateContactDao() }

    single<IContactsRepository> {
        ContactsRepository(androidContext(), get())
    }
    single<ICallLogRepository> {
        CallLogRepository(androidContext(), androidContext().contentResolver, get())
    }
    single {
        PreferenceManager(androidContext())
    }
    // Clean Architecture Wires
    single<ICallerRepository> { CallerRepositoryImpl(get()) }
    single { GetCallerNameUseCase(get()) }
    single { CallStateManager(get()) }
    single { CallNotificationManager(androidContext(), get()) }
    single<ICallRepository> { CallRepositoryImpl() }

    viewModel { ContactsViewModel(androidApplication(), get(), get()) }
    viewModel { CallLogViewModel(androidApplication(), get(), androidContext().contentResolver, get()) }
    viewModel { CallViewModel(androidContext(), get(), get()) }
    viewModel { MainViewModel(get()) }
    single<PurchaseHelper> {
        RuStoreViewModel(androidApplication(), get())
    }
}