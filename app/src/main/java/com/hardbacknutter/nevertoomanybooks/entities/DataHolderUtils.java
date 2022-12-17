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
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

/**
 * Methods to extract a number of objects from a {@link DataHolder}.
 */
public final class DataHolderUtils {

    private DataHolderUtils() {
    }

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
            return Book.from(bookId);
        }
        return null;
    }

    @NonNull
    public static Book requireBook(@NonNull final DataHolder rowData) {
        return Objects.requireNonNull(getBook(rowData));
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
        if (rowData.contains(Book.BKEY_AUTHOR_LIST)) {
            // Ideally the row contains the data as a list. Simply return the first one.
            final List<Author> list = rowData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
            return list.isEmpty() ? null : list.get(0);

        } else if (rowData.getInt(DBKey.BL_NODE_GROUP) == BooklistGroup.BOOK) {
            // The rowData is flagged as containing book data without being a full Book object.
            final long bookId = rowData.getLong(DBKey.FK_BOOK);
            // sanity check
            if (bookId > 0) {
                final List<Author> list = ServiceLocator.getInstance().getAuthorDao()
                                                        .getByBookId(bookId);
                return list.isEmpty() ? null : list.get(0);
            }
        } else if (rowData.contains(DBKey.FK_AUTHOR)) {
            final long id = rowData.getLong(DBKey.FK_AUTHOR);
            if (id > 0) {
                return ServiceLocator.getInstance().getAuthorDao().getById(id);
            }
        }

        return null;
    }

    @NonNull
    public static Author requireAuthor(@NonNull final DataHolder rowData) {
        return Objects.requireNonNull(getAuthor(rowData));
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
        if (rowData.contains(Book.BKEY_SERIES_LIST)) {
            // Ideally the row contains the data as a list. Simply return the first one.
            final List<Series> list = rowData.getParcelableArrayList(Book.BKEY_SERIES_LIST);
            return list.isEmpty() ? null : list.get(0);

        } else if (rowData.getInt(DBKey.BL_NODE_GROUP) == BooklistGroup.BOOK) {
            // The rowData is flagged as containing book data without being a full Book object.
            final long bookId = rowData.getLong(DBKey.FK_BOOK);
            // sanity check
            if (bookId > 0) {
                final List<Series> list = ServiceLocator.getInstance().getSeriesDao()
                                                        .getByBookId(bookId);
                return list.isEmpty() ? null : list.get(0);
            }
        } else if (rowData.contains(DBKey.FK_SERIES)) {
            final long id = rowData.getLong(DBKey.FK_SERIES);
            if (id > 0) {
                return ServiceLocator.getInstance().getSeriesDao().getById(id);
            }
        }

        return null;
    }

    @NonNull
    public static Series requireSeries(@NonNull final DataHolder rowData) {
        return Objects.requireNonNull(getSeries(rowData));
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
        if (rowData.contains(Book.BKEY_PUBLISHER_LIST)) {
            // Ideally the row contains the data as a list. Simply return the first one.
            final List<Publisher> list = rowData.getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
            return list.isEmpty() ? null : list.get(0);

        } else if (rowData.getInt(DBKey.BL_NODE_GROUP) == BooklistGroup.BOOK) {
            // The rowData is flagged as containing book data without being a full Book object.
            final long bookId = rowData.getLong(DBKey.FK_BOOK);
            // sanity check
            if (bookId > 0) {
                final List<Publisher> list = ServiceLocator.getInstance().getPublisherDao()
                                                           .getByBookId(bookId);
                return list.isEmpty() ? null : list.get(0);
            }
        } else if (rowData.contains(DBKey.FK_PUBLISHER)) {
            final long id = rowData.getLong(DBKey.FK_PUBLISHER);
            if (id > 0) {
                return ServiceLocator.getInstance().getPublisherDao().getById(id);
            }
        }

        return null;
    }

    @NonNull
    public static Publisher requirePublisher(@NonNull final DataHolder rowData) {
        return Objects.requireNonNull(getPublisher(rowData));
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
        if (rowData.contains(Book.BKEY_SERIES_LIST)) {
            // Ideally the row contains the data as a list. Simply return the first one.
            final List<Bookshelf> list = rowData.getParcelableArrayList(Book.BKEY_SERIES_LIST);
            return list.isEmpty() ? null : list.get(0);

        } else if (rowData.getInt(DBKey.BL_NODE_GROUP) == BooklistGroup.BOOK) {
            // The rowData is flagged as containing book data without being a full Book object.
            final long bookId = rowData.getLong(DBKey.FK_BOOK);
            // sanity check
            if (bookId > 0) {
                final List<Bookshelf> list = ServiceLocator.getInstance().getBookshelfDao()
                                                           .getBookshelvesByBookId(bookId);
                return list.isEmpty() ? null : list.get(0);
            }
        } else if (rowData.contains(DBKey.FK_BOOKSHELF)) {
            final long id = rowData.getLong(DBKey.FK_BOOKSHELF);
            if (id > 0) {
                return ServiceLocator.getInstance().getBookshelfDao().getById(id);
            }
        }

        return null;
    }

    @NonNull
    public static Bookshelf requireBookshelf(@NonNull final DataHolder rowData) {
        return Objects.requireNonNull(getBookshelf(rowData));
    }
}
