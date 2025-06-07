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

package com.hardbacknutter.nevertoomanybooks.activityresultcontracts;

import android.content.Intent;
import android.os.Parcelable;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf;
import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelfViewModel;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.RebuildBooklist;
import com.hardbacknutter.nevertoomanybooks.core.utils.ParcelUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.TocEntryDao;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

/**
 * Small wrapper to call the {@link ShowBookPagerContract}
 * or to open a NEW {@link BooksOnBookshelf} instance
 * to display a book or list of books based on a list of {@link AuthorWork}s.
 */
public class DisplayBookLauncher {

    @NonNull
    private final ActivityResultLauncher<ShowBookPagerContract.Input> launcher;

    public DisplayBookLauncher(@NonNull final ActivityResultCaller fragment,
                               @NonNull final ActivityResultCallback<Optional<EditBookOutput>>
                                       optionalActivityResultCallback) {
        this.launcher = fragment.registerForActivityResult(new ShowBookPagerContract(),
                                                           optionalActivityResultCallback);
    }

    /**
     * Launch the Book pager screen with the list of books derived
     * from the actual books and the books from TOCEntries.
     *
     * @param works     books and TOCEntries
     * @param position  current position
     * @param bookshelf current Bookshelf displayed by the BoB
     *
     * @throws IllegalArgumentException (debug)
     */
    public void launchBookPager(@NonNull final List<AuthorWork> works,
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
                case BookLight:
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
            case BookLight:
            case Book:
                bookId = currentWork.getId();
                break;
            default:
                throw new IllegalArgumentException(currentWork.toString());
        }
        final long currentWorkId = currentWork.getId();
        final int pos = bookIdList.indexOf(bookId);

        launcher.launch(new ShowBookPagerContract.Input(currentWorkId, bookshelf,
                                                        pos, bookIdList));
    }

    /**
     * Launch either the ShowBookPagerFragment for the given {@link AuthorWork} if it's a book,
     * or start a NEW BooksOnBookshelf Activity if the work is a TocEntry.
     *
     * @param fragment       hosting fragment
     * @param work           to open
     * @param bookshelf      current Bookshelf displayed by the BoB
     * @param allBookshelves flag
     *
     * @throws IllegalArgumentException (debug)
     */
    public void launch(@NonNull final Fragment fragment,
                       @NonNull final AuthorWork work,
                       @NonNull final Bookshelf bookshelf,
                       final boolean allBookshelves) {

        switch (work.getWorkType()) {
            case Book:
            case BookLight: {
                launcher.launch(new ShowBookPagerContract.Input(work.getId(), bookshelf));
                break;
            }
            case TocEntry: {
                launchTocEntry(fragment, (TocEntry) work, bookshelf, allBookshelves);
                break;
            }
            default:
                throw new IllegalArgumentException(String.valueOf(work));
        }
    }

    /**
     * When the user clicks on a {@link TocEntry}.
     * <ul>
     * <li>If the entry belongs to a single Book, just display that Book.</li>
     * <li>If the entry is present in multiple books, open a new BoB with the list of books.</li>
     * </ul>
     *
     * @param fragment       hosting fragment
     * @param tocEntry       to open
     * @param bookshelf      current Bookshelf displayed by the BoB
     * @param allBookshelves flag
     */
    public void launchTocEntry(@NonNull final Fragment fragment,
                               @NonNull final TocEntry tocEntry,
                               @NonNull final Bookshelf bookshelf,
                               final boolean allBookshelves) {

        final List<Long> bookIdList = ServiceLocator
                .getInstance().getTocEntryDao().getBookIds(tocEntry.getId());
        if (bookIdList.size() == 1) {
            launcher.launch(new ShowBookPagerContract.Input(bookIdList.get(0), bookshelf));

        } else {
            final long bookshelfId = allBookshelves ? Bookshelf.ALL_BOOKS : bookshelf.getId();
            // multiple books, open a new BooksOnBookshelf instance
            // (it will have a 'back' button)
            final Intent intent = new Intent(fragment.getContext(), BooksOnBookshelf.class)
                    .putExtra(Book.BKEY_BOOK_ID_LIST, ParcelUtils.wrap(bookIdList))
                    // Open the list expanded, as otherwise you end up with
                    // the author as a single line, and no books shown at all,
                    // which can be quite confusing to the user.
                    .putExtra(BooksOnBookshelfViewModel.BKEY_LIST_STATE,
                              (Parcelable) RebuildBooklist.Expanded)
                    // The Bookshelf id! NOT the parceled Bookshelf object!
                    .putExtra(DBKey.FK_BOOKSHELF, bookshelfId);

            fragment.startActivity(intent);
        }
    }
}
