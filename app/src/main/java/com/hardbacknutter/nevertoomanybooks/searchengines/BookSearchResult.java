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
    @Nullable
    private final String searchErrors;
    /** Routing purposes. */
    @Nullable
    private final ScanMode scanMode;

    private BookSearchResult(@NonNull final Book book) {
        this(0, book, null, null);
    }

    private BookSearchResult(final int searchId,
                             @NonNull final Book book,
                             @Nullable final ScanMode scanMode,
                             @Nullable final String searchErrors) {
        this.searchId = searchId;
        this.book = book;
        this.scanMode = scanMode;
        // paranoia... eliminate empty string
        this.searchErrors = searchErrors == null || searchErrors.isEmpty() ? null : searchErrors;
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
     * @param searchId     id
     * @param book         with data found
     * @param scanMode     (optional) the mode which was active when the search started
     * @param searchErrors (optional) either {@code null} or a valid message to display
     *
     * @return instance
     */
    static BookSearchResult newResult(final int searchId,
                                      @NonNull final Book book,
                                      @Nullable final ScanMode scanMode,
                                      @Nullable final String searchErrors) {
        return new BookSearchResult(searchId, book, scanMode, searchErrors);
    }

    public int getSearchId() {
        return searchId;
    }

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
     * @return either {@code null} or a valid message to display
     */
    @Nullable
    public String getErrorMessage() {
        return searchErrors;
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
               && Objects.equals(searchErrors, that.searchErrors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(searchId, book, searchErrors);
    }

    @Override
    @NonNull
    public String toString() {
        return "BookSearchResult{"
               + "searchId=" + searchId
               + ", scanMode=" + scanMode
               + ", searchErrors=`" + searchErrors + '`'
               + ", book=" + book
               + '}';
    }
}
