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

package com.hardbacknutter.nevertoomanybooks.entities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public interface IdentifierOwner {

    long getId();

    /**
     * Get the list of {@link Identifier.Value}s.
     *
     * @return List
     */
    @NonNull
    List<Identifier.Value> getIdentifiers();

    /**
     * Set/replace the list of {@link Identifier.Value}s.
     *
     * @param ivs list
     */
    void setIdentifiers(@NonNull Collection<Identifier.Value> ivs);

    /**
     * Set the value for the given {@link Identifier}.
     * <p>
     * Convenience method.
     *
     * @param key   to set
     * @param value to set; a {@code 0} will remove the field
     */
    default void setIdentifierValue(@NonNull final String key,
                                    final long value) {
        setIdentifierValue(key, value <= 0 ? null : String.valueOf(value));
    }

    /**
     * Set the value for the given {@link Identifier}.
     *
     * @param key   to set
     * @param value to set; a {@code null}, {@code "0"} or an empty string
     *              will remove the field
     */
    default void setIdentifierValue(@NonNull final String key,
                                    @Nullable final String value) {
        // get and remove old value if present
        final List<Identifier.Value> ivs = getIdentifiers()
                .stream().filter(iv -> !iv.getKey().equals(key))
                .collect(Collectors.toList());

        // add the new value if valid
        if (value != null && !value.isBlank() && !"0".equals(value)) {
            ivs.add(new Identifier.Value(key, value));
        }
        // and store the new list
        setIdentifiers(ivs);
    }

    /**
     * Get the value for the given {@link Identifier}.
     *
     * @param key to get
     *
     * @return a valid, non-empty value
     */
    @NonNull
    default Optional<String> getIdentifierValue(@NonNull final String key) {
        return getIdentifiers().stream()
                               .filter(iv -> iv.getKey().equals(key))
                               .map(Identifier.Value::getSid)
                               .findAny();
    }

    /**
     * Get the value for the given {@link Identifier}.
     *
     * @param key to get
     *
     * @return a valid, non-empty value
     *
     * @throws IllegalArgumentException if not found, which indicates a bug
     */
    @NonNull
    default String requireIdentifierValue(@NonNull final String key) {
        return getIdentifierValue(key)
                .orElseThrow(() -> new IllegalArgumentException("Missing Identifier: " + key));
    }
}
