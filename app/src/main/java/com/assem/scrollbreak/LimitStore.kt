package com.assem.scrollbreak

import com.assem.scrollbreak.core.LimitBook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The per-app limits in force, shared by the screens and the service. All rules about what may be
 * changed live in [LimitBook]; this only keeps the current book, saves it, and tells the service
 * when the set of limited apps changed so it can watch them.
 */
object LimitStore {
    private val _book = MutableStateFlow(LimitBook())
    val book: StateFlow<LimitBook> = _book.asStateFlow()

    /** Set by the service: called after any change so it can watch the right packages. */
    @Volatile var onChanged: (() -> Unit)? = null

    fun load(json: String?) {
        _book.value = LimitBook.fromJson(json)
    }

    fun isLimited(pkg: String): Boolean = _book.value.isLimited(pkg, System.currentTimeMillis())

    fun limitedPackages(): Set<String> = _book.value.limitedPackages(System.currentTimeMillis())

    /** Milliseconds ScrollBreak must refuse to be uninstalled or switched off because of limits. */
    fun protectionRemainingMs(): Long = _book.value.protectionRemainingMs(System.currentTimeMillis())

    /** Each of these returns an error message to show, or null when the change was made. */
    @Synchronized
    fun setDaily(pkg: String, minutes: Int): String? =
        commit(_book.value.setDaily(pkg, minutes, System.currentTimeMillis()))

    @Synchronized
    fun removeDaily(pkg: String): String? = commit(_book.value.removeDaily(pkg, System.currentTimeMillis()))

    @Synchronized
    fun startBlock(pkg: String, minutes: Int): String? =
        commit(_book.value.startBlock(pkg, minutes, System.currentTimeMillis()))

    /** Drops every limit and block, ignoring the locks. Only the emergency password (and the debug broadcast) may call this. */
    @Synchronized
    fun clearAll() {
        commit(LimitBook.Change(LimitBook()))
    }

    fun debugClear() = clearAll()

    private fun commit(change: LimitBook.Change): String? {
        if (change.error != null) return change.error
        _book.value = change.book
        AppSettings.limitsJson = change.book.toJson()
        onChanged?.invoke()
        return null
    }
}
