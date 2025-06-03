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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.BookLight;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.menus.AuthorViewAuthorOnSiteMenuHandler;
import com.hardbacknutter.nevertoomanybooks.menus.MenuHandler;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverTask;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;

@SuppressWarnings("WeakerAccess")
public class AuthorWorksViewModel
        extends ViewModel {

    private static final String PK_PREFIX = "author.works.";
    private static final String PK_ORDER_BY_COLUMN = PK_PREFIX + "orderby";
    /** Show the TOCEntries. Defaults to {@code true}. */
    private static final String PK_SHOW_TOC_ENTRIES = PK_PREFIX + "show.tocs";
    /** Show the Books. Defaults to {@code true}. */
    private static final String PK_SHOW_BOOKS = PK_PREFIX + "show.books";

    /**
     * Update the author details (name, dates, etc...).
     */
    private final MutableLiveData<Author> onAuthor = new MutableLiveData<>();
    /**
     * Update the works list.
     */
    private final MutableLiveData<Void> onWorks = new MutableLiveData<>();
    /**
     * Update the name of the bookshelf + number of items.
     */
    private final MutableLiveData<String> onBookshelf = new MutableLiveData<>();

    private final AuthorResolverTask authorResolverTask = new AuthorResolverTask();

    /** The list of TOC/Books we're displaying. */
    private final List<AuthorWork> works = new ArrayList<>();

    /** Database Access. */
    private BookDao bookDao;
    /** Author is set in {@link #init}. */
    private Author author;
    /** Initial Bookshelf is set in {@link #init}. */
    private Bookshelf bookshelf;
    /** Initially we get toc entries and books. */
    private boolean showTocEntries = true;
    /** Initially we get toc entries and books. */
    private boolean showBooks = true;
    /** Show all shelves, or only the initially selected shelf. */
    private boolean allBookshelves;
    /**
     * Order the list by...
     * For all allowed values, see {@link AuthorDao.WorksOrderBy}
     */
    @AuthorDao.WorksOrderBy
    @NonNull
    private String orderByColumn = DBKey.TITLE_OB;

    /** Set to {@code true} when ... used to report back to BoB to decide rebuilding BoB list. */
    private boolean dataModified;

    private Style style;
    private List<MenuHandler<Author>> menuHandlers;

    @Override
    protected void onCleared() {
        authorResolverTask.cancel();
        super.onCleared();
    }

    @NonNull
    public LiveData<LiveDataEvent<Throwable>> onResolverFailure() {
        return authorResolverTask.onFailure();
    }

    @NonNull
    public LiveData<LiveDataEvent<Boolean>> onResolverCancelled() {
        return authorResolverTask.onCancelled();
    }

    @NonNull
    public LiveData<LiveDataEvent<Boolean>> onResolverFinished() {
        return authorResolverTask.onFinished();
    }

    @NonNull
    MutableLiveData<Author> onAuthor() {
        return onAuthor;
    }

    @NonNull
    public MutableLiveData<Void> onWorks() {
        return onWorks;
    }

    @NonNull
    MutableLiveData<String> onBookshelf() {
        return onBookshelf;
    }

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     *
     * @throws IllegalArgumentException if the args do not contain a valid Author
     */
    void init(@NonNull final Context context,
              @NonNull final Bundle args) {

        if (bookDao == null) {
            bookDao = ServiceLocator.getInstance().getBookDao();

            menuHandlers = List.of(new AuthorViewAuthorOnSiteMenuHandler());

            final long authorId = args.getLong(DBKey.FK_AUTHOR, 0);
            if (authorId <= 0) {
                throw new IllegalArgumentException(DBKey.FK_AUTHOR);
            }

            author = ServiceLocator.getInstance().getAuthorDao()
                                   .findById(authorId)
                                   .orElseThrow();

            bookshelf = Objects.requireNonNull(args.getParcelable(DBKey.FK_BOOKSHELF),
                                               DBKey.FK_BOOKSHELF);
            style = bookshelf.getStyle();

            allBookshelves = bookshelf.getId() == Bookshelf.ALL_BOOKS;

            final SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(context);
            orderByColumn = prefs.getString(PK_ORDER_BY_COLUMN, DBKey.TITLE_OB);
            showTocEntries = prefs.getBoolean(PK_SHOW_TOC_ENTRIES, showTocEntries);
            showBooks = prefs.getBoolean(PK_SHOW_BOOKS, showBooks);

            reloadWorkList();
        }

        onAuthor.setValue(author);
        onBookshelf.setValue(getBookshelfAndNrOfEntries(context));
    }

    @NonNull
    List<MenuHandler<Author>> getMenuHandlers() {
        return menuHandlers;
    }

    void reloadWorkList() {
        works.clear();
        final long bookshelfId = allBookshelves ? Bookshelf.ALL_BOOKS : bookshelf.getId();

        final List<AuthorWork> authorWorks =
                ServiceLocator.getInstance().getAuthorDao()
                              .getAuthorWorks(author, bookshelfId,
                                              showTocEntries, showBooks,
                                              orderByColumn);

        works.addAll(authorWorks);
    }

    @NonNull
    Style getStyle() {
        return style;
    }

    void setFilter(@NonNull final Context context,
                   final boolean showTocEntries,
                   final boolean showBooks) {
        this.showTocEntries = showTocEntries;
        this.showBooks = showBooks;
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                         .putBoolean(PK_SHOW_TOC_ENTRIES, showTocEntries)
                         .putBoolean(PK_SHOW_BOOKS, showBooks)
                         .apply();

        reloadWorkList();
        onWorks.setValue(null);
    }

    public boolean isShowBooks() {
        return showBooks;
    }

    public boolean isShowTocEntries() {
        return showTocEntries;
    }

    @NonNull
    public String getOrderByColumn() {
        return orderByColumn;
    }

    void setOrderByColumn(@NonNull final Context context,
                          @AuthorDao.WorksOrderBy @NonNull final String orderByColumn) {
        this.orderByColumn = orderByColumn;

        PreferenceManager.getDefaultSharedPreferences(context).edit()
                         .putString(PK_ORDER_BY_COLUMN, orderByColumn)
                         .apply();

        reloadWorkList();
        onWorks.setValue(null);
    }

    /**
     * Are we / should we display the list for 'All Bookshelves' or only for the
     * previously set single Bookshelf.
     *
     * @return {@code true} for all shelves.
     */
    boolean isAllBookshelves() {
        return allBookshelves;
    }

    void setAllBookshelves(@NonNull final Context context,
                           final boolean all) {
        allBookshelves = all;
        onBookshelf.setValue(getBookshelfAndNrOfEntries(context));
        reloadWorkList();
        onWorks.setValue(null);
    }

    void onResume() {
        final Author tmp = ServiceLocator.getInstance().getAuthorDao()
                                         .findById(author.getId())
                                         .orElseThrow();
        if (!tmp.equals(author)) {
            author = tmp;
            // the works might not have changed, but reload them anyhow
            reloadWorkList();
            onAuthor.setValue(author);
            onWorks.setValue(null);
        }
    }

    /**
     * Get the author.
     *
     * @return author
     */
    @NonNull
    Author getAuthor() {
        return author;
    }

    /**
     * Set a new author, reload the works, and trigger UI updates.
     *
     * @param id author id to load
     */
    void setAuthor(@IntRange(from = 1) final long id) {
        author = ServiceLocator.getInstance().getAuthorDao()
                               .findById(id)
                               .orElseThrow();
        reloadWorkList();
        onAuthor.setValue(this.author);
        onWorks.setValue(null);
    }

    /**
     * The author was edited by the user.
     * Update the author, and trigger UI updates.
     * The works list is presumed not the have changed, and NOT reloaded!
     *
     * @param author to update
     */
    void onAuthorEditDone(@NonNull final Author author) {
        this.author = author;
        onAuthor.setValue(author);
    }

    @NonNull
    Bookshelf getBookshelf() {
        return bookshelf;
    }

    /**
     * Get the works list.
     *
     * @return list
     */
    @NonNull
    List<AuthorWork> getWorks() {
        // used directly by the adapter
        return works;
    }

    /**
     * Delete the given {@link AuthorWork}.
     * <p>
     * The caller must update the adapter manually.
     *
     * @param context Current context
     * @param work    to delete
     *
     * @return {@code true} if a row was deleted
     *
     * @throws IllegalArgumentException for an invalid AuthorWork type
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
     * Activity subtitle will show the bookshelf name (or empty for all-shelves)
     * + the number of entries shown.
     *
     * @param context Current context
     *
     * @return subtitle
     */
    @NonNull
    private String getBookshelfAndNrOfEntries(@NonNull final Context context) {
        return context.getString(R.string.name_hash_nr,
                                 allBookshelves ? "" : bookshelf.getName(),
                                 works.size());
    }

    @NonNull
    Intent createResultIntent() {
        return EditBookOutput.createResultIntent(dataModified, 0);
    }

    void setDataModified(@NonNull final EditBookOutput data) {
        // ignore the data.bookId
        if (data.isModified()) {
            dataModified = true;
        }
    }

    void resolve(@NonNull final Context context,
                 @NonNull final EngineId engineId) {
        // No need to reload the author data.
        // Store any updates to the database.
        authorResolverTask.start(context, engineId, List.of(author), false, true);
    }

    void cancelResolverTask() {
        authorResolverTask.cancel();
    }
}
