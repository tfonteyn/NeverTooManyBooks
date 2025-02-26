/*
 * @Copyright 2018-2025 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.database.dao;

import android.database.Cursor;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.entities.Book;

public interface FtsDao {

    /**
     * Do a local-search. At least one of the arguments must be non-blank.
     *
     * @param author        Author related keywords to find
     * @param title         Title related keywords to find
     * @param seriesTitle   Series title related keywords to find
     * @param publisherName Publisher name related keywords to find
     * @param keywords      Keywords to find anywhere in book; this includes all of the above
     *
     * @return a list with {@link FtsSearchResult}s
     */
    @NonNull
    List<FtsSearchResult> search(@Nullable String author,
                                 @Nullable String title,
                                 @Nullable String seriesTitle,
                                 @Nullable String publisherName,
                                 @Nullable String keywords);

    @NonNull
    List<FtsSearchResult> search(@NonNull String keywords);

    @Nullable
    Cursor querySearchSuggestions(@NonNull String searchText);

    /**
     * Rebuild the entire FTS database.
     */
    @WorkerThread
    void rebuild();

    /**
     * Insert an FTS record for the given {@link Book}.
     * <p>
     * <strong>Transaction:</strong> required
     * <p>
     * <strong>All Exceptions are ignored</strong>
     *
     * @param bookId the book id
     */
    void insert(@IntRange(from = 1) long bookId);

    /**
     * Update an FTS record for the given {@link Book}.
     * <p>
     * <strong>Transaction:</strong> required
     * <p>
     * <strong>All Exceptions are ignored</strong>
     *
     * @param bookId the book id
     */
    void update(@IntRange(from = 1) long bookId);
}
