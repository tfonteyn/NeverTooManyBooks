package com.eleybourn.bookcatalogue.utils;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BooksOnBookshelf;
import com.eleybourn.bookcatalogue.CoverHandler;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.StartupActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistPreferencesActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.database.DbSync;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKS;

/**
 * non-database upgrades are done here as we also (might) need them for importing/restoring.
 *
 * Version numbers used here refer to the android:versionCode in the manifest.
 *
 * V6.0.0 == 200
 */
public class UpgradeMigrations {

    /**
     * v200 brought a cleanup and re-structuring of the preferences.
     * Some of these are real migrations, some just for esthetics's making the key's standard.
     *
     * @param upgrade true for a real upgrade, false for an import.
     */
    public static void v200preferences(final @NonNull SharedPreferences prefs, final boolean upgrade) {
        Map<String, ?> oldMap = prefs.getAll();
        if (oldMap.isEmpty()) {
            return;
        }

        SharedPreferences.Editor ed = prefs.edit();
        // obsolete keys
        ed.remove("StartupActivity.FAuthorSeriesFixupRequired");
        ed.remove("start_in_my_books");
        ed.remove("App.includeClassicView");
        ed.remove("App.DisableBackgroundImage");
        ed.remove("App.BooklistStyle");
        ed.remove("BookList.Global.FlatBackground");
        ed.remove("state_current_group_count");
        ed.remove("state_sort");
        ed.remove("state_bookshelf");

        for (String key : oldMap.keySet()) {
            // no migration needed for these:
            if (key.startsWith("HintManager")
                    || key.startsWith("GoodReads")
                    || key.startsWith("Backup")
                    || key.startsWith("ScannerManager")
                    || key.startsWith("SoundManager")
                    || key.startsWith("Startup")
                    || key.equals("App.Locale")
                    ) {
                continue;
            }
            try {
                if (key.startsWith("lt_hide_alert_")) {
                    ed.putString(key.replace("lt_hide_alert_", LibraryThingManager.PREFS_HIDE_ALERT),
                            prefs.getString(key, ""));
                } else if (key.startsWith("field_visibility_")) {
                    ed.putBoolean(key.replace("field_visibility_", Fields.PREFS_FIELD_VISIBILITY),
                            prefs.getBoolean(key, true));
                } else if (key.startsWith("state_current_group")) {
                    ed.remove(key);
                } else {
                    // individual keys:
                    switch (key) {
                        case "UpgradeMessages.LastMessage":
                            ed.putInt(StartupActivity.PREF_STARTUP_LAST_VERSION,
                                    prefs.getInt(key, 0));
                            break;
                        case "state_opened":
                            ed.putInt(StartupActivity.PREFS_STARTUP_COUNTDOWN,
                                    prefs.getInt(key, 0));
                            break;

                        case "lt_devkey":
                            ed.putString(LibraryThingManager.PREFS_DEV_KEY,
                                    prefs.getString(key, ""));
                            break;

                        case "App.AutorotateCameraImages":
                            ed.putInt(CoverHandler.PREF_CAMERA_AUTOROTATE,
                                    prefs.getInt(key, 90));
                            break;
                        case "App.CropFrameWholeImage":
                            ed.putBoolean(CoverHandler.PREF_CROPPER_FRAME_IS_WHOLE_IMAGE,
                                    prefs.getBoolean(key, false));
                            break;
                        case "App.UseExternalImageCropper":
                            ed.putBoolean(CoverHandler.PREF_CROPPER_USE_EXTERNAL_APP,
                                    prefs.getBoolean(key, false));
                            break;

                        case "APP.DisplayFirstThenLast":
                            ed.putBoolean(BooklistGroup.BooklistAuthorGroup.PREF_DISPLAY_FIRST_THEN_LAST_NAMES,
                                    prefs.getBoolean(key, false));
                            break;
                        case "APP.ShowAllAuthors":
                            ed.putBoolean(BooklistGroup.BooklistAuthorGroup.PREF_SHOW_ALL_AUTHORS,
                                    prefs.getBoolean(key, false));
                            break;
                        case "APP.ShowAllSeries":
                            ed.putBoolean(BooklistGroup.BooklistSeriesGroup.PREF_SHOW_ALL_SERIES,
                                    prefs.getBoolean(key, false));
                            break;

                        case "App.BooklistGenerationMode":
                            ed.putInt(BooklistPreferencesActivity.PREF_COMPATIBILITY_MODE,
                                    prefs.getInt(key, BooklistPreferencesActivity.PREF_COMPATIBILITY_MODE_DEFAULT));
                            break;

                        case "App.OpenBookReadOnly":
                            ed.putBoolean(BooksOnBookshelf.PREF_BOB_OPEN_BOOK_READ_ONLY,
                                    prefs.getBoolean(key, true));
                            break;
                        case "BooksOnBookshelf.BOOKSHELF":
                            ed.putString(BooksOnBookshelf.PREF_BOB_CURRENT_BOOKSHELF,
                                    prefs.getString(key, BookCatalogueApp.getResourceString(R.string.initial_bookshelf)));
                            break;
                        case "BooksOnBookshelf.TOP_ROW":
                            ed.putInt(BooksOnBookshelf.PREF_BOB_TOP_ROW, prefs.getInt(key, 0));
                            break;
                        case "BooksOnBookshelf.TOP_ROW_TOP":
                            ed.putInt(BooksOnBookshelf.PREF_BOB_TOP_ROW_OFFSET, prefs.getInt(key, 0));
                            break;

                        case "BookList.Global.BooklistState":
                            ed.putInt(BooklistPreferencesActivity.PREF_LIST_REBUILD_STATE,
                                    prefs.getInt(key, BooklistPreferencesActivity.PREF_LIST_REBUILD_ALWAYS_EXPANDED));
                            break;
                        case "BookList.Global.BackgroundThumbnails":
                            ed.putBoolean(BooklistPreferencesActivity.PREF_THUMBNAILS_GENERATE_BACKGROUND,
                                    prefs.getBoolean(key, false));
                            break;
                        case "BookList.Global.CacheThumbnails":
                            ed.putBoolean(BooklistPreferencesActivity.PREF_THUMBNAILS_ARE_CACHED,
                                    prefs.getBoolean(key, false));
                            break;


                        case "BookList.ShowHeaderInfo":
                            ed.putInt(BooklistStyle.PREF_BL_STYLE_SHOW_HEADER_INFO,
                                    prefs.getInt(key, BooklistStyle.SUMMARY_SHOW_ALL));
                            break;
                        case "BookList.ShowAuthor":
                            ed.putBoolean(BooklistStyle.PREF_BL_STYLE_SHOW_AUTHOR,
                                    prefs.getBoolean(key, false));
                            break;
                        case "BookList.ShowBookshelves":
                            ed.putBoolean(BooklistStyle.PREF_BL_STYLE_SHOW_BOOKSHELVES,
                                    prefs.getBoolean(key, false));
                            break;
                        case "BookList.ShowPublisher":
                            ed.putBoolean(BooklistStyle.PREF_BL_STYLE_SHOW_PUBLISHER,
                                    prefs.getBoolean(key, false));
                            break;
                        case "BookList.Condensed":
                            ed.putInt(BooklistStyle.PREF_BL_STYLE_SCALE_SIZE,
                                    prefs.getBoolean(key, false) ? BooklistStyle.SCALE_SIZE_SMALLER : BooklistStyle.SCALE_SIZE_NORMAL);
                            break;

                        case "BooksOnBookshelf.LIST_STYLE": {
                            v200preferencesStyle(prefs, key, ed, BooklistStyles.PREF_BL_STYLE_CURRENT_DEFAULT);
                            break;
                        }
                        case "BooklistStyles.Menu.Items": {
                            v200preferencesStyle(prefs, key, ed, BooklistStyle.PREF_BL_STYLE_MENU_ITEMS);
                            break;
                        }

                        default:
                            Logger.info(UpgradeMigrations.class, "unknown|key=" + key + "|value=" + oldMap.get(key).toString());
                            break;
                    }
                }
                ed.remove(key);
            } catch (Exception e) {
                // to bad... skip that key, not fatal, use default.
                Logger.error(e);
            }
        }
        ed.apply();
    }

