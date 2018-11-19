package com.eleybourn.bookcatalogue.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.properties.ListProperty;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Static class. There is only ONE Locale *active* at any given time.
 */
public class LocaleUtils {
    /** Preferred interface Locale */
    public static final String PREF_APP_LOCALE = "App.Locale";

    /** The SharedPreferences name where we'll maintain our language to ISO3 mappings */
    private static final String LANGUAGE_MAP = "language2iso3";

    /** all registered Listeners */
    private static final Set<WeakReference<OnLocaleChangedListener>> mOnChangedListeners = new HashSet<>();

    /** The locale used at startup; so that we can revert to system locale if we want to */
    private static final Locale mInitialLocale;

    /** List of supported locales */
    @Nullable
    private static List<String> mSupportedLocales = null;

    /** Cache the User-specified locale currently in use */
    private static Locale mCurrentLocale;

    /** Last/previous locale used; cached so we can check if it has genuinely changed */
    @NonNull
    private static Locale mLastLocale;

    /* static constructor */
    static {
        // preserver startup==system Locale
        mInitialLocale = Locale.getDefault();

        loadPreferred();
        mLastLocale = mCurrentLocale;
    }

    private LocaleUtils() {
    }

    /**
     * get the actual system Locale as it was when we started
     */
    @NonNull
    public static Locale getSystemLocal() {
        return mInitialLocale;
    }

    /**
     * There seems to be something fishy in creating locales from full names (like en_AU),
     * so we split it and process it manually.
     *
     * @param name Locale name (eg. 'en_AU')
     *
     * @return Locale corresponding to passed name
     */
    @NonNull
    private static Locale getLocaleFromCode(final @NonNull String name) {
        String[] parts;
        if (name.contains("_")) {
            parts = name.split("_");
        } else {
            parts = name.split("-");
        }
        Locale locale;
        switch (parts.length) {
            case 1:
                locale = new Locale(parts[0]);
                break;
            case 2:
                locale = new Locale(parts[0], parts[1]);
                break;
            default:
                locale = new Locale(parts[0], parts[1], parts[2]);
                break;
        }
        return locale;
    }

    /**
     * Try to convert a DisplayName to an ISO3 code.
     * At installation (or upgrade to v83) we generated the users System Locale + Locale.ENGLISH
     * Each time the user switches language, we generate an additional set.
     * That probably covers a lot if not all.
     *
     * @param displayName the string as normally produced by {@link Locale#getDisplayLanguage}
     *
     * @return the ISO3 code, or if conversion failed, the input string
     */
    @NonNull
    public static String getISO3Language(final @NonNull String displayName) {
        String iso = getLanguageCache().getString(displayName, null);
        if (iso != null) {
            return iso;
        }
        return displayName;
    }

    /**
     * translate the Language ISO3 code to the display name.
     *
     * @return the display name for the language, or the input string if it was an invalid ISO3 code
     */
    @NonNull
    public static String getDisplayName(final @NonNull String iso) {
        return new Locale(iso).getDisplayLanguage();
    }

    /**
     * Load the current Locale setting from the users SharedPreference.
     */
    public static void loadPreferred() {
        String loc = BookCatalogueApp.getStringPreference(PREF_APP_LOCALE, null);
        if (loc != null && !loc.isEmpty()) {
            mCurrentLocale = LocaleUtils.getLocaleFromCode(loc);
        } else {
            mCurrentLocale = mInitialLocale;
        }
    }

    /**
     * Write a key-value pair to our mapping file (a separate SharedPreferences file)
     *
     * @param displayName key
     * @param iso         value
     */
    public static void cacheLanguage(final String displayName, final String iso) {
        getLanguageCache().edit().putString(displayName, iso).apply();
        if (BuildConfig.DEBUG) {
            Logger.info(LocaleUtils.class, "caching `" + displayName + "`=`" + iso + "`");
        }
    }

    private static SharedPreferences getLanguageCache() {
        return BookCatalogueApp.getAppContext().getSharedPreferences(LANGUAGE_MAP, Context.MODE_PRIVATE);
    }

    /**
     * Apply the (potentially changed) current Locale.
     */
    public static void apply(final @NonNull Resources res) {
        if (res.getConfiguration().locale.equals(mCurrentLocale)) {
            return;
        }

        Locale.setDefault(mCurrentLocale);

        Configuration config = new Configuration();
        config.setLocale(mCurrentLocale);

        res.updateConfiguration(config, res.getDisplayMetrics());

        // see if we need to add mappings for the new/current locale
        LocaleUtils.createLanguageMappingCache(mCurrentLocale);
    }

    /**
     * Get the list of supported locale names
     *
     * There are also: cs, pl but those are not complete.
     * (2018-11-10: pt_BR was removed altogether as it was pure english)
     *
     * Original code had full "land_country string"
     * "de_DE","en_AU","fr_FR","nl_NL","it_IT","el_GR","ru_RU","tr_TR"
     *
     * But this is artificially limiting, for example:
     * "fr_FR" gets you the language French + all 'french' locale 'things'
     * But french is spoken in other countries, which share the language, but not the locale 'things'
     *
     * By only specifying the language, we leave the pick of the country to the system locale.
     *
     * @return ArrayList of Locale codes
     */
    @NonNull
    private static List<String> getSupportedLocales() {
        if (mSupportedLocales == null) {
            mSupportedLocales = new ArrayList<>();

            mSupportedLocales.add("de");
            mSupportedLocales.add("en");
            mSupportedLocales.add("fr");
            mSupportedLocales.add("nl");

            mSupportedLocales.add("es");
            mSupportedLocales.add("it");
            mSupportedLocales.add("el");

            mSupportedLocales.add("ru");
            mSupportedLocales.add("tr");
        }
        return mSupportedLocales;
    }

