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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.core.utils.textnormaliser.TextNormalizer;
import com.hardbacknutter.nevertoomanybooks.core.utils.textnormaliser.TextNormalizerFactory;


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
     * Convenience method to compare two Merge-ables.
     * <ol>
     *     <li>diacritics are normalised</li>
     *     <li>white-space is condensed to single-space</li>
     *     <li>case-sensitive</li>
     * </ol>
     *
     * @param that the one to compare with
     *
     * @return {@code true} if it's the same name
     */
    default boolean isSameName(@Nullable final Mergeable that) {
        if (that == null) {
            return false;
        }
        final TextNormalizer textNormalizer = TextNormalizerFactory.create();
        // Single-spaces in the string are preserved.
        return Objects.hash(getNameFields()
                                    .stream()
                                    .map(textNormalizer::normalize)
                                    .collect(Collectors.toList()))
               == Objects.hash(that.getNameFields()
                                   .stream()
                                   .map(textNormalizer::normalize)
                                   .collect(Collectors.toList()));
    }

    /**
     * Convenience method to <strong>diacritics-sensitive</strong> compare two Merge-ables.
     * Always <strong>case-sensitive</strong>.
     *
     * @param that the one to compare with
     *
     * @return {@code true} if it's the same name (including all diacritics)
     */
    default boolean isIdenticalName(@NonNull final Mergeable that) {
        return Objects.hash(getNameFields())
               == Objects.hash(that.getNameFields());
    }
}
