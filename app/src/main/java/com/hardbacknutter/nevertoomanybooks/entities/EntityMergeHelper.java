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

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

public abstract class EntityMergeHelper<T extends Mergeable> {

    /** Keep track of id. */
    private final Map<Long, T> idCodes = new HashMap<>();
    /** Keep track of base data hashCode. */
    private final Map<Integer, T> hashCodes = new HashMap<>();

    /**
     * Called from {@link #merge(Collection, Consumer)} to do the actual merging for each element
     * in the list.
     * <p>
     * This method is called after we determined that the "name" fields of the object are
     * matching. This method should try to merge the non-name fields and the id if possible.
     *
     * @param previous element
     * @param current  element
     *
     * @return {@code true} if the list was modified in any way
     */
    protected abstract boolean merge(@NonNull T previous,
                                     @NonNull T current);

    /**
     * Loop over the list and try to find and merge duplicates.
     *
     * @param list    to process
     * @param idFixer a consumer which should attempt to fix the id of the object passed in
     *
     * @return {@code true} if the list was modified.
     */
    public boolean merge(@NonNull final Collection<T> list,
                         @NonNull final Consumer<T> idFixer) {

        boolean listModified = false;
        final Iterator<T> iterator = list.iterator();
        while (iterator.hasNext()) {
            final T current = iterator.next();
            idFixer.accept(current);

            final long id = current.getId();
            final int hash = current.hashCodeOfNameOnly();

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
                if (merge(previous, current)) {
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
