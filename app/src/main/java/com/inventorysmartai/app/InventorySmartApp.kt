package com.inventorysmartai.app

import android.app.Application
import com.inventorysmartai.app.data.backend.BackendWarmUp
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class InventorySmartApp : Application() {

    @Inject
    lateinit var backendWarmUp: BackendWarmUp

    override fun onCreate() {
        super.onCreate()
        // Free hosting puts an idle server to sleep; waking it takes up to a minute. Pinging it now, in the
        // background, means it is usually awake by the time the user opens the assistant or imports a document.
        backendWarmUp.start()
    }
}
