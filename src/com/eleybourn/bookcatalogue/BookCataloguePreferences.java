package com.eleybourn.bookcatalogue;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/**
 * Class to manage application preferences rather than rely on each activity
 * knowing how to access them.
 *
 * @author Philip Warner
 */
public class BookCataloguePreferences {

    /** the name used for calls to Context.getSharedPreferences(name, ...) */
    public static final String APP_SHARED_PREFERENCES = "bookCatalogue";
    //FIXME: check if these need upgrading to https! If not, then move these back to the Manager class of their service
    // any https in the code was left as-is.
    public static final String WEBSITE_URL_EN_WIKIPEDIA_ORG = "http://en.wikipedia.org";
    public static final String WEBSITE_URL_LIBRARYTHING = "http://www.librarything.com";
    public static final String WEBSITE_URL_LIBRARYTHING_COVERS = "http://covers.librarything.com";
    public static final String WEBSITE_URL_GOOGLE_SCHEMAS = "http://schemas.google.co";
    /** Name to use for global preferences; non-global should be moved to appropriate Activity code */

    // old style prefs without the TAG="App" prefix
    public static final String PREF_START_IN_MY_BOOKS = "start_in_my_books";
    public static final String PREF_BOOKLIST_STYLE = "APP.BooklistStyle";
    /** Last full backup date */
    private static final String PREF_LAST_BACKUP_DATE = "Backup.LastDate";
    /** Last full backup file path */
    private static final String PREF_LAST_BACKUP_FILE = "Backup.LastFile";
    // All new prefs should start with TAG
    private static final String TAG = "App";
    public static final String PREF_INCLUDE_CLASSIC_MY_BOOKS = TAG + ".includeClassicView";
    public static final String PREF_DISABLE_BACKGROUND_IMAGE = TAG + ".DisableBackgroundImage";
    public static final String PREF_USE_EXTERNAL_IMAGE_CROPPER = TAG + ".UseExternalImageCropper";
    public static final String PREF_CROP_FRAME_WHOLE_IMAGE = TAG + ".CropFrameWholeImage";
    /** Degrees by which to rotate images automatically */
    public static final String PREF_AUTOROTATE_CAMERA_IMAGES = TAG + ".AutorotateCameraImages";
    /** Is book info opened in read-only mode. */
    public static final String PREF_OPEN_BOOK_READ_ONLY = TAG + ".OpenBookReadOnly";
    /** Preferred interface locale */
    public static final String PREF_APP_LOCALE = TAG + ".Locale";
    /** Theme */
    public static final String PREF_APP_THEME = TAG + ".Theme";
    /** Force list construction to compatible mode (compatible with Android 1.6) */
    public static final String PREF_BOOKLIST_GENERATION_MODE = TAG + ".BooklistGenerationMode";
    /** Static preference object so that we can respond to events relating to changes */
    private static SharedPreferences mPrefs = null;
    private BookCataloguePreferences() {
    }

    /** Get (or create) the static shared preferences */
    public static SharedPreferences getSharedPreferences() {
        if (mPrefs == null) {
            mPrefs = BookCatalogueApp.getAppContext().getSharedPreferences(APP_SHARED_PREFERENCES, BookCatalogueApp.MODE_PRIVATE);
        }
        return mPrefs;
    }

    /**********************************************************************
     * getters preferences
     * for now, the defaults need to manually synchronized between below and
     * {@link OtherPreferences}
     ***********************************************************************/

    public static boolean getStartInMyBook() {
        return getBoolean(PREF_START_IN_MY_BOOKS, false);
    }

    public static boolean getIncludeClassicMyBook() {
        return getBoolean(PREF_INCLUDE_CLASSIC_MY_BOOKS, true);
    }

    public static boolean getDisableBackgroundImage() {
        return getBoolean(PREF_DISABLE_BACKGROUND_IMAGE, false);
    }

    public static boolean getUseExternalImageCropper() {
        return getBoolean(PREF_USE_EXTERNAL_IMAGE_CROPPER, false);
    }

    public static boolean getCropFrameWholeImage() {
        return getBoolean(PREF_CROP_FRAME_WHOLE_IMAGE, false);
    }

    public static int getAutoRotateCameraImagesInDegrees() {
        return getInt(PREF_AUTOROTATE_CAMERA_IMAGES, 90);
    }

    public static int getTheme(int defaultValue) {
        return getInt(PREF_APP_THEME, defaultValue);
    }

    public static String getLocale() {
        return getString(PREF_APP_LOCALE, null);
    }

    public static String getLastBackupFile() {
        return getString(PREF_LAST_BACKUP_FILE, StorageUtils.getSharedStorage().getAbsolutePath());
    }

    /**
     * Setters
     */
    public static void setLastBackupFile(String file) {
        setString(PREF_LAST_BACKUP_FILE, file);
    }

    public static String getLastBackupDate() {
        return getString(PREF_LAST_BACKUP_DATE, null);
    }

    public static void setLastBackupDate(String date) {
        setString(PREF_LAST_BACKUP_DATE, date);
    }

    public static boolean getOpenBookReadOnly() {
        return getBoolean(PREF_OPEN_BOOK_READ_ONLY, true);
    }

    public static String getBookListStyle(String defaultValue) {
        return getString(PREF_BOOKLIST_STYLE, defaultValue);
    }

    /* *********************************************************************
     * Direct type access to the preferences
     ***********************************************************************/

    /** Get a named boolean preference */
    public static boolean getBoolean(String name, boolean defaultValue) {
        boolean result;
        try {
            result = mPrefs.getBoolean(name, defaultValue);
        } catch (Exception e) {
            result = defaultValue;
        }
        return result;
    }

    /** Set a named boolean preference */
    public static void setBoolean(String name, boolean value) {
        Editor ed = edit();
        try {
            ed.putBoolean(name, value);
        } finally {
            ed.commit();
        }
    }

    /** Get a named string preference */
    public static String getString(String name, String defaultValue) {
        String result;
        try {
            result = mPrefs.getString(name, defaultValue);
        } catch (Exception e) {
            result = defaultValue;
        }
        return result;
    }

    /** Set a named string preference */
    public static void setString(String name, String value) {
        Editor ed = edit();
        try {
            ed.putString(name, value);
        } finally {
            ed.commit();
        }
    }

    /** Get a named string preference */
    public static int getInt(String name, int defaultValue) {
        int result;
        try {
            result = mPrefs.getInt(name, defaultValue);
        } catch (Exception e) {
            result = defaultValue;
        }
        return result;
    }

    /** Set a named string preference */
    public static void setInt(String name, int value) {
        Editor ed = edit();
        try {
            ed.putInt(name, value);
        } finally {
            ed.commit();
        }
    }

    /** Get a standard preferences editor for mass updates */
    public static Editor edit() {
        return mPrefs.edit();
    }

    /**
     * DEBUG method
     */
    @SuppressWarnings("unused")
    public static void dumpPreferences() {
        if (BuildConfig.DEBUG) {
            StringBuilder sb = new StringBuilder("\n\nSharedPreferences: ");
            Map<String, ?> map = getSharedPreferences().getAll();
            ArrayList<String> keyList = new ArrayList<>(map.keySet());
            String[] keys = keyList.toArray(new String[]{});
            Arrays.sort(keys);

            for (String key : keys) {
                Object o = map.get(key);
                sb.append("\n").append(key).append("=").append(o);
            }
            sb.append("\n\n");
            System.out.println(sb);
        }
    }
}