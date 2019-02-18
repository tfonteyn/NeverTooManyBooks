package com.eleybourn.bookcatalogue.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BooksOnBookshelf;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.StartupActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.database.UpgradeDatabase;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;

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

    /**
     * Used by {@link androidx.preference.SwitchPreference} and standard booleans.
     *
     * @return boolean global preference
     */
    public static boolean getBoolean(@StringRes final int keyId,
                                     final boolean defaultValue) {
        return getPrefs().getBoolean(BookCatalogueApp.getResString(keyId), defaultValue);
    }

    /**
     * @return String global preference, can be empty but never null
     */
    @NonNull
    public static String getString(@StringRes final int keyId) {
        String sValue = getPrefs().getString(BookCatalogueApp.getResString(keyId), null);
        return sValue != null ? sValue : "";
    }

    /**
     * @return String global preference
     */
    @Nullable
    public static String getString(@StringRes final int keyId,
                                   @Nullable final String defaultValue) {
        return getPrefs().getString(BookCatalogueApp.getResString(keyId), defaultValue);
    }

    /**
     * {@link ListPreference} store the selected value as a String.
     * But they are really Integer values. Hence this transmogrification....
     *
     * @return int (stored as String) global preference
     */
    public static int getListPreference(@StringRes final int keyId,
                                        final int defaultValue) {
        String sValue = getPrefs().getString(BookCatalogueApp.getResString(keyId), null);
        if (sValue == null || sValue.isEmpty()) {
            return defaultValue;
        }
        return Integer.parseInt(sValue);
    }

    /**
     * {@link MultiSelectListPreference} store the selected value as a StringSet.
     * But they are really Integer values. Hence this transmogrification....
     *
     * @return int (stored as StringSet) global preference
     */
    public static Integer getMultiSelectListPreference(@StringRes final int keyId,
                                                       final int defaultValue) {
        Set<String> sValue = getPrefs().getStringSet(BookCatalogueApp.getResString(keyId), null);
        if (sValue == null || sValue.isEmpty()) {
            return defaultValue;
        }
        return toInteger(sValue);
    }



    /* ****************************************************************************************** */

    /**
     * DEBUG method.
     */
    public static void dumpPreferences(@Nullable final String uuid) {
        if (/* always show debug */ BuildConfig.DEBUG) {
            Map<String, ?> map = uuid != null ? getPrefs(uuid).getAll()
                                              : getPrefs().getAll();
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
            Object oldValue = oldMap.get(key);
            if (oldValue == null) {
                continue;
            }
            try {
                switch (key) {
                    /*
                     * User defined preferences.
                     */
                    case "App.Locale":
                        String tmp = (String) oldValue;
                        if (!tmp.isEmpty()) {
                            ed.putString(key, tmp);
                        }
                        break;

                    case "App.OpenBookReadOnly":
                        ed.putBoolean(BookCatalogueApp.getResString(
                                R.string.pk_bob_open_book_read_only),
                                      (Boolean) oldValue);
                        break;

                    case "BookList.Global.BooklistState":
                        ed.putString(BookCatalogueApp.getResString(R.string.pk_bob_list_state),
                                     String.valueOf((Integer) oldValue - 1));
                        break;

                    case "App.BooklistGenerationMode":
                        int compatMode = (Integer) oldValue;
                        switch (compatMode) {
                            case 4:
                                compatMode = BooklistBuilder.CompatibilityMode.PREF_MODE_DEFAULT;
                                break;
                            case 3:
                                compatMode = BooklistBuilder.CompatibilityMode.PREF_MODE_NESTED_TRIGGERS;
                                break;
                            case 2:
                                compatMode = BooklistBuilder.CompatibilityMode.PREF_MODE_FLAT_TRIGGERS;
                                break;
                            case 1:
                                compatMode = BooklistBuilder.CompatibilityMode.PREF_MODE_OLD_STYLE;
                                break;
                            default:
                                compatMode = BooklistBuilder.CompatibilityMode.PREF_MODE_DEFAULT;
                        }
                        ed.putString(
                                BookCatalogueApp.getResString(R.string.pk_bob_list_generation),
                                String.valueOf(compatMode));
                        break;

                    case "SoundManager.BeepIfScannedIsbnInvalid":
                        ed.putBoolean(key, (Boolean) oldValue);
                        break;

                    case "SoundManager.BeepIfScannedIsbnValid":
                        ed.putBoolean(key, (Boolean) oldValue);
                        break;

                    case "ScannerManager.PreferredScanner":
                        ed.putString(key, String.valueOf((Integer) oldValue - 1));
                        break;

                    case "App.CropFrameWholeImage":
                        ed.putBoolean(BookCatalogueApp.getResString(
                                R.string.pk_thumbnails_crop_frame_is_whole_image),
                                      (Boolean) oldValue);
                        break;

                    case "App.UseExternalImageCropper":
                        ed.putBoolean(BookCatalogueApp.getResString(
                                R.string.pk_thumbnails_external_cropper),
                                      (Boolean) oldValue);
                        break;

                    case "BookList.Global.CacheThumbnails":
                        ed.putBoolean(BookCatalogueApp.getResString(
                                R.string.pk_bob_thumbnails_cache_resized),
                                      (Boolean) oldValue);
                        break;

                    case "BookList.Global.BackgroundThumbnails":
                        ed.putBoolean(BookCatalogueApp.getResString(
                                R.string.pk_bob_thumbnails_generating_mode),
                                      (Boolean) oldValue);
                        break;

                    case "App.AutorotateCameraImages":
                        ed.putString(BookCatalogueApp.getResString(
                                R.string.pk_thumbnails_rotate_auto),
                                     String.valueOf(oldValue));
                        break;

                    /*
                     * Global defaults for styles
                     */
                    case "BookList.ShowAuthor":
                        ed.putBoolean(BookCatalogueApp.getResString(
                                R.string.pk_bob_show_author),
                                      (Boolean) oldValue);
                        break;

                    case "BookList.ShowBookshelves":
                        ed.putBoolean(BookCatalogueApp.getResString(
                                R.string.pk_bob_show_bookshelves),
                                      (Boolean) oldValue);
                        break;

                    case "BookList.ShowPublisher":
                        ed.putBoolean(BookCatalogueApp.getResString(
                                R.string.pk_bob_show_publisher),
                                      (Boolean) oldValue);
                        break;

                    case "BookList.ShowThumbnails":
                        ed.putBoolean(BookCatalogueApp.getResString(
                                R.string.pk_bob_thumbnails_show),
                                      (Boolean) oldValue);
                        break;

                    case "BookList.LargeThumbnails":
                        ed.putBoolean(BookCatalogueApp.getResString(
                                R.string.pk_bob_thumbnails_show_large),
                                      (Boolean) oldValue);
                        break;

                    case "BookList.ShowLocation":
                        ed.putBoolean(BookCatalogueApp.getResString(
                                R.string.pk_bob_show_location),
                                      (Boolean) oldValue);
                        break;

                    case "APP.DisplayFirstThenLast":
                        ed.putBoolean(BookCatalogueApp.getResString(
                                R.string.pk_bob_format_author_name),
                                      (Boolean) oldValue);
                        break;

                    case "APP.ShowAllAuthors":
                        ed.putBoolean(BookCatalogueApp.getResString(
                                R.string.pk_bob_books_under_multiple_authors),
                                      (Boolean) oldValue);
                        break;

                    case "APP.ShowAllSeries":
                        ed.putBoolean(BookCatalogueApp.getResString(
                                R.string.pk_bob_books_under_multiple_series),
                                      (Boolean) oldValue);
                        break;

                    case "BookList.Condensed":
                        int con = (Boolean) oldValue ? BooklistStyle.SCALE_SIZE_SMALLER
                                                     : BooklistStyle.SCALE_SIZE_NORMAL;
                        // this is now a PInteger, stored as a string
                        ed.putString(BookCatalogueApp.getResString(
                                R.string.pk_bob_item_size),
                                     String.valueOf(con));
                        break;

                    case "BookList.ShowHeaderInfo":
                        int shi = ((Integer) oldValue);
                        // this is now a PBitmask, stored as a Set
                        ed.putStringSet(BookCatalogueApp.getResString(
                                R.string.pk_bob_header),
                                        toStringSet(shi));
                        break;

                    /*
                     * User credentials
                     */
                    case "lt_devkey":
                        String tmpDevKey = (String) oldValue;
                        if (!tmpDevKey.isEmpty()) {
                            ed.putString(LibraryThingManager.PREFS_DEV_KEY, (String) oldValue);
                        }
                        break;

                    /*
                     * Internal settings
                     */
                    case "state_opened":
                        ed.putInt(StartupActivity.PREFS_STARTUP_COUNTDOWN,
                                  (Integer) oldValue);
                        break;

                    case StartupActivity.PREF_STARTUP_COUNT:
                        ed.putInt(key, (Integer) oldValue);
                        break;

                    case "BooksOnBookshelf.BOOKSHELF":
                        String tmpBookshelf = (String) oldValue;
                        if (!tmpBookshelf.isEmpty()) {
                            ed.putString(Bookshelf.PREF_BOOKSHELF_CURRENT, tmpBookshelf);
                        }
                        break;

                    case "BooksOnBookshelf.TOP_ROW":
                        ed.putInt(BooksOnBookshelf.PREF_BOB_TOP_ROW,
                                  (Integer) oldValue);
                        break;

                    case "BooksOnBookshelf.TOP_ROW_TOP":
                        ed.putInt(BooksOnBookshelf.PREF_BOB_TOP_ROW_OFFSET,
                                  (Integer) oldValue);
                        break;

                    case "BooksOnBookshelf.LIST_STYLE":
                        String entry = (String) oldValue;
                        ed.putLong(BooklistStyles.PREF_BL_STYLE_CURRENT_DEFAULT,
                                   BooklistStyles.getStyleId(
                                           entry.substring(0, entry.length() - 2)));
                        break;

                    case "BooklistStyles.Menu.Items":
                        // using a set to eliminate duplicates
                        Set<Long> styleIds = new LinkedHashSet<>();
                        String[] styles = ((String) oldValue).split(",");
                        for (String styleStringList : styles) {
                            styleIds.add(BooklistStyles.getStyleId(
                                    styleStringList.substring(0, styleStringList.length() - 2)));
                        }
                        ed.putString(BooklistStyles.PREF_BL_PREFERRED_STYLES,
                                     Csv.join(",", styleIds));
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
                    case "App.BooklistStyle":
                        // skip keys that make no sense to copy
                    case "UpgradeMessages.LastMessage":
                    case UpgradeDatabase.PREF_STARTUP_FTS_REBUILD_REQUIRED:
                        break;

                    default:
                        if (key.startsWith("GoodReads")) {
                            String tmp1 = (String) oldValue;
                            if (!tmp1.isEmpty()) {
                                ed.putString(key, tmp1);
                            }
                        } else if (key.startsWith("Backup")) {
                            String tmp1 = (String) oldValue;
                            if (!tmp1.isEmpty()) {
                                ed.putString(key, tmp1);
                            }
                        } else if (key.startsWith("HintManager")) {
                            ed.putBoolean(key, (Boolean) oldValue);

                        } else if (key.startsWith("lt_hide_alert_")) {
                            ed.putString(key.replace("lt_hide_alert_",
                                                     LibraryThingManager.PREFS_HIDE_ALERT),
                                         (String) oldValue);
                        } else if (key.startsWith("field_visibility_")) {
                            ed.putBoolean(key.replace("field_visibility_",
                                                      Fields.PREFS_FIELD_VISIBILITY),
                                          (Boolean) oldValue);
                        } else if (!key.startsWith("state_current_group")) {

                            Logger.info(Prefs.class, "unknown|key=" + key
                                    + "|value=" + oldValue.toString());
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

    @NonNull
    public static Integer toInteger(@NonNull final Set<String> sValue) {
        int tmp = 0;
        for (String s : sValue) {
            tmp += Integer.parseInt(s);
        }
        return tmp;
    }

    @NonNull
    public static Set<String> toStringSet(@NonNull final Integer value) {
        Set<String> set = new HashSet<>();
        int tmp = value;
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
}
