/*
 * @Copyright 2018-2025 HardBackNutter
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;

/**
 * Reorder display labels (title/name) for:
 * <ul>
 *     <li>Book title</li>
 *     <li>TOC title</li>
 *     <li>Series title</li>
 *     <li>Publisher name</li>
 * </ul>
 * This is <strong>NOT</strong> used for Authors.
 * <p>
 * URGENT: mismatch between book language and series/pub name -> wrong OB name
 * best example: "Het beste uit Robbedoes" nr 11 which is a french book in a dutch series.
 * <p>
 * Problem cases with a book in language X with title in language X
 * - series name is in a different language -> we use the book language,
 *   or when doing a lookup the language of the first book in the series
 * <p>
 * - publisher name is in a different language as compared to the book
 * -> we always use the book language
 * <p>
 * - tocEntry title is in a different language as compared to the book
 * -> we always use the book language
 */
public final class ReorderHelper {

    /**
     * Boolean preference.
     * {@code true} if the title/name should be SORTED by the reordered version.
     */
    public static final String PK_SORT_TITLE_REORDERED = "sort.title.reordered";
    private static final String SUFFIX_SEPARATOR = ", ";

    /**
     * Cache for the pv_reformat_titles_prefixes strings.
     */
    private static final Map<Locale, String> LOCALE_PREFIX_MAP = new HashMap<>();
    @NonNull
    private final List<Locale> allLocales;
    private final boolean sortReordered;

    /**
     * Constructor.
     *
     * @param userLocales the list of all user locales.
     */
    public ReorderHelper(@NonNull final List<Locale> userLocales) {
        this.allLocales = userLocales;
        sortReordered = isSortReordered();
    }

    /**
     * Get the global default for this preference.
     *
     * @return {@code true} if titles should be reordered. e.g. "The title" -> "title, The"
     */
    public static boolean isSortReordered() {
        return ServiceLocator.getInstance().getSharedPreferences()
                             .getBoolean(PK_SORT_TITLE_REORDERED, true);
    }

    /**
     * <strong>Conditionally</strong> reorder the given text
     * for <strong>use as the OrderBy column</strong>.
     *
     * @param context Current context
     * @param text    to reorder
     * @param locale  to use for reordering
     *
     * @return the reordered text or the original text, as to-be used for sorting.
     */
    @NonNull
    public String reorderForSorting(@NonNull final Context context,
                                    @NonNull final String text,
                                    @NonNull final Locale locale) {
        if (sortReordered) {
            return reorder(context, text, locale);
        } else {
            return text;
        }
    }

    /**
     * <strong>Unconditionally</strong> reorder the given text.
     * Uses the users Locale list.
     *
     * @param context Current context
     * @param title   to reorder
     *
     * @return the reordered title
     */
    @NonNull
    public String reorder(@NonNull final Context context,
                          @NonNull final String title) {
        return reorder(context, title, (Locale) null);
    }

    /**
     * <strong>Unconditionally</strong> reorder the given text.
     * Uses the given language to create a Locale to use.
     *
     * @param context  Current context
     * @param title    to reorder
     * @param language (Optional) to try first
     *
     * @return the reordered title
     */
    @NonNull
    public String reorder(@NonNull final Context context,
                          @NonNull final String title,
                          @Nullable final String language) {
        @Nullable
        final Locale localeFromLang;
        if (language == null || language.isBlank()) {
            localeFromLang = null;
        } else {
            localeFromLang = ServiceLocator.getInstance()
                                           .getAppLocale()
                                           .getLocale(language, allLocales.get(0))
                                           .orElse(null);
        }
        return reorder(context, title, localeFromLang);
    }

    /**
     * <strong>Unconditionally</strong> reorder the given text.
     * <p>
     * This method does the actual re-ordering.
     * It move "The, A, An" etc... to the end of the title. e.g. "The title" -> "title, The".
     * This is case sensitive on purpose.
     *
     * @param context     Current context
     * @param title       to reorder
     * @param firstLocale to try first
     *
     * @return reordered title, or the original if the pattern was not found
     */
    @NonNull
    public String reorder(@NonNull final Context context,
                          @NonNull final String title,
                          @Nullable final Locale firstLocale) {

        final String[] titleWords = title.split(" ");
        // Single word titles (or empty titles).. just return.
        if (titleWords.length < 2) {
            return title;
        }

        final List<Locale> locales = concatLocales(firstLocale);
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
     *
     * @return the un-reordered text
     */
    @NonNull
    public String reverse(@NonNull final Context context,
                          @NonNull final String text,
                          @Nullable final Locale firstLocale) {
        final List<Locale> locales = concatLocales(firstLocale);
        for (final Locale locale : locales) {
            final String[] words = getWords(context, locale).split("\\|");
            for (final String word : words) {
                if (text.endsWith(SUFFIX_SEPARATOR + word)) {
                    // This is the (hopefully) original/actual title.
                    final String reconstructed =
                            word + " " + text.substring(0, text.length()
                                                           - SUFFIX_SEPARATOR.length()
                                                           - word.length());
                    // Now reorder it AGAIN, and check if it matches the original text.
                    final String reordered = reorder(context, reconstructed, firstLocale);
                    // IgnoreCase as the incoming text might have an uppercase character to start
                    if (text.equalsIgnoreCase(reordered)) {
                        // We have a good chance that this is the original title.
                        // The case of the first character of the 'word' and the original 'text'
                        // might however be wrong. Leave that to the user...
                        return reconstructed;
                    }
                }
            }
        }

        // No changes
        return text;
    }


    /**
     * Get the '|' separated list of words to check for in the given Locale.
     *
     * @param context Current context
     * @param locale  to lookup
     *
     * @return word list; can be empty.
     */
    @NonNull
    private String getWords(@NonNull final Context context,
                            @NonNull final Locale locale) {
        String words;
        // getLocalizedResources is slow, so we cache it for every Locale.
        synchronized (LOCALE_PREFIX_MAP) {
            words = LOCALE_PREFIX_MAP.get(locale);
            if (words == null) {
                words = ServiceLocator.getInstance()
                                      .getAppLocale()
                                      .getLocalizedResources(context, locale)
                                      .getString(R.string.pv_reformat_titles_prefixes);
                // hack for WebLate removing empty Strings.
                if ("|".equals(words)) {
                    words = "";
                }
                LOCALE_PREFIX_MAP.put(locale, words);
            }
        }
        return words;
    }

    /**
     * Prefix the given 'localeList' with the (optional) 'firstLocale',
     * and suffix it with {@link Locale#ENGLISH}.
     *
     * @param firstLocale (optional) prefix
     *
     * @return concatenated/final Locale list
     */
    @NonNull
    private List<Locale> concatLocales(@Nullable final Locale firstLocale) {
        // Create a NEW list, and add optional prefix at the start, and Locale.ENGLISH at the end
        final List<Locale> locales = new ArrayList<>(allLocales);
        if (firstLocale != null) {
            locales.add(0, firstLocale);
        }
        if (!locales.contains(Locale.ENGLISH)
            && !locales.contains(Locale.US)
            && !locales.contains(Locale.UK)
            && !locales.contains(Locale.CANADA)) {
            locales.add(Locale.ENGLISH);
        }
        return locales;
    }
}
