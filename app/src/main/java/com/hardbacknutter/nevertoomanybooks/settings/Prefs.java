/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * The preference key names here are the ones that define USER settings.
 * See {@link com.hardbacknutter.nevertoomanybooks.settings.SettingsActivity} and children.
 * <p>
 * These keys *MUST* be kept in sync with "res/xml/preferences*.xml"
 * <p>
 * Application internal settings are done where they are needed/used.
 */
public final class Prefs {

    /** PreferenceScreen/PreferenceCategory Key. */
    public static final String psk_style_groupings = "psk_style_groupings";
    /** PreferenceScreen/PreferenceCategory Key. */
    public static final String psk_style_filters = "psk_style_filters";
    /** PreferenceScreen/PreferenceCategory Key. */
    public static final String psk_style_show_details = "psk_style_show_details";
    /** PreferenceScreen/PreferenceCategory Key. */
    public static final String psk_style_author = "psk_style_author";
    /** PreferenceScreen/PreferenceCategory Key. */
    public static final String psk_style_series = "psk_style_series";

    /** PreferenceScreen/PreferenceCategory Key. */
    public static final String psk_barcode_scanner = "psk_barcode_scanner";


    /** Preference Key. */
    public static final String pk_ui_language = "App.Locale";
    public static final String pk_ui_theme = "App.Theme";
    public static final String pk_ui_messages_use = "App.UserMessage";

    public static final String pk_network_allow_metered_data = "network.mobile_data";

    public static final String pk_scanner_preferred = "ScannerManager.PreferredScanner";

    public static final String pk_scanner_beep_if_valid = "SoundManager.BeepIfScannedIsbnValid";
    public static final String pk_scanner_beep_if_invalid = "SoundManager.BeepIfScannedIsbnInvalid";

    public static final String pk_reformat_titles_on_insert = "reformat.title.insert";
    public static final String pk_reformat_titles_on_update = "reformat.title.update";
    public static final String pk_reformat_formats = "reformat.format.update";

    public static final String pk_images_cache_resized = "Image.Cache.Resized";
    public static final String pk_images_zoom_upscale = "Image.Zoom.Upscale";
    public static final String pk_images_rotate_auto = "Image.Camera.Autorotate";
    public static final String pk_images_external_cropper = "Image.Cropper.UseExternalApp";
    public static final String pk_images_crop_whole_image = "Image.Cropper.FrameIsWholeImage";
    public static final String pk_images_cropper_layer_type = "Image.ViewLayerType";

    public static final String pk_bob_open_book_read_only = "BooksOnBookshelf.OpenBookReadOnly";
    public static final String pk_bob_list_state = "BookList.ListRebuildState";
    public static final String pk_bob_list_generation = "BookList.CompatibilityMode";

    public static final String pk_bob_use_task_for_extras = "BookList.UseTaskFor.BookDetails";

    public static final String pk_bob_style_name = "BookList.Style.Name";
    public static final String pk_bob_groups = "BookList.Style.Groups";
    public static final String pk_bob_preferred_style = "BookList.Style.Preferred";
    public static final String pk_bob_text_size = "BookList.Style.Scaling";
    public static final String pk_bob_cover_size = "BookList.Style.Scaling.Thumbnails";

    public static final String pk_bob_books_under_multiple_authors =
            "BookList.Style.Group.Authors.ShowAll";
    public static final String pk_bob_books_under_multiple_series =
            "BookList.Style.Group.Series.ShowAll";
    public static final String pk_bob_format_author_name =
            "BookList.Style.Group.Authors.DisplayFirstThenLast";
    public static final String pk_bob_sort_author_name =
            "BookList.Style.Sort.Author.GivenFirst";

