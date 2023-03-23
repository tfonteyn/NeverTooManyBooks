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

package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;

/**
 * Value class.
 */
public final class OrderByData {

    @NonNull
    public final String title;
    @NonNull
    public final Locale locale;

    private OrderByData(@NonNull final String title,
                        @NonNull final Locale locale) {
        this.title = title;
        this.locale = locale;
    }

    /**
     * Conditionally reformat titles for <strong>use as the OrderBy column</strong>.
     * Optionally lookup/verify the actual Locale of the item.
     * <p>
     * URGENT: mismatch between book language and series/pub name -> wrong OB name
     * best example: "Het beste uit Robbedoes" nr 11 which is a french book in a dutch series.
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
     * @param context Current context
     * @param title   to reorder
     * @param locale  to use for reordering
     *
     * @return title and locale data which should be used to construct ORDER-BY columns
     */
    @NonNull
    public static OrderByData create(@NonNull final Context context,
                                     @NonNull final ReorderHelper reorderHelper,
                                     @NonNull final String title,
                                     @NonNull final Locale locale) {
        if (reorderHelper.forSorting(context)) {
            final String result = reorderHelper.reorder(context, title, locale);
            return new OrderByData(result, locale);
        } else {
            return new OrderByData(title, locale);
        }
    }
}
