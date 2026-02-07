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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.search.ScanMode;
import com.hardbacknutter.nevertoomanybooks.search.SearchBookUpdatesViewModel;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;

/**
 * The final result from a book search.
 * <p>
 * This class has 2 different payloads.
 * <ul>
 *     <li>{@link SearchCoordinator}
 *         <ul>
 *             <li>searchId</li>
 *             <li>scanMode</li>
 *             <li>book</li>
 *             <li>errorsByEngineId</li>
 *         </ul>
 *     </li>
 *     <li>{@link SearchBookUpdatesViewModel}
 *         <ul>
 *             <li>editBookOutput</li>
 *         </ul>
 *     </li>
 * </ul>
 * The reason for this structure is that SearchBookUpdatesViewModel extends SearchCoordinator
 * and shares some LiveData observers and Queue's.
 */
public final class BookSearchResult {

    /** Routing purposes. */
    private final int searchId;
    /** Routing purposes. */
    @Nullable
    private final ScanMode scanMode;

    @Nullable
    private final Book book;
    @NonNull
    private final Map<EngineId, Throwable> errorsByEngineId;

    @Nullable
    private final EditBookOutput editBookOutput;

    /**
     * {@link SearchCoordinator}.
     * Constructor.
     *
     * @param searchId         id
     * @param book             with data found
     * @param scanMode         (optional) the mode which was active when the search started.
     *                         Used for routing purposes.
     * @param errorsByEngineId a map with the errors for each site which was searched
     */
    public BookSearchResult(final int searchId,
                            @NonNull final Book book,
                            @Nullable final ScanMode scanMode,
                            @NonNull final Map<EngineId, Throwable> errorsByEngineId) {
        this.searchId = searchId;
        this.book = book;
        this.scanMode = scanMode;
        this.errorsByEngineId = errorsByEngineId;
        this.editBookOutput = null;
    }

    /**
     * {@link SearchBookUpdatesViewModel}.
     * Constructor.
     *
     * @param editBookOutput report
     */
    public BookSearchResult(@Nullable final EditBookOutput editBookOutput) {
        this.searchId = 0;
        this.book = null;
        this.scanMode = null;
        this.errorsByEngineId = Map.of();
        this.editBookOutput = editBookOutput;
    }


    /**
     * {@link SearchCoordinator}.
     * Get the task id.
     * Used for routing purposes.
     *
     * @return task id
     */
    public int getSearchId() {
        return searchId;
    }

    /**
     * {@link SearchCoordinator}.
     * The mode which was active when the search started.
     * Used for routing purposes.
     *
     * @return mode
     */
    @Nullable
    public ScanMode getScanMode() {
        return scanMode;
    }

    /**
     * {@link SearchCoordinator}.
     * Check if there is a minimal amount of useful data.
     * <p>
     * A non-empty result will have a title, or at least 3 fields:
     * The isbn field will be present as we searched on it.
     * The title field, *might* be there but *might* be empty.
     * So a valid result means we either need a title, or a third field.
     *
     * @return {@code true} if there is
     */
    public boolean hasBook() {
        if (book == null) {
            return false;
        }
        final String title = book.getString(DBKey.TITLE, null);
        return title != null && !title.isEmpty() || book.size() > 2;
    }

    /**
     * {@link SearchCoordinator}.
     * Resulting book found.
     *
     * @return book
     *
     * @throws NullPointerException if called for the final result of a
     *                              {@link SearchBookUpdatesViewModel} search
     * @see #hasBook()
     */
    @NonNull
    public Book getBook() {
        return Objects.requireNonNull(book, "Book was null");
    }

    @VisibleForTesting
    @Nullable
    public Book getRawBook() {
        return book;
    }

    /**
     * {@link SearchCoordinator}.
     * Check if there were any errors.
     *
     * @return flag
     */
    public boolean hasErrors() {
        return !errorsByEngineId.isEmpty();
    }

    /**
     * {@link SearchCoordinator}.
     * Collects all individual website errors (if any).
     *
     * @param context Current context
     *
     * @return list; can be empty
     *
     * @see #hasErrors()
     */
    @NonNull
    public List<String> getErrors(@NonNull final Context context) {
        if (errorsByEngineId.isEmpty()) {
            return List.of();
        }

        return errorsByEngineId
                .values()
                .stream()
                .map(exception -> ExMsg
                        .map(context, exception)
                        .orElseGet(() -> {
                            // generic network related IOException message
                            if (exception instanceof IOException) {
                                return context.getString(
                                        R.string.error_search_failed_network);
                            }
                            // generic unknown message
                            return context.getString(R.string.error_unexpected);
                        }))
                .collect(Collectors.toList());
    }

    /**
     * {@link SearchBookUpdatesViewModel}.
     * Get the final report of a search.
     *
     * @return report
     *
     * @throws NullPointerException when the expected result is <strong>not</strong> the
     *                              final result of a {@link SearchBookUpdatesViewModel} execution.
     */
    @NonNull
    public EditBookOutput getEditBookOutput() {
        return Objects.requireNonNull(editBookOutput, "editBookOutput was null");
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
               && Objects.equals(errorsByEngineId, that.errorsByEngineId)
               && Objects.equals(editBookOutput, that.editBookOutput);
    }

    @Override
    public int hashCode() {
        return Objects.hash(searchId, scanMode, book, errorsByEngineId, editBookOutput);
    }

    @Override
    @NonNull
    public String toString() {
        return "BookSearchResult{"
               + "searchId=" + searchId
               + ", scanMode=" + scanMode
               + ", errorsByEngineId=`" + errorsByEngineId + '`'
               + ", book=" + book
               + ", editBookOutput=" + editBookOutput
               + '}';
    }
}
