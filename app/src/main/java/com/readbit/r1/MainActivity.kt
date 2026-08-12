package com.readbit.r1

import android.content.Intent
import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.text.Html
import android.text.TextPaint
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import android.widget.AdapterView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.net.toUri
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.readbit.r1.databinding.ActivityMainBinding
import com.readbit.r1.databinding.ItemBookBinding
import com.readbit.r1.databinding.ItemPreviewLineBinding
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.text.DecimalFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private val rsvpWordTextSizePx by lazy { resources.displayMetrics.density * 28f }
    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { getSharedPreferences("readbit_prefs", Context.MODE_PRIVATE) }
    private val repository by lazy { BookRepository(this) }
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val wordFitPaint by lazy {
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = rsvpWordTextSizePx
            typeface = ResourcesCompat.getFont(this@MainActivity, R.font.space_grotesk_regular)
        }
    }

    private val books = mutableListOf<BookMeta>()
    private val libraryAdapter =
        LibraryAdapter(
            books,
            onClick = { openPreview(it) },
            onLongClick = { confirmDeleteBook(it) },
        )
    private lateinit var previewAdapter: PreviewAdapter
    private val parsedWordMemoryCache = mutableMapOf<String, List<String>>()
    private val preparedBookMemoryCache = mutableMapOf<String, PreparedBook>()
    private val searchSnapshotMemoryCache = mutableMapOf<String, SearchSnapshot>()

    private var currentBook: BookMeta? = null
    private var previewWords: List<String> = emptyList()
    private var currentWords: List<String> = emptyList()
    private var previewToRsvpStart: IntArray = IntArray(0)
    private var rsvpToPreviewIndex: IntArray = IntArray(0)
    private var selectedWordIndex: Int = 0
    private var isPlaying = false
    private var currentDelayMs = 0L
    private var resumeRampStep = 0
    private val rsvpTicker = object : Runnable {
        override fun run() {
            if (!isPlaying || currentWords.isEmpty()) return
            if (selectedWordIndex >= currentWords.lastIndex) {
                pauseReading(showStatus = getString(R.string.reading_complete))
                return
            }
            selectedWordIndex += 1
            renderCurrentWord()
            previewAdapter.setSelectedWord(previewIndexForRsvpPosition(selectedWordIndex))
            currentDelayMs = smoothDelay(currentDelayMs, calculateDelayForWord(currentWords[selectedWordIndex]))
            resumeRampStep += 1
            mainHandler.postDelayed(this, currentDelayMs)
        }
    }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
            }
            importBook(uri.toString())
        }

    private val doubleTapDetector by lazy {
        GestureDetectorCompat(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                    if (binding.rsvpScreen.visibility == View.VISIBLE) {
                        pauseReading()
                        showPreview()
                        return true
                    }
                    return false
                }
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        previewAdapter = PreviewAdapter(this) { index ->
            updateSelectedWord(rsvpIndexForPreviewPosition(index), scroll = false)
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        configureFullscreen()

        binding.libraryRecycler.layoutManager = LinearLayoutManager(this)
        binding.libraryRecycler.adapter = libraryAdapter
        binding.previewRecycler.layoutManager = LinearLayoutManager(this)
        binding.previewRecycler.adapter = previewAdapter

        binding.addBookButton.setOnClickListener {
            importLauncher.launch(arrayOf("*/*"))
        }
        binding.backButton.setOnClickListener { showLibrary() }
        binding.playButton.setOnClickListener {
            if (currentWords.isNotEmpty()) {
                showRsvp()
                startReading()
            }
        }
        binding.searchButton.setOnClickListener { showSearchDialog() }
        binding.bookmarkButton.setOnClickListener { addBookmarkForCurrentWord() }
        binding.bookmarkButton.setOnLongClickListener {
            showBookmarks()
            true
        }
        binding.rsvpPlayButton.setOnClickListener { startReading() }
        binding.speedButton.setOnClickListener { showSpeedPicker() }
        binding.rsvpScreen.setOnTouchListener { _, event ->
            doubleTapDetector.onTouchEvent(event)
            true
        }

        updateSpeedLabel()
        loadLibrary()
    }

    override fun onResume() {
        super.onResume()
        configureFullscreen()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        backgroundExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (binding.previewScreen.visibility == View.VISIBLE && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    val nextPreviewIndex = previewIndexForRsvpPosition(selectedWordIndex) + 1
                    updateSelectedWord(rsvpIndexForPreviewPosition(nextPreviewIndex), scroll = true)
                    return true
                }

                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    val previousPreviewIndex = previewIndexForRsvpPosition(selectedWordIndex) - 1
                    updateSelectedWord(rsvpIndexForPreviewPosition(previousPreviewIndex), scroll = true)
                    return true
                }
            }
        }
        if (binding.rsvpScreen.visibility == View.VISIBLE && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (isPlaying) {
                        pauseReading()
                    } else {
                        stepForward()
                    }
                    return true
                }

                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (isPlaying) {
                        pauseReading()
                    } else {
                        stepBackward()
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun configureFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun loadLibrary() {
        books.clear()
        books += repository.loadBooks()
        libraryAdapter.notifyDataSetChanged()
        val empty = books.isEmpty()
        binding.libraryEmptyTitle.visibility = if (empty) View.VISIBLE else View.GONE
        binding.libraryEmptyHint.visibility = if (empty) View.VISIBLE else View.GONE
    }

    private fun importBook(uriString: String) {
        val uri = uriString.toUri()
        val document = DocumentFile.fromSingleUri(this, uri)
        if (document == null) {
            toast(R.string.import_failed)
            return
        }
        setPreviewLoading(true, R.string.loading_book, R.string.loading_book_detail)
        backgroundExecutor.execute {
            try {
                val imported = repository.importBook(document.name ?: "Untitled", uri)
                mainHandler.post {
                    setPreviewLoading(false)
                    books.add(0, imported)
                    libraryAdapter.notifyItemInserted(0)
                    binding.libraryEmptyTitle.visibility = View.GONE
                    binding.libraryEmptyHint.visibility = View.GONE
                    openPreview(imported)
                }
            } catch (_: Exception) {
                mainHandler.post {
                    setPreviewLoading(false)
                    toast(R.string.import_failed)
                }
            }
        }
    }

    private fun openPreview(book: BookMeta) {
        currentBook = books.firstOrNull { it.id == book.id } ?: book
        selectedWordIndex = 0
        previewWords = emptyList()
        currentWords = emptyList()
        previewToRsvpStart = IntArray(0)
        rsvpToPreviewIndex = IntArray(0)
        previewAdapter.submitWords(emptyList(), 0)
        binding.previewBookTitle.text = sanitizeTitle(currentBook?.title ?: book.title)
        binding.previewSelectedWord.text = ""
        binding.previewProgressBar.progress = 0
        showPreview()
        setPreviewLoading(true, R.string.opening_book, R.string.opening_book_detail)
        val preparedBook = preparedBookMemoryCache[book.id]
        if (preparedBook != null) {
            bindPreparedBook(currentBook ?: book, preparedBook)
            return
        }
        val cachedWords = parsedWordMemoryCache[book.id]
        if (cachedWords != null) {
            prepareBookAsync(currentBook ?: book, cachedWords, showLoader = true)
            return
        }
        backgroundExecutor.execute {
            try {
                val persistedPreparedBook = repository.loadPreparedBook(book)
                if (persistedPreparedBook != null) {
                    preparedBookMemoryCache[book.id] = persistedPreparedBook
                    mainHandler.post {
                        bindPreparedBook(currentBook ?: book, persistedPreparedBook)
                    }
                    return@execute
                }

                mainHandler.post {
                    setPreviewLoading(true, R.string.loading_book, R.string.loading_book_detail)
                }
                val parsed = repository.loadWords(book)
                parsedWordMemoryCache[book.id] = parsed
                val prepared = prepareBook(parsed)
                preparedBookMemoryCache[book.id] = prepared
                repository.savePreparedBook(book, prepared)
                mainHandler.post {
                    setPreviewLoading(false)
                    bindPreparedBook(currentBook ?: book, prepared)
                }
            } catch (exception: UnsupportedPdfException) {
                mainHandler.post {
                    setPreviewLoading(false)
                    toast(exception.messageRes)
                    showLibrary()
                }
            } catch (_: UnsupportedFormatException) {
                mainHandler.post {
                    setPreviewLoading(false)
                    toast(R.string.unsupported_book)
                    showLibrary()
                }
            } catch (_: Exception) {
                mainHandler.post {
                    setPreviewLoading(false)
                    toast(R.string.parse_failed)
                    showLibrary()
                }
            }
        }
    }

    private fun prepareBookAsync(book: BookMeta, parsed: List<String>, showLoader: Boolean) {
        setPreviewLoading(showLoader, R.string.loading_book, R.string.loading_book_detail)
        backgroundExecutor.execute {
            try {
                val prepared = prepareBook(parsed)
                preparedBookMemoryCache[book.id] = prepared
                repository.savePreparedBook(book, prepared)
                mainHandler.post {
                    setPreviewLoading(false)
                    bindPreparedBook(currentBook ?: book, prepared)
                }
            } catch (_: Exception) {
                mainHandler.post {
                    setPreviewLoading(false)
                    toast(R.string.parse_failed)
                    showLibrary()
                }
            }
        }
    }

    private fun bindPreparedBook(book: BookMeta, prepared: PreparedBook) {
        setPreviewLoading(false)
        previewWords = prepared.previewWords
        currentWords = prepared.rsvpWords
        previewToRsvpStart = prepared.previewToRsvpStart
        rsvpToPreviewIndex = prepared.rsvpToPreviewIndex
        if (!searchSnapshotMemoryCache.containsKey(book.id)) {
            backgroundExecutor.execute {
                searchSnapshotMemoryCache[book.id] = buildSearchSnapshot(prepared.previewWords)
            }
        }
        if (previewWords.isEmpty() || currentWords.isEmpty()) {
            toast(R.string.parse_failed)
            showLibrary()
            return
        }
        val previewIndex = book.lastWordIndex.coerceIn(0, previewWords.lastIndex)
        selectedWordIndex = rsvpIndexForPreviewPosition(previewIndex).coerceIn(0, currentWords.lastIndex)
        previewAdapter.submitWords(previewWords, previewIndex)
        updateSelectedWord(selectedWordIndex, scroll = true)
    }

    private fun updateSelectedWord(index: Int, scroll: Boolean) {
        if (currentWords.isEmpty() || previewWords.isEmpty()) return
        selectedWordIndex = index.coerceIn(0, currentWords.lastIndex)
        persistCurrentPosition(force = true)
        val previewIndex = previewIndexForRsvpPosition(selectedWordIndex)
        binding.previewSelectedWord.text = previewWords[previewIndex]
        binding.previewProgressBar.progress =
            (((previewIndex + 1).toFloat() / previewWords.size.toFloat()) * 1000f).roundToInt()
        previewAdapter.setSelectedWord(previewIndex, ensureVisible = scroll)
        if (scroll) {
            binding.previewRecycler.scrollToPosition(previewAdapter.positionForWord(previewIndex))
        }
    }

    private fun showLibrary() {
        pauseReading()
        setPreviewLoading(false)
        binding.libraryScreen.visibility = View.VISIBLE
        binding.previewScreen.visibility = View.GONE
        binding.rsvpScreen.visibility = View.GONE
    }

    private fun showPreview() {
        binding.libraryScreen.visibility = View.GONE
        binding.previewScreen.visibility = View.VISIBLE
        binding.rsvpScreen.visibility = View.GONE
    }

    private fun showRsvp() {
        binding.libraryScreen.visibility = View.GONE
        binding.previewScreen.visibility = View.GONE
        binding.rsvpScreen.visibility = View.VISIBLE
        binding.rsvpPlayButton.visibility = View.GONE
        renderCurrentWord()
    }

    private fun setPreviewLoading(
        loading: Boolean,
        titleRes: Int = R.string.loading_book,
        detailRes: Int = R.string.loading_book_detail,
    ) {
        binding.previewLoadingOverlay.visibility = if (loading) View.VISIBLE else View.GONE
        binding.loadingLabel.setText(titleRes)
        binding.loadingDetail.setText(detailRes)
    }

    private fun startReading() {
        if (currentWords.isEmpty()) return
        mainHandler.removeCallbacks(rsvpTicker)
        isPlaying = true
        resumeRampStep = 0
        currentDelayMs =
            if (currentDelayMs == 0L) {
                calculateDelayForWord(currentWords[selectedWordIndex])
            } else {
                currentDelayMs
            }
        binding.rsvpStatus.text = getString(R.string.wpm_format, getCurrentWpm())
        binding.rsvpPlayButton.visibility = View.GONE
        renderCurrentWord()
        mainHandler.postDelayed(rsvpTicker, currentDelayMs)
    }

    private fun pauseReading(showStatus: String? = null) {
        isPlaying = false
        mainHandler.removeCallbacks(rsvpTicker)
        persistCurrentPosition(force = true)
        binding.rsvpStatus.text = showStatus ?: getString(R.string.rsvp_hint)
        binding.rsvpPlayButton.visibility = View.VISIBLE
    }

    private fun stepBackward() {
        if (currentWords.isEmpty()) return
        selectedWordIndex = (selectedWordIndex - 1).coerceAtLeast(0)
        renderCurrentWord()
        previewAdapter.setSelectedWord(previewIndexForRsvpPosition(selectedWordIndex))
        persistCurrentPosition(force = true)
    }

    private fun stepForward() {
        if (currentWords.isEmpty()) return
        selectedWordIndex = (selectedWordIndex + 1).coerceAtMost(currentWords.lastIndex)
        renderCurrentWord()
        previewAdapter.setSelectedWord(previewIndexForRsvpPosition(selectedWordIndex))
        persistCurrentPosition(force = true)
    }

    private fun renderCurrentWord() {
        if (currentWords.isEmpty()) return
        val word = currentWords[selectedWordIndex]
        binding.rsvpWord.setWord(word)
        val previewIndex = previewIndexForRsvpPosition(selectedWordIndex)
        if (previewWords.isNotEmpty() && previewIndex in previewWords.indices) {
            binding.previewSelectedWord.text = previewWords[previewIndex]
            binding.previewProgressBar.progress =
                (((previewIndex + 1).toFloat() / previewWords.size.toFloat()) * 1000f).roundToInt()
        }
        persistCurrentPosition(force = selectedWordIndex % 15 == 0)
    }

    private fun calculateBaseDelay(): Long {
        val base = 60000.0 / getCurrentWpm().toDouble()
        return base.roundToInt().toLong().coerceAtLeast(60L)
    }

    private fun calculateDelayForWord(word: String): Long {
        val baseDelay = calculateBaseDelay()
        val extraDelay = sentenceEndingPause(word, baseDelay) ?: return baseDelay
        return baseDelay + extraDelay
    }

    private fun sentenceEndingPause(word: String, baseDelay: Long): Long? {
        val trimmed = word.trimEnd('"', '\'', ')', ']', '}')
        return when {
            trimmed.endsWith(".") -> (baseDelay * 1.5f).roundToInt().toLong().coerceIn(180L, 600L)
            trimmed.endsWith("!") || trimmed.endsWith("?") ->
                (baseDelay * 0.75f).roundToInt().toLong().coerceIn(70L, 300L)
            else -> null
        }
    }

    private fun smoothDelay(previous: Long, target: Long): Long {
        if (resumeRampStep < 10) {
            return (previous * 0.82 + target * 0.18).roundToInt().toLong().coerceAtLeast(target)
        }
        return target
    }

    private fun showSpeedPicker() {
        val speeds = (100..1000 step 25).toList()
        val labels = speeds.map { getString(R.string.wpm_format, it) }.toTypedArray()
        val selected = speeds.indexOf(getCurrentWpm()).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.speed)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                prefs.edit().putInt(KEY_WPM, speeds[which]).apply()
                updateSpeedLabel()
                dialog.dismiss()
            }
            .show()
    }

    private fun updateSpeedLabel() {
        binding.speedButton.text = getString(R.string.wpm_format, getCurrentWpm())
        binding.speedButton.setTextColor(ContextCompat.getColor(this, R.color.rb_accent))
    }

    private fun getCurrentWpm(): Int = prefs.getInt(KEY_WPM, 300)

    private fun toast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun addBookmarkForCurrentWord() {
        val book = currentBook ?: return
        if (previewWords.isEmpty()) return
        val previewIndex = previewIndexForRsvpPosition(selectedWordIndex)
        repository.addBookmark(book.id, previewIndex, previewWords[previewIndex])
        toast(R.string.bookmark_saved)
    }

    private fun showBookmarks() {
        val book = currentBook ?: return
        val bookmarks = repository.loadBookmarks(book.id)
        if (bookmarks.isEmpty()) {
            toast(R.string.no_bookmarks)
            return
        }
        val labels = bookmarks.map { it.word }.toTypedArray()
        val dialog =
            AlertDialog.Builder(this)
            .setTitle(book.title)
            .setItems(labels) { _, which ->
                updateSelectedWord(rsvpIndexForPreviewPosition(bookmarks[which].wordIndex), scroll = true)
            }
            .show()
        dialog.listView.onItemLongClickListener =
            AdapterView.OnItemLongClickListener { _, _, position, _ ->
                AlertDialog.Builder(this)
                    .setTitle(R.string.delete_bookmark_title)
                    .setMessage(R.string.delete_bookmark_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        repository.deleteBookmark(book.id, bookmarks[position].wordIndex)
                        dialog.dismiss()
                        showBookmarks()
                    }
                    .show()
                true
            }
    }

    private fun showSearchDialog() {
        if (previewWords.isEmpty()) return
        val currentBookId = currentBook?.id ?: return
        val input = EditText(this).apply {
            hint = getString(R.string.search)
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.search)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val rawQuery = input.text?.toString().orEmpty().trim()
                if (rawQuery.isBlank()) return@setPositiveButton
                setPreviewLoading(true, R.string.searching_book, R.string.searching_book_detail)
                backgroundExecutor.execute {
                    val snapshot =
                        searchSnapshotMemoryCache[currentBookId]
                            ?: buildSearchSnapshot(previewWords).also { searchSnapshotMemoryCache[currentBookId] = it }
                    val foundIndex = findSearchMatch(rawQuery, snapshot)
                    mainHandler.post {
                        setPreviewLoading(false)
                        if (currentBook?.id != currentBookId) return@post
                        if (foundIndex >= 0) {
                            updateSelectedWord(rsvpIndexForPreviewPosition(foundIndex), scroll = true)
                        } else {
                            toast(R.string.search_no_results)
                        }
                    }
                }
            }
            .show()
    }

    private fun confirmDeleteBook(book: BookMeta) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_book_title)
            .setMessage(getString(R.string.delete_book_message, sanitizeTitle(book.title)))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                backgroundExecutor.execute {
                    repository.deleteBook(book)
                    parsedWordMemoryCache.remove(book.id)
                    preparedBookMemoryCache.remove(book.id)
                    searchSnapshotMemoryCache.remove(book.id)
                    mainHandler.post {
                        val index = books.indexOfFirst { it.id == book.id }
                        if (index >= 0) {
                            books.removeAt(index)
                            libraryAdapter.notifyItemRemoved(index)
                        }
                        val empty = books.isEmpty()
                        binding.libraryEmptyTitle.visibility = if (empty) View.VISIBLE else View.GONE
                        binding.libraryEmptyHint.visibility = if (empty) View.VISIBLE else View.GONE
                    }
                }
            }
            .show()
    }

    private fun normalizeSearchText(text: String): String {
        return text
            .lowercase(Locale.getDefault())
            .replace(Regex("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$"), "")
            .replace(Regex("[^\\p{L}\\p{N}']+"), "")
            .trim()
    }

    private fun normalizeSearchPhrase(text: String): String {
        return text
            .lowercase(Locale.getDefault())
            .replace(Regex("[^\\p{L}\\p{N}']+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun buildSearchSnapshot(words: List<String>): SearchSnapshot {
        val exactWordIndex = LinkedHashMap<String, Int>(words.size)
        val phraseWordOffsets = IntArray(words.size)
        val normalizedWords = ArrayList<String>(words.size)
        val phraseBuilder = StringBuilder()
        words.forEachIndexed { wordIndex, word ->
            val normalized = normalizeSearchText(word)
            normalizedWords += normalized
            if (normalized.isNotEmpty() && normalized !in exactWordIndex) {
                exactWordIndex[normalized] = wordIndex
            }
            phraseWordOffsets[wordIndex] = phraseBuilder.length
            if (wordIndex > 0) {
                phraseBuilder.append(' ')
            }
            phraseBuilder.append(normalized)
        }
        return SearchSnapshot(
            exactWordIndex = exactWordIndex,
            normalizedWords = normalizedWords,
            normalizedPhraseText = phraseBuilder.toString(),
            phraseWordOffsets = phraseWordOffsets,
        )
    }

    private fun findSearchMatch(rawQuery: String, snapshot: SearchSnapshot): Int {
        val normalizedWordQuery = normalizeSearchText(rawQuery)
        if (' ' !in rawQuery.trim() && normalizedWordQuery.isNotEmpty()) {
            snapshot.exactWordIndex[normalizedWordQuery]?.let { return it }
            snapshot.normalizedWords.indexOfFirst { it.contains(normalizedWordQuery) }.takeIf { it >= 0 }?.let { return it }
        }

        val normalizedPhraseQuery = normalizeSearchPhrase(rawQuery)
        if (normalizedPhraseQuery.isBlank()) return -1
        val phraseCharIndex = snapshot.normalizedPhraseText.indexOf(normalizedPhraseQuery)
        if (phraseCharIndex < 0) return -1
        return phraseCharOffsetToWordIndex(snapshot.phraseWordOffsets, phraseCharIndex)
    }

    private fun phraseCharOffsetToWordIndex(offsets: IntArray, charOffset: Int): Int {
        var low = 0
        var high = offsets.lastIndex
        var result = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (offsets[mid] <= charOffset) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    private fun persistCurrentPosition(force: Boolean = false) {
        val bookId = currentBook?.id ?: return
        if (!force && !isPlaying) return
        val previewIndex = previewIndexForRsvpPosition(selectedWordIndex)
        val current = currentBook ?: return
        val updated = current.copy(lastWordIndex = previewIndex)
        currentBook = updated
        val existingIndex = books.indexOfFirst { it.id == bookId }
        if (existingIndex >= 0) {
            books[existingIndex] = updated
        }
        backgroundExecutor.execute {
            repository.updateLastPosition(bookId, previewIndex)
        }
    }

    private fun sanitizeTitle(title: String): String {
        return android.net.Uri.decode(title).replace('+', ' ').trim()
    }

    private fun prepareWordsForRsvp(words: List<String>): List<String> {
        return buildList {
            words.forEach { word ->
                val normalized = word.trim()
                if (normalized.isBlank()) return@forEach
                addAll(splitWordForRsvp(normalized))
            }
        }
    }

    private fun splitWordForRsvp(word: String): List<String> {
        if (fitsWordForOrp(word)) return listOf(word)
        val token = splitEdgeDecorations(word)
        if (token.core.isBlank()) return listOf(word)
        val apostropheSplit = splitByInternalApostrophes(token)
        if (apostropheSplit.size > 1) {
            return apostropheSplit.flatMap { part ->
                if (part == word) listOf(part) else splitWordForRsvp(part)
            }
        }
        val parts = mutableListOf<String>()
        var remainingCore = token.core
        var leadingDecoration = token.leading
        while (remainingCore.isNotEmpty()) {
            val trailingDecoration = if (remainingCore == token.core) token.trailing else ""
            val displayWord = leadingDecoration + remainingCore + trailingDecoration
            if (fitsWordForOrp(displayWord)) {
                parts += displayWord
                break
            }
            val splitIndex = findSplitIndex(remainingCore, leadingDecoration)
            if (splitIndex <= 1 || splitIndex >= remainingCore.length) {
                parts += displayWord
                break
            }
            parts += leadingDecoration + remainingCore.substring(0, splitIndex)
            remainingCore = remainingCore.substring(splitIndex)
            leadingDecoration = ""
        }
        return parts
    }

    private fun splitByInternalApostrophes(token: TokenDecorations): List<String> {
        val core = token.core
        if (core.length < 3) return listOf(token.leading + core + token.trailing)
        val segments = mutableListOf<String>()
        var segmentStart = 0
        for (index in core.indices) {
            val char = core[index]
            if (!isInnerApostrophe(core, index, char)) continue
            val segment = core.substring(segmentStart, index + 1)
            if (segment.isNotEmpty()) {
                segments += segment
                segmentStart = index + 1
            }
        }
        if (segments.isEmpty()) {
            return listOf(token.leading + core + token.trailing)
        }
        if (segmentStart < core.length) {
            segments += core.substring(segmentStart)
        }
        if (segments.size <= 1 || segments.any { it.isBlank() }) {
            return listOf(token.leading + core + token.trailing)
        }
        return segments.mapIndexed { index, segment ->
            buildString {
                if (index == 0) append(token.leading)
                append(segment)
                if (index == segments.lastIndex) append(token.trailing)
            }
        }
    }

    private fun isInnerApostrophe(core: String, index: Int, char: Char): Boolean {
        if (char != '\'' && char != '’') return false
        if (index <= 0 || index >= core.lastIndex) return false
        return core[index - 1].isLetterOrDigit() && core[index + 1].isLetterOrDigit()
    }

    private fun findSplitIndex(coreWord: String, leadingDecoration: String): Int {
        for (candidate in coreWord.length - 1 downTo 3) {
            val prefix = coreWord.substring(0, candidate)
            val suffixLength = coreWord.length - candidate
            if (suffixLength in 1..2) continue
            if (fitsWordForOrp(leadingDecoration + prefix)) {
                return candidate
            }
        }
        return coreWord.length / 2
    }

    private fun fitsWordForOrp(word: String): Boolean {
        val measuredWidth =
            binding.rsvpWord.width.takeIf { it > 0 }?.toFloat()
                ?: (resources.displayMetrics.widthPixels - resources.displayMetrics.density * 48f)
        val fullWidthAllowance = measuredWidth - resources.displayMetrics.density * 8f
        return wordFitPaint.measureText(word) <= fullWidthAllowance
    }

    private fun splitEdgeDecorations(word: String): TokenDecorations {
        var leadingEnd = 0
        var trailingStart = word.length
        while (leadingEnd < word.length && !word[leadingEnd].isLetterOrDigit()) {
            leadingEnd += 1
        }
        while (trailingStart > leadingEnd && !word[trailingStart - 1].isLetterOrDigit()) {
            trailingStart -= 1
        }
        val core = word.substring(leadingEnd, trailingStart)
        if (core.any { it.isLetterOrDigit() }) {
            return TokenDecorations(
                leading = word.substring(0, leadingEnd),
                core = core,
                trailing = word.substring(trailingStart),
            )
        }
        return TokenDecorations(leading = "", core = word, trailing = "")
    }

    private fun prepareBook(words: List<String>): PreparedBook {
        val preview = words.mapNotNull { word ->
            word.trim().takeIf { it.isNotBlank() }
        }
        if (preview.isEmpty()) {
            return PreparedBook(emptyList(), emptyList(), IntArray(0), IntArray(0))
        }
        val previewToRsvp = IntArray(preview.size)
        val rsvpWords = ArrayList<String>(preview.size)
        val rsvpToPreview = ArrayList<Int>(preview.size)
        preview.forEachIndexed { previewIndex, word ->
            previewToRsvp[previewIndex] = rsvpWords.size
            splitWordForRsvp(word).forEach { part ->
                rsvpWords += part
                rsvpToPreview += previewIndex
            }
        }
        return PreparedBook(
            previewWords = preview,
            rsvpWords = rsvpWords,
            previewToRsvpStart = previewToRsvp,
            rsvpToPreviewIndex = rsvpToPreview.toIntArray(),
        )
    }

    private fun previewIndexForRsvpPosition(rsvpIndex: Int): Int {
        if (rsvpToPreviewIndex.isEmpty()) return 0
        return rsvpToPreviewIndex[rsvpIndex.coerceIn(0, rsvpToPreviewIndex.lastIndex)]
    }

    private fun rsvpIndexForPreviewPosition(previewIndex: Int): Int {
        if (previewToRsvpStart.isEmpty()) return 0
        return previewToRsvpStart[previewIndex.coerceIn(0, previewToRsvpStart.lastIndex)]
    }

    companion object {
        private const val KEY_WPM = "reader_wpm"
    }
}

data class BookMeta(
    val id: String,
    val title: String,
    val extension: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val importedAt: Long,
    val lastWordIndex: Int,
)

data class BookmarkEntry(
    val wordIndex: Int,
    val word: String,
)

data class PreparedBook(
    val previewWords: List<String>,
    val rsvpWords: List<String>,
    val previewToRsvpStart: IntArray,
    val rsvpToPreviewIndex: IntArray,
)

data class TokenDecorations(
    val leading: String,
    val core: String,
    val trailing: String,
)

data class SearchSnapshot(
    val exactWordIndex: Map<String, Int>,
    val normalizedWords: List<String>,
    val normalizedPhraseText: String,
    val phraseWordOffsets: IntArray,
)

class LibraryAdapter(
    private val items: List<BookMeta>,
    private val onClick: (BookMeta) -> Unit,
    private val onLongClick: (BookMeta) -> Unit,
) : RecyclerView.Adapter<LibraryAdapter.BookViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = ItemBookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookViewHolder(binding, onClick, onLongClick)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        holder.bind(items[position])
    }

    class BookViewHolder(
        private val binding: ItemBookBinding,
        private val onClick: (BookMeta) -> Unit,
        private val onLongClick: (BookMeta) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: BookMeta) {
            binding.bookTitle.text = android.net.Uri.decode(item.title).replace('+', ' ')
            val formatter = DecimalFormat("#.#")
            val sizeMb = formatter.format(item.fileSizeBytes / (1024f * 1024f))
            binding.bookMeta.text = "${item.extension.uppercase(Locale.getDefault())} • ${sizeMb} MB"
            binding.root.setOnClickListener { onClick(item) }
            binding.root.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }
    }
}

