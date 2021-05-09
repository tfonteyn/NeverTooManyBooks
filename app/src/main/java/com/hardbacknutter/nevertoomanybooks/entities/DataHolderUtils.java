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
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;

/**
 * Methods to extract a number of objects from a {@link DataHolder}.
 * <p>
 * It is <strong>NOT</strong> the intention of passing a {@link Book} in!
 * For some methods this will work, for others it will fail (return {@code null}).
 */
public final class DataHolderUtils {

    private DataHolderUtils() {
    }

    /**
     * Extract the Book from the given Booklist row data.
     *
     * @param rowData with data
     *
     * @return Book, or {@code null}
     */
    @Nullable
    public static Book getBook(@NonNull final DataHolder rowData) {
        final long bookId = rowData.getLong(DBKey.FK_BOOK);
        if (bookId > 0) {
            return Book.from(bookId, ServiceLocator.getInstance().getBookDao());
        }
        return null;
    }

    /**
     * Extract the Author from the given Booklist row data.
     *
     * @param rowData with data
     *
     * @return Author, or {@code null}
     */
    @Nullable
    public static Author getAuthor(@NonNull final DataHolder rowData) {
        final AuthorDao authorDao = ServiceLocator.getInstance().getAuthorDao();
        if (rowData.contains(DBKey.FK_AUTHOR)) {
            final long id = rowData.getLong(DBKey.FK_AUTHOR);
            if (id > 0) {
                return authorDao.getById(id);
            }
        } else if (rowData.getInt(DBKey.KEY_BL_NODE_GROUP) == BooklistGroup.BOOK) {
            // This is the same as calling getBook(rowData).getPrimaryAuthor()
            // but should be slightly faster.
            final List<Author> authors = authorDao.getAuthorsByBookId(
                    rowData.getLong(DBKey.FK_BOOK));
            if (!authors.isEmpty()) {
                return authors.get(0);
            }
        }
        return null;
    }

    /**
     * Extract the Series from the given Booklist row data.
     *
     * @param rowData with book data
     *
     * @return Series, or {@code null}
     */
    @Nullable
    public static Series getSeries(@NonNull final DataHolder rowData) {
        final SeriesDao seriesDao = ServiceLocator.getInstance().getSeriesDao();
        if (rowData.contains(DBKey.FK_SERIES)) {
            final long id = rowData.getLong(DBKey.FK_SERIES);
            if (id > 0) {
                return seriesDao.getById(rowData.getLong(DBKey.FK_SERIES));
            }
        } else if (rowData.getInt(DBKey.KEY_BL_NODE_GROUP) == BooklistGroup.BOOK) {
            // This is the same as calling getBook(rowData).getPrimarySeries()
            // but should be slightly faster.
            final ArrayList<Series> series = seriesDao.getSeriesByBookId(
                    rowData.getLong(DBKey.FK_BOOK));
            if (!series.isEmpty()) {
                return series.get(0);
            }
        }
        return null;
    }

    /**
     * Extract the Publisher from the given Booklist row data.
     *
     * @param rowData with book data
     *
     * @return Publisher, or {@code null}
     */
    @Nullable
    public static Publisher getPublisher(@NonNull final DataHolder rowData) {
        // When needed, add code similar to getAuthor to use a BooklistGroup.BOOK
        final long id = rowData.getLong(DBKey.FK_PUBLISHER);
        if (id > 0) {
            return ServiceLocator.getInstance().getPublisherDao().getById(id);
        }
        return null;
    }

    /**
     * Extract the Bookshelf from the given Booklist row data.
     *
     * @param rowData with book data
     *
     * @return Bookshelf, or {@code null}
     */
    @Nullable
    public static Bookshelf getBookshelf(@NonNull final DataHolder rowData) {
        // When needed, add code similar to getAuthor to use a BooklistGroup.BOOK
        final long id = rowData.getLong(DBKey.FK_BOOKSHELF);
        if (id > 0) {
            return ServiceLocator.getInstance().getBookshelfDao().getById(id);
        }
        return null;
    }
}
