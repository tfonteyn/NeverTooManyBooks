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

import androidx.annotation.NonNull;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

/**
 * Methods to extract a number of objects from a {@link DataHolder}.
 */
public final class DataHolderUtils {

    private DataHolderUtils() {
    }

    /**
     * Check if there is some form of {@link Author} available in the given row.
     *
     * @param rowData to check
     *
     * @return {@code true} if an {@link Author} can be extracted from the row data.
     */
    public static boolean hasAuthor(@NonNull final DataHolder rowData) {
        if (rowData.contains(Book.BKEY_AUTHOR_LIST)) {
            final List<Author> list = rowData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
            return !list.isEmpty();
        } else if (rowData.contains(DBKey.FK_AUTHOR)) {
            return rowData.getLong(DBKey.FK_AUTHOR) > 0;
        } else {
            return false;
        }
    }

    /**
     * Check if there is some form of {@link Series} available in the given row.
     *
     * @param rowData to check
     *
     * @return {@code true} if an {@link Series} can be extracted from the row data.
     */
    public static boolean hasSeries(@NonNull final DataHolder rowData) {
        if (rowData.contains(Book.BKEY_SERIES_LIST)) {
            final List<Series> list = rowData.getParcelableArrayList(Book.BKEY_SERIES_LIST);
            return !list.isEmpty();
        } else if (rowData.contains(DBKey.FK_SERIES)) {
            return rowData.getLong(DBKey.FK_SERIES) > 0;
        } else {
            return false;
        }
    }

    /**
     * Extract the Author from the given {@link DataHolder}.
     *
     * @param holder with data
     *
     * @return Author
     *
     * @throws IllegalArgumentException if there is incompatible data in the DataHolder.
     */
    @NonNull
    public static Author requireAuthor(@NonNull final DataHolder holder) {
        Author result = null;

        if (holder.contains(Book.BKEY_AUTHOR_LIST)) {
            // Ideally the row contains the data as a list. Simply return the first one.
            final List<Author> list = holder.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
            if (!list.isEmpty()) {
                result = list.get(0);
            }
        } else if (holder.getInt(DBKey.BL_NODE_GROUP) == BooklistGroup.BOOK) {
            // The rowData is flagged as containing book data without being a full Book object.
            final long bookId = holder.getLong(DBKey.FK_BOOK);
            // sanity check
            if (bookId > 0) {
                final List<Author> list = ServiceLocator.getInstance().getAuthorDao()
                                                        .getByBookId(bookId);
                if (!list.isEmpty()) {
                    result = list.get(0);
                }
            }
        } else if (holder.contains(DBKey.FK_AUTHOR)) {
            final long id = holder.getLong(DBKey.FK_AUTHOR);
            if (id > 0) {
                result = ServiceLocator.getInstance().getAuthorDao().getById(id).orElse(null);
            }
        }

        if (result != null) {
            return result;
        }
        throw new IllegalArgumentException("DataHolder does not contain any Author");
    }

    /**
     * Extract the Series from the given {@link DataHolder}.
     *
     * @param holder with data
     *
     * @return Series
     *
     * @throws IllegalArgumentException if there is incompatible data in the DataHolder.
     */
    @NonNull
    public static Series requireSeries(@NonNull final DataHolder holder) {
        Series result = null;

        if (holder.contains(Book.BKEY_SERIES_LIST)) {
            // Ideally the row contains the data as a list. Simply return the first one.
            final List<Series> list = holder.getParcelableArrayList(Book.BKEY_SERIES_LIST);
            if (!list.isEmpty()) {
                result = list.get(0);
            }
        } else if (holder.getInt(DBKey.BL_NODE_GROUP) == BooklistGroup.BOOK) {
            // The rowData is flagged as containing book data without being a full Book object.
            final long bookId = holder.getLong(DBKey.FK_BOOK);
            // sanity check
            if (bookId > 0) {
                final List<Series> list = ServiceLocator.getInstance().getSeriesDao()
                                                        .getByBookId(bookId);
                if (!list.isEmpty()) {
                    result = list.get(0);
                }
            }
        } else if (holder.contains(DBKey.FK_SERIES)) {
            final long id = holder.getLong(DBKey.FK_SERIES);
            if (id > 0) {
                result = ServiceLocator.getInstance().getSeriesDao().getById(id).orElse(null);
            }
        }

        if (result != null) {
            return result;
        }
        throw new IllegalArgumentException("DataHolder does not contain any Series");
    }