    private static void v200preferencesStyle(final @NonNull SharedPreferences prefs,
                                             final @NonNull String oldKey,
                                             final @NonNull SharedPreferences.Editor ed,
                                             final @NonNull String newKey) {
        ArrayList<String> list = StringList.decode(prefs.getString(oldKey, ""));
        for (String entry : list) {
            if (entry.endsWith("-u")) {
                ed.putLong(newKey, Long.parseLong(entry.substring(0, entry.length() - 2)));
            } else {
                ed.putLong(newKey, BooklistStyles.getBuiltinStyleId(entry.substring(0, entry.length() - 2)));
            }
        }
    }

    /**
     * For the upgrade to version 200, all cover files were moved to a sub directory.
     *
     * This routine renames all files, if they exist.
     */
    public static void v200moveCoversToDedicatedDirectory(final @NonNull DbSync.SynchronizedDb db) {

        try (Cursor cur = db.rawQuery("SELECT " + DOM_BOOK_UUID + " FROM " + TBL_BOOKS, new String[]{})) {
            while (cur.moveToNext()) {
                final String uuid = cur.getString(0);
                File source = StorageUtils.getFile(uuid + ".jpg");
                if (!source.exists()) {
                    source = StorageUtils.getFile(uuid + ".png");
                    if (!source.exists()) {
                        continue;
                    }
                }
                File destination = StorageUtils.getCoverFile(uuid);
                StorageUtils.renameFile(source, destination);
            }
        }
    }
}

