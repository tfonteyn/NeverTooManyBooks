/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.IsfdbSearchEngine;

/**
 * Uppercase preference keys are internal only.
 * <p>
 * The lowercase pk_* preference key names are the ones that define USER settings.
 * See {@link com.hardbacknutter.nevertoomanybooks.settings}.
 * <p>
 * All keys <strong>MUST</strong> be kept in sync with "src/main/res/xml/preferences*.xml"
 */
public final class Prefs {

    /** Number of app startup's between offers to backup. */
    public static final int STARTUP_BACKUP_COUNTDOWN = 5;
    /** Triggers prompting for a backup when the countdown reaches 0; then gets reset. */
    public static final String PREF_STARTUP_BACKUP_COUNTDOWN = "startup.backupCountdown";

    /** User preference Key. */
    public static final String pk_ui_locale = "ui.locale";
    /** Not the actual theme, but the day/night part only. */
    public static final String pk_ui_theme = "ui.theme";
    public static final String pk_network_allow_metered = "network.allow.metered";
    public static final String pk_scanner_preferred = "scanner.preferred";
    public static final String pk_sounds_scan_isbn_valid = "sounds.scan.isbn.valid";
    public static final String pk_sounds_scan_isbn_invalid = "sounds.scan.isbn.invalid";
    public static final String pk_sounds_scan_found_barcode = "sounds.scan.barcode.found";
    public static final String pk_search_reformat_format = "search.reformat.format";
    public static final String pk_search_reformat_color = "search.reformat.color";
    public static final String pk_search_isbn_prefer_10 = "search.byIsbn.prefer.10";
    public static final String pk_camera_image_autorotate = "camera.image.autorotate";
    public static final String pk_camera_image_action = "camera.image.action";
    public static final String pk_image_cache_resized = "image.cache.resized";
    public static final String pk_image_cropper_frame_whole = "image.cropper.frame.whole";
    public static final String pk_image_cropper_layer_type = "compat.image.cropper.viewlayertype";
    public static final String pk_sort_title_reordered = "sort.title.reordered";
    public static final String pk_show_title_reordered = "show.title.reordered";
    public static final String pk_sort_author_name_given_first = "sort.author.name.given_first";
    public static final String pk_show_author_name_given_first = "show.author.name.given_first";
    public static final String pk_booklist_rebuild_state = "booklist.rebuild.state";


    /** Style group preferences. */
    public static final String pk_style_groups = "style.booklist.groups";


    /** Style - PreferenceScreen/PreferenceCategory Key. */
    public static final String PSK_STYLE_AUTHOR = "psk_style_author";
    /** Style - PreferenceScreen/PreferenceCategory Key. */
    public static final String PSK_STYLE_SERIES = "psk_style_series";
    /** Style - PreferenceScreen/PreferenceCategory Key. */
    public static final String PSK_STYLE_PUBLISHER = "psk_style_publisher";

    /** Style - PreferenceScreen/PreferenceCategory Key. */
    public static final String PSK_STYLE_SHOW_DETAILS = "psk_style_show_details";
    /** Style - PreferenceScreen/PreferenceCategory Key. */
    public static final String PSK_STYLE_FILTERS = "psk_style_filters";

    /** Global - PreferenceScreen/PreferenceCategory Key. */
    public static final String PSK_BARCODE_SCANNER = "psk_barcode_scanner";
    public static final String pk_edit_book_isbn_checks = "edit.book.isbn.checks";
    private static final String pk_edit_book_tabs_external_id = "edit.book.tab.externalId";
    /** Log tag. */
    private static final String TAG = "Prefs";

    private Prefs() {
    }

    public static boolean showEditBookTabExternalId(@NonNull final SharedPreferences preferences) {
        return preferences.getBoolean(pk_edit_book_tabs_external_id, false);
    }

