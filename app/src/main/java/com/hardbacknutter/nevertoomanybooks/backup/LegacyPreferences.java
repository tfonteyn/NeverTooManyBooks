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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistBuilder;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.scanner.ScannerManager;
import com.hardbacknutter.nevertoomanybooks.searches.librarything.LibraryThingManager;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;

public final class LegacyPreferences {

    /** Legacy preferences name, pre-v200. */
    public static final String PREF_LEGACY_PREFS = "bookCatalogue";

    private LegacyPreferences() {
    }

    /**
     * v200 brought a cleanup and re-structuring of the preferences.
     * Some of these are real migrations,
     * some just for aesthetics's making the key's naming standard.
     *
     * @param context Current context
     */
    public static void migratePreV200preferences(@NonNull final Context context) {

        SharedPreferences oldPrefs = context.getSharedPreferences(
                PREF_LEGACY_PREFS,
                Context.MODE_PRIVATE);

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

        String styleName;

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
                            ed.putString(Prefs.pk_ui_language, tmp);
                        }
                        break;

                    case "App.OpenBookReadOnly":
                        ed.putBoolean(Prefs.pk_bob_open_book_read_only, (Boolean) oldValue);
                        break;

                    case "BookList.Global.BooklistState":
                        int oldState = (Integer) oldValue;
                        @BooklistBuilder.ListRebuildMode
                        int bobState;
                        switch (oldState) {
                            case 1:
                                bobState = BooklistBuilder.PREF_LIST_REBUILD_ALWAYS_EXPANDED;
                                break;
                            case 2:
                                bobState = BooklistBuilder.PREF_LIST_REBUILD_ALWAYS_COLLAPSED;
                                break;
                            default:
                                bobState = BooklistBuilder.PREF_LIST_REBUILD_STATE_PRESERVED;
                                break;
                        }
                        ed.putString(Prefs.pk_bob_list_state, String.valueOf(bobState));
                        break;

                    case "App.BooklistGenerationMode":
                        int oldMode = (Integer) oldValue;
                        @BooklistBuilder.CompatibilityMode
                        int compatMode;
                        switch (oldMode) {
                            case 1:
                                compatMode = BooklistBuilder.PREF_MODE_OLD_STYLE;
                                break;
                            case 2:
                                compatMode = BooklistBuilder.PREF_MODE_FLAT_TRIGGERS;
                                break;
                            case 3:
                                compatMode = BooklistBuilder.PREF_MODE_NESTED_TRIGGERS;
                                break;
                            default:
                                compatMode = BooklistBuilder.PREF_MODE_DEFAULT;
                                break;
                        }
                        ed.putString(Prefs.pk_bob_list_generation, String.valueOf(compatMode));
                        break;

                    case "SoundManager.BeepIfScannedIsbnInvalid":
                    case "SoundManager.BeepIfScannedIsbnValid":
                        ed.putBoolean(entry.getKey(), (Boolean) oldValue);
                        break;

                    case "ScannerManager.PreferredScanner":
                        // original code:
                        // public static final int SCANNER_ZXING_COMPATIBLE = 1;
                        // public static final int SCANNER_PIC2SHOP = 2;
                        // public static final int SCANNER_ZXING = 3;
                        int scanner = (Integer) oldValue;
                        switch (scanner) {
                            case 1:
                                scanner = ScannerManager.ZXING_COMPATIBLE;
                                break;
                            case 2:
                                scanner = ScannerManager.PIC2SHOP;
                                break;
                            case 3:
                                scanner = ScannerManager.ZXING;
                                break;
                            default:
                                scanner = ScannerManager.GOOGLE_PLAY_SERVICES;
                                break;
                        }
                        ed.putString(entry.getKey(), String.valueOf(scanner));
                        break;

                    case "App.CropFrameWholeImage":
                        ed.putBoolean(Prefs.pk_images_crop_whole_image, (Boolean) oldValue);
                        break;

                    case "App.UseExternalImageCropper":
                        ed.putBoolean(Prefs.pk_images_external_cropper, (Boolean) oldValue);
                        break;

                    case "BookList.Global.CacheThumbnails":
                        ed.putBoolean(Prefs.pk_images_cache_resized, (Boolean) oldValue);
                        break;

