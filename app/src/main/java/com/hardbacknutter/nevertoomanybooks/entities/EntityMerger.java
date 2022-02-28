/*
 * @Copyright 2018-2021 HardBackNutter
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

public class EntityMerger<T extends Mergeable> {

    /** Keep track of id. */
    private final Map<Long, T> idCodes = new HashMap<>();
    /** Keep track of base data hashCode. */
    private final Map<Integer, T> hashCodes = new HashMap<>();

    @NonNull
    private final Iterator<T> mIt;

    /** Result of the operation. */
    private boolean listModified;

    /**
     * Constructor.
     *
     * @param list with entities to merge/prune
     */
    public EntityMerger(@NonNull final Collection<T> list) {
        mIt = list.iterator();
    }

    public boolean hasNext() {
        return mIt.hasNext();
    }

    @NonNull
    public T next() {
        return mIt.next();
    }

    /**
     * Get the final result when the list is fully processed.
     *
     * @return {@code true} on <strong>ANY</strong> modifications to the list or list elements
     */
    public boolean isListModified() {
        return listModified;
    }

    /**
     * Merge with the incoming element.
     *
     * @param incoming element to merge with (a potential) matching previous
     *                 and eliminate if possible
     */
    public void merge(@NonNull final T incoming) {

        final long id = incoming.getId();
        final int hash = incoming.asciiHashCodeNoId();

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
                idCodes.put(id, incoming);
            }
            hashCodes.put(hash, incoming);

        } else {
            // try merging
            if (previous.merge(incoming)) {
                // merged successfully, drop current
                mIt.remove();

            } else {
                // Merge conflict, keep and track as appropriate
                if (id > 0 && previous.getId() == 0) {
                    // current has a valid id, but previous does not, track and keep by id
                    idCodes.put(id, incoming);
                }
                // Override previous
                hashCodes.put(hash, incoming);
            }

            // we very likely did 'something' to the list of elements of the list.
            listModified = true;
        }
    }
}
