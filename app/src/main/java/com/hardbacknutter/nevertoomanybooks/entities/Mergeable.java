/*
 * @Copyright 2018-2026 HardBackNutter
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

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.core.utils.textnormaliser.TextNormaliser;

public interface Mergeable {

    /**
     * Get the database row id of the Entity.
     *
     * @return id; can be {@code 0} if the entity is considered 'new' and not stored yet
     */
    @IntRange(from = 0)
    long getId();

    /**
     * Set the id of the entity.
     *
     * @param id to set
     */
    void setId(long id);

    /**
     * Get a list of 'name' fields which represent this object.
     * <p>
     * Dev note: the fields don't have to be actual names, just fields that
     * are used to check basic equality.
     * <p>
     * Examples:
     * <ul>
     *     <li>{@link Author}: the family AND given-names</li>
     *     <li>{@link Bookshelf}: the name</li>
     *     <li>{@link Publisher}: the name</li>
     *     <li>{@link Series}: the title</li>
     *     <li>{@link TocEntry}: the title, the {@link Author} name-fields</li>
     * </ul>
     *
     * @return list
     */
    @NonNull
    List<String> getNameFields();

    /**
     * Compare the names of two Merge-ables.
     * <ol>
     *     <li>White-space is condensed to single-space</li>
     *     <li>Diacritics are <strong>normalised</strong></li>
     *     <li><strong>Case-sensitive</strong></li>
     * </ol>
     *
     * @param that the one to compare with
     *
     * @return {@code true} if it's loosely the same
     *
     * @see #getNameFields()
     */
    default boolean isSameName(@Nullable final Mergeable that) {
        if (that == null) {
            return false;
        }
        final TextNormaliser textNormaliser = new TextNormaliser();
        // Use basic normalisation as we want to detect differences
        // in case and whitespace usage.
        return Objects.hash(getNameFields()
                                    .stream()
                                    .map(textNormaliser::normalise)
                                    .collect(Collectors.toList()))
               == Objects.hash(that.getNameFields()
                                   .stream()
                                   .map(textNormaliser::normalise)
                                   .collect(Collectors.toList()));
    }

    /**
     * Compare the names of two Merge-ables.
     * <ol>
     *     <li><strong>Diacritics-sensitive</strong></li>
     *     <li><strong>Case-sensitive</strong></li>
     * </ol>
     *
     * @param that the one to compare with
     *
     * @return {@code true} if it's the exact same
     *
     * @see #getNameFields()
     */
    default boolean isExactSameName(@NonNull final Mergeable that) {
        return Objects.hash(getNameFields())
               == Objects.hash(that.getNameFields());
    }
}
