/*
 * @Copyright 2018-2023 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;

/**
 * Reorder display labels (title/name) for:
 * <ul>
 *     <li>Book title</li>
 *     <li>TOC title</li>
 *     <li>Series title</li>
 *     <li>Publisher name</li>
 * </ul>
 * This is <strong>NOT</strong> used for Authors.
 */
public final class ReorderHelper {

    /**
     * Boolean preference.
     * {@code true} if the title/name should be DISPLAYED reordered.
     */
    public static final String PK_SHOW_TITLE_REORDERED = "show.title.reordered";
    /**
     * Boolean preference.
     * {@code true} if the title/name should be SORTED by the reordered version.
     */

    public static final String PK_SORT_TITLE_REORDERED = "sort.title.reordered";
    private static final String SUFFIX_SEPARATOR = ", ";

    /**
     * Cache for the pv_reformat_titles_prefixes strings.
     */
    private final Map<Locale, String> localePrefixMap = new HashMap<>();
    @NonNull
    private final Supplier<AppLocale> appLocaleSupplier;

    /**
     * Constructor.
     *
     * @param appLocaleSupplier deferred supplier for the {@link AppLocale}.
     */
    public ReorderHelper(@NonNull final Supplier<AppLocale> appLocaleSupplier) {
        this.appLocaleSupplier = appLocaleSupplier;
    }

    /**
     * Get the global default for this preference.
     *
     * @param context Current context
     *
     * @return {@code true} if titles should be reordered. e.g. "The title" -> "title, The"
     */
    public boolean forDisplay(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(PK_SHOW_TITLE_REORDERED, false);
    }

    /**
     * Get the global default for this preference.
     *
     * @param context Current context
     *
     * @return {@code true} if titles should be reordered. e.g. "The title" -> "title, The"
     */
    public boolean forSorting(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(PK_SORT_TITLE_REORDERED, true);
    }

    /**
     * Try reordering using the device Locale list.
     *
     * @param context Current context
     * @param title   to reorder
     *
     * @return the reordered title
     */
    @NonNull
    public String reorder(@NonNull final Context context,
                          @NonNull final String title) {
        return reorder(context, title, (Locale) null, LocaleListUtils.asList(context));
    }

    /**
     * Try reordering using the 'locale' or the device Locale list.
     *
     * @param context Current context
     * @param title   to reorder
     * @param locale  to try first
     *
     * @return the reordered title
     */
    @NonNull
    public String reorder(@NonNull final Context context,
                          @NonNull final String title,
                          @NonNull final Locale locale) {
        return reorder(context, title, locale, LocaleListUtils.asList(context));
    }

    /**
     * Try reordering using the 'language' or the given Locale list.
     *
     * @param context    Current context
     * @param title      to reorder
     * @param language   to try first
     * @param localeList to try if the the language locale fails
     *
     * @return the reordered title
     */
    @NonNull
    public String reorder(@NonNull final Context context,
                          @NonNull final String title,
                          @Nullable final String language,
                          @NonNull final List<Locale> localeList) {
        @Nullable
        final Locale localeFromLang;
        if (language == null || language.isBlank()) {
            localeFromLang = null;
        } else {
            localeFromLang = appLocaleSupplier.get().getLocale(context, language).orElse(null);
        }
        return reorder(context, title, localeFromLang, localeList);
    }

    /**
     * Unconditionally reformat the given (title) string.
     * <p>
     * This method does the actual re-ordering.
     * It move "The, A, An" etc... to the end of the title. e.g. "The title" -> "title, The".
     * This is case sensitive on purpose.
     *
     * @param context     Current context
     * @param title       to reorder
     * @param firstLocale to try first
     * @param localeList  to try if the the firstLocale fails
     *
     * @return reordered title, or the original if the pattern was not found
     */
    @NonNull
    public String reorder(@NonNull final Context context,
                          @NonNull final String title,
                          @Nullable final Locale firstLocale,
                          @NonNull final List<Locale> localeList) {

        final String[] titleWords = title.split(" ");
        // Single word titles (or empty titles).. just return.
        if (titleWords.length < 2) {
            return title;
        }

        final List<Locale> locales = concatLocales(firstLocale, localeList);
        for (final Locale locale : locales) {
            // case sensitive, see notes in
            // src/main/res/values/string.xml/pv_reformat_titles_prefixes
            if (getWords(context, locale).contains(titleWords[0])) {
                final StringBuilder newTitle = new StringBuilder();
                for (int i = 1; i < titleWords.length; i++) {
                    if (i != 1) {
                        newTitle.append(' ');
                    }
                    newTitle.append(titleWords[i]);
                }
                newTitle.append(SUFFIX_SEPARATOR).append(titleWords[0]);
                return newTitle.toString();
            }
        }
        return title;
    }


    /**
     * Check if the given text is reordered.
     * If not, just return the text as-is; otherwise reverse the reordering.
     *
     * @param context     Current context
     * @param text        to process
     * @param firstLocale to try first
     * @param localeList  to try after
     *
     * @return the un-reordered text
     */
    @NonNull
    public String reverse(@NonNull final Context context,
                          @NonNull final String text,
                          @Nullable final Locale firstLocale,
                          @NonNull final List<Locale> localeList) {
        final List<Locale> locales = concatLocales(firstLocale, localeList);
        for (final Locale locale : locales) {
            final String[] words = getWords(context, locale).split("\\|");
            for (final String word : words) {
                if (text.endsWith(SUFFIX_SEPARATOR + word)) {
                    // This is the (hopefully) original/actual title.
                    final String reconstructed =
                            word + " " + text.substring(0, word.length() + 2);
                    // Now reorder it AGAIN, and check if it matches the original text.
                    final String reordered =
                            reorder(context, reconstructed, firstLocale, localeList);
                    if (text.equalsIgnoreCase(reordered)) {
                        // We have a good chance that this is the original title.
                        // The first character of the 'word' and the original 'text'
                        // might however be wrong. Leave that to the user...
                        return reconstructed;
                    }
                }
            }
        }

        // No changes
        return text;
    }


    @NonNull
    private String getWords(@NonNull final Context context,
                            @NonNull final Locale locale) {
        // getLocalizedResources is slow, so we cache it for every Locale.
        String words = localePrefixMap.get(locale);
        if (words == null) {
            words = appLocaleSupplier.get()
                                     .getLocalizedResources(context, locale)
                                     .getString(R.string.pv_reformat_titles_prefixes);
            localePrefixMap.put(locale, words);
        }
        return words;
    }

    /**
     * Prefix the given 'localeList' with the (optional) 'firstLocale',
     * and suffix it with {@link Locale#ENGLISH}.
     *
     * @param firstLocale (optional) prefix
     * @param localeList  main list
     *
     * @return concatenated/final Locale list
     */
    @NonNull
    private List<Locale> concatLocales(@Nullable final Locale firstLocale,
                                       @NonNull final List<Locale> localeList) {
        // Create a NEW list, and add optional prefix at the start, and Locale.ENGLISH at the end
        final List<Locale> locales = new ArrayList<>(localeList);
        if (firstLocale != null) {
            locales.add(0, firstLocale);
        }
        locales.add(Locale.ENGLISH);
        return locales;
    }

}
