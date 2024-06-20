/*
 * @Copyright 2018-2024 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.tasks.BuildLanguageMappingsTask;

/**
 * Languages.
 * <ul>
 *      <li><a href="https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes">ISO 639-1</a>
 *          two-letter codes, one per language</li>
 *      <li><a href="https://en.wikipedia.org/wiki/ISO_639-2">ISO 639-2</a>
 *          three-letter codes, for the same languages as 639-1</li>
 * </ul>
 * The JDK uses "ISO3" for the 3-character ISO 639-2 format (not to be confused with ISO 639-3)
 */
@SuppressWarnings("WeakerAccess")
public class Languages {

    /**
     * The SharedPreferences name where we'll maintain our language to ISO mappings.
     * <br>
     * Key: the {@link Locale#getDisplayLanguage()} in all lowercase
     * (i.e. WITHOUT country/script)
     * <br>
     * Value: {@link #getIsoCode(Locale)}
     */
    @VisibleForTesting
    public static final String LANGUAGE_MAP = "language2iso3";
    /** Prefix added to the iso code for the 'done' flag in the language cache. */
    private static final String LANG_CREATED_PREFIX = "___";

    @NonNull
    private final Map<String, String> lang3ToLang2Map;
    @NonNull
    private final Supplier<AppLocale> appLocaleSupplier;


    /**
     * Constructor.
     *
     * @param appLocaleSupplier deferred supplier for the {@link AppLocale}.
     */
    public Languages(@NonNull final Supplier<AppLocale> appLocaleSupplier) {
        this.appLocaleSupplier = appLocaleSupplier;

        final String[] languages = Locale.getISOLanguages();
        lang3ToLang2Map = new HashMap<>(languages.length);
        Arrays.stream(languages).forEach(
                language -> lang3ToLang2Map.put(getIsoCode(new Locale(language)), language));
    }

    /**
     * Try to convert a Language to a Locale.
     *
     * @param context  Current context
     * @param language ISO codes (2 or 3 char), or a display-string (4+ characters)
     *
     * @return the best matching Locale we could determine
     *
     * @see AppLocale#getLocale(Context, String)
     */
    @NonNull
    public Locale toLocale(@NonNull final Context context,
                           @Nullable final String language) {

        if (language == null || language.isBlank()) {
            return context.getResources().getConfiguration().getLocales().get(0);
        }
        return appLocaleSupplier.get().getLocale(context, language).orElseGet(
                () -> context.getResources().getConfiguration().getLocales().get(0));
    }

    /**
     * Try to convert a Language ISO code to the display name.
     *
     * @param context Current context
     * @param iso3    the ISO code
     *
     * @return the display name for the language,
     *         or the input string itself if it was an invalid ISO code
     */
    @NonNull
    public String getDisplayLanguageFromISO3(@NonNull final Context context,
                                             @NonNull final String iso3) {
        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        return appLocaleSupplier.get().getLocale(context, iso3)
                                .map(locale -> locale.getDisplayLanguage(userLocale))
                                .orElse(iso3);
    }

    /**
     * Try to convert a language string to an ISO3 code.
     * At installation time we generated the users System Locale + Locale.ENGLISH
     * Each time the user switches language, we generate an additional set.
     * That probably covers a lot if not all.
     *
     * @param context  Current context
     * @param locale   the locale of the language string
     * @param language the string as normally produced by {@link Locale#getDisplayLanguage}
     *
     * @return the ISO code, or if conversion failed, the input string
     */
    @NonNull
    public String getISO3FromDisplayLanguage(@NonNull final Context context,
                                             @NonNull final Locale locale,
                                             @NonNull final String language) {

        final String source = language.trim().toLowerCase(locale);
        if (source.isEmpty()) {
            return "";
        }
        // create the mappings for the given locale if they don't exist yet
        createLanguageMappingCache(context, locale);

        return getCacheFile(context).getString(source, source);
    }

    /**
     * Try to convert a "language-country" code to an ISO3 code.
     *
     * @param code a standard ISO string like "en" or "en-GB" or "en-GB*"
     *
     * @return the ISO code, or if conversion failed, the input string
     */
    @NonNull
    public String getISO3FromCode(@NonNull final String code) {
        // shortcut for English "en", "en-GB", etc
        if ("en".equals(code) || code.startsWith("en_") || code.startsWith("en-")) {
            return "eng";
        } else {
            try {
                return getIsoCode(appLocaleSupplier.get().create(code));
            } catch (@NonNull final MissingResourceException ignore) {
                return code;
            }
        }
    }

