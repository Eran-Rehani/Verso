package app.verso.reader

import android.app.Application
import app.verso.core.importer.DocumentImporter
import app.verso.core.store.LibraryStore
import app.verso.core.store.SettingsStore
import app.verso.reader.data.db.VersoDatabase
import app.verso.reader.data.repository.BookRepository
import app.verso.reader.pdf.PdfBoxPageSource
import java.io.File

class VersoApp : Application() {
    val env: AppEnv by lazy {
        val libraryDir = File(filesDir, "library")
        val settingsFile = File(filesDir, "settings.json")
        val library = LibraryStore(libraryDir)
        val settings = SettingsStore(settingsFile)
        val pdfSource = PdfBoxPageSource(this)
        val importer = DocumentImporter(pdfSource)
        val database = VersoDatabase.getInstance(this)
        val bookRepository = BookRepository(database.bookDao())
        AppEnv(library, settings, importer, bookRepository)
    }
}
