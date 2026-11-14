package app.verso.reader

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.verso.core.importer.DocumentImporter
import app.verso.core.model.Document
import kotlinx.coroutines.flow.MutableStateFlow
import app.verso.core.store.LibraryStore
import app.verso.core.store.ReaderSettings
import app.verso.core.store.SettingsStore
import app.verso.reader.data.db.VersoDatabase
import app.verso.reader.data.repository.BookRepository
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SettingsRecoveryTest {
    @Test
    fun failedSaveKeepsReaderAndLaterWriteSavesLatestSettings() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val dir = Files.createTempDirectory("settings-recovery").toFile()
        val settingsDir = File(dir, "settings").apply { mkdirs() }
        val savedDir = File(dir, "saved")
        val file = File(settingsDir, "settings.json")
        val store = SettingsStore(file)
        val initial = ReaderSettings(fontSizeSp = 22f)
        store.save(initial)
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), VersoDatabase::class.java).allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
        val owner = ViewModelStore()
        try {
            val vm = AppViewModel(AppEnv(LibraryStore(dir), store, DocumentImporter(), BookRepository(db, dir)), dispatcher)
            owner.put("vm", vm)
            advanceUntilIdle()
            val reader = Screen.Reading("id", "Document", Document("Document", emptyList()), 0, 0)
            @Suppress("UNCHECKED_CAST")
            val screen = AppViewModel::class.java.getDeclaredField("_screen").apply { isAccessible = true }.get(vm) as MutableStateFlow<Screen>
            screen.value = reader
            assertTrue(settingsDir.renameTo(savedDir))
            settingsDir.writeText("blocks directory")
            vm.updateSettings(initial.copy(fontSizeSp = 24f))
            advanceUntilIdle()
            assertEquals(reader, vm.screen.value)
            assertTrue(vm.settingsSaveError.value)
            vm.dismissSettingsSaveError()
            assertFalse(vm.settingsSaveError.value)
            vm.updateSettings(initial.copy(fontSizeSp = 26f))
            advanceUntilIdle()
            assertTrue(vm.settingsSaveError.value)
            assertEquals(26f, vm.settings.value.fontSizeSp)
            assertEquals(initial, SettingsStore(File(savedDir, "settings.json")).load())
            assertTrue(settingsDir.delete())
            assertTrue(savedDir.renameTo(settingsDir))
            vm.retrySettingsSave()
            advanceUntilIdle()
            assertFalse(vm.settingsSaveError.value)
            assertEquals(reader, vm.screen.value)
            assertEquals(26f, store.load().fontSizeSp)
        } finally {
            owner.clear()
            db.close()
            Dispatchers.resetMain()
        }
    }
}
