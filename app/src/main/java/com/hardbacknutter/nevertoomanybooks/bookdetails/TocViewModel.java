/*
 * @Copyright 2018-2022 HardBackNutter
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
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.BookData;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

public class TocViewModel
        extends ViewModel {

    /** The list of TOC entries we're displaying. */
    @NonNull
    private final List<AuthorWork> works = new ArrayList<>();

    private long bookId;

    @Nullable
    private String bookTitle;

    @Nullable
    private String authors;

    @Nullable
    private Author primaryAuthor;
    private boolean embedded;

    /**
     * Pseudo constructor.
     * <p>
     * In full-screen mode, we get all we need from the arguments.
     * In embedded mode, we don't use any arguments,
     * but rely on {@link #reload(Context, Book)} being called.
     *
     * @param context Current context
     * @param args    Bundle with arguments
     */
    public void init(@NonNull final Context context,
                     @NonNull final Bundle args) {
        if (works.isEmpty()) {
            embedded = args.getBoolean(TocFragment.BKEY_EMBEDDED, false);

            bookId = args.getLong(DBKey.FK_BOOK, 0);
            // optional, display purpose only
            bookTitle = args.getString(DBKey.TITLE);

            final ArrayList<TocEntry> tocList = args.getParcelableArrayList(BookData.BKEY_TOC_LIST);
            Objects.requireNonNull(tocList, BookData.BKEY_TOC_LIST);
            works.addAll(tocList);

            final List<Author> authorList = args.getParcelableArrayList(BookData.BKEY_AUTHOR_LIST);
            if (authorList != null && !authorList.isEmpty()) {
                authors = Author.getLabel(context, authorList);
                primaryAuthor = authorList.get(0);
            }
        }
    }

    public boolean isEmbedded() {
        return embedded;
    }

    public void reload(@NonNull final Context context,
                       @NonNull final Book book) {
        bookId = book.getId();
        bookTitle = book.getTitle();

        works.clear();
        works.addAll(book.getToc());

        final List<Author> authorList = book.getAuthors();
        if (!authorList.isEmpty()) {
            authors = Author.getLabel(context, authorList);
            primaryAuthor = authorList.get(0);
        }
    }

    public long getBookId() {
        return bookId;
    }

    @Nullable
    public Author getPrimaryAuthor() {
        return primaryAuthor;
    }

    @NonNull
    public List<AuthorWork> getWorks() {
        return works;
    }

    @NonNull
    Optional<String> getScreenTitle() {
        if (authors != null && !authors.isEmpty()) {
            return Optional.of(authors);
        } else {
            return Optional.empty();
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

    @NonNull
    ArrayList<Long> getBookIds(@NonNull final TocEntry tocEntry) {
        return ServiceLocator.getInstance().getTocEntryDao().getBookIds(tocEntry.getId());
    }
}