    /**
     * Map an ISO 639-2 (3-char) language code to a language code suited
     * to be use with {@code new Locale(x)}.
     * <pre>
     * Rant:
     * Java 8 as bundled with Android Studio 3.5 on Windows:
     * new Locale("fr") ==> valid French Locale
     * new Locale("fre") ==> valid French Locale
     * new Locale("fra") ==> INVALID French Locale
     *
     * Android 8.0 in emulator bundled with Android Studio 3.5 on Windows:
     * new Locale("fr") ==> valid French Locale
     * new Locale("fre") ==> INVALID French Locale
     * new Locale("fra") ==>  valid French Locale
     * </pre>
     * A possible explanation is that Android use ICU classes internally.<br>
     * Also see {@link #toBibliographic} and {@link #toTerminology}.
     * <br><br>
     * <strong>Note:</strong> check the javadoc on {@link Locale#getISOLanguages()} for caveats.
     *
     * @param userLocale Current Locale
     * @param iso3       ISO 639-2 (3-char) language code
     *                   (either bibliographic or terminology coded)
     *
     * @return a language code that can be used with {@code new Locale(x)},
     *         or the incoming string if conversion failed.
     */
    @NonNull
    String getLocaleIsoFromISO3(@NonNull final Locale userLocale,
                                @NonNull final String iso3) {

        String iso2 = lang3ToLang2Map.get(iso3);
        if (iso2 != null) {
            return iso2;
        }

        // try again ('terminology' seems to be preferred/standard on Android (ICU?)
        String lang = toTerminology(userLocale, iso3);
        iso2 = lang3ToLang2Map.get(lang);
        if (iso2 != null) {
            return iso2;
        }

        // desperate and last attempt using 'bibliographic'.
        lang = toBibliographic(userLocale, iso3);
        iso2 = lang3ToLang2Map.get(lang);
        if (iso2 != null) {
            return iso2;
        }

        // give up
        return iso3;
    }

    /**
     * Convert the 3-char terminology code to bibliographic code.
     * <p>
     * <a href="https://www.loc.gov/standards/iso639-2/php/code_list.php">iso639-2</a>
     * <p>
     * This is the entire set correct as on 2019-08-22.
     *
     * @param locale to use for case manipulation
     * @param iso3   ISO 639-2 (3-char) language code (either bibliographic or terminology coded)
     *
     * @return the bibliographic code
     */
    @NonNull
    public String toBibliographic(@NonNull final Locale locale,
                                  @NonNull final String iso3) {
        final String source = iso3.trim().toLowerCase(locale);
        if (source.length() != 3) {
            return source;
        }

        switch (source) {
            // Albanian
            case "sqi":
                return "alb";
            // Armenian
            case "hye":
                return "arm";
            // Basque
            case "eus":
                return "baq";
            // Burmese
            case "mya":
                return "bur";
            // Chinese
            case "zho":
                return "chi";
            // Czech
            case "ces":
                return "cze";
            // Dutch
            case "dut":
                return "nld";
            // French
            case "fra":
                return "fre";
            // Georgian
            case "kat":
                return "geo";
            // German
            case "deu":
                return "ger";
            // Greek
            case "ell":
                return "gre";
            // Icelandic
            case "isl":
                return "ice";
            // Macedonian
            case "mkd":
                return "mac";
            // Maori
            case "mri":
                return "mao";
            // Malay
            case "msa":
                return "may";
            // Persian
            case "fas":
                return "per";
            // Romanian
            case "ron":
                return "rum";
            // Slovak
            case "slk":
                return "slo";
            // Tibetan
            case "bod":
                return "tib";
            // Welsh
            case "cym":
                return "wel";

            default:
                return source;
        }
    }

    /**
     * Convert the 3-char bibliographic code to terminology code.
     * <p>
     * <a href="https://www.loc.gov/standards/iso639-2/php/code_list.php">iso639-2</a>
     * <p>
     * This is the entire set correct as on 2019-08-22.
     *
     * @param locale to use for case manipulation
     * @param iso3   ISO 639-2 (3-char) language code (either bibliographic or terminology coded)
     *
     * @return the terminology code
     */
    @NonNull
    public String toTerminology(@NonNull final Locale locale,
                                @NonNull final String iso3) {
        final String source = iso3.trim().toLowerCase(locale);
        if (source.length() != 3) {
            return source;
        }
        switch (source) {
            // Albanian
            case "alb":
                return "sqi";
            // Armenian
            case "arm":
                return "hye";
            // Basque
            case "baq":
                return "eus";
            // Burmese
            case "bur":
                return "mya";
            // Chinese
            case "chi":
                return "zho";
            // Czech
            case "cze":
                return "ces";
            // Dutch
            case "dut":
                return "nld";
            // French
            case "fre":
                return "fra";
            // Georgian
            case "geo":
                return "kat";
            // German
            case "ger":
                return "deu";
            // Greek
            case "gre":
                return "ell";
            // Icelandic
            case "ice":
                return "isl";
            // Macedonian
            case "mac":
                return "mkd";
            // Maori
            case "mao":
                return "mri";
            // Malay
            case "may":
                return "msa";
            // Persian
            case "per":
                return "fas";
            // Romanian
            case "rum":
                return "ron";
            // Slovak
            case "slo":
                return "slk";
            // Tibetan
            case "tib":
                return "bod";
            // Welsh
            case "wel":
                return "cym";

            default:
                return source;
        }
    }


