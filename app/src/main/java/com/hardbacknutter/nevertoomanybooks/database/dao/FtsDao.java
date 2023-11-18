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
package com.hardbacknutter.nevertoomanybooks.database.dao;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

public interface FtsDao {

    /**
     * Return a {@link Cursor}, suited for a local-search.
     * This is used by the advanced search activity.
     *
     * @param author        Author related keywords to find
     * @param title         Title related keywords to find
     * @param seriesTitle   Series title related keywords to find
     * @param publisherName Publisher name related keywords to find
     * @param keywords      Keywords to find anywhere in book; this includes titles and authors
     * @param limit         maximum number of rows to return
     *
     * @return a cursor, or {@code null} if all input was empty
     */
    @NonNull
    List<Long> search(@Nullable String author,
                      @Nullable String title,
                      @Nullable String seriesTitle,
                      @Nullable String publisherName,
                      @Nullable String keywords,
                      int limit);

    /**
     * Rebuild the entire FTS database.
     *
     * @param context Current context
     */
    void rebuild(@NonNull Context context);

    /**
     * Insert an FTS record for the given {@link Book}.
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param context Current context
     * @param bookId  the book id
     *
     * @throws TransactionException a transaction must be started before calling this method
     */
    void insert(@NonNull Context context,
                @IntRange(from = 1) long bookId)
            throws TransactionException;

    /**
     * Update an FTS record for the given {@link Book}.
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param context Current context
     * @param bookId  the book id
     *
     * @throws TransactionException a transaction must be started before calling this method
     */
    void update(@NonNull Context context,
                @IntRange(from = 1) long bookId)
            throws TransactionException;
}
