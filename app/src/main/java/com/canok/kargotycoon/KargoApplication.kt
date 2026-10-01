package com.canok.kargotycoon

import android.app.Application
import com.canok.kargotycoon.data.SessionController
import java.io.File

class KargoApplication : Application() {
    val session by lazy { SessionController(File(filesDir, "saves")) }
    override fun onCreate() {
        super.onCreate()
        session.start()
    }
}
