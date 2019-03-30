package com.eleybourn.bookcatalogue.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;

/**
 * Static class. There is only ONE Locale *active* at any given time.
 */
public final class LocaleUtils {

    /** The SharedPreferences name where we'll maintain our language to ISO3 mappings. */
    private static final String LANGUAGE_MAP = "language2iso3";
    /**
     * A Map to translate currency symbols to their official ISO3 code.
     * <p>
     * ENHANCE: surely this can be done more intelligently ?
     */
    private static final Map<String, String> CURRENCY_MAP = new HashMap<>();
    /** The locale used at startup; so that we can revert to system locale if we want to. */
    private static Locale sSystemInitialLocale;

    private LocaleUtils() {
    }

    /**
     * Needs to be called from main thread at App startup.
     */
    @UiThread
    public static void init() {

        // preserve startup==system Locale
        sSystemInitialLocale = Locale.getDefault();

        if (LocaleUtils.CURRENCY_MAP.isEmpty()) {
            // key in map should always be lowercase
            LocaleUtils.CURRENCY_MAP.put("", "");
            LocaleUtils.CURRENCY_MAP.put("€", "EUR");
        /*
        English
        https://en.wikipedia.org/wiki/List_of_territorial_entities_where_English_is_an_official_language
         */
            LocaleUtils.CURRENCY_MAP.put("a$", "AUD"); // Australian Dollar
            LocaleUtils.CURRENCY_MAP.put("nz$", "NZD"); // New Zealand Dollar
            LocaleUtils.CURRENCY_MAP.put("£", "GBP"); // British Pound
            LocaleUtils.CURRENCY_MAP.put("$", "USD"); // Trump Disney's

            LocaleUtils.CURRENCY_MAP.put("c$", "CAD"); // Canadian Dollar
            LocaleUtils.CURRENCY_MAP.put("ir£", "IEP"); // Irish Punt
            LocaleUtils.CURRENCY_MAP.put("s$", "SGD"); // Singapore dollar

            // supported locales (including pre-euro)

            LocaleUtils.CURRENCY_MAP.put("br", "RUB"); // Russian Rouble
            LocaleUtils.CURRENCY_MAP.put("zł", "PLN"); // Polish Zloty
            LocaleUtils.CURRENCY_MAP.put("kč", "CZK "); // Czech Koruna
            LocaleUtils.CURRENCY_MAP.put("kc", "CZK "); // Czech Koruna
            LocaleUtils.CURRENCY_MAP.put("dm", "DEM"); //german marks
            LocaleUtils.CURRENCY_MAP.put("ƒ", "NLG"); // Dutch Guilder
            LocaleUtils.CURRENCY_MAP.put("fr", "BEF"); // Belgian Franc
            LocaleUtils.CURRENCY_MAP.put("fr.", "BEF"); // Belgian Franc
            LocaleUtils.CURRENCY_MAP.put("f", "FRF"); // French Franc
            LocaleUtils.CURRENCY_MAP.put("ff", "FRF"); // French Franc
            LocaleUtils.CURRENCY_MAP.put("pta", "ESP"); // Spanish Peseta
            LocaleUtils.CURRENCY_MAP.put("L", "ITL"); // Italian Lira
            LocaleUtils.CURRENCY_MAP.put("Δρ", "GRD"); // Greek Drachma
            LocaleUtils.CURRENCY_MAP.put("₺", "TRY "); // Turkish Lira

            // some others as seen on ISFDB site
            LocaleUtils.CURRENCY_MAP.put("r$", "BRL"); // Brazilian Real
            LocaleUtils.CURRENCY_MAP.put("kr", "DKK"); // Denmark Krone
            LocaleUtils.CURRENCY_MAP.put("Ft", "HUF"); // Hungarian Forint
        }
    }

