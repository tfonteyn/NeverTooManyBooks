/*
 * @Copyright 2018-2021 HardBackNutter
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
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

/**
 * Encapsulates getting the title and whether to reorder it.
 * <p>
 * Note this is not limited to Books, but anywhere where title/name re-ordering is applicable.
 */
public interface ReorderTitle {

    /**
     * Cache for the pv_reformat_titles_prefixes strings.
     * PRIVATE, do not use elsewhere.
     */
    Map<Locale, String> LOCALE_PREFIX_MAP = new HashMap<>();

    /**
     * Get the global default for this preference.
     *
     * @return {@code true} if titles should be reordered. e.g. "The title" -> "title, The"
     */
    static boolean forDisplay(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(Prefs.pk_show_title_reordered, false);
    }

    /**
     * Get the global default for this preference.
     *
     * @return {@code true} if titles should be reordered. e.g. "The title" -> "title, The"
     */
    static boolean forSorting(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(Prefs.pk_sort_title_reordered, true);
    }

    /**
     * Reorder the given title using the given language.
     * If the language is invalid in any way, the user Locale will be used to reorder.
     *
     * @param context  Current context
     * @param title    to reorder
     * @param language ISO codes (2 or 3 char), or a display-string (4+ characters)
     *
     * @return reordered title
     */
    @NonNull
    static String reorder(@NonNull final Context context,
                          @NonNull final String title,
                          @Nullable final String language) {
        if (language == null || language.isEmpty()) {
            final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
            return reorder(context, title, userLocale);

        } else {
            final Locale locale = ServiceLocator.getInstance().getAppLocale()
                                                .getLocale(context, language);
            if (locale != null) {
                return reorder(context, title, locale);
            } else {
                final Locale userLocale = context.getResources().getConfiguration().getLocales()
                                                 .get(0);
                return reorder(context, title, userLocale);
            }
        }
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
    static String reorder(@NonNull final Context context,
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
        final Locale[] locales = {titleLocale,
                                  context.getResources().getConfiguration().getLocales().get(0),
                                  ServiceLocator.getSystemLocale(),
                                  Locale.ENGLISH};

        final AppLocale appLocale = ServiceLocator.getInstance().getAppLocale();

        for (final Locale locale : locales) {
            if (locale != null) {
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
        }
        return title;
    }

    /**
     * Convenience method which always uses the user-locale.
     *
     * @param context Current context
     *
     * @return reordered title
     */
    @NonNull
    default String reorder(@NonNull final Context context) {
        return reorder(context, getTitle(), (Locale) null);
    }

    @NonNull
    default String reorder(@NonNull final Context context,
                           @Nullable final Locale locale) {
        return reorder(context, getTitle(), locale);
    }

    /** Get the <strong>unformatted</strong> title or name. */
    @NonNull
    String getTitle();

    /**
     * Lookup/get the item Locale.
     *
     * @param context  Current context
     * @param defValue to return if the item Locale could not be found
     *
     * @return Locale
     */
    @NonNull
    Locale getLocale(@NonNull Context context,
                     @NonNull Locale defValue);

    /**
     * Conditionally reformat titles for <strong>use as the OrderBy column</strong>.
     * Optionally lookup/verify the actual Locale of the item.
     *
     * @param context      Current context
     * @param lookupLocale {@code true} if an attempt should be done to lookup/detect the actual
     *                     Locale of the item.
     * @param bookLocale   to use if the lookup fails, or if lookupLocale was {@code false}
     *
     * @return title and locale data which should be used to construct ORDER-BY columns
     */
    default OrderByData createOrderByData(@NonNull final Context context,
                                          final boolean lookupLocale,
                                          @NonNull final Locale bookLocale) {
        final Locale locale;
        if (lookupLocale) {
            locale = getLocale(context, bookLocale);
        } else {
            locale = bookLocale;
        }

        final String title;
        if (forSorting(context)) {
            title = reorder(context, getTitle(), locale);
        } else {
            title = getTitle();
        }

        return new OrderByData(title, locale);
    }

    /**
     * Value class.
     */
    class OrderByData {

        @NonNull
        public final String title;
        @NonNull
        public final Locale locale;

        OrderByData(@NonNull final String title,
                    @NonNull final Locale locale) {
            this.title = title;
            this.locale = locale;
        }
    }
}
