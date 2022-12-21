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

public class TocEntryMergeHelper
        extends EntityMergeHelper<TocEntry> {

    @Override
    protected boolean merge(@NonNull final TocEntry previous,
                            @NonNull final TocEntry current) {
        // If the current TocEntry has no date set, we're done
        if (!current.getFirstPublicationDate().isPresent()) {
            if (previous.getId() == 0 && current.getId() > 0) {
                previous.setId(current.getId());
            }
            return true;
        }

        // If the previous TocEntry has no date set, copy the current data
        if (!previous.getFirstPublicationDate().isPresent()) {
            previous.setFirstPublicationDate(current.getFirstPublicationDate());
            if (previous.getId() == 0 && current.getId() > 0) {
                previous.setId(current.getId());
            }
            return true;
        }

        // Both have a date set.
        // If they are the same, we're done
        if (previous.getFirstPublicationDate().equals(current.getFirstPublicationDate())) {
            if (previous.getId() == 0 && current.getId() > 0) {
                previous.setId(current.getId());
            }
            return true;
        }

        // The entries have a different date.
        // This is almost certainly invalid.
        // We can't decide which is the 'right' one.
        // The user will need to clean up manually - we force the current id to be 'new'
        current.setId(0);
        // conflicting dates, don't merge.
        return false;
    }
}
