/*
 * @Copyright 2018-2022 HardBackNutter
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
import android.content.res.Resources;
import android.os.LocaleList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.util.HashMap;
import java.util.LinkedHashSet;
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
 */
public final class ReorderHelper {

    public static final String PK_SHOW_TITLE_REORDERED = "show.title.reordered";

    /**
     * Static cache for the pv_reformat_titles_prefixes strings.
     */
    private static final Map<Locale, String> LOCALE_PREFIX_MAP = new HashMap<>();

    private ReorderHelper() {
    }

    /**
     * Get the global default for this preference.
     *
     * @param context Current context
     *
     * @return {@code true} if titles should be reordered. e.g. "The title" -> "title, The"
     */
    public static boolean forDisplay(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(PK_SHOW_TITLE_REORDERED, false);
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
     * @param titleLocale Locale for the title.
     *
     * @return reordered title, or the original if the pattern was not found
     */
    @NonNull
    public static String reorder(@NonNull final Context context,
                                 @NonNull final String title,
                                 @Nullable final Locale titleLocale) {

        final String[] titleWords = title.split(" ");
        // Single word titles (or empty titles).. just return.
        if (titleWords.length < 2) {
            return title;
        }

        // we check in order - first match returns.
        // 1. the Locale passed in (can be 'null' -> we skip it)
        // 2. the user preferred Locale
        // 3. the user device Locale
        // 4. ENGLISH.
        final LinkedHashSet<Locale> locales = new LinkedHashSet<>();
        if (titleLocale != null) {
            locales.add(titleLocale);
        }
        final LocaleList localeList = context.getResources().getConfiguration().getLocales();
        for (int i = 0; i < localeList.size(); i++) {
            locales.add(localeList.get(i));
        }
        locales.addAll(ServiceLocator.getSystemLocales());
        locales.add(Locale.ENGLISH);

        final AppLocale appLocale = ServiceLocator.getInstance().getAppLocale();

        for (final Locale locale : locales) {
            // Creating the pattern is slow, so we cache it for every Locale.
            String words = LOCALE_PREFIX_MAP.get(locale);
            if (words == null) {
                // the resources bundle in the language that the book (item) is written in.
                final Resources res = appLocale.getLocalizedResources(context, locale);
                words = res.getString(R.string.pv_reformat_titles_prefixes);
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
}
