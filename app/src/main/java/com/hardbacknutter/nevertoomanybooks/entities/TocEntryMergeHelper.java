/*
 * @Copyright 2018-2023 HardBackNutter
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

import androidx.annotation.NonNull;

import java.util.Locale;

public class TocEntryMergeHelper
        extends EntityMergeHelper<TocEntry> {

    @Override
    protected boolean merge(@NonNull final Context context,
                            @NonNull final TocEntry previous,
                            @NonNull final Locale previousLocale,
                            @NonNull final TocEntry current,
                            @NonNull final Locale currentLocale) {

        final boolean canMerge = mergeDate(previous, current)
                                 && mergeAuthor(previous, previousLocale,
                                                current, currentLocale);

        if (canMerge && current.getId() > 0) {
            previous.setId(current.getId());
        }

        return canMerge;
    }

    private boolean mergeDate(@NonNull final TocEntry previous,
                              @NonNull final TocEntry current) {
        // If the current TocEntry has no date set, we're done
        if (!current.getFirstPublicationDate().isPresent()) {
            return true;
        }

        // If the previous TocEntry has no date set,
        // copy the current data to the previous one.
        if (!previous.getFirstPublicationDate().isPresent()) {
            previous.setFirstPublicationDate(current.getFirstPublicationDate());
            return true;
        }

        // Both have a date set.
        // If they are the same, we're done; else we can't merge.
        return previous.getFirstPublicationDate().equals(current.getFirstPublicationDate());
    }

    private boolean mergeAuthor(@NonNull final TocEntry previous,
                                @NonNull final Locale previousLocale,
                                @NonNull final TocEntry current,
                                @NonNull final Locale currentLocale) {

        final Author previousAuthor = previous.getPrimaryAuthor();
        final Author currentAuthor = current.getPrimaryAuthor();
        final boolean canMerge = previousAuthor
                .isSameNameIgnoreCase(previousLocale, currentAuthor, currentLocale);

        if (canMerge) {
            final long currentId = currentAuthor.getId();
            if (currentId > 0) {
                previousAuthor.setId(currentId);
            }
        }
        return canMerge;
    }
}
