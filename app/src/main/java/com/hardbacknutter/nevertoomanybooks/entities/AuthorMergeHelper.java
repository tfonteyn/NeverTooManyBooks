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

public class AuthorMergeHelper
        extends EntityMergeHelper<Author> {

    @Override
    protected boolean merge(@NonNull final Author previous,
                            @NonNull final Author current) {
        // always combine the types
        previous.setType(previous.getType() | current.getType());

        // If the current Author has no 'real-author' set, we're done
        if (current.getRealAuthor() == null) {
            if (previous.getId() == 0 && current.getId() > 0) {
                previous.setId(current.getId());
            }
            return true;
        }

        // If the current Author has no 'real-author' set, copy the current data
        if (previous.getRealAuthor() == null) {
            previous.setRealAuthor(current.getRealAuthor());
            if (previous.getId() == 0 && current.getId() > 0) {
                previous.setId(current.getId());
            }
            return true;
        }

        // Both have a 'real-author' set,
        // If they are the same, we're done
        if (previous.getRealAuthor().equals(current.getRealAuthor())) {
            if (previous.getId() == 0 && current.getId() > 0) {
                previous.setId(current.getId());
            }
            return true;
        }

        // The book has two authors which have the same 'real-author'.
        // This might be strange, but absolutely valid.
        // The user can clean up manually if needed.
        // While we cannot merge the actual objects, we CAN copy the id if appropriate.
        if (previous.getId() == 0 && current.getId() > 0) {
            previous.setId(current.getId());
        }
        return false;
    }
}
