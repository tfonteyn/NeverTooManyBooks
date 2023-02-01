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
package com.hardbacknutter.nevertoomanybooks.entities;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;


interface Mergeable {

    /**
     * Create a hash on the {@link #getNameFields()} of the given Mergeable.
     * <p>
     * Uses the ascii version of the names.
     *
     * @param x to hash
     *
     * @return hash
     */
    static int createNameHash(@NonNull final Mergeable x) {
        return Objects.hash(
                x.getNameFields()
                 .stream()
                 .map(ParseUtils::toAscii)
                 .collect(Collectors.toList()));
    }

    /**
     * Create a hash on the {@link #getNameFields()} of the given Mergeable.
     * <p>
     * Uses the ascii version of the names converted to an "ORDER BY" value.
     *
     * @param x to hash
     *
     * @return hash
     */
    static int createNameHash(@NonNull final Mergeable x,
                              @NonNull final Locale locale) {
        return Objects.hash(
                x.getNameFields()
                 .stream()
                 .map(name -> SqlEncode.orderByColumn(name, locale))
                 .collect(Collectors.toList()));
    }

    long getId();

    void setId(long id);

    /**
     * Get a list of names which represent this object.
     * <p>
     * Examples: Publisher: the name;  Author: the family AND given-names
     *
     * @return list
     */
    @NonNull
    List<String> getNameFields();

    /**
     * Convenience method to compare two Mergeable's.
     */
    default boolean isSameName(@NonNull final Mergeable that) {
        return createNameHash(this) == createNameHash(that);
    }

    /**
     * Convenience method to compare two Mergeable's.
     */
    default boolean isSameName(@NonNull final Locale locale,
                               @NonNull final Mergeable that,
                               @NonNull final Locale thatLocale) {
        return createNameHash(this, locale) == createNameHash(that, thatLocale);
    }
}
