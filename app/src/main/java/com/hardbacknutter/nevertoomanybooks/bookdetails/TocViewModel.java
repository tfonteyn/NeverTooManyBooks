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
package com.hardbacknutter.nevertoomanybooks.bookdetails;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

public class TocViewModel
        extends ViewModel {

    /**
     * The list of TOC entries we're displaying.
     * Permanent reference; the adapter will not need refreshing.
     */
    @NonNull
    private final List<AuthorWork> works = new ArrayList<>();

    private long bookId;
    /**
     * The list of Author. We normally only use the first one as primary-author.
     * But a side effect is that this forms a permanent reference;
     * the adapter will not need refreshing.
     */
    @NonNull
    private final List<Author> authors = new ArrayList<>();
    /** screen sub title. */
    @Nullable
    private String bookTitle;
    private boolean embedded;

    /**
     * Pseudo constructor.
     * <p>
     * In full-screen mode, we get all we need from the arguments.
     * In embedded mode, we don't use any arguments,
     * but rely on {@link #reload(Book)} being called.
     *
     * @param args Bundle with arguments
     */
    public void init(@NonNull final Bundle args) {
        if (works.isEmpty()) {
            embedded = args.getBoolean(TocFragment.BKEY_EMBEDDED, false);

            bookId = args.getLong(DBKey.FK_BOOK, 0);
            // optional, display purpose only
            bookTitle = args.getString(DBKey.TITLE);

            final List<TocEntry> tocList = args.getParcelableArrayList(Book.BKEY_TOC_LIST);
            Objects.requireNonNull(tocList, Book.BKEY_TOC_LIST);
            works.addAll(tocList);

            final List<Author> authorList = args.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
            if (authorList != null && !authorList.isEmpty()) {
                authors.addAll(authorList);
            }
        }
    }

    public boolean isEmbedded() {
        return embedded;
    }

    public void reload(@NonNull final Book book) {
        bookId = book.getId();
        bookTitle = book.getTitle();

        works.clear();
        works.addAll(book.getToc());

        final List<Author> authorList = book.getAuthors();
        authors.clear();
        authors.addAll(authorList);
    }

    public long getBookId() {
        return bookId;
    }

    @NonNull
    public List<Author> getAuthors() {
        return authors;
    }

    @NonNull
    public List<AuthorWork> getWorks() {
        return works;
    }

    @NonNull
    Optional<String> getScreenTitle(@NonNull final Context context) {
        if (authors.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(Author.getLabel(context, authors));
        }
    }

    @NonNull
    Optional<String> getScreenSubtitle() {
        if (bookTitle != null && !bookTitle.isEmpty()) {
            if (BuildConfig.DEBUG /* always */) {
                return Optional.of("[" + bookId + "] " + bookTitle);
            } else {
                return Optional.of(bookTitle);
            }
        } else {
            return Optional.empty();
        }
    }
}
