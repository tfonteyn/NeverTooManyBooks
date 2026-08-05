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

package com.hardbacknutter.nevertoomanybooks.core.utils;

import android.os.LocaleList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Simple helper which transforms a {@link LocaleList} (which is NOT a List) to
 * an actual List.
 */
public final class LocaleListUtils {

    private LocaleListUtils() {
    }

    /**
     * Get an <strong>unmodifiable</strong> List of the user Locales.
     *
     * @param localeList the {@link LocaleList} to convert
     *
     * @return an <strong>unmodifiable</strong> List
     */
    @NonNull
    public static List<Locale> asList(@NonNull final LocaleList localeList) {
        return asList(null, localeList);
    }

    /**
     * Get an <strong>unmodifiable</strong> List of the user Locales.
     *
     * @param firstLocale (optional) Locale to add to the top of the list
     * @param localeList  the {@link LocaleList} to convert
     *
     * @return an <strong>unmodifiable</strong> List
     */
    @NonNull
    public static List<Locale> asList(@Nullable final Locale firstLocale,
                                      @NonNull final LocaleList localeList) {
        final int size = localeList.size();
        final int capacity = (firstLocale != null) ? size + 1 : size;
        final List<Locale> result = new ArrayList<>(capacity);

        if (firstLocale != null) {
            result.add(firstLocale);
        }

        for (int i = 0; i < size; i++) {
            final Locale locale = localeList.get(i);
            if (locale != null && !result.contains(locale)) {
                result.add(locale);
            }
        }

        return List.copyOf(result);
    }
}