    /**
     * Create all cache files. This method is called during startup
     * from {@link BuildLanguageMappingsTask}.
     *
     * @param context Current context
     */
    public void createLanguageMappingCache(@NonNull final Context context) {
        final List<Locale> locales = new ArrayList<>(LocaleListUtils.asList(context));
        // Always add English
        locales.add(Locale.ENGLISH);
        locales.forEach(locale -> createLanguageMappingCache(context, locale));

        // Locales from SearchEngine's are added automatically as/when needed
    }

    /**
     * Generate language mappings for a given Locale.
     *
     * @param context Current context
     * @param locale  the Locale for which to create a mapping
     */
    private void createLanguageMappingCache(@NonNull final Context context,
                                            @NonNull final Locale locale) {
        final SharedPreferences cacheFile = getCacheFile(context);

        final String isoCode = getIsoCode(locale);

        // just return if already done for this Locale.
        if (cacheFile.getBoolean(LANG_CREATED_PREFIX + isoCode, false)) {
            return;
        }
        final SharedPreferences.Editor ed = cacheFile.edit();
        for (final Locale loc : Locale.getAvailableLocales()) {
            ed.putString(loc.getDisplayLanguage(locale).toLowerCase(locale), getIsoCode(loc));
        }
        // signal this Locale was done
        ed.putBoolean(LANG_CREATED_PREFIX + isoCode, true);
        ed.apply();
    }

    /**
     * We've seen {@link Locale#getISO3Language()} throw {@link MissingResourceException}
     * on a HUAWEI model "ADA-AL10U" with Android 12; see github #58
     * The OS code returned the invalid code "zz" for a language
     * in {@link Locale#getAvailableLocales()}.
     *
     * @param locale to get the ISO3/ISO2 code from.
     *
     * @return iso3/2 code
     *
     * @see <a href="https://github.com/tfonteyn/NeverTooManyBooks/issues/58">github #58</a>
     */
    @NonNull
    private String getIsoCode(@NonNull final Locale locale) {
        String isoCode;
        try {
            isoCode = locale.getISO3Language();
        } catch (@NonNull final MissingResourceException mre) {
            // Fallback to 2-character code
            isoCode = locale.getLanguage();
        }
        return isoCode;
    }

    /**
     * Convenience method to get the language SharedPreferences file.
     *
     * @param context Current context
     *
     * @return the SharedPreferences representing the language mapper
     */
    @NonNull
    private SharedPreferences getCacheFile(@NonNull final Context context) {
        return context.getSharedPreferences(LANGUAGE_MAP, Context.MODE_PRIVATE);
    }

    /**
     * Check if the device or user locale has the given language enabled.
     * <p>
     * Non-english sites are by default only enabled if either the device or
     * this app is has the specified language enabled.
     * The user can still enable/disable them at will of course.
     *
     * @param context Current context
     * @param iso     language code to check
     *
     * @return {@code true} if sites should be enabled by default.
     */
    public boolean isUserLanguage(@NonNull final Context context,
                                  @NonNull final String iso) {
        return LocaleListUtils.asList(context)
                              .stream()
                              .map(this::getIsoCode)
                              .anyMatch(iso::equals);
    }

    /**
     * Generate and return a list of language ISO codes consisting (in order)
     * of the device language and the the supported app locales.
     * <p>
     * This can used as an initial list for new users when the database does not contain
     * any languages yet.
     *
     * @param context Current context
     *
     * @return The list of ISO 639-2 codes
     */
    @NonNull
    public List<String> getDefaultCodes(@NonNull final Context context) {
        final Resources res = context.getResources();
        // to make it easier for first time users, add some defaults.
        // Keep in mind all these are JDK language/locale codes which need converting to ISO 639-2
        final Set<String> set = new LinkedHashSet<>();
        // the device language
        set.add(getISO3FromCode(res.getConfiguration().getLocales().get(0).getLanguage()));

        // and all supported locales.
        Arrays.stream(BuildConfig.SUPPORTED_LOCALES)
              .map(this::getISO3FromCode)
              .forEachOrdered(set::add);

        return new ArrayList<>(set);
    }
}
