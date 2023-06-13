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

package com.hardbacknutter.nevertoomanybooks.activityresultcontracts;

import android.content.Intent;
import android.os.Parcelable;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.core.util.Supplier;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf;
import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelfViewModel;
import com.hardbacknutter.nevertoomanybooks.booklist.RebuildBooklist;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.utils.ParcelUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.TocEntryDao;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

public class DisplayBookLauncher {

    @NonNull
    private final ActivityResultLauncher<ShowBookPagerContract.Input> launcher;
    @NonNull
    private final Supplier<TocEntryDao> tocEntryDaoSupplier;

    public DisplayBookLauncher(@NonNull final ActivityResultCaller fragment,
                               @NonNull final Supplier<TocEntryDao> tocEntryDaoSupplier,
                               @NonNull final ActivityResultCallback<Optional<EditBookOutput>>
                                       optionalActivityResultCallback) {
        this.tocEntryDaoSupplier = tocEntryDaoSupplier;
        this.launcher = fragment.registerForActivityResult(new ShowBookPagerContract(),
                                                           optionalActivityResultCallback);
    }

    public void launch(@NonNull final Fragment fragment,
                       @NonNull final AuthorWork work,
                       @NonNull final Style style,
                       final boolean allBookshelves) {

        switch (work.getWorkType()) {
            case TocEntry: {
                final ArrayList<Long> bookIdList = tocEntryDaoSupplier
                        .get().getBookIds(work.getId());
                if (bookIdList.size() == 1) {
                    launcher.launch(new ShowBookPagerContract.Input(
                            bookIdList.get(0), style.getUuid(), null, 0));

                } else {
                    // multiple books, open the list as a NEW ACTIVITY
                    final Intent intent = new Intent(fragment.getContext(), BooksOnBookshelf.class)
                            .putExtra(Book.BKEY_BOOK_ID_LIST, ParcelUtils.wrap(bookIdList))
                            // Open the list expanded, as otherwise you end up with
                            // the author as a single line, and no books shown at all,
                            // which can be quite confusing to the user.
                            .putExtra(BooksOnBookshelfViewModel.BKEY_LIST_STATE,
                                      (Parcelable) RebuildBooklist.Expanded);

                    if (allBookshelves) {
                        intent.putExtra(DBKey.FK_BOOKSHELF, Bookshelf.ALL_BOOKS);
                    }
                    fragment.startActivity(intent);
                }
                break;
            }
            case Book:
            case BookLight: {
                launcher.launch(new ShowBookPagerContract.Input(
                        work.getId(), style.getUuid(), null, 0));

                break;
            }
            default:
                throw new IllegalArgumentException(String.valueOf(work));
        }
    }
}
