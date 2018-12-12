package com.eleybourn.bookcatalogue.utils;

import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.properties.ListOfValuesProperty;

/**
 * Static class. There is only ONE Theme *active* at any given time.
 */
public class ThemeUtils {
    /** Preferred interface Theme */
    public static final String PREF_APP_THEME = "App.Theme";
    /**
     * NEWKIND: APP THEME
     * Also add new themes in R.array.supported_themes,
     * the string-array order must match the APP_THEMES order
     * The preferences choice will be build according to the string-array list/order.
     */
    public static final int DEFAULT_THEME = 0;
    private static final int[] APP_THEMES = {
            R.style.AppTheme,
            R.style.AppTheme_Light
    };
    private static final int[] DIALOG_THEMES = {
            R.style.AppTheme_Dialog,
            R.style.AppTheme_Light_Dialog
    };
    private static final int[] DIALOG_ALERT_THEMES = {
            R.style.AppTheme_Dialog_Alert,
            R.style.AppTheme_Dialog_Alert_Light
    };

    /** Cache the User-specified theme currently in use */
    private static int mCurrentTheme;

    /** Last theme used; cached so we can check if it has genuinely changed */
    private static int mLastTheme;

    /* static constructor */
    static {
        loadPreferred();
        mLastTheme = mCurrentTheme;
    }

    private ThemeUtils() {
    }

    @StyleRes
    public static int getThemeResId() {
        return APP_THEMES[mCurrentTheme];
    }

    @StyleRes
    public static int getDialogThemeResId() {
        return DIALOG_THEMES[mCurrentTheme];
    }

    @SuppressWarnings("unused")
    @StyleRes
    public static int getDialogAlertThemeResId() {
        return DIALOG_ALERT_THEMES[mCurrentTheme];
    }

    /**
     * Format the list of themes
     *
     * @return List of preference themes
     */
    @NonNull
    public static ListOfValuesProperty.ItemList<Integer> getThemePreferencesListItems() {
        ListOfValuesProperty.ItemList<Integer> items = new ListOfValuesProperty.ItemList<>();
        String[] themeList = BookCatalogueApp.getResourceStringArray(R.array.user_interface_theme_supported);
        for (int i = 0; i < themeList.length; i++) {
            items.add(i, R.string.single_string, themeList[i]);
        }
        return items;
    }

    /**
     * Load the Theme setting from the users SharedPreference.
     *
     * @return true if the theme was changed
     */
    public static boolean loadPreferred() {
        mCurrentTheme = BookCatalogueApp.getIntPreference(PREF_APP_THEME, DEFAULT_THEME);
        if (mCurrentTheme != mLastTheme) {
            mLastTheme = mCurrentTheme;
            return true;
        }
        return false;
    }
}
