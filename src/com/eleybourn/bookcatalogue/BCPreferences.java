package com.eleybourn.bookcatalogue;

import android.content.SharedPreferences.Editor;
import android.support.annotation.NonNull;

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
public class BCPreferences {

    /** Name to use for global preferences; non-global should be moved to appropriate Activity code */

    // old style prefs without the TAG="App" prefix
    public static final String PREF_BOOKLIST_STYLE = "APP.BooklistStyle";
    /** Last full backup date */
    private static final String PREF_LAST_BACKUP_DATE = "Backup.LastDate";
    /** Last full backup file path */
    private static final String PREF_LAST_BACKUP_FILE = "Backup.LastFile";
    // All new prefs should start with TAG
    private static final String TAG = "App";
    public static final String PREF_CLASSIC_MY_BOOKS = TAG + ".includeClassicView";
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

    private BCPreferences() {
    }

    /**********************************************************************
     * getters preferences
     * for now, the defaults need to manually synchronized between below and
     * {@link PreferencesActivity}
     ***********************************************************************/

    public static boolean getStartInClassic() {
        return getBoolean(PREF_CLASSIC_MY_BOOKS, false);
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
    public static void setLastBackupFile(@NonNull final String file) {
        setString(PREF_LAST_BACKUP_FILE, file);
    }

    public static String getLastBackupDate() {
        return getString(PREF_LAST_BACKUP_DATE, null);
    }

    public static void setLastBackupDate(@NonNull final String date) {
        setString(PREF_LAST_BACKUP_DATE, date);
    }

    public static boolean getOpenBookReadOnly() {
        return getBoolean(PREF_OPEN_BOOK_READ_ONLY, true);
    }

    public static String getBookListStyle(@NonNull final String defaultValue) {
        return getString(PREF_BOOKLIST_STYLE, defaultValue);
    }

    /* *********************************************************************
     * Direct type access to the preferences
     ***********************************************************************/

    /** Get a named boolean preference */
    public static boolean getBoolean(@NonNull final String name, final boolean defaultValue) {
        boolean result;
        try {
            result = BookCatalogueApp.getSharedPreferences().getBoolean(name, defaultValue);
        } catch (Exception e) {
            result = defaultValue;
        }
        return result;
    }

    /** Set a named boolean preference */
    public static void setBoolean(@NonNull final String name, final boolean value) {
        Editor ed = edit();
        try {
            ed.putBoolean(name, value);
        } finally {
            ed.commit();
        }
    }

    /** Get a named string preference */
    public static String getString(@NonNull final String name, final String defaultValue) {
        String result;
        try {
            result = BookCatalogueApp.getSharedPreferences().getString(name, defaultValue);
        } catch (Exception e) {
            result = defaultValue;
        }
        return result;
    }

    /** Set a named string preference */
    public static void setString(@NonNull final String name, String value) {
        Editor ed = edit();
        try {
            ed.putString(name, value);
        } finally {
            ed.commit();
        }
    }

    /** Get a named string preference */
    public static int getInt(@NonNull final String name, int defaultValue) {
        int result;
        try {
            result = BookCatalogueApp.getSharedPreferences().getInt(name, defaultValue);
        } catch (Exception e) {
            result = defaultValue;
        }
        return result;
    }

    /** Set a named string preference */
    public static void setInt(@NonNull final String name, int value) {
        Editor ed = edit();
        try {
            ed.putInt(name, value);
        } finally {
            ed.commit();
        }
    }

    /** Get a standard preferences editor for mass updates */
    public static Editor edit() {
        return BookCatalogueApp.getSharedPreferences().edit();
    }


    /**
     * DEBUG method
     */
    @SuppressWarnings("unused")
    public static void dumpPreferences() {
        if (BuildConfig.DEBUG) {
            StringBuilder sb = new StringBuilder("\n\nSharedPreferences: ");
            Map<String, ?> map = BookCatalogueApp.getSharedPreferences().getAll();
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