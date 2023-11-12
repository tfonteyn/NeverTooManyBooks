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

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.entities.Book;

abstract class MapperBase
        implements Mapper {

    @NonNull
    abstract Map<String, Integer> getMappings();

    @Override
    public void map(@NonNull final Context context,
                    @NonNull final Book book) {

        String value = book.getString(getKey(), null);
        if (value != null && !value.isEmpty()) {
            final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
            final String lcValue = value.toLowerCase(userLocale);
            final Map<String, Integer> mapper = getMappings();
            final Optional<String> oKey = mapper.keySet()
                                                .stream()
                                                .filter(lcValue::startsWith)
                                                .findFirst();

            if (oKey.isPresent()) {
                //noinspection DataFlowIssue
                value = (context.getString(mapper.get(oKey.get()))
                         + ' ' + value.substring(oKey.get().length()).trim())
                        .trim();
            }
            // return either the found mapping, or the incoming value.
            book.putString(getKey(), value);
        }
    }
}