    /**
     * Format the list of locales (languages)
     *
     * @return List of preference items
     */
    @NonNull
    public static ListProperty.ItemEntries<String> getLocalesPreferencesListItems() {
        ListProperty.ItemEntries<String> items = new ListProperty.ItemEntries<>();

        Locale locale = getSystemLocal();
        items.add("", R.string.preferred_language_x,
                BookCatalogueApp.getResourceString(R.string.user_interface_system_locale),
                locale.getDisplayLanguage());

        for (String loc : getSupportedLocales()) {
            locale = getLocaleFromCode(loc);
            items.add(loc, R.string.preferred_language_x,
                    locale.getDisplayLanguage(locale),
                    locale.getDisplayLanguage());
        }
        return items;
    }

    /**
     * Test if the passed Locale is actually a 'real' Locale by checking ISO3 codes.
     */
    public static boolean isValid(final @Nullable Locale locale) {
        if (locale == null) {
            return false;
        }
        try {
            // MissingResourceException
            // NullPointerException can be thrown from within, when the ISO3Language fails.
            return locale.getISO3Language() != null && locale.getISO3Country() != null;
        } catch (Exception ignore) {
            return false;
        }
    }

    /**
     * Generate language mappings (to a dedicated SharedPreferences) for a given locale
     */
    public static void createLanguageMappingCache(final @NonNull Locale myLocale) {
        SharedPreferences prefs = getLanguageCache();
        // just return if already done for this locale.
        if (prefs.getBoolean(myLocale.getISO3Language(), false)) {
            return;
        }

        SharedPreferences.Editor editor = prefs.edit();
        for (Locale loc : Locale.getAvailableLocales()) {
            editor.putString(loc.getDisplayLanguage(myLocale), loc.getISO3Language());
        }
        // signal this one was done
        editor.putBoolean(myLocale.getISO3Language(), true);
        editor.apply();
    }


    /**
     * Tests if the Locale has really changed + updates the global setting
     *
     * @return true is a change was detected
     */
    public synchronized static boolean hasLocalReallyChanged() {
        if ((!mCurrentLocale.equals(mLastLocale))) {
            mLastLocale = mCurrentLocale;
            return true;
        }
        return false;
    }

    /**
     * Add a new OnLocaleChangedListener, and cleanup any dead references.
     */
    public static void addListener(final @NonNull OnLocaleChangedListener listener) {
        List<WeakReference<OnLocaleChangedListener>> toRemove = new ArrayList<>();

        boolean alreadyAdded = false;

        // make sure we're not adding the same twice + collect dead listeners
        for (WeakReference<OnLocaleChangedListener> ref : mOnChangedListeners) {
            OnLocaleChangedListener localeChangedListener = ref.get();
            if (localeChangedListener == null) {
                toRemove.add(ref);
            } else if (localeChangedListener == listener) {
                alreadyAdded = true;
            }
        }

        for (WeakReference<OnLocaleChangedListener> ref : toRemove) {
            mOnChangedListeners.remove(ref);
        }

        if (!alreadyAdded) {
            mOnChangedListeners.add(new WeakReference<>(listener));
        }
    }

    /**
     * Remove the passed OnLocaleChangedListener, and cleanup any dead references.
     */
    public static void removeListener(final @NonNull OnLocaleChangedListener listener) {
        List<WeakReference<OnLocaleChangedListener>> toRemove = new ArrayList<>();

        // find the listener to remove + collect dead listeners
        for (WeakReference<OnLocaleChangedListener> ref : mOnChangedListeners) {
            OnLocaleChangedListener localeChangedListener = ref.get();
            if ((localeChangedListener == null) || (localeChangedListener == listener)) {
                toRemove.add(ref);
            }
        }
        for (WeakReference<OnLocaleChangedListener> ref : toRemove) {
            mOnChangedListeners.remove(ref);
        }
    }

    /**
     * Send a message to all registered OnLocaleChangedListeners, and cleanup any dead references.
     */
    public static void notifyListeners() {
        List<WeakReference<OnLocaleChangedListener>> toRemove = new ArrayList<>();

        for (WeakReference<OnLocaleChangedListener> ref : mOnChangedListeners) {
            OnLocaleChangedListener listener = ref.get();
            if (listener == null) {
                toRemove.add(ref);
            } else {
                try {
                    listener.onLocaleChanged(mCurrentLocale);
                } catch (Exception ignore) {
                }
            }
        }
        for (WeakReference<OnLocaleChangedListener> ref : toRemove) {
            mOnChangedListeners.remove(ref);
        }
    }

    /**
     * Interface definition
     */
    public interface OnLocaleChangedListener {
        void onLocaleChanged(final @NonNull Locale currentLocale);
    }
}
