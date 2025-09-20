package com.example.sermontimer

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sermontimer.data.TimerDataProvider
import com.example.sermontimer.domain.model.Preset
import com.example.sermontimer.tile.TileUpdateDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.google.common.truth.Truth.assertThat

@RunWith(AndroidJUnit4::class)
class TileUpdatePropagationTest {

    private lateinit var context: Context
    private lateinit var fakeDispatcher: RecordingTileUpdateDispatcher

    private val presetA = Preset(
        id = "preset_a",
        title = "Preset A",
        introSec = 60,
        mainSec = 600,
        outroSec = 120
    )

    private val presetB = presetA.copy(
        id = "preset_b",
        title = "Preset B"
    )

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        fakeDispatcher = RecordingTileUpdateDispatcher()
        TimerDataProvider.initialize(context, fakeDispatcher)

        val repository = TimerDataProvider.getRepository()
        repository.clearAll()
        repository.savePreset(presetA)
        repository.savePreset(presetB)
        repository.setDefaultPresetId(presetA.id)
        fakeDispatcher.reset()
    }

    @After
    fun tearDown() = runBlocking {
        TimerDataProvider.getRepository().clearAll()
        TimerDataProvider.initialize(context)
    }

    @Test
    fun changingDefaultPreset_requestsTileRefresh() = runBlocking {
        TimerDataProvider.getRepository().setDefaultPresetId(presetB.id)
        assertThat(fakeDispatcher.requestCount).isEqualTo(1)
    }

    @Test
    fun updatingDefaultPreset_requestsTileRefresh() = runBlocking {
        val updatedPreset = presetA.copy(title = "Preset A Updated")
        TimerDataProvider.getRepository().savePreset(updatedPreset)
        assertThat(fakeDispatcher.requestCount).isEqualTo(1)
    }

    private class RecordingTileUpdateDispatcher : TileUpdateDispatcher {
        var requestCount: Int = 0
            private set

        override fun requestTileUpdate() {
            requestCount += 1
        }

        fun reset() {
            requestCount = 0
        }
    }
}
