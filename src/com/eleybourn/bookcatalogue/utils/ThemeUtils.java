package com.eleybourn.bookcatalogue.utils;

import androidx.annotation.StyleRes;

import com.eleybourn.bookcatalogue.R;

/**
 * Static class. There is only ONE Theme *active* at any given time.
 */
public final class ThemeUtils {

    /**
     * NEWKIND: APP THEME.
     * Also add new themes in {@link R.array#pv_ui_theme},
     * the string-array order must match the APP_THEMES order
     * The preferences choice will be build according to the string-array list/order.
     */
    private static final int DEFAULT_THEME = 0;
    private static final int[] APP_THEMES = {
            R.style.AppTheme,
            R.style.AppTheme_Light,
            };
    private static final int[] DIALOG_THEMES = {
            R.style.AppTheme_Dialog,
            R.style.AppTheme_Light_Dialog,
            };
    private static final int[] DIALOG_ALERT_THEMES = {
            R.style.AppTheme_Dialog_Alert,
            R.style.AppTheme_Dialog_Alert_Light,
            };

    /** Cache the User-specified theme currently in use. */
    private static int mCurrentTheme;

    /** Last theme used; cached so we can check if it has genuinely changed. */
    private static int mLastTheme;

    /* static constructor. */
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
     * Load the Theme setting from the users SharedPreference.
     *
     * @return <tt>true</tt> if the theme was changed
     */
    public static boolean loadPreferred() {
        mCurrentTheme = Prefs.getInt(R.string.pk_ui_theme, DEFAULT_THEME);
        if (mCurrentTheme != mLastTheme) {
            mLastTheme = mCurrentTheme;
            return true;
        }
        return false;
    }
}
