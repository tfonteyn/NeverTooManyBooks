package com.eleybourn.bookcatalogue.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BooksOnBookshelf;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.StartupActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Quick and easy way to:
 * - get the default prefs using the application context
 * - get preferences ClassCastException protected
 * - global debug flag
 * - use string resource id's instead of strings for the preference key.
 */
public final class Prefs {

    private Prefs() {
    }

    @NonNull
    public static SharedPreferences getPrefs() {
        return PreferenceManager.getDefaultSharedPreferences(BookCatalogueApp.getAppContext());
    }

    @NonNull
    public static SharedPreferences getPrefs(@NonNull final String uuid) {
        if (uuid == null) {
            return PreferenceManager.getDefaultSharedPreferences(BookCatalogueApp.getAppContext());
        }
        return BookCatalogueApp.getAppContext().getSharedPreferences(uuid, Context.MODE_PRIVATE);
    }

    /** Convenience wrapper - Get a GLOBAL preference */
    public static boolean getBoolean(@StringRes final int keyId,
                                     final boolean defaultValue) {
        return getBoolean(null, BookCatalogueApp.getResourceString(keyId), defaultValue);
    }

    /** Convenience wrapper - Get a GLOBAL preference */
    public static boolean getBoolean(@NonNull final String key,
                                     final boolean defaultValue) {
        return getBoolean(null, key, defaultValue);
    }

    /** Get a named boolean preference from a specific SharedPreferences */
    public static boolean getBoolean(@Nullable final String uuid,
                                     @NonNull final String key,
                                     final boolean defaultValue) {
        boolean result;
        try {
            result = getPrefs(uuid).getBoolean(key, defaultValue);
        } catch (ClassCastException e) {
            result = defaultValue;
        }
        if (DEBUG_SWITCHES.PREFS && BuildConfig.DEBUG) {
            Logger.info(Prefs.class,
                        "uuid=" + uuid + "|getBoolean|key=" + key +
                                "|defaultValue=`" + defaultValue + "`|result=`" + result + '`');
        }
        return result;
    }

    /** Convenience wrapper - Get a GLOBAL preference */
    public static int getInt(@StringRes final int keyId,
                             final int defaultValue) {
        return getInt(null, BookCatalogueApp.getResourceString(keyId), defaultValue);
    }

    /** Convenience wrapper - Get a GLOBAL preference */
    public static int getInt(@NonNull final String key,
                             final int defaultValue) {
        return getInt(null, key, defaultValue);
    }

    /** Get a named int preference from a specific SharedPreferences */
    public static int getInt(@Nullable final String uuid,
                             @NonNull final String key,
                             final int defaultValue) {
        int result;
        try {
            result = getPrefs(uuid).getInt(key, defaultValue);
        } catch (ClassCastException e) {
            result = defaultValue;
        }
        if (DEBUG_SWITCHES.PREFS && BuildConfig.DEBUG) {
            Logger.info(Prefs.class,
                        "uuid=" + uuid + "|getInt|key=" + key +
                                "|defaultValue=`" + defaultValue + "`|result=`" + result + '`');
        }
        return result;
    }

    /** Convenience wrapper - Get a GLOBAL preference */
    public static long getLong(@StringRes final int keyId,
                               final long defaultValue) {
        return getLong(null, BookCatalogueApp.getResourceString(keyId), defaultValue);
    }

    /** Convenience wrapper - Get a GLOBAL preference */
    public static long getLong(@NonNull final String keyId,
                               final long defaultValue) {
        return getLong(null, keyId, defaultValue);
    }

    /** Get a named long preference from a specific SharedPreferences */
    public static long getLong(@Nullable final String uuid,
                               @NonNull final String key,
                               final long defaultValue) {
        long result;
        try {
            result = getPrefs(uuid).getLong(key, defaultValue);
        } catch (ClassCastException e) {
            result = defaultValue;
        }
        if (DEBUG_SWITCHES.PREFS && BuildConfig.DEBUG) {
            Logger.info(Prefs.class,
                        "uuid=" + uuid + "|getLong|key=" + key +
                                "|defaultValue=`" + defaultValue + "`|result=`" + result + '`');
        }
        return result;
    }

