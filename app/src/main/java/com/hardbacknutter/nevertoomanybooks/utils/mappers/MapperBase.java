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
package com.hardbacknutter.nevertoomanybooks.utils.mappers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.entities.Book;

abstract class MapperBase
        implements Mapper {

    @NonNull
    private final String key;

    /**
     * key: the text that needs replacing.
     * value: for {@link #mapString} the StringRes for the replacement,
     */
    @NonNull
    private final Map<String, Integer> mappings;

    MapperBase(@NonNull final String key,
               @NonNull final Map<String, Integer> mappings) {
        this.key = key;
        this.mappings = mappings;
    }

    @VisibleForTesting
    @NonNull
    String getKey() {
        return key;
    }

    @VisibleForTesting
    @NonNull
    Map<String, Integer> getMappings() {
        return mappings;
    }

    void mapString(@NonNull final Context context,
                   @NonNull final Book book) {
        final String value = book.getString(key, null);
        if (value == null || value.isEmpty()) {
            return;
        }

        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        final String lcValue = value.toLowerCase(userLocale);
        // We do a "startsWith" substitution; and concatenate any remaining characters.
        //noinspection DataFlowIssue
        mappings.keySet()
                .stream()
                .filter(lcValue::startsWith)
                .findFirst()
                .map(key -> (context.getString(mappings.get(key))
                             + ' ' + value.substring(key.length()).trim())
                        .trim())
                .ifPresent(replacement -> book.putString(key, replacement));
    }
}
