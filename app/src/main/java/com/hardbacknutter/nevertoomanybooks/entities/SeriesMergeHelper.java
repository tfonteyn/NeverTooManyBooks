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

public class SeriesMergeHelper
        extends EntityMergeHelper<Series> {

    @Override
    protected boolean merge(@NonNull final Series previous,
                            @NonNull final Series current) {
        // If the current Series has no number set, we're done
        if (current.getNumber().isEmpty()) {
            if (previous.getId() == 0 && current.getId() > 0) {
                previous.setId(current.getId());
            }
            return true;
        }

        // If the previous Series has no number set, copy the current data
        if (previous.getNumber().isEmpty()) {
            previous.setNumber(current.getNumber());
            if (previous.getId() == 0 && current.getId() > 0) {
                previous.setId(current.getId());
            }
            return true;
        }

        // Both have a number set.
        // If they are the same, we're done
        if (previous.getNumber().equals(current.getNumber())) {
            if (previous.getId() == 0 && current.getId() > 0) {
                previous.setId(current.getId());
            }
            return true;
        }

        // The book has two numbers in a series.
        // This might be strange, but absolutely valid.
        // The user can clean up manually if needed.
        // While we cannot merge the actual objects, we CAN copy the id if appropriate.
        if (previous.getId() == 0 && current.getId() > 0) {
            previous.setId(current.getId());
        }
        // 2 different numbers, don't merge.
        return false;
    }
}
