package com.canok.kargotycoon.game.engine

import com.canok.kargotycoon.game.domain.*
import com.canok.kargotycoon.game.persistence.*
import org.junit.Assert.*
import org.junit.Test

class SettingsAndTutorialTest {
    private val engine = GameEngine()

    @Test fun preferencesPersistWithoutChangingTheEconomyOrGameTime() {
        val initial = newGame(seed = 9)
        val settings = GameSettings(languageTag = "en", soundEnabled = false, hapticsEnabled = false)
        val result = engine.reduce(initial, GameCommand.ChangeSettings(CommandId("settings"), settings)) as GameResult.Applied
        val loaded = (SaveCodec().decode(SaveCodec().encode(result.state)) as SaveDecodeResult.Success).state
        assertEquals(settings, loaded.settings)
        assertEquals(initial.money, loaded.money)
        assertEquals(initial.gameTime, loaded.gameTime)
        assertEquals(initial.randomCounter, loaded.randomCounter)
    }

    @Test fun unsupportedLanguageDoesNotModifyTheState() {
        val initial = newGame(seed = 9)
        val result = engine.reduce(initial, GameCommand.ChangeSettings(CommandId("unsupported"), GameSettings(languageTag = "bad-tag")))
        assertTrue(result is GameResult.Rejected)
        assertEquals(Rejection.InvalidSettings, (result as GameResult.Rejected).reason)
        assertEquals(initial, result.state)
    }

    @Test fun tutorialVisibilitySurvivesSaveWithoutAdvancingItsStep() {
        val initial = newGame(seed = 9)
        val dismissed = (engine.reduce(initial, GameCommand.SetTutorialDismissed(CommandId("hide"), true)) as GameResult.Applied).state
        val loaded = (SaveCodec().decode(SaveCodec().encode(dismissed)) as SaveDecodeResult.Success).state
        assertTrue(loaded.tutorial.dismissed)
        assertEquals(initial.tutorial.step, loaded.tutorial.step)
        val shown = (engine.reduce(loaded, GameCommand.SetTutorialDismissed(CommandId("show"), false)) as GameResult.Applied).state
        assertFalse(shown.tutorial.dismissed)
        assertEquals(initial.tutorial.step, shown.tutorial.step)
    }
}
