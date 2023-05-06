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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.BookLight;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Details;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

@SuppressWarnings("WeakerAccess")
public class AuthorWorksViewModel
        extends ViewModel {

    /** The list of TOC/Books we're displaying. */
    private final ArrayList<AuthorWork> works = new ArrayList<>();

    /** Database Access. */
    private BookDao bookDao;
    /** Author is set in {@link #init}. */
    private Author author;
    /** Initial Bookshelf is set in {@link #init}. */
    private Bookshelf bookshelf;
    /** Initially we get toc entries and books. */
    private boolean withTocEntries = true;
    /** Initially we get toc entries and books. */
    private boolean withBooks = true;
    /** Show all shelves, or only the initially selected shelf. */
    private boolean allBookshelves;
    /**
     * Order the list by...  initially always {@code null}, i.e. sort by the default column.
     * For all allowed values, see {@link AuthorDao.WorksOrderBy}
     */
    @AuthorDao.WorksOrderBy
    @Nullable
    private String orderByColumn;

    /** Set to {@code true} when ... used to report back to BoB to decide rebuilding BoB list. */
    private boolean dataModified;

    private Style style;

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     *
     * @throws IllegalArgumentException <strong>DEBUG</strong>:
     *                                  if the args do not contain a valid Author
     */
    void init(@NonNull final Context context,
              @NonNull final Bundle args) {

        if (bookDao == null) {
            bookDao = ServiceLocator.getInstance().getBookDao();

            // the style is allowed to be 'null' here. If it is, the default will be used.
            final String styleUuid = args.getString(Style.BKEY_UUID);
            style = ServiceLocator.getInstance().getStyles().getStyleOrDefault(context, styleUuid);

            final long authorId = args.getLong(DBKey.FK_AUTHOR, 0);
            if (authorId <= 0) {
                throw new IllegalArgumentException(DBKey.FK_AUTHOR);
            }

            author = ServiceLocator.getInstance().getAuthorDao()
                                   .getById(authorId)
                                   .orElseThrow();

            final long bookshelfId = args.getLong(DBKey.FK_BOOKSHELF, Bookshelf.ALL_BOOKS);

            bookshelf = Bookshelf.getBookshelf(context, bookshelfId, Bookshelf.ALL_BOOKS)
                                 .orElseThrow();
            allBookshelves = bookshelf.getId() == Bookshelf.ALL_BOOKS;

            withTocEntries = args.getBoolean(AuthorWorksFragment.BKEY_WITH_TOC, withTocEntries);
            withBooks = args.getBoolean(AuthorWorksFragment.BKEY_WITH_BOOKS, withBooks);
            reloadWorkList();
        }
    }

    void setFilter(final boolean withTocEntries,
                   final boolean withBooks) {
        this.withTocEntries = withTocEntries;
        this.withBooks = withBooks;
    }

    void reloadWorkList() {
        works.clear();
        final long bookshelfId = allBookshelves ? Bookshelf.ALL_BOOKS : bookshelf.getId();

        final List<AuthorWork> authorWorks =
                ServiceLocator.getInstance().getAuthorDao()
                              .getAuthorWorks(author, bookshelfId,
                                              withTocEntries, withBooks,
                                              orderByColumn);

        works.addAll(authorWorks);
    }

    @NonNull
    public Style getStyle() {
        return style;
    }

    long getBookshelfId() {
        return bookshelf.getId();
    }

    boolean isAllBookshelves() {
        return allBookshelves;
    }

    void setAllBookshelves(final boolean all) {
        allBookshelves = all;
    }

    public void setOrderByColumn(@AuthorDao.WorksOrderBy @Nullable final String orderByColumn) {
        this.orderByColumn = orderByColumn;
    }

    /**
     * Get the author.
     *
     * @return author
     */
    @NonNull
    public Author getAuthor() {
        return author;
    }

    @NonNull
    ArrayList<AuthorWork> getWorks() {
        // used directly by the adapter
        return works;
    }

    /**
     * Delete the given {@link AuthorWork}.
     *
     * @param context Current context
     * @param work    to delete
     *
     * @return {@code true} if a row was deleted
     *
     * @throws IllegalArgumentException <strong>DEBUG</strong>: for an invalid AuthorWork type
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean delete(@NonNull final Context context,
                   @NonNull final AuthorWork work) {
        final boolean success;
        switch (work.getWorkType()) {
            case TocEntry: {
                success = ServiceLocator.getInstance().getTocEntryDao()
                                        .delete(context, (TocEntry) work);
                break;
            }
            case Book: {
                success = bookDao.delete((Book) work);
                if (success) {
                    dataModified = true;
                }
                break;
            }
            case BookLight: {
                success = bookDao.delete((BookLight) work);
                if (success) {
                    dataModified = true;
                }
                break;
            }
            default:
                throw new IllegalArgumentException(String.valueOf(work));
        }

        if (success) {
            works.remove(work);
        }
        return success;
    }

    /**
     * Activity title consists of the author name + the number of entries shown.
     *
     * @param context Current context
     *
     * @return title
     */
    @NonNull
    String getScreenTitle(@NonNull final Context context) {
        return context.getString(R.string.name_hash_nr,
                                 author.getLabel(context, Details.AutoSelect, style),
                                 works.size());
    }

    /**
     * Activity subtitle will show the bookshelf name or nothing if all-shelves.
     *
     * @param context Current context
     *
     * @return subtitle
     */
    @Nullable
    String getScreenSubtitle(@NonNull final Context context) {
        if (allBookshelves) {
            return null;
        } else {
            return context.getString(R.string.name_colon_value,
                                     context.getString(R.string.lbl_bookshelf),
                                     bookshelf.getName());
        }
    }

    @NonNull
    Intent createResultIntent() {
        return EditBookOutput.createResult(0, dataModified);
    }

    void setDataModified(@NonNull final EditBookOutput data) {
        // ignore the data.bookId
        if (data.isModified()) {
            dataModified = true;
        }
    }
}