    /**
     * Extract the Bookshelf from the given {@link DataHolder}.
     *
     * @param holder with data
     *
     * @return Bookshelf
     *
     * @throws IllegalArgumentException if there is incompatible data in the DataHolder.
     */
    @NonNull
    public static Bookshelf requireBookshelf(@NonNull final DataHolder holder) {
        Bookshelf result = null;

        if (holder.contains(Book.BKEY_BOOKSHELF_LIST)) {
            // Ideally the row contains the data as a list. Simply return the first one.
            final List<Bookshelf> list = holder.getParcelableArrayList(Book.BKEY_BOOKSHELF_LIST);
            if (!list.isEmpty()) {
                result = list.get(0);
            }
        } else if (holder.getInt(DBKey.BL_NODE_GROUP) == BooklistGroup.BOOK) {
            // The rowData is flagged as containing book data without being a full Book object.
            final long bookId = holder.getLong(DBKey.FK_BOOK);
            // sanity check
            if (bookId > 0) {
                final List<Bookshelf> list = ServiceLocator.getInstance().getBookshelfDao()
                                                           .getByBookId(bookId);
                if (!list.isEmpty()) {
                    result = list.get(0);
                }
            }
        } else if (holder.contains(DBKey.FK_BOOKSHELF)) {
            final long id = holder.getLong(DBKey.FK_BOOKSHELF);
            if (id > 0) {
                result = ServiceLocator.getInstance().getBookshelfDao().getById(id).orElse(null);
            }
        }

        if (result != null) {
            return result;
        }
        throw new IllegalArgumentException("DataHolder does not contain any Bookshelf");
    }

    /**
     * Extract the Publisher from the given {@link DataHolder}.
     *
     * @param holder with data
     *
     * @return Publisher
     *
     * @throws IllegalArgumentException if there is incompatible data in the DataHolder.
     */
    @NonNull
    public static Publisher requirePublisher(@NonNull final DataHolder holder) {
        Publisher result = null;

        if (holder.contains(Book.BKEY_PUBLISHER_LIST)) {
            // Ideally the row contains the data as a list. Simply return the first one.
            final List<Publisher> list = holder.getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
            if (!list.isEmpty()) {
                result = list.get(0);
            }
        } else if (holder.getInt(DBKey.BL_NODE_GROUP) == BooklistGroup.BOOK) {
            // The rowData is flagged as containing book data without being a full Book object.
            final long bookId = holder.getLong(DBKey.FK_BOOK);
            // sanity check
            if (bookId > 0) {
                final List<Publisher> list = ServiceLocator.getInstance().getPublisherDao()
                                                           .getByBookId(bookId);
                if (!list.isEmpty()) {
                    result = list.get(0);
                }
            }
        } else if (holder.contains(DBKey.FK_PUBLISHER)) {
            final long id = holder.getLong(DBKey.FK_PUBLISHER);
            if (id > 0) {
                result = ServiceLocator.getInstance().getPublisherDao().getById(id).orElse(null);
            }
        }

        if (result != null) {
            return result;
        }
        throw new IllegalArgumentException("DataHolder does not contain any Publisher");
    }

    /**
     * Extract a Book from the given {@link DataHolder}.
     *
     * @param holder with data
     *
     * @return Book
     *
     * @throws IllegalArgumentException if there is incompatible data in the DataHolder.
     */
    @NonNull
    public static Book requireBook(@NonNull final DataHolder holder) {
        final long bookId = holder.getLong(DBKey.FK_BOOK);
        if (bookId > 0) {
            return Book.from(bookId);
        }
        throw new IllegalArgumentException("DataHolder does not contain any Book");
    }
}
