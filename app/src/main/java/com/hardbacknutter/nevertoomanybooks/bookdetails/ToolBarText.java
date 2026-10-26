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

package com.hardbacknutter.nevertoomanybooks.bookdetails;

import android.content.Context;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

public final class ToolBarText {

    private ToolBarText() {
    }

    /**
     * Get the toolbar title+subtitle reflecting the book.
     *
     * @param context Current context
     * @param book    to use
     *
     * @return the title and subtitle for the toolbar
     */
    @NonNull
    public static Pair<String, String> getText(@NonNull final Context context,
                                               @NonNull final Book book) {
        final String authorLabel = Author.getLabel(context, book.getAuthors());
        String bookTitle = book.getTitle();

        if (BuildConfig.DEBUG /* always */) {
            bookTitle = "[" + book.getId() + "] " + bookTitle;
        }
        return new Pair<>(authorLabel, bookTitle);
    }
}
