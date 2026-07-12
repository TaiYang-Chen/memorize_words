package com.chen.memorizewords.data.wordbook.repository.wordbook

import android.content.Context
import com.chen.memorizewords.core.network.CoreNetworkHeaders
import com.chen.memorizewords.data.sync.remoteapi.di.DownloadHttpClient
import com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook.WordDto
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.model.WordBookContentPackage
import com.google.gson.Gson
import com.google.gson.JsonParseException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

interface WordBookContentPackageImporter {
    suspend fun importPackage(
        book: WordBook,
        contentPackage: WordBookContentPackage,
        beforeImport: suspend () -> Unit = {},
        progress: suspend (downloadedWords: Int, totalWords: Int) -> Unit
    ): WordBookPackageImportResult
}

class HttpWordBookContentPackageImporter @Inject constructor(
    @ApplicationContext context: Context,
    @param:DownloadHttpClient
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val contentLocalStore: WordBookContentLocalStore
) : WordBookContentPackageImporter {
    private val cacheDir = File(context.cacheDir, "wordbook-packages")

    override suspend fun importPackage(
        book: WordBook,
        contentPackage: WordBookContentPackage,
        beforeImport: suspend () -> Unit,
        progress: suspend (downloadedWords: Int, totalWords: Int) -> Unit
    ): WordBookPackageImportResult = withContext(Dispatchers.IO) {
        cacheDir.mkdirs()
        val packageFile = File(cacheDir, "wordbook-${book.id}-${contentPackage.contentVersion}.zip")
        try {
            val actualSize = downloadPackage(contentPackage.url, packageFile)
            if (contentPackage.sizeBytes > 0L && actualSize != contentPackage.sizeBytes) {
                throw WordBookPackageValidationException(
                    "Package size mismatch: expected=${contentPackage.sizeBytes}, actual=$actualSize"
                )
            }
            val actualSha256 = packageFile.sha256()
            if (!actualSha256.equals(contentPackage.sha256, ignoreCase = true)) {
                throw WordBookPackageValidationException("Package sha256 mismatch")
            }
            return@withContext ZipFile(packageFile).use { zipFile ->
                importZip(
                    zipFile = zipFile,
                    book = book,
                    contentPackage = contentPackage,
                    beforeImport = beforeImport,
                    progress = progress
                )
            }
        } finally {
            packageFile.delete()
        }
    }

    private fun downloadPackage(url: String, destination: File): Long {
        val request = Request.Builder()
            .url(url)
            .header(CoreNetworkHeaders.SKIP_AUTHORIZATION, "true")
            .header("Accept", "application/zip, application/octet-stream;q=0.9, */*;q=0.8")
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Package download failed: http ${response.code}")
            }
            val body = response.body ?: throw IOException("Package download failed: empty body")
            destination.outputStream().use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }
        }
        return destination.length()
    }

    private suspend fun importZip(
        zipFile: ZipFile,
        book: WordBook,
        contentPackage: WordBookContentPackage,
        beforeImport: suspend () -> Unit,
        progress: suspend (downloadedWords: Int, totalWords: Int) -> Unit
    ): WordBookPackageImportResult {
        val manifestEntry = zipFile.getEntry(MANIFEST_FILE)
            ?: throw WordBookPackageValidationException("manifest.json missing")
        val manifest = try {
            zipFile.getInputStream(manifestEntry).reader(Charsets.UTF_8).use { reader ->
                gson.fromJson(reader, WordBookPackageManifest::class.java)
            }
        } catch (exception: JsonParseException) {
            throw WordBookPackageValidationException(
                "manifest.json invalid: ${exception.message.orEmpty()}"
            )
        } ?: throw WordBookPackageValidationException("manifest.json invalid")

        manifest.validate(book, contentPackage)

        val wordsEntry = zipFile.getEntry(manifest.wordsFile)
            ?: throw WordBookPackageValidationException("${manifest.wordsFile} missing")
        validateWords(zipFile, wordsEntry.name, manifest)
        beforeImport()

        return importWords(
            zipFile = zipFile,
            wordsFile = wordsEntry.name,
            bookId = book.id,
            totalWords = manifest.totalWords,
            progress = progress
        )
    }

    private fun validateWords(
        zipFile: ZipFile,
        wordsFile: String,
        manifest: WordBookPackageManifest
    ) {
        val wordsEntry = zipFile.getEntry(wordsFile)
            ?: throw WordBookPackageValidationException("$wordsFile missing")
        val wordsDigest = MessageDigest.getInstance("SHA-256")
        var parsedCount = 0
        DigestInputStream(zipFile.getInputStream(wordsEntry), wordsDigest).use { digestInput ->
            BufferedReader(InputStreamReader(digestInput, Charsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    parseWordLine(line)
                    parsedCount++
                }
            }
        }
        val actualWordsSha256 = wordsDigest.hex()
        if (!actualWordsSha256.equals(manifest.wordsSha256, ignoreCase = true)) {
            throw WordBookPackageValidationException("words.ndjson sha256 mismatch")
        }
        if (parsedCount != manifest.totalWords) {
            throw WordBookPackageValidationException(
                "Word count mismatch: expected=${manifest.totalWords}, actual=$parsedCount"
            )
        }
    }

    private suspend fun importWords(
        zipFile: ZipFile,
        wordsFile: String,
        bookId: Long,
        totalWords: Int,
        progress: suspend (downloadedWords: Int, totalWords: Int) -> Unit
    ): WordBookPackageImportResult {
        val wordsEntry = zipFile.getEntry(wordsFile)
            ?: throw WordBookPackageValidationException("$wordsFile missing")
        val batch = ArrayList<WordDto>(IMPORT_BATCH_SIZE)
        var imported = 0
        BufferedReader(InputStreamReader(zipFile.getInputStream(wordsEntry), Charsets.UTF_8)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                batch += parseWordLine(line)
                if (batch.size < IMPORT_BATCH_SIZE) continue
                contentLocalStore.persistPage(bookId, batch.toList())
                imported += batch.size
                batch.clear()
                progress(imported, totalWords)
            }
        }
        if (batch.isNotEmpty()) {
            contentLocalStore.persistPage(bookId, batch.toList())
            imported += batch.size
            batch.clear()
            progress(imported, totalWords)
        }
        return WordBookPackageImportResult(importedWords = imported, totalWords = totalWords)
    }

    private fun parseWordLine(line: String): WordDto {
        return try {
            gson.fromJson(line, WordDto::class.java)
                ?: throw WordBookPackageValidationException("words.ndjson contains invalid word")
        } catch (exception: JsonParseException) {
            throw WordBookPackageValidationException(
                "words.ndjson contains invalid json: ${exception.message.orEmpty()}"
            )
        }
    }

    private fun WordBookPackageManifest.validate(
        book: WordBook,
        contentPackage: WordBookContentPackage
    ) {
        if (bookId != book.id) {
            throw WordBookPackageValidationException("manifest bookId mismatch")
        }
        if (contentVersion != contentPackage.contentVersion) {
            throw WordBookPackageValidationException("manifest contentVersion mismatch")
        }
        if (schemaVersion != contentPackage.schemaVersion) {
            throw WordBookPackageValidationException("manifest schemaVersion mismatch")
        }
        if (book.totalWords > 0 && totalWords != book.totalWords) {
            throw WordBookPackageValidationException("manifest totalWords mismatch")
        }
        if (wordsFile.isBlank()) {
            throw WordBookPackageValidationException("manifest wordsFile missing")
        }
        if (wordsSha256.isBlank()) {
            throw WordBookPackageValidationException("manifest wordsSha256 missing")
        }
    }
}

data class WordBookPackageImportResult(
    val importedWords: Int,
    val totalWords: Int
)

class WordBookPackageValidationException(message: String) : IllegalStateException(message)

private data class WordBookPackageManifest(
    val schemaVersion: Int = 0,
    val bookId: Long = 0L,
    val contentVersion: Long = 0L,
    val totalWords: Int = 0,
    val generatedAt: String? = null,
    val wordsFile: String = "",
    val wordsSha256: String = ""
)

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        DigestInputStream(input, digest).use { digestInput ->
            digestInput.copyTo(OutputSink)
        }
    }
    return digest.hex()
}

private fun MessageDigest.hex(): String {
    return digest().joinToString(separator = "") { byte ->
        "%02x".format(Locale.US, byte)
    }
}

private object OutputSink : java.io.OutputStream() {
    override fun write(b: Int) = Unit
    override fun write(b: ByteArray, off: Int, len: Int) = Unit
}

private const val MANIFEST_FILE = "manifest.json"
private const val IMPORT_BATCH_SIZE = 150
