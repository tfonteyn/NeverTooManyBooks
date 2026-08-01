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

package com.hardbacknutter.nevertoomanybooks.activityresultcontracts;

import android.content.Intent;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf;
import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelfInput;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.bookdetails.ShowBookPagerInput;
import com.hardbacknutter.nevertoomanybooks.booklist.RebuildBooklist;
import com.hardbacknutter.nevertoomanybooks.database.dao.TocEntryDao;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

/**
 * Small wrapper to call the {@link ShowBookPagerContract}
 * or to open a NEW {@link BooksOnBookshelf} instance
 * to display a book or list of books based on a list of {@link AuthorWork}s.
 */
public class DisplayBookLauncher {

    @NonNull
    private final ActivityResultLauncher<ShowBookPagerInput> launcher;

    /**
     * Constructor.
     *
     * @param contractOwner  the component which handles the {@link ActivityResultContract}
     * @param resultCallback with the activity result
     */
    public DisplayBookLauncher(@NonNull final ActivityResultCaller contractOwner,
                               @NonNull final ActivityResultCallback<Optional<EditBookOutput>>
                                       resultCallback) {
        this.launcher = contractOwner.registerForActivityResult(new ShowBookPagerContract(),
                                                                resultCallback);
    }

    /**
     * ENHANCE: GitHub #151 potential replacement for {@link #launcher}
     *  when we decided on inconsistencies between ShowTocFragment/AuthorWorksFragment.
     * <p>
     * Launch the Book pager screen with the list of books derived
     * from the actual books and the books from TOCEntries.
     *
     * @param works     books and TOCEntries
     * @param position  current position
     * @param bookshelf current Bookshelf displayed by the BoB
     *
     * @throws IllegalArgumentException (debug)
     */
    public void launchBookPager(@NonNull final List<? extends AuthorWork> works,
                                final int position,
                                @NonNull final Bookshelf bookshelf) {
        final AuthorWork currentWork = works.get(position);
        // Collect all actual books, removing duplicates
        final Set<Long> bookIds = new LinkedHashSet<>();
        final TocEntryDao tocEntryDao = ServiceLocator.getInstance().getTocEntryDao();
        for (final AuthorWork work : works) {
            switch (work.getWorkType()) {
                case TocEntry:
                    bookIds.addAll(tocEntryDao.getBookIds(work.getId()));
                    break;
                case BookLite:
                case Book:
                    bookIds.add(work.getId());
                    break;
            }
        }
        final List<Long> bookIdList = new ArrayList<>(bookIds);

        final long bookId;
        switch (currentWork.getWorkType()) {
            case TocEntry:
                final List<Long> bookIds1 = tocEntryDao.getBookIds(currentWork.getId());
                // Paranoia....
                if (bookIds1.isEmpty()) {
                    // Orphaned TocEntry, no book to display
                    return;
                }
                bookId = bookIds1.get(0);
                break;
            case BookLite:
            case Book:
                bookId = currentWork.getId();
                break;
            default:
                throw new IllegalArgumentException(currentWork.toString());
        }
        final long currentWorkId = currentWork.getId();
        final int pos = bookIdList.indexOf(bookId);

        launcher.launch(new ShowBookPagerInput(currentWorkId, bookshelf,
                                               pos, bookIdList));
    }

    /**
     * Launch either the ShowBookPagerFragment for the given {@link AuthorWork} if it's a book,
     * or start a NEW BooksOnBookshelf Activity if the work is a TocEntry.
     *
     * @param fragment       hosting fragment
     * @param works          the list of works
     * @param workPosition   to open the list on
     * @param bookshelf      current Bookshelf displayed by the BoB
     * @param allBookshelves flag
     *
     * @throws IllegalArgumentException (debug)
     */
    public void launch(@NonNull final Fragment fragment,
                       @NonNull final List<? extends AuthorWork> works,
                       final int workPosition,
                       @NonNull final Bookshelf bookshelf,
                       final boolean allBookshelves) {

        final AuthorWork work = works.get(workPosition);
        switch (work.getWorkType()) {
            case Book:
            case BookLite: {
                launcher.launch(new ShowBookPagerInput(work.getId(), bookshelf));
                break;
            }
            case TocEntry: {
                final List<Long> bookIdList = ServiceLocator
                        .getInstance().getTocEntryDao().getBookIds(work.getId());
                launchList(fragment, bookIdList, bookshelf, allBookshelves);
                break;
            }
            default:
                throw new IllegalArgumentException(String.valueOf(work));
        }
    }

    /**
     * If there is only a single Book, just display that Book.
     * Otherwise, open a new BoB with the list of books.
     *
     * @param fragment       hosting fragment
     * @param bookIdList       to open
     * @param bookshelf      current Bookshelf displayed by the BoB
     * @param allBookshelves flag
     */
    private void launchList(@NonNull final Fragment fragment,
                            @NonNull final List<Long> bookIdList,
                            @NonNull final Bookshelf bookshelf,
                            final boolean allBookshelves) {

        if (bookIdList.size() == 1) {
            launcher.launch(new ShowBookPagerInput(bookIdList.get(0), bookshelf));

        } else {
            final long bookshelfId = allBookshelves ? Bookshelf.ALL_BOOKS : bookshelf.getId();
            // multiple books, open a new BooksOnBookshelf instance
            // (it will have a 'back' button)

            // Open the list expanded, as otherwise you end up with
            // the author as a single line, and no books shown at all,
            // which can be quite confusing to the user.
            final BooksOnBookshelfInput input = new BooksOnBookshelfInput(
                    bookshelfId, bookIdList, RebuildBooklist.Expanded);

            final Intent intent = new Intent(fragment.getContext(), BooksOnBookshelf.class)
                    .putExtras(input.toBundle());
            fragment.startActivity(intent);
        }
    }
}
