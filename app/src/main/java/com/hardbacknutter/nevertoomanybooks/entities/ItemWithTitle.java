/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Encapsulates getting the title and whether to reorder it.
 */
public interface ItemWithTitle {

    /**
     * Cache for the pv_reformat_titles_prefixes strings.
     * PRIVATE, do not use elsewhere.
     */
    Map<Locale, String> LOCALE_PREFIX_MAP = new HashMap<>();

    /**
     * Move "The, A, An" etc... to the end of the title. e.g. "The title" -> "title, The".
     * This method is case sensitive on purpose.
     *
     * @param context     Current context
     * @param title       to reorder
     * @param titleLocale Locale for the title.
     *
     * @return reordered title, or the original if the pattern was not found
     */
    @NonNull
    static String reorder(@NonNull final Context context,
                          @NonNull final String title,
                          @NonNull final Locale titleLocale) {

        final String[] titleWords = title.split(" ");
        // Single word titles (or empty titles).. just return.
        if (titleWords.length < 2) {
            return title;
        }

        // we check in order - first match returns.
        // 1. the Locale passed in
        // 2. the user preferred Locale
        // 3. the user device Locale
        // 4. ENGLISH.
        final Locale[] locales = {titleLocale,
                                  LocaleUtils.getUserLocale(context),
                                  LocaleUtils.getSystemLocale(),
                                  Locale.ENGLISH};

        for (Locale locale : locales) {
            if (locale == null) {
                continue;
            }
            // Creating the pattern is slow, so we cache it for every Locale.
            String words = LOCALE_PREFIX_MAP.get(locale);
            if (words == null) {
                // the resources bundle in the language that the book (item) is written in.
                Resources localeResources = LocaleUtils.getLocalizedResources(context, locale);
                words = localeResources.getString(R.string.pv_reformat_titles_prefixes);
                LOCALE_PREFIX_MAP.put(locale, words);
            }
            // case sensitive, see notes in
            // src/main/res/values/string.xml/pv_reformat_titles_prefixes
            if (words.contains(titleWords[0])) {
                final StringBuilder newTitle = new StringBuilder();
                for (int i = 1; i < titleWords.length; i++) {
                    if (i != 1) {
                        newTitle.append(' ');
                    }
                    newTitle.append(titleWords[i]);
                }
                newTitle.append(", ").append(titleWords[0]);
                return newTitle.toString();
            }
        }
        return title;
    }

    @NonNull
    static String reorder(@NonNull final Context context,
                          @NonNull final String title,
                          @NonNull final String language) {
        final Locale locale = LocaleUtils.getLocale(context, language);
        if (locale != null) {
            return reorder(context, title, locale);
        } else {
            return title;
        }
    }

    /**
     * Get the global default for this preference.
     *
     * @param context Current context
     *
     * @return {@code true} if titles should be reordered. e.g. "The title" -> "title, The"
     */
    static boolean isReorderTitleForDisplaying(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(Prefs.pk_show_title_reordered, false);
    }

    /**
     * Get the global default for this preference.
     *
     * @param context Current context
     *
     * @return {@code true} if titles should be reordered. e.g. "The title" -> "title, The"
     */
    static boolean isReorderTitleForSorting(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(Prefs.pk_sort_title_reordered, true);
    }

    @NonNull
    String getTitle();

    /**
     * Reformat titles for <strong>displaying</strong>.
     *
     * @param context     Current context
     * @param titleLocale Locale to use
     *
     * @return reordered title / original title
     */
    default String reorderTitleForDisplaying(@NonNull final Context context,
                                             @NonNull final Locale titleLocale) {

        if (isReorderTitleForDisplaying(context)) {
            return reorder(context, getTitle(), titleLocale);
        } else {
            return getTitle();
        }
    }

    /**
     * Reformat titles for <strong>use as the OrderBy column</strong>.
     *
     * @param context     Current context
     * @param titleLocale Locale to use
     *
     * @return reordered title / original title
     */
    default String reorderTitleForSorting(@NonNull final Context context,
                                          @NonNull final Locale titleLocale) {

        if (isReorderTitleForSorting(context)) {
            return reorder(context, getTitle(), titleLocale);
        } else {
            return getTitle();
        }
    }
}
