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

package net.bible.android.view.activity.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ImageSpan
import android.view.Menu
import android.view.MenuItem
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import kotlinx.serialization.Serializable
import net.bible.android.activity.R
import net.bible.android.activity.databinding.SettingsDialogBinding
import net.bible.android.database.SettingsBundle
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings.Types
import net.bible.android.database.WorkspaceEntities
import net.bible.android.view.activity.page.Preference as ItemPreference
import net.bible.android.database.json
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.page.ColorPreference
import net.bible.android.view.activity.page.CommandPreference
import net.bible.android.view.activity.page.FontFamilyPreference
import net.bible.android.view.activity.page.FontSizePreference
import net.bible.android.view.activity.page.HideLabelsPreference
import net.bible.android.view.activity.page.LineSpacingPreference
import net.bible.android.view.activity.page.MainBibleActivity.Companion.COLORS_CHANGED
import net.bible.android.view.activity.page.MainBibleActivity.Companion.mainBibleActivity
import net.bible.android.view.activity.page.MarginSizePreference
import net.bible.android.view.activity.page.MorphologyPreference
import net.bible.android.view.activity.page.MyNotesPreference
import net.bible.android.view.activity.page.OptionsMenuItemInterface
import net.bible.android.view.activity.page.StrongsPreference
import net.bible.android.view.activity.page.TopMarginPreference
import net.bible.service.common.CommonUtils
import java.lang.IllegalArgumentException
import java.lang.RuntimeException


class TextDisplaySettingsDataStore(
    private val activity: TextDisplaySettingsActivity,
    private val settingsBundle: SettingsBundle
): PreferenceDataStore() {
    override fun putBoolean(key: String, value: Boolean) {
        val type = Types.valueOf(key)
        val prefItem = getPrefItem(settingsBundle, type)
        val oldValue = prefItem.value
        prefItem.value = value
        if(oldValue != value) {
            activity.setDirty(type)
        }
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        val type = Types.valueOf(key)
        val settings = TextDisplaySettings.actual(settingsBundle.pageManagerSettings, settingsBundle.workspaceSettings)

        return (settings.getValue(type) ?: TextDisplaySettings.default.getValue(type)) as Boolean
    }
}

fun getPrefItem(settings: SettingsBundle, key: String): OptionsMenuItemInterface {
    return try {
        val type = Types.valueOf(key)
        getPrefItem(settings, type)
    } catch (e: IllegalArgumentException) {
        when(key) {
            "apply_to_all_workspaces" -> CommandPreference()
            else -> throw RuntimeException("Unsupported item key $key")
        }
    }
}

fun getPrefItem(settings: SettingsBundle, type: Types): OptionsMenuItemInterface =
    when(type) {
        Types.BOOKMARKS_SHOW -> ItemPreference(settings, Types.BOOKMARKS_SHOW)
        Types.REDLETTERS -> ItemPreference(settings, Types.REDLETTERS)
        Types.SECTIONTITLES -> ItemPreference(settings, Types.SECTIONTITLES)
        Types.VERSENUMBERS -> ItemPreference(settings, Types.VERSENUMBERS)
        Types.VERSEPERLINE -> ItemPreference(settings, Types.VERSEPERLINE)
        Types.FOOTNOTES -> ItemPreference(settings, Types.FOOTNOTES)
        Types.MYNOTES -> MyNotesPreference(settings)

        Types.STRONGS -> StrongsPreference(settings)
        Types.MORPH -> MorphologyPreference(settings)
        Types.FONTSIZE -> FontSizePreference(settings)
        Types.FONTFAMILY -> FontFamilyPreference(settings)
        Types.MARGINSIZE -> MarginSizePreference(settings)
        Types.COLORS -> ColorPreference(settings)
        Types.JUSTIFY -> ItemPreference(settings, Types.JUSTIFY)
        Types.HYPHENATION -> ItemPreference(settings, Types.HYPHENATION)
        Types.TOPMARGIN -> TopMarginPreference(settings)
        Types.LINE_SPACING -> LineSpacingPreference(settings)
        Types.BOOKMARKS_HIDELABELS -> HideLabelsPreference(settings, Types.BOOKMARKS_HIDELABELS)
    }

