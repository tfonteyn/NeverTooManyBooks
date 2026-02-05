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

package com.hardbacknutter.nevertoomanybooks.searchengines;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.search.ScanMode;

/**
 * The final result from a book search.
 */
public final class BookSearchResult {
    private final int searchId;

    @NonNull
    private final Book book;
    @NonNull
    private final List<String> errors;
    /** Routing purposes. */
    @Nullable
    private final ScanMode scanMode;

    private BookSearchResult(@NonNull final Book book) {
        this(0, book, null, List.of());
    }

    private BookSearchResult(final int searchId,
                             @NonNull final Book book,
                             @Nullable final ScanMode scanMode,
                             @NonNull final List<String> errors) {
        this.searchId = searchId;
        this.book = book;
        this.scanMode = scanMode;
        this.errors = errors;
    }

    /**
     * Constructor.
     * The data does not contain an actual book, but contains meta data about the search.
     *
     * @param data meta data
     *
     * @return instance
     */
    public static BookSearchResult metaResult(@NonNull final Book data) {
        return new BookSearchResult(data);
    }

    /**
     * Constructor.
     *
     * @param searchId id
     * @param book     with data found
     * @param scanMode (optional) the mode which was active when the search started
     * @param errors   list; can be empty
     *
     * @return instance
     */
    static BookSearchResult newResult(final int searchId,
                                      @NonNull final Book book,
                                      @Nullable final ScanMode scanMode,
                                      @NonNull final List<String> errors) {
        return new BookSearchResult(searchId, book, scanMode, errors);
    }

    public int getSearchId() {
        return searchId;
    }

    /**
     * Resulting book found.
     *
     * @return book
     */
    @NonNull
    public Book getBook() {
        return book;
    }

    @Nullable
    public ScanMode getScanMode() {
        return scanMode;
    }

    /**
     * Get the error message if any.
     *
     * @return list with valid message to display;  can be empty
     */
    @NonNull
    public List<String> getErrors() {
        return errors;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final BookSearchResult that = (BookSearchResult) o;
        return searchId == that.searchId
               && scanMode == that.scanMode
               && Objects.equals(book, that.book)
               && Objects.equals(errors, that.errors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(searchId, book, errors);
    }

    @Override
    @NonNull
    public String toString() {
        return "BookSearchResult{"
               + "searchId=" + searchId
               + ", scanMode=" + scanMode
               + ", errors=`" + errors + '`'
               + ", book=" + book
               + '}';
    }
}
