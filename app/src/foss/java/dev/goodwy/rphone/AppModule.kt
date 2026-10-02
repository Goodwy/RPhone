package dev.goodwy.rphone

import androidx.room.Room
import dev.goodwy.rphone.controller.CallAnalyticsViewModel
import dev.goodwy.rphone.controller.CallLogViewModel
import dev.goodwy.rphone.controller.CallNotificationManager
import dev.goodwy.rphone.controller.CallStateManager
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.DonateViewModel
import dev.goodwy.rphone.controller.PurchaseHelper
import dev.goodwy.rphone.model.`interface`.ICallerRepository
import dev.goodwy.rphone.model.`interface`.ICallLogRepository
import dev.goodwy.rphone.model.`interface`.IContactsRepository
import dev.goodwy.rphone.model.repository.CallerRepositoryImpl
import dev.goodwy.rphone.model.repository.CallLogRepository
import dev.goodwy.rphone.model.repository.ContactsRepository
import dev.goodwy.rphone.controller.CallViewModel
import dev.goodwy.rphone.controller.GetCallerNameUseCase
import dev.goodwy.rphone.controller.MainViewModel
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.model.db.RillDatabase
import dev.goodwy.rphone.model.`interface`.ICallRepository
import dev.goodwy.rphone.model.repository.CallRepositoryImpl
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
            .addMigrations(RillDatabase.MIGRATION_2_3)
            .addMigrations(RillDatabase.MIGRATION_3_4)
            .build()
    }
    single { get<RillDatabase>().privateContactDao() }
    single { get<RillDatabase>().trashedContactDao() }

    single<IContactsRepository> {
        ContactsRepository(androidContext(), get(), get())
    }
    single<ICallLogRepository> {
        CallLogRepository(androidContext(), androidContext().contentResolver,  get())
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

    viewModel { ContactsViewModel(androidApplication(), get(), get(), get()) }
    viewModel { CallLogViewModel(androidApplication(), get(), androidContext().contentResolver, get()) }
    viewModel { CallViewModel(androidContext(), get(), get()) }
    viewModel { MainViewModel(get()) }
    viewModel { CallAnalyticsViewModel(get(), get()) }
    single<PurchaseHelper> {
        DonateViewModel(get())
    }
}