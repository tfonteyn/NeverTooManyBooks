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

package com.hardbacknutter.nevertoomanybooks.core.utils;

import android.content.Context;
import android.os.LocaleList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LocaleListUtils {

    private LocaleListUtils() {
    }

    /**
     * Get an <strong>unmodifiable</strong> List of the user Locales.
     *
     * @param context Current context
     *
     * @return unmodifiable list
     */
    @NonNull
    public static List<Locale> asList(@NonNull final Context context) {
        return asList(context, null);
    }

    @NonNull
    public static List<Locale> asList(@NonNull final Context context,
                                      @Nullable final Locale prefix) {
        final Set<Locale> locales = new LinkedHashSet<>();
        if (prefix != null) {
            locales.add(prefix);
        }

        final LocaleList localeList = context.getResources().getConfiguration().getLocales();
        for (int i = 0; i < localeList.size(); i++) {
            locales.add(localeList.get(i));
        }
        return List.copyOf(locales);
    }
}
