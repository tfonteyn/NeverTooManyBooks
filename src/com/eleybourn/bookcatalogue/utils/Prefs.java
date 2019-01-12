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
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.StartupActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Quick and easy.
 * - get the default prefs using the application context
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
            throw new IllegalStateException();
        }
        return BookCatalogueApp.getAppContext().getSharedPreferences(uuid, Context.MODE_PRIVATE);
    }

    /** Convenience wrapper - Get a GLOBAL preference. */
    public static boolean getBoolean(@StringRes final int keyId,
                                     final boolean defaultValue) {
        return getPrefs().getBoolean(BookCatalogueApp.getResString(keyId), defaultValue);
    }

    /** Convenience wrapper - Get a GLOBAL preference. */
    public static int getInt(@StringRes final int keyId,
                             final int defaultValue) {
        return getPrefs().getInt(BookCatalogueApp.getResString(keyId), defaultValue);
    }

    /** Convenience wrapper - Get a GLOBAL preference. */
    @Nullable
    public static String getString(@StringRes final int keyId,
                                   @Nullable final String defaultValue) {
        return getPrefs().getString(BookCatalogueApp.getResString(keyId), defaultValue);
    }

    /**
     * DEBUG method.
     */
    public static void dumpPreferences(@Nullable final String uuid) {
        if (/* always show debug */ BuildConfig.DEBUG) {
            Map<String, ?> map = uuid == null ? getPrefs().getAll() : getPrefs(uuid).getAll();
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
     * Some of these are real migrations,
     * some just for aesthetics's making the key's naming standard.
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
                switch (key) {
                    case "ScannerManager.PreferredScanner":
                        ed.putInt(key, ((Integer) oldMap.get(key)) - 1);
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

                    case "lt_devkey":
                        String tmpDevKey = (String) oldMap.get(key);
                        if (!tmpDevKey.isEmpty()) {
                            ed.putString(LibraryThingManager.PREFS_DEV_KEY,
                                         (String) oldMap.get(key));
                        }
                        break;

                    case "App.AutorotateCameraImages":
                        ed.putInt(BookCatalogueApp.getResString(
                                R.string.pk_thumbnails_rotate_auto),
                                  (Integer) oldMap.get(key));
                        break;

                    case "App.CropFrameWholeImage":
                        ed.putBoolean(BookCatalogueApp.getResString(
                                R.string.pk_thumbnails_crop_frame_is_whole_image),
                                      (Boolean) oldMap.get(key));
                        break;

                    case "App.UseExternalImageCropper":
                        ed.putBoolean(BookCatalogueApp.getResString(
                                R.string.pk_thumbnails_external_cropper),
                                      (Boolean) oldMap.get(key));
                        break;

                    case "SoundManager.BeepIfScannedIsbnInvalid":
                        ed.putBoolean(key, (Boolean) oldMap.get(key));
                        break;

                    case "SoundManager.BeepIfScannedIsbnValid":
                        ed.putBoolean(key, (Boolean) oldMap.get(key));
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
                                BookCatalogueApp.getResString(
                                        R.string.pk_bob_list_generation),
                                compatmode);
                        break;
                    }
                    case "App.OpenBookReadOnly":
                        ed.putBoolean(BookCatalogueApp.getResString(
                                R.string.pk_bob_open_book_read_only),
                                      (Boolean) oldMap.get(key));
                        break;

                    case "BooksOnBookshelf.BOOKSHELF":
                        String tmpBookshelf = (String) oldMap.get(key);
                        if (!tmpBookshelf.isEmpty()) {
                            ed.putString(BooksOnBookshelf.PREF_BOB_CURRENT_BOOKSHELF, tmpBookshelf);
                        }
                        break;

                    case "BooksOnBookshelf.TOP_ROW":
                        ed.putInt(BooksOnBookshelf.PREF_BOB_TOP_ROW,
                                  (Integer) oldMap.get(key));
                        break;

                    case "BooksOnBookshelf.TOP_ROW_TOP":
                        ed.putInt(BooksOnBookshelf.PREF_BOB_TOP_ROW_OFFSET,
                                  (Integer) oldMap.get(key));
                        break;

                    case "BookList.Global.BooklistState":
                        ed.putInt(
                                BookCatalogueApp.getResString(R.string.pk_bob_list_state),
                                (Integer) oldMap.get(key) - 1);
                        break;

                    case "BookList.Global.BackgroundThumbnails":
                        ed.putBoolean(BookCatalogueApp.getResString(
                                R.string.pk_bob_thumbnails_generating_mode),
                                      (Boolean) oldMap.get(key));
                        break;

                    case "BookList.Global.CacheThumbnails":
                        ed.putBoolean(BookCatalogueApp.getResString(
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
                    case "BooklistStyles.Menu.Items":
                        // set to eliminate duplicates
                        Set<Long> styleIds = new LinkedHashSet<>();
                        for (String entry : StringList.decode((String) oldMap.get(key))) {
                            styleIds.add(BooklistStyles.getStyleId(
                                    entry.substring(0, entry.length() - 2)));
                        }
                        ed.putString(BooklistStyle.PREF_BL_PREFERRED_STYLES,
                                     TextUtils.join(",", styleIds));
                        break;


                    // skip obsolete keys
                    case "StartupActivity.FAuthorSeriesFixupRequired":
                    case "start_in_my_books":
                    case "App.includeClassicView":
                    case "App.DisableBackgroundImage":
                    case "BookList.Global.FlatBackground":
                    case "state_current_group_count":
                    case "state_sort":
                    case "state_bookshelf":
                        // skip all global style settings; for now all is local to a style.
                    case "App.BooklistStyle":
                    case "APP.DisplayFirstThenLast":
                    case "APP.ShowAllAuthors":
                    case "APP.ShowAllSeries":
                    case "BookList.Condensed":
                    case "BookList.ShowHeaderInfo":
                    case "BookList.ShowAuthor":
                    case "BookList.ShowBookshelves":
                    case "BookList.ShowPublisher":
                    case "BookList.ShowThumbnails":
                    case "BookList.LargeThumbnails":
                    case "BookList.ShowLocation":
                        break;

                    default:

                        if (key.startsWith("GoodReads")
                                || key.startsWith("Backup")
                                || "App.Locale".equals(key)
                                ) {
                            String tmp = (String) oldMap.get(key);
                            if (!tmp.isEmpty()) {
                                ed.putString(key, tmp);
                            }
                        } else if (key.startsWith("HintManager")) {
                            ed.putBoolean(key, (Boolean) oldMap.get(key));

                        } else if (key.startsWith("lt_hide_alert_")) {
                            ed.putString(key.replace("lt_hide_alert_",
                                                     LibraryThingManager.PREFS_HIDE_ALERT),
                                         (String) oldMap.get(key));
                        } else if (key.startsWith("field_visibility_")) {
                            ed.putBoolean(key.replace("field_visibility_",
                                                      Fields.PREFS_FIELD_VISIBILITY),
                                          (Boolean) oldMap.get(key));
                        } else if (!key.startsWith("state_current_group")) {

                            Logger.info(Prefs.class, "unknown|key=" + key
                                    + "|value=" + oldMap.get(key).toString());
                        }
                        break;
                }

            } catch (RuntimeException e) {
                // to bad... skip that key, not fatal, use default.
                Logger.error(e, "key=" + key);
            }
        }
        ed.apply();
    }
}
