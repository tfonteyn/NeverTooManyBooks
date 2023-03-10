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
package com.hardbacknutter.nevertoomanybooks.booklist;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.entities.Book;

public interface BookChangedListener {

    /**
     * Receives notifications that a {@link Book} was updated.
     *
     * @param book the book that changed,
     *             or {@code null} to indicate multiple books were potentially changed.
     * @param keys the item(s) that changed,
     *             or {@code null} to indicate ALL data was potentially changed.
     */
    void onBookUpdated(@NonNull Book book,
                       @Nullable String... keys);

    /**
     * Receives notifications that a {@link Book} was updated.
     *
     * @param bookId the book that changed
     * @param keys   the item(s) that changed,
     *               or {@code null} to indicate ALL data was potentially changed.
     */
    void onBookUpdated(final long bookId,
                       @Nullable String... keys);

    /**
     * Receives notifications that a {@link Book} has been deleted.
     *
     * @param bookId the book that was deleted
     */
    void onBookDeleted(long bookId);

    /**
     * Request to update/synchronize the given {@link Book}.
     *
     * @param bookId to handle
     */
    void onSyncBook(long bookId);
}
