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

package com.hardbacknutter.nevertoomanybooks.backup.csv;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.AuthorCoder;
import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.BookshelfCoder;
import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.StringList;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

public final class CsvGoodreads {

    static final String PREFIX = "goodreads_";

    /**
     * The Goodreads Author field will be mapped to {@link DBKey.AUTHOR#FORMATTED_FULL_NAME}.
     * Any additional authors come in this key and will need to be added.
     */
    public static final String ADDITIONAL_AUTHORS = PREFIX + "additional authors";
    /**
     * Data will have the Isbn13 field mapped to {@link DBKey#ISBN}.
     * If empty, we get the Isbn10 code from this key.
     */
    public static final String ISBN10 = PREFIX + "isbn10";
    /** Goodreads bookshelves need special decoding. */
    public static final String BOOKSHELVES = PREFIX + "bookshelves";
    public static final String EXCLUSIVE_SHELF = PREFIX + "exclusive shelf";
    /**
     * An {@code int} 1..5; can be missing.
     * Decoded in combination with {@link #AVERAGE_RATING}.
     */
    public static final String MY_RATING = PREFIX + "my rating";
    /**
     * A {@code float} 0..5; can be missing.
     * Decoded in combination with {@link #MY_RATING}.
     */
    public static final String AVERAGE_RATING = PREFIX + "average rating";
    public static final String MY_REVIEW = PREFIX + "my review";

    @NonNull
    private final Style defaultStyle;

    /** This is a <strong>COMMA</strong> separated string list. */
    @Nullable
    private StringList<Author> authorCoder;
    /** This is a <strong>SPACE</strong> separated string list. */
    @Nullable
    private StringList<Bookshelf> bookshelfCoder;

    /**
     * Constructor.
     *
     * @param defaultStyle to use
     */
    public CsvGoodreads(@NonNull final Style defaultStyle) {
        this.defaultStyle = defaultStyle;
    }

    @NonNull
    public StringList<Author> getAuthorCoder() {
        if (authorCoder == null) {
            authorCoder = new StringList<>(new AuthorCoder(','));
        }
        return authorCoder;
    }

    @NonNull
    public StringList<Bookshelf> getBookshelfCoder() {
        if (bookshelfCoder == null) {
            bookshelfCoder = new StringList<>(new BookshelfCoder(',', defaultStyle));
        }
        return bookshelfCoder;
    }
}
