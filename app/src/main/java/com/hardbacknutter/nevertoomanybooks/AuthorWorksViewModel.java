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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.TocEntryDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.BookLite;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.menus.AuthorViewAuthorOnSiteMenuHandler;
import com.hardbacknutter.nevertoomanybooks.menus.MenuHandler;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverFactory;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverTask;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.util.livedataevent.LiveDataEvent;

@SuppressWarnings("WeakerAccess")
public class AuthorWorksViewModel
        extends ViewModel {

    private static final String PK_PREFIX = "author.works.";
    private static final String PK_ORDER_BY_COLUMN = PK_PREFIX + "orderby";
    /** Show the TOCEntries. Defaults to {@code true}. */
    private static final String PK_SHOW_TOC_ENTRIES = PK_PREFIX + "show.tocs";
    /** Show the Books. Defaults to {@code true}. */
    private static final String PK_SHOW_BOOKS = PK_PREFIX + "show.books";

    /** Update the author details (name, dates, etc...). */
    private final MutableLiveData<Author> onAuthorUpdated = new MutableLiveData<>();
    /** Update the works list. */
    private final MutableLiveData<Void> onWorksUpdated = new MutableLiveData<>();
    /**
     * A single work was deleted.
     * param1: position in list/adapter
     */
    private final MutableLiveData<Integer> onWorkDeleted = new MutableLiveData<>();

    /** Update the name of the bookshelf + number of items. */
    private final MutableLiveData<String> onBookshelfUpdated = new MutableLiveData<>();

    private final AuthorResolverTask authorResolverTask = new AuthorResolverTask();

    /** The list of TOC/Books we're displaying. */
    private final List<AuthorWork> works = new ArrayList<>();
    /** Author is set in {@link #init} and {@link #setAuthor(Context, Author, boolean)}. */
    private final List<Author> authors = new ArrayList<>();

    /** Database Access. */
    private AuthorDao authorDao;
    private BookDao bookDao;
    private TocEntryDao tocEntryDao;

    /** Initial Bookshelf is set in {@link #init}. */
    private Bookshelf bookshelf;
    /** Initially we get toc entries and books. */
    private boolean showTocEntries = true;
    /** Initially we get toc entries and books. */
    private boolean showBooks = true;
    /** Show all shelves, or only the initially selected shelf. */
    private boolean allBookshelves;
    /** Order the list by... */
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
    }

    @NonNull
    LiveData<LiveDataEvent<Throwable>> onResolverFailure() {
        return authorResolverTask.onFailure();
    }

    @NonNull
    LiveData<LiveDataEvent<Boolean>> onResolverCancelled() {
        return authorResolverTask.onCancelled();
    }

    @NonNull
    LiveData<LiveDataEvent<Boolean>> onResolverFinished() {
        return authorResolverTask.onFinished();
    }

    @NonNull
    LiveData<Author> onAuthorUpdated() {
        return onAuthorUpdated;
    }

    @NonNull
    LiveData<Void> onWorksUpdated() {
        return onWorksUpdated;
    }

    @NonNull
    LiveData<Integer> onWorkDeleted() {
        return onWorkDeleted;
    }

    @NonNull
    LiveData<String> onBookshelfUpdated() {
        return onBookshelfUpdated;
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

        if (authorDao == null) {
            final ServiceLocator serviceLocator = ServiceLocator.getInstance();
            authorDao = serviceLocator.getAuthorDao();
            bookDao = serviceLocator.getBookDao();
            tocEntryDao = serviceLocator.getTocEntryDao();

            menuHandlers = List.of(new AuthorViewAuthorOnSiteMenuHandler());
        }

        final long authorId = args.getLong(DBKey.FK_AUTHOR, 0);
        if (authorId <= 0) {
            throw new IllegalArgumentException(DBKey.FK_AUTHOR);
        }

        // The author id from the arguments has priority if we get here a second time.
        // 1. If the author list is empty -> load the author etc
        // 2. If we already have an author loaded, check if it's the one from the author id
        //      if not reload the author etc; otherwise don't bother reloading.
        // Note we only use/add a single author. Using a list for future compatibility though
        if (authors.isEmpty() || getPrimaryAuthor().getId() != authorId) {
            authors.clear();
            authors.add(authorDao.findById(authorId).orElseThrow());

            bookshelf = Objects.requireNonNull(args.getParcelable(DBKey.FK_BOOKSHELF),
                                               DBKey.FK_BOOKSHELF);
            style = bookshelf.getStyle();

            allBookshelves = bookshelf.getId() == Bookshelf.ALL_BOOKS;

            final SharedPreferences prefs = ServiceLocator.getInstance().getSharedPreferences();
            orderByColumn = prefs.getString(PK_ORDER_BY_COLUMN, DBKey.TITLE_OB);
            showTocEntries = prefs.getBoolean(PK_SHOW_TOC_ENTRIES, showTocEntries);
            showBooks = prefs.getBoolean(PK_SHOW_BOOKS, showBooks);

            onAuthorUpdated.setValue(getPrimaryAuthor());
            reloadWorkList(context);
        }
    }

    @NonNull
    List<MenuHandler<Author>> getMenuHandlers() {
        return menuHandlers;
    }

    private void reloadWorkList(@NonNull final Context context) {
        ASyncExecutor.PARALLEL.execute(() -> {
            works.clear();
            final long bookshelfId = allBookshelves ? Bookshelf.ALL_BOOKS : bookshelf.getId();

            final List<AuthorWork> authorWorks =
                    authorDao.getAuthorWorks(getPrimaryAuthor(), bookshelfId,
                                             showTocEntries, showBooks,
                                             orderByColumn);

            works.addAll(authorWorks);
            onWorksUpdated.postValue(null);
            // Activity subtitle will show the bookshelf name (or empty for all-shelves)
            // + the number of works shown.
            onBookshelfUpdated.postValue(context.getString(
                    R.string.name_hash_nr,
                    allBookshelves ? "" : bookshelf.getName(),
                    works.size()));
        });
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
        ServiceLocator.getInstance().getSharedPreferences()
                      .edit()
                      .putBoolean(PK_SHOW_TOC_ENTRIES, showTocEntries)
                      .putBoolean(PK_SHOW_BOOKS, showBooks)
                      .apply();

        reloadWorkList(context);
    }

    boolean isShowBooks() {
        return showBooks;
    }

    boolean isShowTocEntries() {
        return showTocEntries;
    }

    @NonNull
    String getOrderByColumn() {
        return orderByColumn;
    }

    void setOrderByColumn(@NonNull final Context context,
                          @AuthorDao.WorksOrderBy @NonNull final String orderByColumn) {
        this.orderByColumn = orderByColumn;

        ServiceLocator.getInstance().getSharedPreferences()
                      .edit()
                      .putString(PK_ORDER_BY_COLUMN, orderByColumn)
                      .apply();

        reloadWorkList(context);
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
        reloadWorkList(context);
    }

    void reloadAuthorIfChanged(@NonNull final Context context) {
        final Author tmp = authorDao.findById(getPrimaryAuthor().getId()).orElseThrow();
        if (!tmp.equals(getPrimaryAuthor())) {
            // the works might not have changed, but we need to be sure here.
            setAuthor(context, tmp, true);
        }
    }

    /**
     * Get the primary author.
     *
     * @return author
     */
    @NonNull
    Author getPrimaryAuthor() {
        return authors.get(0);
    }

    /**
     * Get the list of authors (usually just the one) who own these works.
     *
     * @return list
     */
    @NonNull
    List<Author> getAuthors() {
        // used directly by the adapter
        return authors;
    }

    /**
     * The author was edited by the user.
     * Update the author, and trigger UI updates.
     *
     * @param context     Current context
     * @param author      to update
     * @param reloadWorks whether to reload the works as well
     */
    void setAuthor(@NonNull final Context context,
                   @NonNull final Author author,
                   final boolean reloadWorks) {
        dataModified = !author.equals(getPrimaryAuthor());
        this.authors.clear();
        this.authors.add(author);
        onAuthorUpdated.setValue(author);
        if (reloadWorks) {
            reloadWorkList(context);
        }
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
     * @param context  Current context
     * @param position of the work to delete in the list
     *
     * @throws IllegalArgumentException (debug) for an invalid AuthorWork type
     */
    void delete(@NonNull final Context context,
                final int position) {
        ASyncExecutor.STORAGE_WRITES.execute(() -> {
            final AuthorWork work = works.get(position);

            final boolean success;
            switch (work.getWorkType()) {
                case TocEntry: {
                    success = tocEntryDao.delete(context, (TocEntry) work);
                    break;
                }
                case Book: {
                    success = bookDao.delete((Book) work);
                    if (success) {
                        dataModified = true;
                    }
                    break;
                }
                case BookLite: {
                    success = bookDao.delete((BookLite) work);
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
                onWorkDeleted.postValue(position);
            }
        });
    }

    @NonNull
    Intent createResultIntent() {
        return new EditBookOutput(dataModified, 0, 0).createResultIntent();
    }

    void setDataModified(@NonNull final EditBookOutput data) {
        // ignore the data.bookId
        if (data.isModified()) {
            dataModified = true;
        }
    }

    @NonNull
    List<EngineId> getEnabledEnginesForSearch(@NonNull final Context context) {
        // The SIDs we have for the author
        final List<String> sidKeys = getPrimaryAuthor()
                .getIdentifiers()
                .stream()
                .map(Identifier.Value::getKey)
                .collect(Collectors.toList());

        return AuthorResolverFactory.getEngines(context, sidKeys);
    }

    void resolve(@NonNull final Context context,
                 @NonNull final EngineId engineId) {
        // No need to reload the author data.
        // Store any updates to the database.
        authorResolverTask.start(context, engineId, authors, false, true);
    }

    void cancelResolverTask() {
        authorResolverTask.cancel();
    }
}