                    case "App.AutorotateCameraImages":
                        ed.putString(Prefs.pk_images_rotate_auto, String.valueOf(oldValue));
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
                        int tSize = (Boolean) oldValue ? ImageUtils.SCALE_MEDIUM
                                                       : ImageUtils.SCALE_SMALL;
                        // this is now a PInteger (a ListPreference), stored as a string
                        ed.putString(Prefs.pk_bob_cover_size, String.valueOf(tSize));
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
                        @BooklistStyle.TextScale
                        int con = (Boolean) oldValue ? BooklistStyle.TEXT_SCALE_SMALL
                                                     : BooklistStyle.TEXT_SCALE_MEDIUM;
                        // this is now a PInteger (a ListPreference), stored as a string
                        ed.putString(Prefs.pk_bob_text_size, String.valueOf(con));
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

                    case "BooksOnBookshelf.LIST_STYLE":
                        String e = (String) oldValue;
                        styleName = e.substring(0, e.length() - 2);
                        ed.putString(BooklistStyle.PREF_BL_STYLE_CURRENT_DEFAULT,
                                     BooklistStyle.getStyle(context, styleName).getUuid());
                        break;

                    case "BooklistStyles.Menu.Items":
                        // using a set to eliminate duplicates
                        Set<String> uuidSet = new LinkedHashSet<>();
                        String[] styles = ((String) oldValue).split(",");
                        for (String style : styles) {
                            styleName = style.substring(0, style.length() - 2);
                            uuidSet.add(BooklistStyle.getStyle(context, styleName).getUuid());
                        }
                        ed.putString(BooklistStyle.PREF_BL_PREFERRED_STYLES,
                                     TextUtils.join(",", uuidSet));
                        break;

                    // skip obsolete keys
                    case "StartupActivity.FAuthorSeriesFixupRequired":
                    case "start_in_my_books":
                    case "App.includeClassicView":
                    case "App.DisableBackgroundImage":
                    case "BookList.Global.FlatBackground":
                    case "BookList.Global.BackgroundThumbnails":
                    case "state_current_group_count":
                    case "state_sort":
                    case "state_bookshelf":
                    case "App.BooklistStyle":
                    case "HintManager.Hint.hint_amazon_links_blurb":

                        // skip keys that make no sense to copy
                    case "UpgradeMessages.LastMessage":
                    case "Startup.FtsRebuildRequired":
                    case "Startup.StartCount":
                    case "state_opened":
                    case "BooksOnBookshelf.TOP_ROW":
                    case "BooksOnBookshelf.TOP_ROW_TOP":
                        break;

                    default:
                        if (entry.getKey().startsWith("GoodReads")) {
                            String tmp1 = (String) oldValue;
                            if (!tmp1.isEmpty()) {
                                ed.putString(entry.getKey(), tmp1);
                            }

                        } else if (entry.getKey().startsWith("Backup")) {
                            String tmp1 = (String) oldValue;
                            if (!tmp1.isEmpty()) {
                                ed.putString(entry.getKey(), tmp1);
                            }

                        } else if (entry.getKey().startsWith("HintManager.Hint.hint_")) {
                            ed.putBoolean(
                                    entry.getKey().replace("HintManager.Hint.hint_",
                                                           TipManager.PREF_TIP),
                                    (Boolean) oldValue);

                        } else if (entry.getKey().startsWith("HintManager.Hint.")) {
                            ed.putBoolean(entry.getKey().replace("HintManager.Hint.",
                                                                 TipManager.PREF_TIP),
                                          (Boolean) oldValue);

                        } else if (entry.getKey().startsWith("lt_hide_alert_")) {
                            ed.putString(entry.getKey()
                                              .replace("lt_hide_alert_",
                                                       LibraryThingManager.PREFS_HIDE_ALERT),
                                         (String) oldValue);

                        } else if (entry.getKey().startsWith("field_visibility_")) {
                            //noinspection SwitchStatementWithTooFewBranches
                            switch (entry.getKey()) {
                                // remove these as obsolete
                                case "field_visibility_series_num":
                                    break;

                                default:
                                    // move everything else
                                    ed.putBoolean(entry.getKey()
                                                       .replace("field_visibility_",
                                                                App.PREFS_FIELD_VISIBILITY),
                                                  (Boolean) oldValue);
                                    break;
                            }

                        } else if (!entry.getKey().startsWith("state_current_group")) {
                            Logger.info(Prefs.class, "migratePreV200preferences",
                                        "unknown key=" + entry.getKey(),
                                        "value=" + oldValue);
                        }
                        break;
                }

            } catch (@NonNull final RuntimeException e) {
                // to bad... skip that key, not fatal, use default.
                Logger.error(Prefs.class, e, "key=" + entry.getKey());
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
