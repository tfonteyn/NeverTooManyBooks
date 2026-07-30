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

package com.hardbacknutter.nevertoomanybooks.localsearch;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

public final class SearchFtsInput {
    @NonNull
    private final Bookshelf bookshelf;
    @Nullable
    private final LocalSearchCriteria criteria;

    public SearchFtsInput(@NonNull final Bookshelf bookshelf,
                          @Nullable final LocalSearchCriteria criteria) {
        this.criteria = criteria;
        this.bookshelf = bookshelf;
    }

    @SuppressWarnings("deprecation")
    @NonNull
    static SearchFtsInput fromBundle(@NonNull final Bundle args) {
        final Bookshelf bookshelf = Objects.requireNonNull(
                args.getParcelable(DBKey.FK_BOOKSHELF),
                DBKey.FK_BOOKSHELF);
        final LocalSearchCriteria criteria = args.getParcelable(LocalSearchCriteria.BKEY);
        return new SearchFtsInput(bookshelf, criteria);
    }

    @NonNull
    public Bundle toBundle() {
        final Bundle args = new Bundle(2);
        args.putParcelable(DBKey.FK_BOOKSHELF, bookshelf);
        if (criteria != null && !criteria.isEmpty()) {
            args.putParcelable(LocalSearchCriteria.BKEY, criteria);
        }
        return args;
    }

    @NonNull
    public Bookshelf getBookshelf() {
        return bookshelf;
    }

    @Nullable
    public LocalSearchCriteria getCriteria() {
        return criteria;
    }
}
