/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.preference.PreferenceManager;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

/**
 * Languages:
 * <ul>
 * <li>ISO 639-1: two-letter codes, one per language</li>
 * <li>ISO 639-2: three-letter codes, for the same languages as 639-1</li>
 * </ul>
 * The JDK uses "ISO3" for the 3-character ISO 639-2 format (not to be confused with ISO 639-3)
 */
public final class LocaleUtils {

    /** The SharedPreferences name where we'll maintain our language to ISO mappings. */
    private static final String LANGUAGE_MAP = "language2iso3";
    /**
     * A Map to translate currency symbols to their official ISO code.
     * <p>
     * ENHANCE: surely this can be done more intelligently ?
     */
    private static final Map<String, String> CURRENCY_MAP = new HashMap<>();
    /**
     * Prices are split into currency and actual amount.
     * Split on first digit, but leave it in the second part.
     */
    private static final Pattern SPLIT_PRICE_CURRENCY_AMOUNT_PATTERN = Pattern.compile("(?=\\d)");

    private LocaleUtils() {
    }

    /**
     * Populate CURRENCY_MAP.
     *
     * <a href="https://en.wikipedia.org/wiki/List_of_territorial_entities_where_English_is_an_official_language>
     * https://en.wikipedia.org/wiki/List_of_territorial_entities_where_English_is_an_official_language</a>
     */
    @UiThread
    private static void createCurrencyMap() {
        // allow re-creating
        CURRENCY_MAP.clear();

        // key in map should always be lowercase
        CURRENCY_MAP.put("", "");
        CURRENCY_MAP.put("€", "EUR");

        // English
        CURRENCY_MAP.put("a$", "AUD"); // Australian Dollar
        CURRENCY_MAP.put("nz$", "NZD"); // New Zealand Dollar
        CURRENCY_MAP.put("£", "GBP"); // British Pound
        CURRENCY_MAP.put("$", "USD"); // Trump Disney's

        CURRENCY_MAP.put("c$", "CAD"); // Canadian Dollar
        CURRENCY_MAP.put("ir£", "IEP"); // Irish Punt
        CURRENCY_MAP.put("s$", "SGD"); // Singapore dollar

        // supported locales (including pre-euro)
        CURRENCY_MAP.put("br", "RUB"); // Russian Rouble
        CURRENCY_MAP.put("zł", "PLN"); // Polish Zloty
        CURRENCY_MAP.put("kč", "CZK "); // Czech Koruna
        CURRENCY_MAP.put("kc", "CZK "); // Czech Koruna
        CURRENCY_MAP.put("dm", "DEM"); //german marks
        CURRENCY_MAP.put("ƒ", "NLG"); // Dutch Guilder
        CURRENCY_MAP.put("fr", "BEF"); // Belgian Franc
        CURRENCY_MAP.put("fr.", "BEF"); // Belgian Franc
        CURRENCY_MAP.put("f", "FRF"); // French Franc
        CURRENCY_MAP.put("ff", "FRF"); // French Franc
        CURRENCY_MAP.put("pta", "ESP"); // Spanish Peseta
        CURRENCY_MAP.put("L", "ITL"); // Italian Lira
        CURRENCY_MAP.put("Δρ", "GRD"); // Greek Drachma
        CURRENCY_MAP.put("₺", "TRY "); // Turkish Lira

        // some others as seen on ISFDB site
        CURRENCY_MAP.put("r$", "BRL"); // Brazilian Real
        CURRENCY_MAP.put("kr", "DKK"); // Denmark Krone
        CURRENCY_MAP.put("Ft", "HUF"); // Hungarian Forint
    }

