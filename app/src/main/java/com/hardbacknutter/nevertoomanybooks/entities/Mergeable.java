/*
 * @Copyright 2018-2024 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;


public interface Mergeable {

    long getId();

    void setId(long id);

    /**
     * Get a list of names which represent this object.
     * <p>
     * Examples:
     * <ul>
     *     <li>{@link Bookshelf}: the name</li>
     *     <li>{@link Author}: the family AND given-names</li>
     *     <li>{@link Series}: the title</li>
     *     <li>{@link Publisher}: the name</li>
     *     <li>{@link TocEntry}: the title, the {@link Author} name-fields</li>
     * </ul>
     *
     * @return list
     */
    @NonNull
    List<String> getNameFields();

    /**
     * Convenience method to <strong>case-sensitive</strong> compare two Mergeable's.
     *
     * @param that the one to compare with
     *
     * @return {@code true} if its the same name
     */
    default boolean isSameName(@NonNull final Mergeable that) {
        return Objects.hash(getNameFields()
                                    .stream()
                                    .map(SqlEncode::normalize)
                                    .collect(Collectors.toList()))
               == Objects.hash(that.getNameFields()
                                   .stream()
                                   .map(SqlEncode::normalize)
                                   .collect(Collectors.toList()));
    }

    /**
     * Convenience method to <strong>case-insensitive</strong> compare two Mergeable's.
     * <p>
     * Same as {@link #isSameName(Mergeable)} but lower-casing the names based
     * on the given locales.
     *
     * @param locale     the locale of the name.
     *                   Used for case manipulation.
     * @param that       the one to compare with
     * @param thatLocale the locale of the one to compare with.
     *                   Used for case manipulation.
     *
     * @return {@code true} if its the same name
     */
    default boolean isSameNameIgnoreCase(@NonNull final Locale locale,
                                         @NonNull final Mergeable that,
                                         @NonNull final Locale thatLocale) {
        return Objects.hash(getNameFields()
                                    .stream()
                                    .map(SqlEncode::normalize)
                                    .map(name -> name.toLowerCase(locale))
                                    .collect(Collectors.toList()))
               == Objects.hash(that.getNameFields()
                                   .stream()
                                   .map(SqlEncode::normalize)
                                   .map(name -> name.toLowerCase(thatLocale))
                                   .collect(Collectors.toList()));
    }
}