    /**
     * Check if the preferred theme is different from the currently in use Locale.
     *
     * @param context to check the configuration Locale
     *
     * @return <tt>true</tt> if there is a change (difference)
     */
    public static boolean isChanged(@NonNull final Context context) {
        boolean changed = !context.getResources().getConfiguration().locale.equals(getPreferredLocal());
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
            Logger.info(LocaleUtils.class, "isChanged", "==false");
        }
        return changed;
    }

    /**
     * Load the Locale setting from the users SharedPreference if needed.
     */
    public static void applyPreferred(@NonNull final Context context) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
            Logger.info(context, Tracker.State.Enter, "applyPreferred",
                        toDebugString(context));
        }
        if (!isChanged(context)) {
            return;
        }

        Locale userLocale = getPreferredLocal();

        // apply the user-preferred Locale
        Locale.setDefault(userLocale);

        // create a delta-configuration; i.e. only to be used to modify the added items.
        Configuration deltaOnlyConfig = new Configuration();
        deltaOnlyConfig.setLocale(userLocale);

        Resources res = context.getResources();
        res.updateConfiguration(deltaOnlyConfig, res.getDisplayMetrics());

        // see if we need to add mappings for the new/current locale
        createLanguageMappingCache(userLocale);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
            Logger.info(context, Tracker.State.Exit, "applyPreferred", "==true",
                        toDebugString(context));
        }
    }

    /**
     * Test if the passed Locale is actually a 'real' Locale by checking ISO3 codes.
     */
    public static boolean isValid(@Nullable final Locale locale) {
        if (locale == null) {
            return false;
        }
        try {
            // MissingResourceException
            // NullPointerException can be thrown from within, when the ISO3Language fails.
            return locale.getISO3Language() != null && locale.getISO3Country() != null;
        } catch (MissingResourceException e) {
            throw new IllegalStateException(e);
        } catch (RuntimeException ignore) {
            return false;
        }
    }

    @NonNull
    public static Locale getPreferredLocal() {
        String lang = App.getPrefString(Prefs.pk_ui_language);
        // the string "system" is also hardcoded in the preference string-array and
        // in the default setting in the preference screen.
        if (lang.isEmpty() || "system".equalsIgnoreCase(lang)) {
            return sSystemInitialLocale;
        } else {
            return LocaleUtils.getLocaleFromCode(lang);
        }
    }

    /**
     * Creates a Locale from a concatenated locale string.
     *
     * @param code Locale name (eg. 'de', 'en_AU')
     *
     * @return Locale corresponding to passed name
     */
    @NonNull
    private static Locale getLocaleFromCode(@NonNull final String code) {
        String[] parts;
        if (code.contains("_")) {
            parts = code.split("_");
        } else {
            parts = code.split("-");
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
     * At installation (or upgrade to v200) we generated the users System Locale + Locale.ENGLISH
     * Each time the user switches language, we generate an additional set.
     * That probably covers a lot if not all.
     *
     * @param displayName the string as normally produced by {@link Locale#getDisplayLanguage}
     *
     * @return the ISO3 code, or if conversion failed, the input string
     */
    @NonNull
    public static String getISO3Language(@NonNull final String displayName) {
        String iso = getLanguageCache().getString(displayName, null);
        if (iso != null) {
            return iso;
        }
        return displayName;
    }

    /**
     * Convert the passed string with a (hopefully valid) currency unit, into the ISO3 code
     * for that currency.
     *
     * @param currency  to convert
     *
     * @return ISO3 code.
     */
    @Nullable
    public static String currencyToISO(@NonNull final String currency) {
        return CURRENCY_MAP.get(currency.trim().toLowerCase());
    }

    /**
     * translate the Language ISO3 code to the display name.
     *
     * @return the display name for the language,
     * or the input string if it was an invalid ISO3 code
     */
    @NonNull
    public static String getDisplayName(@NonNull final String iso) {
        return new Locale(iso).getDisplayLanguage();
    }

    /**
     * generate initial language2iso mappings.
     */
    public static void createLanguageMappingCache() {
        // the system default
        createLanguageMappingCache(sSystemInitialLocale);
        // the one the user has configured our app into using
        createLanguageMappingCache(Locale.getDefault());
        // and English for compatibility with lots of websites.
        createLanguageMappingCache(Locale.ENGLISH);
    }

    /** Convenience method to get the language SharedPreferences file. */
    private static SharedPreferences getLanguageCache() {
        return App.getAppContext().getSharedPreferences(LANGUAGE_MAP, Context.MODE_PRIVATE);
    }

    /**
     * Generate language mappings for a given locale.
     */
    private static void createLanguageMappingCache(@NonNull final Locale myLocale) {
        SharedPreferences prefs = getLanguageCache();
        // just return if already done for this locale.
        if (prefs.getBoolean(myLocale.getISO3Language(), false)) {
            return;
        }

        SharedPreferences.Editor ed = prefs.edit();
        for (Locale loc : Locale.getAvailableLocales()) {
            ed.putString(loc.getDisplayLanguage(myLocale), loc.getISO3Language());
        }
        // signal this locale was done
        ed.putBoolean(myLocale.getISO3Language(), true);
        ed.apply();
    }

    public static String toDebugString(@NonNull final Context context) {
        return    "\nsSystemInitialLocale            : " + sSystemInitialLocale.getDisplayName()
                + "\ncontext configuration.locale    : " + context.getResources().getConfiguration().locale.getDisplayName()
                + "\nLocale.getDefault()             : " + Locale.getDefault().getDisplayName()
                + "\nLocaleUtils.getPreferredLocal() : " + getPreferredLocal().getDisplayName()
                + "\nApp.isInNeedOfRecreating()      : " + App.isInNeedOfRecreating()
                + "\nApp.isRecreating()              : " + App.isRecreating();
    }
}
