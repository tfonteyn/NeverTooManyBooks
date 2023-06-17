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
package com.hardbacknutter.nevertoomanybooks.database.dao;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.entities.Book;

public interface LoaneeDao {

    /**
     * Lend out a book / return a book.
     * <p>
     * This method should only be called from places where only the book id is available.
     * If the full Book is available, use {@link #setLoanee(Book, String)} instead.
     *
     * @param bookId book to lend
     * @param loanee person to lend to; set to {@code null} or {@code ""} to delete the loan
     *
     * @return {@code true} for success.
     */
    boolean setLoanee(@IntRange(from = 1) long bookId,
                      @Nullable String loanee);

    /**
     * Lend out a book / return a book.
     *
     * @param book   to lend
     * @param loanee person to lend to; set to {@code null} or {@code ""} to delete the loan
     *
     * @return {@code true} for success.
     */
    boolean setLoanee(@NonNull Book book,
                      @Nullable String loanee);

    /**
     * Get the name of the loanee for a given book, if any.
     *
     * @param bookId book to search for
     *
     * @return Who the book is lend to, or {@code null} when not lend out
     */
    @Nullable
    String getLoaneeByBookId(@IntRange(from = 1) long bookId);

    /**
     * Returns a unique list of all loanee in the database.
     *
     * @return The list
     */
    @NonNull
    List<String> getList();

}
