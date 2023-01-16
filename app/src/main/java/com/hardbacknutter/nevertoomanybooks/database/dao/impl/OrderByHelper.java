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
package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.util.Locale;
import java.util.function.BiFunction;

import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;

public final class OrderByHelper {

    public static final String PK_SORT_TITLE_REORDERED = "sort.title.reordered";

    private OrderByHelper() {
    }

    /**
     * Get the global default for this preference.
     *
     * @param context Current context
     *
     * @return {@code true} if titles should be reordered. e.g. "The title" -> "title, The"
     */
    static boolean forSorting(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(PK_SORT_TITLE_REORDERED, true);
    }

    /**
     * Conditionally reformat titles for <strong>use as the OrderBy column</strong>.
     * Optionally lookup/verify the actual Locale of the item.
     * <p>
     * URGENT: mismatch between book language and series/pub name -> wrong OB name
     * best example: "Het beste uit Robbedoes" nr 11 which is a french book.
     * <p>
     * Problem cases with a book in language X with title in language X
     * - series name is in a different language -> we use the book language,
     *   or when doing a lookup the language of the first book in the series
     * <p>
     * - publisher name is in a different language as compared to the book
     * -> we  always use the book language
     * <p>
     * - tocEntry title is in a different language as compared to the book
     * -> we  always use the book language
     *
     * @param context      Current context
     * @param title        to reorder
     * @param bookLocale   to use if the lookup fails, or if lookupLocale was {@code false}
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     *
     * @return title and locale data which should be used to construct ORDER-BY columns
     */
    @NonNull
    static OrderByData createOrderByData(
            @NonNull final Context context,
            @NonNull final String title,
            @NonNull final Locale bookLocale,
            @Nullable final BiFunction<Context, Locale, Locale> lookupLocale) {

        final Locale locale;
        if (lookupLocale != null) {
            locale = lookupLocale.apply(context, bookLocale);
        } else {
            locale = bookLocale;
        }

        final String result;
        if (forSorting(context)) {
            result = ReorderHelper.reorder(context, title, locale);
        } else {
            result = title;
        }

        return new OrderByData(result, locale);
    }

    /**
     * Value class.
     */
    static class OrderByData {

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