class TextDisplaySettingsFragment: PreferenceFragmentCompat() {
    private val settingsBundle get() = (activity as TextDisplaySettingsActivity).settingsBundle
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val activity = activity as TextDisplaySettingsActivity
        preferenceManager.preferenceDataStore = TextDisplaySettingsDataStore(activity, settingsBundle)
        setPreferencesFromResource(R.xml.text_display_settings, rootKey)
        updateItems()
    }

    internal fun updateItems() {
        for(p in getPreferenceList()) {
            updateItem(p)
        }
    }

    private val windowId get() = settingsBundle.windowId

    private fun updateItem(p: Preference) {
        val itmOptions = getPrefItem(settingsBundle, p.key)
        if(windowId != null) {
            if (itmOptions.inherited) {
                p.setIcon(R.drawable.ic_sync_white_24dp)
            } else {
                p.setIcon(R.drawable.ic_sync_disabled_green_24dp)
            }
        }
        if(itmOptions is MorphologyPreference) {
            p.isEnabled = StrongsPreference(settingsBundle).value as Int > 0
        }
        if(itmOptions.title != null) {
            p.title = itmOptions.title
        }
        p.isVisible = itmOptions.visible
    }

    private fun getPreferenceList(p_: Preference? = null, list_: ArrayList<Preference>? = null): ArrayList<Preference> {
        val p = p_?: preferenceScreen
        val list = list_?: ArrayList()
        if (p is PreferenceCategory || p is PreferenceScreen) {
            val pGroup: PreferenceGroup = p as PreferenceGroup
            val pCount: Int = pGroup.preferenceCount
            for (i in 0 until pCount) {
                getPreferenceList(pGroup.getPreference(i), list) // recursive call
            }
        } else {
            list.add(p)
        }
        return list
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        var returnValue = true
        val prefItem = getPrefItem(settingsBundle, preference.key)
        val type = try {Types.valueOf(preference.key)} catch (e: IllegalArgumentException) { null }
        val activity = activity as TextDisplaySettingsActivity
        val resetFunc = {
            if(prefItem is ItemPreference) {
                prefItem.setNonSpecific()
                activity.setDirty(prefItem.type)
            }
            updateItem(preference)
        }
        val handled = prefItem.openDialog(activity, {
            updateItem(preference)
            if(type != null)
                activity.setDirty(type)
        }, resetFunc)

        if(!handled) {
            returnValue = super.onPreferenceTreeClick(preference)
            updateItems()
        }

        return returnValue
    }
}

@Serializable
data class DirtyTypesSerializer(val dirtyTypes: MutableSet<Types>) {
    fun toJson(): String {
        return json.encodeToString(serializer(), this)
    }
    companion object {
        fun fromJson(jsonString: String): DirtyTypesSerializer {
            return json.decodeFromString(serializer(), jsonString)
        }
    }
}

@ActivityScope
class TextDisplaySettingsActivity: ActivityBase() {
    private lateinit var fragment: TextDisplaySettingsFragment
    private var requiresReload = false
    private var reset = false
    private val dirtyTypes = mutableSetOf<Types>()