    /** Convenience wrapper - Get a GLOBAL preference */
    @Nullable
    public static String getString(@StringRes final int keyId,
                                   @Nullable final String defaultValue) {
        return getString(null, BookCatalogueApp.getResourceString(keyId), defaultValue);
    }

    /** Convenience wrapper - Get a GLOBAL preference */
    @Nullable
    public static String getString(@NonNull final String key,
                                   @Nullable final String defaultValue) {
        return getString(null, key, defaultValue);
    }

    /** Get a named string preference from a specific SharedPreferences */
    @Nullable
    public static String getString(@Nullable final String uuid,
                                   @NonNull final String key,
                                   @Nullable final String defaultValue) {
        String result;
        try {
            result = getPrefs(uuid).getString(key, defaultValue);
        } catch (ClassCastException e) {
            result = defaultValue;
        }
        if (DEBUG_SWITCHES.PREFS && BuildConfig.DEBUG) {
            Logger.info(Prefs.class,
                        "uuid=" + uuid + "|getString|key=" + key +
                                "|defaultValue=`" + defaultValue + "`|result=`" + result + '`');
        }
        return result;
    }

//    @Nullable
//    public static Set<String> getStringSet(@Nullable final String uuid,
//                                           @NonNull final String key,
//                                           @Nullable final Set<String> defaultValue) {
//        Set<String> result;
//        try {
//            result = getPrefs(uuid).getStringSet(key, defaultValue);
//        } catch (ClassCastException e) {
//            result = defaultValue;
//        }
//        if (DEBUG_SWITCHES.PREFS && BuildConfig.DEBUG) {
//            Logger.info(Prefs.class,
//                        "uuid=" + uuid + "|getStringSet|key=" + key +
//                                "|defaultValue=`" + defaultValue + "`|result=`" + result + '`');
//        }
//        return result;
//    }

    /**
     * DEBUG method
     */
    public static void dumpPreferences(@Nullable final String uuid) {
        if (/* always show debug */ BuildConfig.DEBUG) {
            Map<String, ?> map = getPrefs(uuid).getAll();
            List<String> keyList = new ArrayList<>(map.keySet());
            String[] keys = keyList.toArray(new String[]{});
            Arrays.sort(keys);

            StringBuilder sb = new StringBuilder(
                    "\n\nSharedPreferences: " + (uuid == null ? "global" : uuid));
            for (String key : keys) {
                Object value = map.get(key);
                sb.append('\n').append(key).append('=').append(value);
            }
            sb.append("\n\n");
            Logger.info(BookCatalogueApp.class, sb.toString());
        }
    }

