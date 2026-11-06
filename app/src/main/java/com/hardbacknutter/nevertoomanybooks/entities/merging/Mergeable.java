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
package com.hardbacknutter.nevertoomanybooks.entities.merging;

import android.content.Context;

import androidx.annotation.EmptySuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.core.utils.textnormaliser.TextNormaliser;

/**
 * Entities which can appear in a list, and are (semi) duplicates are considered mergeable.
 * This interface contains the method the checks for sameness, and the merge method itself.
 *
 * @param <T> entity
 *
 * @see Pruner
 */
public interface Mergeable<T extends Mergeable<T>> {

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
     *     <li>{@code Author}: the family AND given-names</li>
     *     <li>{@code Bookshelf}: the name</li>
     *     <li>{@code Publisher}: the name</li>
     *     <li>{@code Series}: the title</li>
     *     <li>{@code TocEntry}: the title, the {@code Author} name-fields</li>
     * </ul>
     *
     * @return list
     */
    @NonNull
    List<String> getNameFields();

    /**
     * This method is called after we determined that the "name" fields of the object are
     * matching. This method should try to merge the non-name fields and the id if possible.
     * <p>
     * <strong>The default implementation ONLY handles the id. You MUST override
     * if there are more attributes to consider.</strong>
     *
     * @param context  Current context
     * @param source   to copy from
     *
     * @return {@code true} if the merge was successful.
     *         <br>{@code false} if merging is not possible.
     */
    @EmptySuper
    default boolean merge(@NonNull final Context context,
                          @NonNull final T source) {
        // overwrite the id unless we're 'new'
        if (source.getId() > 0) {
            setId(source.getId());
        }

        return true;
    }

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
     * @return {@code true} if it's the same after normalisation.
     *
     * @see #getNameFields()
     */
    default boolean isSameName(@Nullable final T that) {
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
    default boolean isExactSameName(@NonNull final T that) {
        return Objects.hash(getNameFields())
               == Objects.hash(that.getNameFields());
    }
}
