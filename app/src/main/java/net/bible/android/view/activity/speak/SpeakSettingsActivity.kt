/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.view.activity.speak

import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import android.widget.CheckBox
import android.widget.TextView
import net.bible.android.activity.R
import net.bible.android.activity.databinding.SpeakSettingsBinding
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.speak.*
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.view.activity.ActivityScope
import net.bible.service.common.automaticSpeakBookmarkingVideo

@ActivityScope
class SpeakSettingsActivity : AbstractSpeakActivity() {
    companion object {
        const val TAG = "SpeakSettingsActivity"
    }

    lateinit var binding: SpeakSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SpeakSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        super.buildActivityComponent().inject(this)
        ABEventBus.register(this)
        resetView(SpeakSettings.load())
        binding.apply {
            synchronize.setOnClickListener { updateSettings() }
            replaceDivineName.setOnClickListener { updateSettings() }
            autoBookmark.setOnClickListener { updateSettings() }
            restoreSettingsFromBookmarks.setOnClickListener { updateSettings() }
        }
    }

    override val sleepTimer: CheckBox? = null

    override fun onDestroy() {
        ABEventBus.unregister(this)
        super.onDestroy()
    }

    override fun resetView(settings: SpeakSettings) {
        binding.apply {
            synchronize.isChecked = settings.synchronize
            replaceDivineName.isChecked = settings.replaceDivineName
            restoreSettingsFromBookmarks.isChecked = settings.restoreSettingsFromBookmarks

            autoBookmark.isChecked = settings.autoBookmark
            if (!autoBookmark.isChecked) {
                restoreSettingsFromBookmarks.isChecked = false
                restoreSettingsFromBookmarks.isEnabled = false
            } else {
                restoreSettingsFromBookmarks.isEnabled = true
            }
        }
    }

    fun onEventMainThread(ev: SpeakSettingsChangedEvent) {
        currentSettings = ev.speakSettings
        resetView(ev.speakSettings)
    }

    fun updateSettings() {
        val settings = SpeakSettings.load().apply {
            sleepTimer = currentSettings.sleepTimer
            lastSleepTimer = currentSettings.lastSleepTimer
        }
        binding.apply {
            settings.synchronize = synchronize.isChecked
            settings.autoBookmark = autoBookmark.isChecked
            settings.replaceDivineName = replaceDivineName.isChecked
            settings.restoreSettingsFromBookmarks = restoreSettingsFromBookmarks.isChecked
        }
        settings.save(updateBookmark = true)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.speak_bible_actionbar_menu, menu)
        menu.findItem(R.id.systemSettings).isVisible = false
        menu.findItem(R.id.advancedSettings).isVisible = false
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.help -> {
                onHelpButtonClick()
                return true
            }
        }
        return false
    }

    fun onHelpButtonClick() {
        val htmlMessage = (
                "<b>${getString(R.string.conf_speak_auto_bookmark)}</b><br><br>"
                + "<b><a href=\"$automaticSpeakBookmarkingVideo\">"
                + "${getString(R.string.watch_tutorial_video)}</a></b><br><br>"
                + getString(R.string.speak_help_auto_bookmark)
                + "<br><br><b>${getString(R.string.conf_save_playback_settings_to_bookmarks)}</b><br><br>"
                + getString(R.string.speak_help_playback_settings)
                + "<br><br>"
                + getString(R.string.speak_help_playback_settings_example)
                )

        val spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(htmlMessage, Html.FROM_HTML_MODE_LEGACY)
        } else {
            Html.fromHtml(htmlMessage)
        }

        val d = AlertDialog.Builder(this)
                .setMessage(spanned)
                .setPositiveButton(android.R.string.ok) { _, _ ->  }
                .create()

        d.show()
        d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
    }
}
