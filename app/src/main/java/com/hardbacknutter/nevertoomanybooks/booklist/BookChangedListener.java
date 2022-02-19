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
package com.hardbacknutter.nevertoomanybooks.booklist;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.entities.Book;

public interface BookChangedListener {

    /**
     * Receive notifications that a Book was updated.
     *
     * @param book the book that was changed,
     *             or {@code null} to indicate multiple books were potentially changed.
     * @param key  the item that was changed,
     *             or {@code null} to indicate ALL data was potentially changed.
     */
    void onBookUpdated(@NonNull Book book,
                       @Nullable String key);

    /**
     * React to a book having been deleted.
     *
     * @param bookId which was deleted
     */
    void onBookDeleted(long bookId);

    void onSyncBook(long id);
}
