/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android.control.page.window

import android.util.Log
import net.bible.android.control.ApplicationScope
import net.bible.android.control.event.EventManager
import net.bible.android.control.event.passage.SynchronizeWindowsEvent
import net.bible.android.control.event.passage.CurrentVerseChangedEvent
import net.bible.android.control.event.window.NumberOfWindowsChangedEvent
import net.bible.android.control.event.window.WindowSizeChangedEvent
import net.bible.android.control.page.ChapterVerse
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.service.common.Logger

import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil

import java.util.HashMap

import javax.inject.Inject

/**
 * Central control of windows especially synchronization
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
open class WindowControl @Inject constructor(
        val windowRepository: WindowRepository,
        private val eventManager: EventManager
) : ActiveWindowPageManagerProvider {

    private var isSeparatorMoving = false
    private var stoppedMovingTime: Long = 0
    val windowSync: WindowSync = WindowSync(windowRepository)

    private val logger = Logger(this.javaClass.name)

    override val activeWindowPageManager: CurrentPageManager
        get() = activeWindow.pageManager

    val isMultiWindow: Boolean
        get() = windowRepository.isMultiWindow

    var activeWindow: Window
        get() = windowRepository.activeWindow
        set(currentActiveWindow) {
            windowRepository.activeWindow = currentActiveWindow
        }

    val activeWindowPosition get() = windowRepository.windowList.indexOf(activeWindow)
    fun windowPosition(windowId: Long) = windowRepository.windowList.indexOf(windowRepository.getWindow(windowId))

    /**
     * Get current chapter.verse for each window displaying a Bible
     *
     * @return Map of window num to verse num
     */
    private// get page offsets to maintain for each window
    val windowChapterVerseMap: Map<Window, ChapterVerse>
        get() {
            val windowVerseMap = HashMap<Window, ChapterVerse>()
            for (window in windowRepository.windows) {
                val currentPage = window.pageManager.currentPage
                if (BookCategory.BIBLE == currentPage.currentDocument?.bookCategory) {
                    val chapterVerse = ChapterVerse.fromVerse(KeyUtil.getVerse(currentPage.singleKey))
                    windowVerseMap[window] = chapterVerse
                }
            }
            return windowVerseMap
        }

    init {
        eventManager.register(this)
    }

    fun isActiveWindow(window: Window): Boolean {
        return window == windowRepository.activeWindow
    }

    /**
     * Show link using whatever is the current Bible in the Links window
     */
    fun showLinkUsingDefaultBible(key: Key) {
        val linksWindow = windowRepository.dedicatedLinksWindow
        val currentBiblePage = linksWindow.pageManager.currentBible

        val isBible = windowRepository.activeWindow.pageManager.isBibleShown
        val bibleDoc = windowRepository.activeWindow.pageManager.currentBible.currentDocument

        // default either to links window bible or if closed then active window bible
        val defaultBible = if (currentBiblePage.isCurrentDocumentSet) {
            currentBiblePage.currentDocument
        } else {
            windowRepository.activeWindow.pageManager.currentBible.currentDocument
        }
        if(defaultBible == null) {
            Log.e(TAG, "Default bible is null! Can't show link.")
            return
        }
        showLink(if (isBible && bibleDoc != null) bibleDoc else defaultBible, key)
    }

    fun showLink(document: Book, key: Key) {
        val linksWindow = windowRepository.dedicatedLinksWindow
        linksWindow.restoreOngoing = true
        val linksWindowWasVisible = linksWindow.isVisible

        linksWindow.initialisePageStateIfClosed(activeWindow)

        //TODO do not set links window active -  currently need to set links window to active
        // window otherwise BibleContentMediator logic does not refresh that window
        windowRepository.activeWindow = linksWindow


        // redisplay the current page
        if (!linksWindowWasVisible) {
            linksWindow.windowState = WindowState.SPLIT
        }

        linksWindow.pageManager.setCurrentDocumentAndKey(document, key)

        if (!linksWindowWasVisible) {
            eventManager.post(NumberOfWindowsChangedEvent(windowChapterVerseMap))
        }
        linksWindow.restoreOngoing = false
    }


    fun addNewWindow(): Window {
        val oldActiveWindow = activeWindow
        val window = windowRepository.addNewWindow()

        // default state to active window
        if (!isActiveWindow(window)) {
            window.isSynchronised = oldActiveWindow.isSynchronised
            if (windowRepository.isMaximisedState) {
                activeWindow = window
            }
        }

        window.updateText()

        // redisplay the current page
        eventManager.post(NumberOfWindowsChangedEvent(windowChapterVerseMap))

        return window
    }

    fun addNewWindow(document: Book, key: Key): Window {
        val window = windowRepository.addNewWindow()
        val pageManager = window.pageManager
        window.isSynchronised = false
        pageManager.setCurrentDocumentAndKey(document, key)

        if (windowRepository.isMaximisedState) {
            activeWindow = window
        }

        // redisplay the current page
        eventManager.post(NumberOfWindowsChangedEvent(windowChapterVerseMap))

        return window
    }

    fun minimiseWindow(window: Window, force: Boolean = false) {
        if(force || isWindowMinimisable(window)) {
            windowRepository.minimise(window)

            // redisplay the current page
            eventManager.post(NumberOfWindowsChangedEvent(windowChapterVerseMap))
        }
    }

    fun maximiseWindow(window: Window) {
        windowRepository.minimisedWindows.forEach {
            it.wasMinimised = true
        }
        windowRepository.windowList.forEach {
            if (it != window) {
                if(it.isPinMode) {
                    it.windowState = WindowState.MAXIMISED
                } else {
                    it.windowState = WindowState.MINIMISED
                }
            }
        }

        window.windowState = WindowState.MAXIMISED
        activeWindow = window

        // also remove the links window because it may possibly displayed even though a window is
        // maximised if a link is pressed
        if (!window.isLinksWindow) {
            windowRepository.dedicatedLinksWindow.windowState = WindowState.CLOSED
        }

        // redisplay the current page
        eventManager.post(NumberOfWindowsChangedEvent(windowChapterVerseMap))
    }

    fun unmaximiseWindow(window: Window) {
        for (it in windowRepository.windowList) {
            it.windowState = if(it.wasMinimised) WindowState.MINIMISED else WindowState.SPLIT
            it.wasMinimised = false
        }

        window.windowState = WindowState.SPLIT

        // redisplay the current page
        eventManager.post(NumberOfWindowsChangedEvent(windowChapterVerseMap))

        windowSync.synchronizeWindows()
        windowSync.reloadAllWindows()
    }

    fun closeWindow(window: Window) {

        if (isWindowRemovable(window)) {
            logger.debug("Closing window " + window.id)
            windowRepository.close(window)

            val visibleWindows = windowRepository.visibleWindows
            if (visibleWindows.count() == 1) visibleWindows[0].weight = 1.0F

            // redisplay the current page
            eventManager.post(NumberOfWindowsChangedEvent(windowChapterVerseMap))
            windowSync.reloadAllWindows()
        }
    }

    fun isWindowMinimisable(window: Window): Boolean {
        return !windowRepository.isMaximisedState && isWindowRemovable(window) && !window.isLinksWindow
    }

    fun isWindowRemovable(window: Window): Boolean {
        if(windowRepository.isMaximisedState && windowRepository.minimisedAndMaximizedScreens.size > 1) return true
        var normalWindows = windowRepository.visibleWindows.size
        if (windowRepository.dedicatedLinksWindow.isVisible) {
            normalWindows--
        }

        return window.isLinksWindow || normalWindows > 1 || !window.isVisible
    }

    fun restoreWindow(window: Window) {
        if (window == activeWindow) return
        window.restoreOngoing = true

        var switchingMaximised = false

        for (it in windowRepository.maximisedWindows) {
            switchingMaximised = true
            if(!it.isPinMode) {
                it.windowState = WindowState.MINIMISED
            }
        }

        window.windowState = if(switchingMaximised) WindowState.MAXIMISED else WindowState.SPLIT

        // causes BibleViews to be created and laid out
        windowSync.synchronizeWindows()
        windowSync.reloadAllWindows()

        if (switchingMaximised) {
            if (activeWindow.isSynchronised)
                windowRepository.lastMaximizedAndSyncWindowId =  activeWindow.id
            activeWindow = window
        } else {
            windowRepository.lastMaximizedAndSyncWindowId = null
        }

        eventManager.post(NumberOfWindowsChangedEvent(windowChapterVerseMap))

        window.restoreOngoing = false
    }

    /*
	 * Move the current window to first
	 */

    /** screen orientation has changed  */
    fun orientationChange() {
        // causes BibleViews to be created and laid out
        eventManager.post(NumberOfWindowsChangedEvent(windowChapterVerseMap))
    }

    fun onEvent(event: CurrentVerseChangedEvent) {
        windowSync.synchronizeWindows(event.window)
    }

    fun onEvent(event: SynchronizeWindowsEvent) {
        if(event.forceSyncAll) {
            windowSync.setResyncRequired()
        } else {
            windowSync.setResyncBiblesRequired()
        }
        if(activeWindowPageManager.isMyNoteShown) return
        windowSync.reloadAllWindows()
    }

    fun isSeparatorMoving(): Boolean {
        // allow 1 sec for screen to settle after window separator drag
        if (stoppedMovingTime > 0) {
            // allow a second after stopping for screen to settle
            if (stoppedMovingTime + SCREEN_SETTLE_TIME_MILLIS > System.currentTimeMillis()) {
                return true
            }
            stoppedMovingTime = 0
        }
        return isSeparatorMoving
    }

    fun setSeparatorMoving(isSeparatorMoving: Boolean) {
        if (!isSeparatorMoving) {
            // facilitate time for the screen to settle
            this.stoppedMovingTime = System.currentTimeMillis()
        }
        this.isSeparatorMoving = isSeparatorMoving

        val isMoveFinished = !isSeparatorMoving

        eventManager.post(WindowSizeChangedEvent(isMoveFinished, windowChapterVerseMap))
    }

    fun windowSizesChanged() {
        if (isMultiWindow) {
            // need to layout multiple windows differently
            orientationChange()
        }
    }

    fun setMaximized(window: Window, value: Boolean) {
        if(value == window.isMaximised) return
        if(value) {
            maximiseWindow(window)
        } else {
            unmaximiseWindow(window)
        }
    }

    fun setSynchronised(window: Window, value: Boolean) {
        if(value == window.isSynchronised) return
        if(value) {
            window.isSynchronised = true
            windowSync.synchronizeWindows()
        } else {
            window.isSynchronised = false
        }
    }

    fun moveWindow(window: Window, position: Int) {
        windowRepository.moveWindowToPosition(window, position)

        // redisplay the current page
        eventManager.post(NumberOfWindowsChangedEvent(windowChapterVerseMap))
    }

    fun setPinMode(window: Window, value: Boolean) {
        window.isPinMode = value
        if (windowRepository.isMaximisedState) {
            if(value) {
                maximiseWindow(window)
            } else if(windowRepository.maximisedWindows.size > 1) {
                minimiseWindow(window, true)
            }
        }
    }

    companion object {
        var SCREEN_SETTLE_TIME_MILLIS = 1000
        const val TAG = "WindowControl"
    }
}