    internal lateinit var settingsBundle: SettingsBundle
    private lateinit var binding: SettingsDialogBinding

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.text_options_opts, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isHandled = true
        when(item.itemId) {
            R.id.reset -> reset()
            R.id.help -> help()
            android.R.id.home -> onBackPressed()
            else -> isHandled = false
        }
        if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item)
        }
        return isHandled
    }

    fun reset() {
        AlertDialog.Builder(this)
            .setPositiveButton(R.string.yes) {_, _ ->
                reset = true
                requiresReload = true
                setResult()
                finish()
            }
            .setNegativeButton(R.string.no,null)
            .setMessage(getString(R.string.reset_are_you_sure))
            .create()
            .show()
    }

    fun help() {
        fun getTintedDrawable(res: Int, color: Int): Drawable {
            val d = CommonUtils.getResourceDrawable(res, context = this)!!
            d.mutate().setTint(CommonUtils.getResourceColor(color))
            d.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight)
            return d
        }

        val resetIcon = ImageSpan(getTintedDrawable(R.drawable.ic_baseline_undo_24, R.color.grey_500))
        val length = 9

        val text = if(isWindow) {
            val w1 = getString(R.string.window_text_options_help1, "__ICON1__")
            val w2 = getString(R.string.window_text_options_help2, "__ICON2__")
            val w3 = getString(R.string.window_text_options_help3)
            val w4 = getString(R.string.text_options_reset_help, "__ICON3__", getString(R.string.reset_workspace_defaults))
            val icon1 = ImageSpan(this, R.drawable.ic_sync_white_24dp)
            val icon2 = ImageSpan(this, R.drawable.ic_sync_disabled_green_24dp)
            val text = "$w1 \n\n$w2 $w3\n\n$w4"
            val start1 = text.indexOf("__ICON1__")
            val start2 = text.indexOf("__ICON2__")
            val start3 = text.indexOf("__ICON3__")
            val span = SpannableString(text)
            span.setSpan(icon1, start1, start1 + length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            span.setSpan(icon2, start2, start2 + length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            span.setSpan(resetIcon, start3, start3 + length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            span
        } else {
            val h1 = getString(R.string.workspace_text_options_help1)
            val h2 = getString(R.string.workspace_text_options_help2)
            val h3 = getString(R.string.text_options_reset_help, "__ICON1__", getString(R.string.reset_defaults))
            val text = "$h1 $h2 \n\n$h3"
            val start1 = text.indexOf("__ICON1__")
            val span = SpannableString(text)
            span.setSpan(resetIcon, start1, start1 + length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            span
        }

        val title = if(isWindow) getString(R.string.window_text_options_help_title)
                    else getString(R.string.workspace_text_options_help_title)

        AlertDialog.Builder(this)
            .setPositiveButton(R.string.okay, null)// {_, _ -> undoTint()}
            .setTitle(title)
            .setMessage(text)
            .create()
            .show()
    }

    override fun onBackPressed() {
        finish()
    }

    private val isWindow get() = settingsBundle.windowId != null

    override fun onCreate(savedInstanceState: Bundle?) {
        settingsBundle = SettingsBundle.fromJson(intent.extras?.getString("settingsBundle")!!)

        super.onCreate(savedInstanceState)

        binding = SettingsDialogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        super.buildActivityComponent().inject(this)
        dirtyTypes.clear()
        requiresReload = false
        reset = false

        val windowId = settingsBundle.windowId
        title = if(windowId != null) {
            getString(R.string.window_text_display_settings_title, mainBibleActivity.windowControl.windowPosition(windowId) + 1)
        } else {
            getString(R.string.workspace_text_display_settings_title, settingsBundle.workspaceName)
        }

        val fragment = TextDisplaySettingsFragment()
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, fragment)
            .commit()
        this.fragment = fragment
        binding.okButton.setOnClickListener {finish()}
        binding.cancelButton.setOnClickListener {
            dirtyTypes.clear()
            setResult()
            finish()
        }
        binding.resetButton.setOnClickListener {
            reset()
        }
        setResult()
    }

    fun setDirty(type: Types) {
        dirtyTypes.add(type)
        setResult()
    }

    fun setResult() {
        val resultIntent = Intent(this, ColorSettingsActivity::class.java)

        resultIntent.putExtra("settingsBundle", settingsBundle.toJson())
        resultIntent.putExtra("reset", reset)
        resultIntent.putExtra("edited", dirtyTypes.isNotEmpty())
        resultIntent.putExtra("dirtyTypes", DirtyTypesSerializer(dirtyTypes).toJson())

        setResult(Activity.RESULT_OK, resultIntent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            COLORS_CHANGED -> {
                val extras = data?.extras!!
                val edited = extras.getBoolean("edited")
                val reset = extras.getBoolean("reset")
                val prefItem = getPrefItem(settingsBundle, Types.COLORS)
                if(reset) {
                    prefItem.setNonSpecific()
                    setDirty(Types.COLORS)
                    fragment.updateItems()
                }
                else if(edited) {
                    val colors = WorkspaceEntities.Colors.fromJson(data.extras?.getString("colors")!!)
                    prefItem.value = colors
                    setDirty(Types.COLORS)
                    fragment.updateItems()
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
