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

package com.hardbacknutter.nevertoomanybooks.entities;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Optional;

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
     * Check if there is some form of {@link Author} available in the given {@link DataHolder}.
     *
     * @param dataHolder to check
     *
     * @return {@code true} if an {@link Author} can be extracted from the {@link DataHolder}.
     */
    public static boolean hasAuthor(@NonNull final DataHolder dataHolder) {
        if (dataHolder.contains(Book.BKEY_AUTHOR_LIST)) {
            final List<Author> list = dataHolder.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
            return !list.isEmpty();
        } else if (dataHolder.contains(DBKey.FK_AUTHOR)) {
            return dataHolder.getLong(DBKey.FK_AUTHOR) > 0;
        } else {
            return false;
        }
    }

    /**
     * Check if there is some form of {@link Series} available in the given {@link DataHolder}.
     *
     * @param dataHolder to check
     *
     * @return {@code true} if an {@link Series} can be extracted from the {@link DataHolder}.
     */
    public static boolean hasSeries(@NonNull final DataHolder dataHolder) {
        if (dataHolder.contains(Book.BKEY_SERIES_LIST)) {
            final List<Series> list = dataHolder.getParcelableArrayList(Book.BKEY_SERIES_LIST);
            return !list.isEmpty();
        } else if (dataHolder.contains(DBKey.FK_SERIES)) {
            return dataHolder.getLong(DBKey.FK_SERIES) > 0;
        } else {
            return false;
        }
    }

    /**
     * Extract the {@link Author} from the given {@link DataHolder}.
     *
     * @param dataHolder with {@link Author} data
     *
     * @return Author
     *
     * @throws IllegalArgumentException if there is incompatible data in the {@link DataHolder}.
     */
    @NonNull
    public static Author requireAuthor(@NonNull final DataHolder dataHolder)
            throws IllegalArgumentException {
        Author author = null;

        if (dataHolder.contains(Book.BKEY_AUTHOR_LIST)) {
            // Ideally the row contains the data as a list. Simply return the first one.
            final List<Author> list = dataHolder.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
            if (!list.isEmpty()) {
                author = list.get(0);
            }
        } else if (dataHolder.getInt(DBKey.BL_NODE_GROUP) == BooklistGroup.BOOK) {
            // The rowData is flagged as containing book data without being a full Book object.
            final long bookId = dataHolder.getLong(DBKey.FK_BOOK);
            // sanity check
            if (bookId > 0) {
                final List<Author> list = ServiceLocator.getInstance().getAuthorDao()
                                                        .getByBookId(bookId);
                if (!list.isEmpty()) {
                    author = list.get(0);
                }
            }
        } else if (dataHolder.contains(DBKey.FK_AUTHOR)) {
            final long id = dataHolder.getLong(DBKey.FK_AUTHOR);
            if (id > 0) {
                author = ServiceLocator.getInstance().getAuthorDao().findById(id).orElse(null);
            }
        }

        if (author != null) {
            return author;
        }
        throw new IllegalArgumentException("No Author found");
    }

    /**
     * Extract the {@link Series} from the given {@link DataHolder}.
     *
     * @param dataHolder with {@link Series} data
     *
     * @return Series
     *
     * @throws IllegalArgumentException if there is incompatible data in the {@link DataHolder}.
     */
    @NonNull
    public static Series requireSeries(@NonNull final DataHolder dataHolder)
            throws IllegalArgumentException {
        Series series = null;

        if (dataHolder.contains(Book.BKEY_SERIES_LIST)) {
            // Ideally the row contains the data as a list. Simply return the first one.
            final List<Series> list = dataHolder.getParcelableArrayList(Book.BKEY_SERIES_LIST);
            if (!list.isEmpty()) {
                series = list.get(0);
            }
        } else if (dataHolder.getInt(DBKey.BL_NODE_GROUP) == BooklistGroup.BOOK) {
            // The rowData is flagged as containing book data without being a full Book object.
            final long bookId = dataHolder.getLong(DBKey.FK_BOOK);
            // sanity check
            if (bookId > 0) {
                final List<Series> list = ServiceLocator.getInstance().getSeriesDao()
                                                        .getByBookId(bookId);
                if (!list.isEmpty()) {
                    series = list.get(0);
                }
            }
        } else if (dataHolder.contains(DBKey.FK_SERIES)) {
            final long id = dataHolder.getLong(DBKey.FK_SERIES);
            if (id > 0) {
                series = ServiceLocator.getInstance().getSeriesDao().findById(id).orElse(null);
            }
        }

        if (series != null) {
            return series;
        }
        throw new IllegalArgumentException("No Series found");
    }

    /**
     * Extract the {@link Bookshelf} from the given {@link DataHolder}.
     *
     * @param dataHolder with {@link Bookshelf} data
     *
     * @return Bookshelf
     *
     * @throws IllegalArgumentException if there is incompatible data in the {@link DataHolder}.
     */
    @NonNull
    public static Bookshelf requireBookshelf(@NonNull final DataHolder dataHolder)
            throws IllegalArgumentException {
        Bookshelf bookshelf = null;

        if (dataHolder.contains(Book.BKEY_BOOKSHELF_LIST)) {
            // Ideally the row contains the data as a list. Simply return the first one.
            final List<Bookshelf> list = dataHolder.getParcelableArrayList(
                    Book.BKEY_BOOKSHELF_LIST);
            if (!list.isEmpty()) {
                bookshelf = list.get(0);
            }
        } else if (dataHolder.getInt(DBKey.BL_NODE_GROUP) == BooklistGroup.BOOK) {
            // The rowData is flagged as containing book data without being a full Book object.
            final long bookId = dataHolder.getLong(DBKey.FK_BOOK);
            // sanity check
            if (bookId > 0) {
                final List<Bookshelf> list = ServiceLocator.getInstance().getBookshelfDao()
                                                           .getByBookId(bookId);
                if (!list.isEmpty()) {
                    bookshelf = list.get(0);
                }
            }
        } else if (dataHolder.contains(DBKey.FK_BOOKSHELF)) {
            final long id = dataHolder.getLong(DBKey.FK_BOOKSHELF);
            if (id > 0) {
                bookshelf = ServiceLocator.getInstance().getBookshelfDao().findById(id)
                                          .orElse(null);
            }
        }

        if (bookshelf != null) {
            return bookshelf;
        }
        throw new IllegalArgumentException("No Bookshelf found");
    }

    /**
     * Extract the {@link Publisher} from the given {@link DataHolder}.
     *
     * @param dataHolder with {@link Publisher} data
     *
     * @return Publisher
     *
     * @throws IllegalArgumentException if there is incompatible data in the {@link DataHolder}.
     */
    @NonNull
    public static Publisher requirePublisher(@NonNull final DataHolder dataHolder)
            throws IllegalArgumentException {
        Publisher result = null;

        if (dataHolder.contains(Book.BKEY_PUBLISHER_LIST)) {
            // Ideally the row contains the data as a list. Simply return the first one.
            final List<Publisher> list = dataHolder.getParcelableArrayList(
                    Book.BKEY_PUBLISHER_LIST);
            if (!list.isEmpty()) {
                result = list.get(0);
            }
        } else if (dataHolder.getInt(DBKey.BL_NODE_GROUP) == BooklistGroup.BOOK) {
            // The rowData is flagged as containing book data without being a full Book object.
            final long bookId = dataHolder.getLong(DBKey.FK_BOOK);
            // sanity check
            if (bookId > 0) {
                final List<Publisher> list = ServiceLocator.getInstance().getPublisherDao()
                                                           .getByBookId(bookId);
                if (!list.isEmpty()) {
                    result = list.get(0);
                }
            }
        } else if (dataHolder.contains(DBKey.FK_PUBLISHER)) {
            final long id = dataHolder.getLong(DBKey.FK_PUBLISHER);
            if (id > 0) {
                result = ServiceLocator.getInstance().getPublisherDao().findById(id).orElse(null);
            }
        }

        if (result != null) {
            return result;
        }
        throw new IllegalArgumentException("No Publisher found");
    }

    /**
     * Extract the {@link Tag} from the given {@link DataHolder}.
     *
     * @param dataHolder with {@link Tag} data
     *
     * @return Tag
     *
     * @throws IllegalArgumentException if there is incompatible data in the {@link DataHolder}.
     */
    @NonNull
    public static Tag requireTag(@NonNull final DataHolder dataHolder)
            throws IllegalArgumentException {
        Tag result = null;

        if (dataHolder.contains(Book.BKEY_TAG_LIST)) {
            // Ideally the row contains the data as a list. Simply return the first one.
            final List<Tag> list = dataHolder.getParcelableArrayList(Book.BKEY_TAG_LIST);
            if (!list.isEmpty()) {
                result = list.get(0);
            }
        } else if (dataHolder.getInt(DBKey.BL_NODE_GROUP) == BooklistGroup.BOOK) {
            // The rowData is flagged as containing book data without being a full Book object.
            final long bookId = dataHolder.getLong(DBKey.FK_BOOK);
            // sanity check
            if (bookId > 0) {
                final List<Tag> list = ServiceLocator.getInstance().getTagDao()
                                                     .getByBookId(bookId);
                if (!list.isEmpty()) {
                    result = list.get(0);
                }
            }
        } else if (dataHolder.contains(DBKey.FK_TAG)) {
            final long id = dataHolder.getLong(DBKey.FK_TAG);
            if (id > 0) {
                result = ServiceLocator.getInstance().getTagDao().findById(id).orElse(null);
            }
        }

        if (result != null) {
            return result;
        }
        throw new IllegalArgumentException("No Tag found");
    }

    /**
     * Extract an {@link Identifier} value (sid) from the given {@link DataHolder}.
     *
     * @param dataHolder    with data
     * @param identifierKey to get
     *
     * @return a sid
     */
    @NonNull
    public static Optional<String> getExternalId(@NonNull final DataHolder dataHolder,
                                                 @NonNull final String identifierKey) {

        // The cursor REFERENCES a book
        // This is the common case used by the BoB
        if (dataHolder.contains(DBKey.FK_BOOK)) {
            final long bookId = dataHolder.getLong(DBKey.FK_BOOK);
            if (bookId > 0) {
                return ServiceLocator.getInstance().getIdentifierDao()
                                     .findSid(identifierKey, bookId);
            }
        }

        // If the row IS a Book; This is the case used with the book details screen
        if (dataHolder instanceof Book) {
            return ((Book) dataHolder).getIdentifierValue(identifierKey);
        }

        // Paranoia.. DO NOT throw here; Not entirely sure we can never get here..
        // e.g. if this method was called in error on for example a Series.
        return Optional.empty();
    }

    @NonNull
    public static List<Identifier.Value> getExternalIds(@NonNull final DataHolder dataHolder) {

        // The cursor REFERENCES a book
        // This is the common case used by the BoB
        if (dataHolder.contains(DBKey.FK_BOOK)) {
            final long bookId = dataHolder.getLong(DBKey.FK_BOOK);
            if (bookId > 0) {
                return ServiceLocator.getInstance().getIdentifierDao().getByBookId(bookId);
            }
        }

        // If the row IS a Book; This is the case used with the book details screen
        if (dataHolder instanceof Book) {
            return ((Book) dataHolder).getIdentifiers();
        }

        // Paranoia.. DO NOT throw here; Not entirely sure we can never get here..
        // e.g. if this method was called in error
        return List.of();
    }

}
