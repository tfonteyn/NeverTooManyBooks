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

package com.hardbacknutter.nevertoomanybooks.searchengines;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.entities.Book;

/**
 * The final result from a book search.
 */
public class BookSearchResult {
    private final int searchId;

    @NonNull
    private final Book book;
    @Nullable
    private String searchErrors;

    public BookSearchResult(final int searchId,
                            @NonNull final Book book,
                            @Nullable final String searchErrors) {
        this.searchId = searchId;
        this.book = book;
        // paranoia... make sure null or "something"
        this.searchErrors = searchErrors == null || searchErrors.isEmpty() ? null : searchErrors;
    }

    public int getSearchId() {
        return searchId;
    }

    @NonNull
    public Book getBook() {
        return book;
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

    public void clearError() {
        searchErrors = null;
    }
}