class PreviewAdapter(
    private val context: Context,
    private val onWordSelected: (Int) -> Unit,
) : RecyclerView.Adapter<PreviewAdapter.PreviewViewHolder>() {
    private var allWords: List<String> = emptyList()
    private var selectedWord: Int = 0
    private val chunkSize = 30

    fun submitWords(words: List<String>, selectedWord: Int) {
        this.allWords = words
        this.selectedWord = selectedWord
        notifyDataSetChanged()
    }

    fun setSelectedWord(selectedWord: Int, ensureVisible: Boolean = false) {
        if (allWords.isEmpty()) return
        val previousLine = positionForWord(this.selectedWord)
        this.selectedWord = selectedWord
        val nextLine = positionForWord(selectedWord)
        if (previousLine == nextLine) {
            notifyItemChanged(nextLine)
            return
        }
        if (previousLine >= 0) notifyItemChanged(previousLine)
        if (nextLine >= 0) notifyItemChanged(nextLine)
    }

    fun getSelectedWord(): Int = selectedWord

    fun positionForWord(wordIndex: Int): Int {
        if (allWords.isEmpty()) return 0
        return (wordIndex.coerceIn(0, allWords.lastIndex) / chunkSize)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewViewHolder {
        val binding =
            ItemPreviewLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PreviewViewHolder(binding, onWordSelected)
    }

    override fun getItemCount(): Int = if (allWords.isEmpty()) 0 else (allWords.size + chunkSize - 1) / chunkSize

    override fun onBindViewHolder(holder: PreviewViewHolder, position: Int) {
        val startIndex = position * chunkSize
        val endIndex = (startIndex + chunkSize).coerceAtMost(allWords.size)
        val chunk = allWords.subList(startIndex, endIndex)
        holder.bind(chunk, startIndex, selectedWord)
    }

    class PreviewViewHolder(
        private val binding: ItemPreviewLineBinding,
        private val onWordSelected: (Int) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(words: List<String>, startIndex: Int, selectedWord: Int) {
            binding.previewLineView.bind(words, startIndex, selectedWord, onWordSelected)
        }
    }
}

class BookRepository(private val context: Context) {
    private val booksFile = File(context.filesDir, "library.json")
    private val libraryDir = File(context.filesDir, "library").apply { mkdirs() }
    private val cacheDir = File(context.filesDir, "parsed").apply { mkdirs() }
    private val preparedDir = File(context.filesDir, "prepared").apply { mkdirs() }
    private val bookmarkDir = File(context.filesDir, "bookmarks").apply { mkdirs() }

    fun loadBooks(): List<BookMeta> {
        if (!booksFile.exists()) return emptyList()
        val array = JSONArray(booksFile.readText())
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    BookMeta(
                        id = item.getString("id"),
                        title = android.net.Uri.decode(item.getString("title")).replace('+', ' '),
                        extension = item.getString("extension"),
                        fileName = item.getString("fileName"),
                        fileSizeBytes = item.getLong("fileSizeBytes"),
                        importedAt = item.getLong("importedAt"),
                        lastWordIndex = item.optInt("lastWordIndex", 0),
                    ),
                )
            }
        }
    }

    fun importBook(name: String, uri: android.net.Uri): BookMeta {
        val decodedName = android.net.Uri.decode(name).replace('+', ' ')
        val extension = decodedName.substringAfterLast('.', "").lowercase(Locale.getDefault())
        val id = UUID.randomUUID().toString()
        val fileName = "$id.${extension.ifBlank { "bin" }}"
        val targetFile = File(libraryDir, fileName)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            FileOutputStream(targetFile).use { output -> input.copyTo(output) }
        }
        val meta =
            BookMeta(
                id = id,
                title = decodedName.substringBeforeLast('.'),
                extension = extension,
                fileName = fileName,
                fileSizeBytes = targetFile.length(),
                importedAt = System.currentTimeMillis(),
                lastWordIndex = 0,
            )
        val books = loadBooks().toMutableList()
        books.add(0, meta)
        saveBooks(books)
        return meta
    }

    fun deleteBook(book: BookMeta) {
        File(libraryDir, book.fileName).takeIf { it.exists() }?.delete()
        cacheFile(book).takeIf { it.exists() }?.delete()
        preparedDir(book).takeIf { it.exists() }?.deleteRecursively()
        legacyPreparedFile(book).takeIf { it.exists() }?.delete()
        bookmarkFile(book.id).takeIf { it.exists() }?.delete()
        saveBooks(loadBooks().filterNot { it.id == book.id })
    }

    fun hasCachedWords(book: BookMeta): Boolean {
        return cacheFile(book).exists()
    }

    fun loadPreparedBook(book: BookMeta): PreparedBook? {
        val directory = preparedDir(book)
        if (!directory.exists()) {
            legacyPreparedFile(book).takeIf { it.exists() }?.delete()
            return null
        }
        return try {
            val previewWords = cacheFile(book).readLines().filter { it.isNotBlank() }
            if (previewWords.isEmpty()) return null
            PreparedBook(
                previewWords = previewWords,
                rsvpWords = preparedRsvpFile(book).readLines(),
                previewToRsvpStart = preparedPreviewToRsvpFile(book).readIntList(),
                rsvpToPreviewIndex = preparedRsvpToPreviewFile(book).readIntList(),
            )
        } catch (_: Exception) {
            directory.deleteRecursively()
            legacyPreparedFile(book).takeIf { it.exists() }?.delete()
            null
        }
    }

    fun savePreparedBook(book: BookMeta, preparedBook: PreparedBook) {
        preparedDir(book).apply { mkdirs() }
        preparedRsvpFile(book).writeText(preparedBook.rsvpWords.joinToString(separator = "\n"))
        preparedPreviewToRsvpFile(book).writeText(preparedBook.previewToRsvpStart.joinToString(separator = "\n"))
        preparedRsvpToPreviewFile(book).writeText(preparedBook.rsvpToPreviewIndex.joinToString(separator = "\n"))
        legacyPreparedFile(book).takeIf { it.exists() }?.delete()
    }

    fun addBookmark(bookId: String, wordIndex: Int, word: String) {
        val bookmarks = loadBookmarks(bookId).toMutableList()
        if (bookmarks.none { it.wordIndex == wordIndex }) {
            bookmarks += BookmarkEntry(wordIndex = wordIndex, word = word)
            saveBookmarks(bookId, bookmarks.sortedBy { it.wordIndex })
        }
    }

    fun loadBookmarks(bookId: String): List<BookmarkEntry> {
        val file = bookmarkFile(bookId)
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(BookmarkEntry(item.getInt("wordIndex"), item.getString("word")))
            }
        }
    }

    fun deleteBookmark(bookId: String, wordIndex: Int) {
        val updated = loadBookmarks(bookId).filterNot { it.wordIndex == wordIndex }
        saveBookmarks(bookId, updated)
    }

    fun updateLastPosition(bookId: String, wordIndex: Int) {
        val updated =
            loadBooks().map {
                if (it.id == bookId) it.copy(lastWordIndex = wordIndex) else it
            }
        saveBooks(updated)
    }

    fun loadWords(book: BookMeta): List<String> {
        val cacheFile = cacheFile(book)
        if (cacheFile.exists()) {
            return cacheFile.readLines().filter { it.isNotBlank() }
        }
        val sourceFile = File(libraryDir, book.fileName)
        val text =
            when (book.extension.lowercase(Locale.getDefault())) {
                "txt", "md", "markdown" -> sourceFile.readTextSafely()
                "html", "htm", "xhtml", "xml" -> htmlToText(sourceFile.readTextSafely())
                "epub" -> parseEpub(sourceFile)
                "pdf" -> parsePdf(sourceFile)
                else -> throw UnsupportedFormatException()
            }
        val words = tokenize(text)
        cacheFile.writeText(words.joinToString(separator = "\n"))
        return words
    }

    private fun cacheFile(book: BookMeta): File = File(cacheDir, "${book.id}.txt")
    private fun preparedDir(book: BookMeta): File = File(preparedDir, book.id)
    private fun preparedRsvpFile(book: BookMeta): File = File(preparedDir(book), "rsvp.txt")
    private fun preparedPreviewToRsvpFile(book: BookMeta): File = File(preparedDir(book), "preview_to_rsvp.txt")
    private fun preparedRsvpToPreviewFile(book: BookMeta): File = File(preparedDir(book), "rsvp_to_preview.txt")
    private fun legacyPreparedFile(book: BookMeta): File = File(preparedDir, "${book.id}.json")
    private fun bookmarkFile(bookId: String): File = File(bookmarkDir, "$bookId.json")

    private fun saveBookmarks(bookId: String, bookmarks: List<BookmarkEntry>) {
        val array = JSONArray()
        bookmarks.forEach { bookmark ->
            array.put(
                JSONObject()
                    .put("wordIndex", bookmark.wordIndex)
                    .put("word", bookmark.word),
            )
        }
        bookmarkFile(bookId).writeText(array.toString())
    }

    private fun saveBooks(books: List<BookMeta>) {
        val array = JSONArray()
        books.forEach { book ->
            array.put(
                JSONObject()
                    .put("id", book.id)
                    .put("title", book.title)
                    .put("extension", book.extension)
                    .put("fileName", book.fileName)
                    .put("fileSizeBytes", book.fileSizeBytes)
                    .put("importedAt", book.importedAt)
                    .put("lastWordIndex", book.lastWordIndex),
            )
        }
        booksFile.writeText(array.toString())
    }

    private fun htmlToText(html: String): String {
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
    }

    private fun parseEpub(file: File): String {
        val builder = StringBuilder()
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase(Locale.getDefault())
                if (!entry.isDirectory && (name.endsWith(".html") || name.endsWith(".xhtml") || name.endsWith(".htm") || name.endsWith(".xml"))) {
                    builder.append(htmlToText(zip.readAllText()))
                    builder.append('\n')
                } else if (!entry.isDirectory && name.endsWith(".txt")) {
                    builder.append(zip.readAllText())
                    builder.append('\n')
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return builder.toString()
    }

    private fun parsePdf(file: File): String {
        if (Build.VERSION.SDK_INT < 35) {
            throw UnsupportedPdfException(R.string.pdf_requires_android_15)
        }
        val builder = StringBuilder()
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(descriptor).use { renderer ->
            for (pageIndex in 0 until renderer.pageCount) {
                renderer.openPage(pageIndex).use { page ->
                    page.textContents.forEach { content ->
                        builder.append(content.text)
                        builder.append(' ')
                    }
                    builder.append('\n')
                }
            }
        }
        descriptor.close()
        return builder.toString()
    }

    private fun tokenize(text: String): List<String> {
        return text
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(" ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun File.readTextSafely(): String {
        val bytes = readBytes()
        val encodings = listOf(Charsets.UTF_8, Charset.forName("UTF-16"), Charset.forName("ISO-8859-1"))
        for (charset in encodings) {
            try {
                return bytes.toString(charset)
            } catch (_: Exception) {
            }
        }
        return bytes.toString(Charsets.UTF_8)
    }

    private fun InputStream.readAllText(): String {
        return InputStreamReader(this, Charsets.UTF_8).readText()
    }

    private fun File.readIntList(): IntArray {
        return readLines()
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.toInt() }
            .toList()
            .toIntArray()
    }
}

class UnsupportedFormatException : Exception()

class UnsupportedPdfException(val messageRes: Int) : Exception()
