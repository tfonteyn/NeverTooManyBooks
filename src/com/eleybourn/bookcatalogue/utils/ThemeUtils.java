package com.eleybourn.bookcatalogue.utils;

import android.support.annotation.NonNull;
import android.support.annotation.StyleRes;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.properties.ListProperty;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    /** all registered Listeners */
    private static final Set<WeakReference<OnThemeChangedListener>> mOnChangedListeners = new HashSet<>();

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
    public static ListProperty.ItemEntries<Integer> getThemePreferencesListItems() {
        ListProperty.ItemEntries<Integer> items = new ListProperty.ItemEntries<>();

        String[] themeList = BookCatalogueApp.getResourceStringArray(R.array.user_interface_theme_supported);
        for (int i = 0; i < themeList.length; i++) {
            items.add(i, R.string.single_string, themeList[i]);
        }
        return items;
    }

    /**
     * Load the Locale setting from the users SharedPreference.
     */
    public static void loadPreferred() {
        mCurrentTheme = BookCatalogueApp.getIntPreference(PREF_APP_THEME, DEFAULT_THEME);
    }

    /**
     * Tests if the Theme has really changed + updates the global setting
     *
     * @return true is a change was detected
     */
    public synchronized static boolean hasThemeReallyChanged() {
        if (mCurrentTheme != mLastTheme) {
            mLastTheme = mCurrentTheme;
            return true;
        }
        return false;
    }

    /**
     * Add a new OnLocaleChangedListener, and cleanup any dead references.
     */
    public static void addListener(final @NonNull OnThemeChangedListener listener) {
        List<WeakReference<OnThemeChangedListener>> toRemove = new ArrayList<>();

        boolean alreadyAdded = false;

        // make sure we're not adding the same twice + collect dead listeners
        for (WeakReference<OnThemeChangedListener> ref : mOnChangedListeners) {
            OnThemeChangedListener themeChangedListener = ref.get();
            if (themeChangedListener == null) {
                toRemove.add(ref);
            } else if (themeChangedListener == listener) {
                alreadyAdded = true;
            }
        }

        for (WeakReference<OnThemeChangedListener> ref : toRemove) {
            mOnChangedListeners.remove(ref);
        }

        if (!alreadyAdded) {
            mOnChangedListeners.add(new WeakReference<>(listener));
        }
    }

    /**
     * Remove the passed OnLocaleChangedListener, and cleanup any dead references.
     */
    public static void removeListener(final @NonNull OnThemeChangedListener listener) {
        List<WeakReference<OnThemeChangedListener>> toRemove = new ArrayList<>();

        // find the listener to remove + collect dead listeners
        for (WeakReference<OnThemeChangedListener> ref : mOnChangedListeners) {
            OnThemeChangedListener themeChangedListener = ref.get();
            if ((themeChangedListener == null) || (themeChangedListener == listener)) {
                toRemove.add(ref);
            }
        }
        for (WeakReference<OnThemeChangedListener> ref : toRemove) {
            mOnChangedListeners.remove(ref);
        }
    }

    /**
     * Send a message to all registered OnLocaleChangedListeners, and cleanup any dead references.
     */
    public static void notifyListeners() {
        List<WeakReference<OnThemeChangedListener>> toRemove = new ArrayList<>();

        for (WeakReference<OnThemeChangedListener> ref : mOnChangedListeners) {
            OnThemeChangedListener listener = ref.get();
            if (listener == null) {
                toRemove.add(ref);
            } else {
                try {
                    listener.onThemeChanged(mCurrentTheme);
                } catch (Exception ignore) {
                }
            }
        }
        for (WeakReference<OnThemeChangedListener> ref : toRemove) {
            mOnChangedListeners.remove(ref);
        }
    }

    /**
     * Interface definition
     */
    public interface OnThemeChangedListener {
        void onThemeChanged(final int currentTheme);
    }
}
