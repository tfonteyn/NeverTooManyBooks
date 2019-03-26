package com.eleybourn.bookcatalogue.utils;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;

import com.eleybourn.bookcatalogue.App;
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

    /** Cache the User-specified theme currently in use. */
    private static int mCurrentTheme;

    /** Last theme used; cached so we can check if it has genuinely changed. */
    private static int mLastTheme;

    /* static constructor. */
    static {
        mCurrentTheme = App.getListPreference(Prefs.pk_ui_theme, DEFAULT_THEME);
        mLastTheme = mCurrentTheme;
    }

    private ThemeUtils() {
    }

    @StyleRes
    public static int getThemeResId() {
        return APP_THEMES[mCurrentTheme];
    }

    /**
     * Load the Theme setting from the users SharedPreference.
     *
     * @return <tt>true</tt> if the theme was changed
     */
    public static boolean applyPreferred(@NonNull final Activity activity) {
        mCurrentTheme = App.getListPreference(Prefs.pk_ui_theme, DEFAULT_THEME);

        if (mCurrentTheme != mLastTheme) {
            mLastTheme = mCurrentTheme;

            activity.setTheme(APP_THEMES[mCurrentTheme]);
            return true;
        }
        return false;
    }
}