    /**
     * Check if the user-preferred Locale is different from the currently in use Locale.
     *
     * @return {@code true} if there is a change (difference)
     */
    public static boolean isChanged(@NonNull final Context context) {
        boolean changed = !from(context).equals(getPreferredLocale());
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
            Logger.debug(LocaleUtils.class, "isChanged", "=" + changed);
        }
        return changed;
    }

    /**
     * Load the Locale setting from the users SharedPreference if needed.
     *
     * @param context to apply the user-preferred locale to.
     */
    public static void applyPreferred(@NonNull final Context context) {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
            Logger.debugEnter(LocaleUtils.class, "applyPreferred",
                              toDebugString(context));
        }

        if (!isChanged(context)) {
            return;
        }

        Locale userLocale = getPreferredLocale();

        // Apply the user-preferred Locale globally.... but this does not override already
        // loaded resources. So.. this is for FUTURE use when new resources get initialised.
        Locale.setDefault(userLocale);

        // Apply to the resources as passed in.
        // create a delta-configuration; i.e. only to be used to modify the added items.
        Configuration deltaOnlyConfig = new Configuration();
        deltaOnlyConfig.setLocale(userLocale);

        Resources resources = context.getResources();
        resources.updateConfiguration(deltaOnlyConfig, resources.getDisplayMetrics());

        // see if we need to add mappings for the new/current locale
        createLanguageMappingCache(userLocale);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
            Logger.debugExit(LocaleUtils.class, "applyPreferred",
                             toDebugString(context));
        }
    }

    /**
     * Test if the passed Locale is actually a 'real' Locale by checking ISO codes.
     */
    public static boolean isValid(@Nullable final Locale locale) {
        if (locale == null) {
            return false;
        }
        try {
            // MissingResourceException
            // NullPointerException can be thrown from within, when ISO3Language() fails.
            return locale.getISO3Language() != null && locale.getISO3Country() != null;
        } catch (@NonNull final MissingResourceException e) {
            // log but ignore.
            Logger.debug(LocaleUtils.class, "isValid",
                         "e=" + e.getLocalizedMessage(),
                         "locale=" + locale);
            return false;

        } catch (@NonNull final RuntimeException ignore) {
            return false;
        }
    }

    /**
     * @return the user-preferred Locale as stored in the preferences.
     */
    @NonNull
    public static Locale getPreferredLocale() {
        String lang = PreferenceManager.getDefaultSharedPreferences(App.getAppContext())
                                       .getString(Prefs.pk_ui_language, null);
        // the string "system" is also hardcoded in the preference string-array and
        // in the default setting in the preference screen.
        if (lang == null || lang.isEmpty() || "system".equalsIgnoreCase(lang)) {
            return App.getSystemLocale();
        } else {
            return from(lang);
        }
    }

    /**
     * syntax sugar...
     *
     * @return the current Locale for the passed context.
     */
    @NonNull
    public static Locale from(@NonNull final Context context) {
        Locale locale = context.getResources().getConfiguration().locale;
        return locale != null ? locale : Locale.ENGLISH;
    }

    /**
     * Creates a Locale from a concatenated locale string.
     *
     * @param code Locale name (e.g. 'de', 'en_AU')
     *
     * @return Locale corresponding to passed name
     */
    @NonNull
    private static Locale from(@NonNull final String code) {
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
     * Try to convert a DisplayName to an ISO code.
     * At installation (or upgrade to v200) we generated the users System Locale + Locale.ENGLISH
     * Each time the user switches language, we generate an additional set.
     * That probably covers a lot if not all.
     *
     * @param displayName the string as normally produced by {@link Locale#getDisplayLanguage}
     *
     * @return the ISO code, or if conversion failed, the input string
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
     * Try to convert a "language-country" code to an ISO code.
     *
     * @param source a standard ISO string like "en" or "en-GB" or "en-GB*"
     *
     * @return the ISO code, or if conversion failed, the input string
     */
    @NonNull
    public static String getISO3LanguageFromISO2(@NonNull final String source) {
        // shortcut for English "en", "en-GB", etc
        if ("en".equals(source) || source.startsWith("en-")) {
            return "eng";
        } else {
            try {
                Locale bl = new Locale(source.split("-")[0]);
                return bl.getISO3Language();
            } catch (@NonNull final MissingResourceException ignore) {
                return source;
            }
        }
    }

    /**
     * Convert the passed string with a (hopefully valid) currency unit, into the ISO code
     * for that currency.
     *
     * @param currency to convert
     *
     * @return ISO code.
     */
    @Nullable
    private static String currencyToISO(@NonNull final String currency) {
        if (CURRENCY_MAP.isEmpty()) {
            createCurrencyMap();
        }
        String key = currency.trim().toLowerCase(App.getSystemLocale());
        return CURRENCY_MAP.get(key);
    }

    /**
     * Translate the Language ISO code to the display name.
     *
     * @param iso the ISO code
     *
     * @return the display name for the language,
     * or the input string itself if it was an invalid ISO code
     */
    @NonNull
    public static String getDisplayName(@NonNull final Context context,
                                        @NonNull final String iso) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
            Logger.debugEnter(LocaleUtils.class, "getLabel",
                              "iso=" + iso, toDebugString(context));
        }
        return getDisplayName(from(context), iso);
    }

    /**
     * Translate the Language ISO code to the display name.
     *
     * @param locale to use for the output language
     * @param iso    the ISO code
     *
     * @return the display name for the language,
     * or the input string itself if it was an invalid ISO code
     */
    @NonNull
    public static String getDisplayName(@NonNull final Locale locale,
                                        @NonNull final String iso) {
        Locale isoLocale = new Locale(iso);
        if (isValid(isoLocale)) {
            return isoLocale.getDisplayLanguage(locale);
        }
        return iso;
    }

    /**
     * Load a Resources set for the specified Locale.
     * ENHANCE: should we cache these ?
     *
     * @param context       Current context
     * @param desiredLocale the desired Locale, e.g. the locale of a book,series,toc,...
     *
     * @return the Resources
     */
    @NonNull
    public static Resources getLocalizedResources(@NonNull final Context context,
                                                  @NonNull final Locale desiredLocale) {
        Configuration current = context.getResources().getConfiguration();
        Configuration configuration = new Configuration(current);
        String lang = desiredLocale.getLanguage();
        //FIXME: resources want 2-chars, locale 3-chars... is there a better way ?
        if (lang.length() == 2) {
            configuration.setLocale(desiredLocale);
        } else {
            // any 3-character code needs to be converted to be able to find the resource.
            configuration.setLocale(new Locale(mapLanguageCode(lang)));
        }

        Context localizedContext = context.createConfigurationContext(configuration);
        return localizedContext.getResources();
    }

    /**
     * Map an ISO 639-2 (3-char) language code to an ISO 639-1 (2-char) language code.
     * <p>
     * There is one entry here for each language supported.
     * NEWKIND: if a new resource language is added, enable the mapping here.
     *
     * @param iso3LanguageCode ISO 639-2 (3-char) language code
     *
     * @return ISO 639-1 (2-char) language code
     */
    private static String mapLanguageCode(@NonNull final String iso3LanguageCode) {

        switch (iso3LanguageCode) {
            case "eng":
                // English
                return "en";
            case "ces":
                // Czech
                return "cs";
            case "deu":
                // German
                return "de";
            case "ell":
                // Greek
                return "el";
            case "spa":
                // Spanish
                return "es";
            case "fra":
                // French
                return "fr";
            case "ita":
                // Italian
                return "it";
            case "nld":
                // Dutch
                return "nl";
            case "pol":
                // Polish
                return "pl";
            case "rus":
                // Russian
                return "ru";
            case "tur":
                // Turkish
                return "tr";
            default:
                // English
                return "en";
//
//            case "afr":
//                // Afrikaans
//                return "af";
//            case "sqi":
//                // Albanian
//                return "sq";
//            case "ara":
//                // Arabic
//                return "ar";
//            case "aze":
//                // Azeri
//                return "az";
//            case "eus":
//                // Basque
//                return "eu";
//            case "bel":
//                // Belarusian
//                return "be";
//            case "ben":
//                // Bengali
//                return "bn";
//            case "bul":
//                // Bulgarian
//                return "bg";
//            case "cat":
//                // Catalan
//                return "ca";
//            case "chi_sim":
//                // Chinese (Simplified)
//                return "zh-CN";
//            case "chi_tra":
//                // Chinese (Traditional)
//                return "zh-TW";
//            case "hrv":
//                // Croatian
//                return "hr";
//            case "dan":
//                // Danish
//                return "da";
//            case "est":
//                // Estonian
//                return "et";
//            case "fin":
//                // Finnish
//                return "fi";
//            case "glg":
//                // Galician
//                return "gl";
//            case "heb":
//                // Hebrew
//                return "he";
//            case "hin":
//                // Hindi
//                return "hi";
//            case "hun":
//                // Hungarian
//                return "hu";
//            case "isl":
//                // Icelandic
//                return "is";
//            case "ind":
//                // Indonesian
//                return "id";
//            case "jpn":
//                // Japanese
//                return "ja";
//            case "kan":
//                // Kannada
//                return "kn";
//            case "kor":
//                // Korean
//                return "ko";
//            case "lav":
//                // Latvian
//                return "lv";
//            case "lit":
//                // Lithuanian
//                return "lt";
//            case "mkd":
//                // Macedonian
//                return "mk";
//            case "msa":
//                // Malay
//                return "ms";
//            case "mal":
//                // Malayalam
//                return "ml";
//            case "mlt":
//                // Maltese
//                return "mt";
//            case "nor":
//                // Norwegian
//                return "no";
//            case "por":
//                // Portuguese
//                return "pt";
//            case "ron":
//                // Romanian
//                return "ro";
//            case "srp":
//                // Serbian (Latin)
//                // TODO is google expecting Cyrillic?
//                return "sr";
//            case "slk":
//                // Slovak
//                return "sk";
//            case "slv":
//                // Slovenian
//                return "sl";
//            case "swa":
//                // Swahili
//                return "sw";
//            case "swe":
//                // Swedish
//                return "sv";
//            case "tgl":
//                // Tagalog
//                return "tl";
//            case "tam":
//                // Tamil
//                return "ta";
//            case "tel":
//                // Telugu
//                return "te";
//            case "tha":
//                // Thai
//                return "th";
//            case "ukr":
//                // Ukrainian
//                return "uk";
//            case "vie":
//                // Vietnamese
//                return "vi";
//            default:
//                return iso3LanguageCode;
        }
    }

    /**
     * generate initial language2iso mappings.
     */
    public static void createLanguageMappingCache() {
        // the system default
        createLanguageMappingCache(App.getSystemLocale());
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
        Locale current = from(context);
        Locale configLocale = context.getResources().getConfiguration().locale;
        if (configLocale == null) {
            configLocale = Locale.ENGLISH;
        }
        return ""
               + "\nsSystemInitialLocale       : " + App.getSystemLocale().getDisplayName()
               + "\nsSystemInitialLocale(cur)  : " + App.getSystemLocale().getDisplayName(current)
               + "\nconfiguration.locale       : " + configLocale.getDisplayName()
               + "\nconfiguration.locale(cur)  : " + configLocale.getDisplayName(current)

               + "\nLocale.getDefault()        : " + Locale.getDefault().getDisplayName()
               + "\nLocale.getDefault(cur)     : " + Locale.getDefault().getDisplayName(current)
               + "\ngetPreferredLocale()       : " + getPreferredLocale().getDisplayName()
               + "\ngetPreferredLocale(cur)    : " + getPreferredLocale().getDisplayName(current)

               + "\nApp.isInNeedOfRecreating() : " + App.isInNeedOfRecreating()
               + "\nApp.isRecreating()         : " + App.isRecreating();
    }

    /**
     * Takes a combined price field, and returns the value/currency in the Bundle.
     *
     * <b>Note:</b>
     * The UK (GBP) pre-decimal had Shilling/Pence as subdivisions of the pound.
     * UK Shilling was written as "1/-", for example:
     * three shillings and six pence => 3/6
     * We don't convert this, but return that value as-is.
     * It's used on the ISFDB web site.
     * <a href="https://en.wikipedia.org/wiki/Pound_sterling#Pre-decimal">
     * https://en.wikipedia.org/wiki/Pound_sterling#Pre-decimal</a>
     *
     * @param priceWithCurrency price, e.g. "Bf459", "$9.99", ...
     * @param keyPrice          bundle key for the value
     * @param keyCurrency       bundle key for the currency
     * @param destination       bundle to add the two keys to.
     */
    public static void splitPrice(@NonNull final String priceWithCurrency,
                                  @NonNull final String keyPrice,
                                  @NonNull final String keyCurrency,
                                  @NonNull final Bundle destination) {
        String[] data = SPLIT_PRICE_CURRENCY_AMOUNT_PATTERN.split(priceWithCurrency, 2);
        if (data.length > 1) {
            String currencyCode = currencyToISO(data[0]);
            if (currencyCode != null && currencyCode.length() == 3) {
                try {
                    java.util.Currency currency = java.util.Currency.getInstance(currencyCode);

                    int decDigits = currency.getDefaultFractionDigits();
                    // format with 'digits' decimal places
                    Float price = Float.parseFloat(data[1]);
                    String priceStr = String.format("%." + decDigits + 'f', price);

                    destination.putString(keyPrice, priceStr);
                    // re-get the code just in case it used a recognised but non-standard string
                    destination.putString(keyCurrency, currency.getCurrencyCode());
                    return;

                } catch (@NonNull final NumberFormatException e) {
                    // accept the 'broken' price data[1]
                    destination.putString(keyPrice, data[1]);
                    destination.putString(keyCurrency, currencyCode);
                    return;

                } catch (@NonNull final IllegalArgumentException e) {
                    // Currency.getInstance sanity catch....
                    if (BuildConfig.DEBUG /* always */) {
                        Logger.error(LocaleUtils.class, e, "splitPrice",
                                     "data[0]=" + data[0], "data[1]=" + data[1]);
                    }
                }
            }
        }

        // fall back to the input
        destination.putString(keyPrice, priceWithCurrency);
        destination.putString(keyCurrency, "");
    }
}