    /**
     * v200 brought a cleanup and re-structuring of the preferences.
     * Some of these are real migrations, some just for aesthetics's making the key's naming standard.
     */
    public static void migratePreV200preferences(@NonNull final Map<String, ?> oldMap) {
        if (oldMap.isEmpty()) {
            return;
        }

        // write to default prefs
        SharedPreferences.Editor ed = getPrefs().edit();

        // note that strings could be empty. Check if needed
        for (String key : oldMap.keySet()) {
            try {
                if (key.startsWith("GoodReads")
                        || key.startsWith("Backup")
                        || key.equals("App.Locale")
                        ) {
                    String tmp = (String) oldMap.get(key);
                    if (!tmp.isEmpty()) {
                        ed.putString(key, tmp);
                    }

                } else if (key.startsWith("HintManager")) {
                    ed.putBoolean(key, (Boolean) oldMap.get(key));

                } else if (key.startsWith("lt_hide_alert_")) {
                    ed.putString(
                            key.replace("lt_hide_alert_",
                                        LibraryThingManager.PREFS_HIDE_ALERT),
                            (String) oldMap.get(key));

                } else if (key.startsWith("field_visibility_")) {
                    ed.putBoolean(key.replace("field_visibility_",
                                              Fields.PREFS_FIELD_VISIBILITY),
                                  (Boolean) oldMap.get(key));

                } else if (key.startsWith("state_current_group")) {
                    // obsolete
                } else {
                    // individual keys:
                    switch (key) {
                        case "ScannerManager.PreferredScanner":
                            ed.putInt(key, ((Integer) oldMap.get(
                                    "ScannerManager.PreferredScanner")) - 1);
                            break;
                        case "UpgradeMessages.LastMessage": {
                            int v = (Integer) oldMap.get(key);
                            ed.putLong(StartupActivity.PREF_STARTUP_LAST_VERSION, v);
                            break;
                        }
                        case "state_opened":
                            ed.putInt(StartupActivity.PREFS_STARTUP_COUNTDOWN,
                                      (Integer) oldMap.get(key));
                            break;
                        case StartupActivity.PREF_STARTUP_COUNT:
                            ed.putInt(key, (Integer) oldMap.get(key));
                            break;
                        case StartupActivity.PREF_STARTUP_FTS_REBUILD_REQUIRED:
                            ed.putBoolean(key, (Boolean) oldMap.get(key));
                            break;
                        case "lt_devkey": {
                            String tmp = (String) oldMap.get(key);
                            if (!tmp.isEmpty()) {
                                ed.putString(LibraryThingManager.PREFS_DEV_KEY,
                                             (String) oldMap.get(key));
                            }
                            break;
                        }
                        case "App.AutorotateCameraImages":
                            ed.putInt(BookCatalogueApp.getResourceString(
                                    R.string.pk_thumbnails_rotate_auto), (Integer) oldMap.get(key));
                            break;
                        case "App.CropFrameWholeImage":
                            ed.putBoolean(BookCatalogueApp.getResourceString(
                                    R.string.pk_thumbnails_crop_frame_is_whole_image),
                                          (Boolean) oldMap.get(key));
                            break;
                        case "App.UseExternalImageCropper":
                            ed.putBoolean(BookCatalogueApp.getResourceString(
                                    R.string.pk_thumbnails_external_cropper),
                                          (Boolean) oldMap.get(key));
                            break;
                        case "SoundManager.BeepIfScannedIsbnInvalid":
                            ed.putBoolean(key, (Boolean) oldMap.get(key));
                            break;
                        case "SoundManager.BeepIfScannedIsbnValid":
                            ed.putBoolean(key, (Boolean) oldMap.get(key));
                            break;
                        case "APP.DisplayFirstThenLast":
                            ed.putBoolean(BookCatalogueApp.getResourceString(
                                    R.string.pk_bob_format_author_name),
                                          (Boolean) oldMap.get(key));
                            break;
                        case "APP.ShowAllAuthors":
                            ed.putBoolean(BookCatalogueApp.getResourceString(
                                    R.string.pk_bob_books_under_multiple_authors),
                                          (Boolean) oldMap.get(key));
                            break;
                        case "APP.ShowAllSeries":
                            ed.putBoolean(BookCatalogueApp.getResourceString(
                                    R.string.pk_bob_books_under_multiple_series),
                                          (Boolean) oldMap.get(key));
                            break;
                        case "App.BooklistGenerationMode": {
                            int compatmode = (Integer) oldMap.get(key);
                            switch (compatmode) {
//                                case 4:
//                                    compatmode = 0;
//                                    break;
                                case 3:
                                    compatmode = 1;
                                    break;
//                                case 2:
//                                    compatmode = 2;
//                                    break;
                                case 1:
                                    compatmode = 3;
                                    break;
                                default:
                                    compatmode = 0;
                            }
                            ed.putInt(
                                    BookCatalogueApp.getResourceString(
                                            R.string.pk_bob_list_generation),
                                    compatmode);
                            break;
                        }
                        case "App.OpenBookReadOnly":
                            ed.putBoolean(BookCatalogueApp.getResourceString(
                                    R.string.pk_bob_open_book_read_only),
                                          (Boolean) oldMap.get(key));
                            break;
                        case "BooksOnBookshelf.BOOKSHELF": {
                            String tmp = (String) oldMap.get(key);
                            if (!tmp.isEmpty()) {
                                ed.putString(BooksOnBookshelf.PREF_BOB_CURRENT_BOOKSHELF, tmp);
                            }
                            break;
                        }
                        case "BooksOnBookshelf.TOP_ROW":
                            ed.putInt(BooksOnBookshelf.PREF_BOB_TOP_ROW, (Integer) oldMap.get(key));
                            break;
                        case "BooksOnBookshelf.TOP_ROW_TOP":
                            ed.putInt(BooksOnBookshelf.PREF_BOB_TOP_ROW_OFFSET,
                                      (Integer) oldMap.get(key));
                            break;
                        case "BookList.Global.BooklistState":
                            ed.putInt(
                                    BookCatalogueApp.getResourceString(R.string.pk_bob_list_state),
                                    (Integer) oldMap.get(key) - 1);
                            break;
                        case "BookList.Global.BackgroundThumbnails":
                            ed.putBoolean(BookCatalogueApp.getResourceString(
                                    R.string.pk_bob_thumbnails_generating_mode),
                                          (Boolean) oldMap.get(key));
                            break;
                        case "BookList.Global.CacheThumbnails":
                            ed.putBoolean(BookCatalogueApp.getResourceString(
                                    R.string.pk_bob_thumbnails_cache_resized),
                                          (Boolean) oldMap.get(key));
                            break;
                        case "BooksOnBookshelf.LIST_STYLE": {
                            String entry = (String) oldMap.get(key);
                            ed.putLong(BooklistStyles.PREF_BL_STYLE_CURRENT_DEFAULT,
                                       BooklistStyles.getStyleId(
                                               entry.substring(0, entry.length() - 2)));
                            break;
                        }
                        case "BooklistStyles.Menu.Items": {
                            List<String> styleIds = new ArrayList<>();
                            List<String> list = StringList.decode((String) oldMap.get(key));
                            for (String entry : list) {
                                styleIds.add(String.valueOf(BooklistStyles.getStyleId(
                                        entry.substring(0, entry.length() - 2))));
                            }
                            ed.putString(BooklistStyle.PREF_BL_PREFERRED_STYLES,
                                         TextUtils.join(",", styleIds));
                            break;
                        }
                        case "BookList.Condensed":
                            ed.putInt(BookCatalogueApp.getResourceString(R.string.pk_bob_item_size),
                                      (Boolean) oldMap.get(
                                              key) ? BooklistStyle.SCALE_SIZE_SMALLER
                                                   : BooklistStyle.SCALE_SIZE_NORMAL);
                            break;
                        case "BookList.ShowHeaderInfo":
                            ed.putInt(BookCatalogueApp.getResourceString(R.string.pk_bob_header),
                                      (Integer) oldMap.get(key));
                            break;
                        case "BookList.ShowAuthor":
                            ed.putBoolean(
                                    BookCatalogueApp.getResourceString(R.string.pk_bob_show_author),
                                    (Boolean) oldMap.get(key));
                            break;
                        case "BookList.ShowBookshelves":
                            ed.putBoolean(BookCatalogueApp.getResourceString(
                                    R.string.pk_bob_show_bookshelves), (Boolean) oldMap.get(key));
                            break;
                        case "BookList.ShowPublisher":
                            ed.putBoolean(
                                    BookCatalogueApp.getResourceString(
                                            R.string.pk_bob_show_publisher),
                                    (Boolean) oldMap.get(key));
                            break;
                        case "BookList.ShowThumbnails":
                            ed.putBoolean(
                                    BookCatalogueApp.getResourceString(
                                            R.string.pk_bob_thumbnails_show),
                                    (Boolean) oldMap.get(key));
                            break;
                        case "BookList.LargeThumbnails":
                            ed.putBoolean(BookCatalogueApp.getResourceString(
                                    R.string.pk_bob_thumbnails_show_large),
                                          (Boolean) oldMap.get(key));
                            break;
                        case "BookList.ShowLocation":
                            ed.putBoolean(
                                    BookCatalogueApp.getResourceString(
                                            R.string.pk_bob_show_location),
                                    (Boolean) oldMap.get(key));
                            break;

                        // skip obsolete keys
                        case "StartupActivity.FAuthorSeriesFixupRequired":
                        case "start_in_my_books":
                        case "App.includeClassicView":
                        case "App.DisableBackgroundImage":
                        case "App.BooklistStyle":
                        case "BookList.Global.FlatBackground":
                        case "state_current_group_count":
                        case "state_sort":
                        case "state_bookshelf":
                            break;

                        default:
                            Logger.info(Prefs.class, "unknown|key=" + key + "|value=" + oldMap.get(
                                    key).toString());
                            break;
                    }
                }
            } catch (RuntimeException e) {
                // to bad... skip that key, not fatal, use default.
                Logger.error(e, "key=" + key);
            }
        }
        ed.apply();
    }
}
