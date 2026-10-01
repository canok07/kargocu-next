package com.canok.kargotycoon.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.canok.kargotycoon.KargoApplication
import com.canok.kargotycoon.game.engine.GameCommand

class GameViewModel(application: Application) : AndroidViewModel(application) {
    val session = (application as KargoApplication).session
    val state = session.state
    fun send(command: GameCommand) = session.enqueue(command)
}