    /**
     * Whether a search should (also) use the publisher name to search for books.
     * <p>
     * Hardcoded to ISFDB only for now, as that's the only site supporting this flag.
     * This method will be refactored/moved/... at some point.
     *
     * @param context Current context
     *
     * @return flag
     */
    public static boolean usePublisher(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(IsfdbSearchEngine.PREFS_USE_PUBLISHER, false);
    }

    /**
     * {@link ListPreference} stores the selected value as a String.
     * But they are really Integer values. Hence this transmogrification....
     *
     * @param preferences SharedPreferences
     * @param key         The name of the preference to retrieve.
     * @param defValue    Value to return if this preference does not exist.
     *
     * @return int (stored as String) global preference
     */
    public static int getListPreference(@NonNull final SharedPreferences preferences,
                                        @NonNull final String key,
                                        final int defValue) {
        final String value = preferences.getString(key, null);
        if (value == null || value.isEmpty()) {
            return defValue;
        }
        return Integer.parseInt(value);
    }

    /**
     * DEBUG only.
     *
     * @param context Current context
     * @param uuid    SharedPreferences
     */
    @SuppressLint("LogConditional")
    public static void dumpPreferences(@NonNull final Context context,
                                       @Nullable final String uuid) {
        Map<String, ?> map;
        if (uuid != null) {
            map = context.getSharedPreferences(uuid, Context.MODE_PRIVATE).getAll();
        } else {
            map = PreferenceManager.getDefaultSharedPreferences(context).getAll();
        }
        List<String> keyList = new ArrayList<>(map.keySet());
        //noinspection ZeroLengthArrayAllocation
        String[] keys = keyList.toArray(new String[]{});
        Arrays.sort(keys);

        StringBuilder sb = new StringBuilder("\n\nSharedPreferences: "
                                             + (uuid != null ? uuid : "global"));
        for (String key : keys) {
            Object value = map.get(key);
            sb.append('\n').append(key).append('=').append(value);
        }
        sb.append("\n\n");

        Log.d(TAG, "dumpPreferences|" + sb);
    }

    /**
     * Copy all preferences from source to destination.
     *
     * @param context     Current context
     * @param source      SharedPreferences name
     * @param destination SharedPreferences name
     * @param clearSource flag: clear/delete the source after copying
     */
    @SuppressWarnings("unused")
    protected static void copyPrefs(@NonNull final Context context,
                                    @NonNull final String source,
                                    @NonNull final String destination,
                                    final boolean clearSource) {
        SharedPreferences sourcePrefs =
                context.getSharedPreferences(source, Context.MODE_PRIVATE);
        SharedPreferences destinationPrefs =
                context.getSharedPreferences(destination, Context.MODE_PRIVATE);

        SharedPreferences.Editor ed = destinationPrefs.edit();
        Map<String, ?> all = sourcePrefs.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {

            if (entry.getValue() instanceof Boolean) {
                ed.putBoolean(entry.getKey(), (Boolean) entry.getValue());
            } else if (entry.getValue() instanceof Float) {
                ed.putFloat(entry.getKey(), (Float) entry.getValue());
            } else if (entry.getValue() instanceof Integer) {
                ed.putInt(entry.getKey(), (Integer) entry.getValue());
            } else if (entry.getValue() instanceof Long) {
                ed.putLong(entry.getKey(), (Long) entry.getValue());
            } else if (entry.getValue() instanceof String) {
                ed.putString(entry.getKey(), (String) entry.getValue());
            } else if (entry.getValue() instanceof Set) {
                //noinspection unchecked
                ed.putStringSet(entry.getKey(), (Set<String>) entry.getValue());
            } else {
                Logger.warnWithStackTrace(context, TAG, "Unknown type",
                                          entry.getValue().getClass().getCanonicalName());
            }
        }
        ed.apply();
        if (clearSource) {
            if (Build.VERSION.SDK_INT >= 24) {
                context.deleteSharedPreferences(source);
            } else {
                sourcePrefs.edit().clear().apply();
            }
        }
    }
}
