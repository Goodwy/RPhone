package dev.goodwy.rphone

import dev.goodwy.rphone.controller.DonateViewModel
import dev.goodwy.rphone.controller.PurchaseHelper
import org.koin.dsl.module

val appModule = module {
    includes(commonModule)
    single<PurchaseHelper> {
        DonateViewModel(get())
    }
}