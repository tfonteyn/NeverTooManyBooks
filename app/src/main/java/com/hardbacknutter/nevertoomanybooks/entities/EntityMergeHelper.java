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

import android.content.Context;

import androidx.annotation.EmptySuper;
import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;

public class EntityMergeHelper<T extends Mergeable> {

    /** Keep track of id. */
    private final Map<Long, T> idCodes = new HashMap<>();
    /** Keep track of base data hashCode. */
    private final Map<Integer, T> hashCodes = new HashMap<>();

    /**
     * Called from {@link #merge(Context, Collection, Function, BiConsumer)}
     * to do the actual merging for each element in the list.
     * <p>
     * This method is called after we determined that the "name" fields of the object are
     * matching. This method should try to merge the non-name fields and the id if possible.
     * <p>
     * <strong>The default implementation ONLY handles the id. You MUST override
     * if the {@link Mergeable} has more attributes to consider.</strong>
     *
     * @param context  Current context
     * @param previous element to copy into
     * @param source   to copy from
     *
     * @return {@code true} if the <em>previous</em> was modified in any way
     */
    @EmptySuper
    protected boolean merge(@NonNull final Context context,
                            @NonNull final T previous,
                            @NonNull final T source) {
        if (source.getId() > 0) {
            previous.setId(source.getId());
        }

        // no other attributes, so we can always merge
        return true;
    }

    /**
     * Loop over the list and try to find and merge duplicates.
     * <p>
     * Comparing the name fields of items is done
     * <strong>after normalisation and lowercasing</strong>.
     * This makes sure we can merge names which are only different in for example an accent on
     * a character, or in upper/lower case characters.
     *
     * @param context        Current context
     * @param list           to process
     * @param localeSupplier a supplier to get the Locale; called for each item in the list
     * @param idFixer        a consumer which should attempt to fix the id of
     *                       the item passed in
     *
     * @return {@code true} if the list was modified.
     */
    public final boolean merge(@NonNull final Context context,
                               @NonNull final Collection<T> list,
                               @NonNull final Function<T, Locale> localeSupplier,
                               @NonNull final BiConsumer<T, Locale> idFixer) {

        boolean listModified = false;
        final Iterator<T> iterator = list.iterator();
        while (iterator.hasNext()) {
            final T current = iterator.next();
            final Locale currentLocale = localeSupplier.apply(current);

            idFixer.accept(current, currentLocale);

            final long id = current.getId();
            // Single-spaces in the string are preserved.
            final int hash = Objects.hash(current.getNameFields()
                                                 .stream()
                                                 .map(SqlEncode::normalize)
                                                 .map(name -> name.toLowerCase(currentLocale))
                                                 .collect(Collectors.toList()));

            // Check if there is a previous occurrence, either by id, or by value (hash)
            T previous = null;
            if (id > 0) {
                previous = idCodes.get(id);
            }
            if (previous == null) {
                previous = hashCodes.get(hash);
            }

            if (previous == null) {
                // There is no previous occurrence, keep and track as appropriate
                if (id > 0) {
                    idCodes.put(id, current);
                }
                hashCodes.put(hash, current);

            } else {
                // There is a previous one with the same "name" as the current one.
                // Try merging the "non-name" attributes from the current into the previous.
                if (merge(context, previous, current)) {
                    // merged successfully, remove the current, we're keeping the previous one.
                    iterator.remove();

                } else {
                    // Merge conflict, keep and track as appropriate
                    if (id > 0 && previous.getId() == 0) {
                        // current has a valid id, but previous does not, track and keep by id
                        idCodes.put(id, current);
                    }
                    // Override previous
                    hashCodes.put(hash, current);
                }

                // we very likely did 'something' to the list of elements of the list.
                listModified = true;
            }
        }
        return listModified;
    }
}
