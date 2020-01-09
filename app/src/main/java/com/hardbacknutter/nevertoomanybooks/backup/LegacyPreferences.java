/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.booklist.BooklistBuilder;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.searches.librarything.LibraryThingManager;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;

public final class LegacyPreferences {

    /** Legacy preferences name. */
    private static final String PREF_LEGACY_PREFS = "bookCatalogue";

    private LegacyPreferences() {
    }

    public static SharedPreferences getPrefs(@NonNull final Context context) {
        return context
                .getSharedPreferences(LegacyPreferences.PREF_LEGACY_PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Migrate BookCatalogue preferences.
     * <p>
     * Refactoring brought a cleanup and re-structuring of the preferences.
     * Some of these are real migrations,
     * some just for aesthetics's making the key's naming standard.
     *
     * Not all keys are migrated.
     *
     * @param context Current context
     */
    public static void migrateLegacyPreferences(@NonNull final Context context) {

        SharedPreferences oldPrefs = context.getSharedPreferences(
                PREF_LEGACY_PREFS, Context.MODE_PRIVATE);

        Map<String, ?> oldMap = oldPrefs.getAll();
        if (oldMap.isEmpty()) {
            if (Build.VERSION.SDK_INT >= 24) {
                context.deleteSharedPreferences(PREF_LEGACY_PREFS);
            } else {
                oldPrefs.edit().clear().apply();
            }
            return;
        }

        // write to default prefs
        SharedPreferences.Editor ed = PreferenceManager.getDefaultSharedPreferences(context)
                                                       .edit();

        // note that strings could be empty. Check if needed
        for (Map.Entry<String, ?> entry : oldMap.entrySet()) {
            Object oldValue = entry.getValue();
            if (oldValue == null) {
                continue;
            }
            try {
                switch (entry.getKey()) {
                    /*
                     * User defined preferences.
                     */
                    case "App.Locale":
                        String tmp = (String) oldValue;
                        if (!tmp.isEmpty()) {
                            ed.putString(Prefs.pk_ui_locale, tmp);
                        }
                        break;

                    case "BookList.Global.BooklistState":
                        int oldState = (Integer) oldValue;
                        @BooklistBuilder.ListRebuildMode
                        int bobState;
                        switch (oldState) {
                            case 1:
                                bobState = BooklistBuilder.PREF_REBUILD_ALWAYS_EXPANDED;
                                break;
                            case 2:
                                bobState = BooklistBuilder.PREF_REBUILD_ALWAYS_COLLAPSED;
                                break;
                            default:
                                bobState = BooklistBuilder.PREF_REBUILD_SAVED_STATE;
                                break;
                        }
                        ed.putString(Prefs.pk_bob_levels_rebuild_state, String.valueOf(bobState));
                        break;

                    case "SoundManager.BeepIfScannedIsbnInvalid":
                        ed.putBoolean(Prefs.pk_sounds_scan_isbn_invalid, (Boolean) oldValue);
                        break;

                    case "SoundManager.BeepIfScannedIsbnValid":
                        ed.putBoolean(Prefs.pk_sounds_scan_isbn_valid, (Boolean) oldValue);
                        break;

                    case "App.CropFrameWholeImage":
                        ed.putBoolean(Prefs.pk_image_cropper_frame_whole, (Boolean) oldValue);
                        break;

                    case "BookList.Global.CacheThumbnails":
                        ed.putBoolean(Prefs.pk_image_cache_resized, (Boolean) oldValue);
                        break;

                    case "App.AutorotateCameraImages":
                        ed.putString(Prefs.pk_camera_image_autorotate, String.valueOf(oldValue));
                        break;

                    /*
                     * Global defaults for styles
                     */
                    case "BookList.ShowAuthor":
                        ed.putBoolean(Prefs.pk_bob_show_author, (Boolean) oldValue);
                        break;

                    case "BookList.ShowBookshelves":
                        ed.putBoolean(Prefs.pk_bob_show_bookshelves, (Boolean) oldValue);
                        break;

                    case "BookList.ShowPublisher":
                        ed.putBoolean(Prefs.pk_bob_show_publisher, (Boolean) oldValue);
                        break;

                    case "BookList.ShowThumbnails":
                        ed.putBoolean(Prefs.pk_bob_show_thumbnails, (Boolean) oldValue);
                        break;

                    case "BookList.LargeThumbnails":
                        @ImageUtils.Scale
                        int scale = (Boolean) oldValue ? ImageUtils.SCALE_MEDIUM
                                                       : ImageUtils.SCALE_SMALL;
                        // this is now a PIntString (a ListPreference), stored as a string
                        ed.putString(Prefs.pk_bob_thumbnail_scale, String.valueOf(scale));
                        break;

                    case "BookList.ShowLocation":
                        ed.putBoolean(Prefs.pk_bob_show_location, (Boolean) oldValue);
                        break;

                    case "APP.DisplayFirstThenLast":
                        ed.putBoolean(Prefs.pk_bob_format_author_name, (Boolean) oldValue);
                        break;

                    case "APP.ShowAllAuthors":
                        ed.putBoolean(Prefs.pk_bob_books_under_multiple_authors,
                                      (Boolean) oldValue);
                        break;

                    case "APP.ShowAllSeries":
                        ed.putBoolean(Prefs.pk_bob_books_under_multiple_series, (Boolean) oldValue);
                        break;

                    case "BookList.Condensed":
                        @BooklistStyle.FontScale
                        int textScale = (Boolean) oldValue ? BooklistStyle.FONT_SCALE_SMALL
                                                           : BooklistStyle.FONT_SCALE_MEDIUM;
                        ed.putInt(Prefs.pk_bob_font_scale, textScale);
                        break;

                    case "BookList.ShowHeaderInfo":
//                        @BooklistStyle.ListHeaderOption
                        int shi = ((Integer) oldValue) & BooklistStyle.SUMMARY_SHOW_ALL;
                        // this is now a PBitmask, stored as a Set
                        ed.putStringSet(Prefs.pk_bob_header, Prefs.toStringSet(shi));
                        break;

                    /*
                     * User credentials
                     */
                    case "lt_devkey":
                        String tmpDevKey = (String) oldValue;
                        if (!tmpDevKey.isEmpty()) {
                            ed.putString(LibraryThingManager.PREFS_DEV_KEY, tmpDevKey);
                        }
                        break;

                    /*
                     * Internal settings
                     */
                    case "BooksOnBookshelf.BOOKSHELF":
                        String tmpBookshelf = (String) oldValue;
                        if (!tmpBookshelf.isEmpty()) {
                            ed.putString(Bookshelf.PREF_BOOKSHELF_CURRENT, tmpBookshelf);
                        }
                        break;

                    case "BooksOnBookshelf.LIST_STYLE": {
                        String e = (String) oldValue;
                        String styleName = e.substring(0, e.length() - 2);
                        ed.putString(BooklistStyle.PREF_BL_STYLE_CURRENT_DEFAULT,
                                     BooklistStyle.getStyle(context, styleName).getUuid());
                        break;
                    }
                    case "BooklistStyles.Menu.Items": {
                        // using a set to eliminate duplicates
                        Collection<String> uuidSet = new LinkedHashSet<>();
                        String[] styles = ((String) oldValue).split(",");
                        for (String style : styles) {
                            String styleName = style.substring(0, style.length() - 2);
                            uuidSet.add(BooklistStyle.getStyle(context, styleName).getUuid());
                        }
                        ed.putString(BooklistStyle.PREF_BL_PREFERRED_STYLES,
                                     TextUtils.join(",", uuidSet));
                        break;
                    }
                    default:
                        if (entry.getKey().startsWith("GoodReads")) {
                            String tmp1 = (String) oldValue;
                            if (!tmp1.isEmpty()) {
                                String key = entry.getKey().replace("GoodReads.",
                                                                    GoodreadsManager.PREF_PREFIX);
                                ed.putString(key, tmp1);
                            }
                        }
                        break;
                }

            } catch (@NonNull final RuntimeException ignore) {
                // to bad... skip that key, not fatal, use default.
            }
        }
        ed.apply();

        if (Build.VERSION.SDK_INT >= 24) {
            context.deleteSharedPreferences(PREF_LEGACY_PREFS);
        } else {
            oldPrefs.edit().clear().apply();
        }
    }
}
