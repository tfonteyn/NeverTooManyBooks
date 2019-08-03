package com.hardbacknutter.nevertomanybooks.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.StartupActivity;
import com.hardbacknutter.nevertomanybooks.booklist.BooklistBuilder;
import com.hardbacknutter.nevertomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertomanybooks.booklist.BooklistStyles;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertomanybooks.scanner.ScannerManager;
import com.hardbacknutter.nevertomanybooks.searches.librarything.LibraryThingManager;
import com.hardbacknutter.nevertomanybooks.utils.Csv;
import com.hardbacknutter.nevertomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertomanybooks.viewmodels.BooksOnBookshelfModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The preference key names here are the ones that define USER settings.
 * See {@link com.hardbacknutter.nevertomanybooks.settings.SettingsActivity} and children.
 * <p>
 * These keys *MUST* be kept in sync with "res/xml/preferences*.xml"
 * <p>
 * Application internal settings are done where they are needed/used.
 * <p>
 * The pre-v200 migration method {@link #migratePreV200preferences} is also located here.
 */
public final class Prefs {

    public static final String pk_ui_language = "App.Locale";
    public static final String pk_ui_theme = "App.Theme";
    public static final String pk_ui_messages_use = "App.UserMessage";

    public static final String pk_ui_network_mobile_data = "network.mobile_data";

    public static final String pk_thumbnails_zoom_upscale = "Image.Zoom.Upscale";
    public static final String pk_thumbnails_rotate_auto = "Image.Camera.Autorotate";
    public static final String pk_thumbnail_cropper_layer_type = "Image.ViewLayerType";
    public static final String pk_thumbnails_external_cropper = "Image.Cropper.UseExternalApp";
    public static final String pk_thumbnails_crop_whole_image = "Image.Cropper.FrameIsWholeImage";

    public static final String pk_scanning_beep_if_isbn_valid = "SoundManager.BeepIfScannedIsbnValid";
    public static final String pk_scanning_beep_if_isbn_invalid = "SoundManager.BeepIfScannedIsbnInvalid";

    public static final String pk_bob_open_book_read_only = "BooksOnBookshelf.OpenBookReadOnly";

    public static final String pk_scanning_preferred_scanner = "ScannerManager.PreferredScanner";

    public static final String pk_bob_list_state = "BookList.ListRebuildState";
    public static final String pk_bob_list_generation = "BookList.CompatibilityMode";
    public static final String pk_bob_thumbnails_generating_mode = "BookList.ThumbnailsInBackground";
    public static final String pk_bob_thumbnails_cache_resized = "BookList.ThumbnailsCached";

    public static final String pk_bob_style_name = "BookList.Style.Name";
    public static final String pk_bob_groups = "BookList.Style.Groups";
    public static final String pk_bob_preferred_style = "BookList.Style.Preferred";
    /** MultiSelectListPreference. */
    public static final String pk_bob_header = "BookList.Style.Show.HeaderInfo";

    public static final String pk_bob_text_size = "BookList.Style.Scaling";
    public static final String pk_bob_cover_size = "BookList.Style.Scaling.Thumbnails";

    public static final String pk_bob_books_under_multiple_authors = "BookList.Style.Group.Authors.ShowAll";
    public static final String pk_bob_books_under_multiple_series = "BookList.Style.Group.Series.ShowAll";
    public static final String pk_bob_format_author_name = "BookList.Style.Group.Authors.DisplayFirstThenLast";
    public static final String pk_bob_sort_author_name = "BookList.Style.Sort.Author.GivenFirst";

    public static final String pk_reformat_titles_on_insert = "title.reformat.insert";
    public static final String pk_reformat_titles_on_update = "title.reformat.update";

    /** Show the cover image for each book. */
    public static final String pk_bob_thumbnails_show = "BookList.Style.Show.Thumbnails";
    /** Show list of bookshelves for each book. */
    public static final String pk_bob_show_bookshelves = "BookList.Style.Show.Bookshelves";
    /** Show location for each book. */
    public static final String pk_bob_show_location = "BookList.Style.Show.Location";
    /** Show author for each book. */
    public static final String pk_bob_show_author = "BookList.Style.Show.Author";
    /** Show publisher for each book. */
    public static final String pk_bob_show_publisher = "BookList.Style.Show.Publisher";
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

    /** Legacy preferences name, pre-v200. */
    public static final String PREF_LEGACY_BOOK_CATALOGUE = "bookCatalogue";

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
     * v200 brought a cleanup and re-structuring of the preferences.
     * Some of these are real migrations,
     * some just for aesthetics's making the key's naming standard.
     */
    public static void migratePreV200preferences(@NonNull final Context context,
                                                 @NonNull final String name) {

        SharedPreferences oldPrefs = context.getSharedPreferences(name, Context.MODE_PRIVATE);

        Map<String, ?> oldMap = oldPrefs.getAll();
        if (oldMap.isEmpty()) {
            // API: 24 -> context.deleteSharedPreferences(name);
            oldPrefs.edit().clear().apply();
            return;
        }

        // write to default prefs
        SharedPreferences.Editor ed = PreferenceManager.getDefaultSharedPreferences(context)
                .edit();

        String styleName;

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
                            ed.putString(pk_ui_language, tmp);
                        }
                        break;

                    case "App.OpenBookReadOnly":
                        ed.putBoolean(pk_bob_open_book_read_only, (Boolean) oldValue);
                        break;

                    case "BookList.Global.BooklistState":
                        int bobState = (Integer) oldValue;
                        switch (bobState) {
                            case 1:
                                bobState = BooklistBuilder.PREF_LIST_REBUILD_ALWAYS_EXPANDED;
                                break;
                            case 2:
                                bobState = BooklistBuilder.PREF_LIST_REBUILD_ALWAYS_COLLAPSED;
                                break;
                            case 3:
                                bobState = BooklistBuilder.PREF_LIST_REBUILD_STATE_PRESERVED;
                                break;

                        }
                        ed.putString(pk_bob_list_state, String.valueOf(bobState));
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
                                break;
                        }
                        ed.putString(pk_bob_list_generation, String.valueOf(compatMode));
                        break;

                    case "SoundManager.BeepIfScannedIsbnInvalid":
                        ed.putBoolean(key, (Boolean) oldValue);
                        break;

                    case "SoundManager.BeepIfScannedIsbnValid":
                        ed.putBoolean(key, (Boolean) oldValue);
                        break;

                    case "ScannerManager.PreferredScanner":
                        int scanner = (Integer) oldValue;
                        switch (scanner) {
                            case 1:
                                scanner = ScannerManager.SCANNER_ZXING_COMPATIBLE;
                                break;
                            case 2:
                                scanner = ScannerManager.SCANNER_PIC2SHOP;
                                break;
                            case 3:
                                scanner = ScannerManager.SCANNER_ZXING;
                                break;
                        }
                        ed.putString(key, String.valueOf(scanner));
                        break;

                    case "App.CropFrameWholeImage":
                        ed.putBoolean(pk_thumbnails_crop_whole_image, (Boolean) oldValue);
                        break;

                    case "App.UseExternalImageCropper":
                        ed.putBoolean(pk_thumbnails_external_cropper, (Boolean) oldValue);
                        break;

                    case "BookList.Global.CacheThumbnails":
                        ed.putBoolean(pk_bob_thumbnails_cache_resized, (Boolean) oldValue);
                        break;

                    case "BookList.Global.BackgroundThumbnails":
                        ed.putBoolean(pk_bob_thumbnails_generating_mode, (Boolean) oldValue);
                        break;

                    case "App.AutorotateCameraImages":
                        ed.putString(pk_thumbnails_rotate_auto, String.valueOf(oldValue));
                        break;

                    /*
                     * Global defaults for styles
                     */
                    case "BookList.ShowAuthor":
                        ed.putBoolean(pk_bob_show_author, (Boolean) oldValue);
                        break;

                    case "BookList.ShowBookshelves":
                        ed.putBoolean(pk_bob_show_bookshelves, (Boolean) oldValue);
                        break;

                    case "BookList.ShowPublisher":
                        ed.putBoolean(pk_bob_show_publisher, (Boolean) oldValue);
                        break;

                    case "BookList.ShowThumbnails":
                        ed.putBoolean(pk_bob_thumbnails_show, (Boolean) oldValue);
                        break;

                    case "BookList.LargeThumbnails":
                        int tSize = (Boolean) oldValue ? ImageUtils.SCALE_MEDIUM
                                : ImageUtils.SCALE_SMALL;
                        // this is now a PInteger (a ListPreference), stored as a string
                        ed.putString(pk_bob_cover_size, String.valueOf(tSize));
                        break;

                    case "BookList.ShowLocation":
                        ed.putBoolean(pk_bob_show_location, (Boolean) oldValue);
                        break;

                    case "APP.DisplayFirstThenLast":
                        ed.putBoolean(pk_bob_format_author_name, (Boolean) oldValue);
                        break;

                    case "APP.ShowAllAuthors":
                        ed.putBoolean(pk_bob_books_under_multiple_authors, (Boolean) oldValue);
                        break;

                    case "APP.ShowAllSeries":
                        ed.putBoolean(pk_bob_books_under_multiple_series, (Boolean) oldValue);
                        break;

                    case "BookList.Condensed":
                        int con = (Boolean) oldValue ? BooklistStyle.TEXT_SCALE_SMALL
                                : BooklistStyle.TEXT_SCALE_MEDIUM;
                        // this is now a PInteger (a ListPreference), stored as a string
                        ed.putString(pk_bob_text_size, String.valueOf(con));
                        break;

                    case "BookList.ShowHeaderInfo":
                        int shi = ((Integer) oldValue) & BooklistStyle.SUMMARY_SHOW_ALL;
                        // this is now a PBitmask, stored as a Set
                        ed.putStringSet(pk_bob_header, toStringSet(shi));
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
                    case "state_opened":
                        ed.putInt(StartupActivity.PREF_STARTUP_COUNTDOWN, (Integer) oldValue);
                        break;

                    case "Startup.StartCount":
                        ed.putInt(StartupActivity.PREF_STARTUP_COUNT, (Integer) oldValue);
                        break;

                    case "BooksOnBookshelf.BOOKSHELF":
                        String tmpBookshelf = (String) oldValue;
                        if (!tmpBookshelf.isEmpty()) {
                            ed.putString(Bookshelf.PREF_BOOKSHELF_CURRENT, tmpBookshelf);
                        }
                        break;

                    case "BooksOnBookshelf.TOP_ROW":
                        ed.putInt(BooksOnBookshelfModel.PREF_BOB_TOP_ROW, (Integer) oldValue);
                        break;

                    case "BooksOnBookshelf.TOP_ROW_TOP":
                        ed.putInt(BooksOnBookshelfModel.PREF_BOB_TOP_ROW_OFFSET,
                                (Integer) oldValue);
                        break;

                    case "BooksOnBookshelf.LIST_STYLE":
                        String e = (String) oldValue;
                        styleName = e.substring(0, e.length() - 2);
                        ed.putString(BooklistStyles.PREF_BL_STYLE_CURRENT_DEFAULT,
                                BooklistStyles.getStyle(context, styleName).getUuid());
                        break;

                    case "BooklistStyles.Menu.Items":
                        // using a set to eliminate duplicates
                        Set<String> uuidSet = new LinkedHashSet<>();
                        String[] styles = ((String) oldValue).split(",");
                        for (String style : styles) {
                            styleName = style.substring(0, style.length() - 2);
                            uuidSet.add(BooklistStyles.getStyle(context, styleName).getUuid());
                        }
                        ed.putString(BooklistStyles.PREF_BL_PREFERRED_STYLES,
                                Csv.join(",", uuidSet));
                        break;

                    // skip obsolete keys
                    //noinspection SpellCheckingInspection
                    case "StartupActivity.FAuthorSeriesFixupRequired":
                    case "start_in_my_books":
                    case "App.includeClassicView":
                    case "App.DisableBackgroundImage":
                    case "BookList.Global.FlatBackground":
                    case "state_current_group_count":
                    case "state_sort":
                    case "state_bookshelf":
                    case "App.BooklistStyle":
                    case "HintManager.Hint.hint_amazon_links_blurb":
                        // skip keys that make no sense to copy
                    case "UpgradeMessages.LastMessage":
                    case "Startup.FtsRebuildRequired":
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

                        } else if (key.startsWith("HintManager.Hint.hint_")) {
                            ed.putBoolean(key.replace("HintManager.Hint.hint_", TipManager.PREF_TIP),
                                    (Boolean) oldValue);

                        } else if (key.startsWith("HintManager.Hint.")) {
                            ed.putBoolean(key.replace("HintManager.Hint.", TipManager.PREF_TIP),
                                    (Boolean) oldValue);

                        } else if (key.startsWith("lt_hide_alert_")) {
                            ed.putString(key.replace("lt_hide_alert_",
                                    LibraryThingManager.PREFS_HIDE_ALERT),
                                    (String) oldValue);

                        } else if (key.startsWith("field_visibility_")) {
                            //noinspection SwitchStatementWithTooFewBranches
                            switch (key) {
                                // remove these as obsolete
                                case "field_visibility_series_num":
                                    break;

                                default:
                                    // move everything else
                                    ed.putBoolean(key.replace("field_visibility_",
                                            App.PREFS_FIELD_VISIBILITY),
                                            (Boolean) oldValue);
                                    break;
                            }

                        } else if (!key.startsWith("state_current_group")) {
                            Logger.warn(Prefs.class, "migratePreV200preferences",
                                    "unknown key=" + key,
                                    "value=" + oldValue);
                        }
                        break;
                }

            } catch (@NonNull final RuntimeException e) {
                // to bad... skip that key, not fatal, use default.
                Logger.warnWithStackTrace(Prefs.class, e, "key=" + key);
            }
        }
        ed.apply();

        // API: 24 -> context.deleteSharedPreferences(name);
        oldPrefs.edit().clear().apply();
    }

}