    /** MultiSelectListPreference. */
    public static final String pk_bob_header = "BookList.Style.Show.HeaderInfo";
    /** Show the cover image for each book. */
    public static final String pk_bob_show_thumbnails = "BookList.Style.Show.Thumbnails";
    /** Show list of bookshelves for each book. */
    public static final String pk_bob_show_bookshelves = "BookList.Style.Show.Bookshelves";
    /** Show location for each book. */
    public static final String pk_bob_show_location = "BookList.Style.Show.Location";
    /** Show author for each book. */
    public static final String pk_bob_show_author = "BookList.Style.Show.Author";
    /** Show publisher for each book. */
    public static final String pk_bob_show_publisher = "BookList.Style.Show.Publisher";
    /** Show publication date for each book. */
    public static final String pk_bob_show_pub_date = "BookList.Style.Show.Publication.Date";
    /** Show ISBN for each book. */
    public static final String pk_bob_show_isbn = "BookList.Style.Show.ISBN";
    /** Show format for each book. */
    public static final String pk_bob_show_format = "BookList.Style.Show.Format";

    /** Booklist Filter - ListPreference. */
    public static final String pk_bob_filter_read = "BookList.Style.Filter.Read";
    /** Booklist Filter - ListPreference. */
    public static final String pk_bob_filter_signed = "BookList.Style.Filter.Signed";
    /** Booklist Filter - ListPreference. */
    public static final String pk_bob_filter_loaned = "BookList.Style.Filter.Loaned";
    /** Booklist Filter - ListPreference. */
    public static final String pk_bob_filter_anthology = "BookList.Style.Filter.Anthology";
    /** Booklist Filter - MultiSelectListPreference. */
    public static final String pk_bob_filter_editions = "BookList.Style.Filter.Editions";

    private Prefs() {
    }

    /**
     * Convert a set where each element represents one bit to an int bitmask.
     *
     * @param set the set
     *
     * @return the value
     */
    @NonNull
    public static Integer toInteger(@NonNull final Set<String> set) {
        int tmp = 0;
        for (String s : set) {
            tmp += Integer.parseInt(s);
        }
        return tmp;
    }

    /**
     * Convert an int (bitmask) to a set where each element represents one bit.
     *
     * @param bitmask the value
     *
     * @return the set
     */
    @NonNull
    public static Set<String> toStringSet(@IntRange(from = 0, to = 0xFFFF)
                                          @NonNull final Integer bitmask) {
        // sanity check.
        if (bitmask < 0) {
            throw new IllegalArgumentException("bitmask=" + bitmask);
        }

        Set<String> set = new HashSet<>();
        int tmp = bitmask;
        int bit = 1;
        while (tmp != 0) {
            if ((tmp & 1) == 1) {
                set.add(String.valueOf(bit));
            }
            bit *= 2;
            tmp = tmp >> 1;
        }
        return set;
    }

    /**
     * Copy all preferences from source to destination.
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
                Logger.warnWithStackTrace(Prefs.class,
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

    /**
     * DEBUG method.
     */
    public static void dumpPreferences(@NonNull final Context context,
                                       @Nullable final String uuid) {
        if (BuildConfig.DEBUG /* always */) {
            Map<String, ?> map;
            if (uuid != null) {
                map = context.getSharedPreferences(uuid, Context.MODE_PRIVATE).getAll();
            } else {
                map = PreferenceManager.getDefaultSharedPreferences(context).getAll();
            }
            List<String> keyList = new ArrayList<>(map.keySet());
            String[] keys = keyList.toArray(new String[]{});
            Arrays.sort(keys);

            StringBuilder sb = new StringBuilder("\n\nSharedPreferences: "
                                                 + (uuid != null ? uuid : "global"));
            for (String key : keys) {
                Object value = map.get(key);
                sb.append('\n').append(key).append('=').append(value);
            }
            sb.append("\n\n");

            Logger.debug(App.class, "dumpPreferences", sb);
        }
    }

    /**
     * Get the global default for this preference.
     *
     * @param context Current context
     *
     * @return {@code true} if we want "given-names last-name" formatted authors.
     */
    public static boolean displayAuthorGivenNameFirst(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(pk_bob_format_author_name, false);
    }

    /**
     * Get the global default for this preference.
     *
     * @param context Current context
     *
     * @return {@code true} if we want "given-names last-name" sorted authors.
     */
    public static boolean sortAuthorGivenNameFirst(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(pk_bob_sort_author_name, false);
    }
}
