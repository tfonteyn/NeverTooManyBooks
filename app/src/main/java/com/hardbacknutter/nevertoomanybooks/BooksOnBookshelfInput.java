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

import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.booklist.RebuildBooklist;
import com.hardbacknutter.nevertoomanybooks.core.utils.ParcelUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.localsearch.LocalSearchCriteria;

public class BooksOnBookshelfInput {

    private static final String TAG = "BooksOnBookshelfInput";

    /** Passed in by the {@link StartupActivity} if the user confirmed to take a backup. */
    private static final String BKEY_PROPOSE_BACKUP = TAG + ":pb";

    private static final String BKEY_LIST_REBUILD = TAG + ":rb";

    @Nullable
    private final Boolean proposeBackup;
    @Nullable
    private final Long bookshelfId;

    @Nullable
    private final List<Long> bookIdList;

    @Nullable
    private final RebuildBooklist rebuildBooklist;
    @Nullable
    private final LocalSearchCriteria criteria;


    /**
     * Constructor used by the standard startup procedure.
     *
     * @param proposeBackup flag
     */
    BooksOnBookshelfInput(final boolean proposeBackup) {
        this(null, null, null, null, proposeBackup);
    }

    /**
     * Constructor used to display a given list of books.
     *
     * @param bookshelfId     to use
     * @param bookIdList      to display
     * @param rebuildBooklist to overrule the default
     */
    public BooksOnBookshelfInput(@IntRange(from = 1) final long bookshelfId,
                                 @Nullable final List<Long> bookIdList,
                                 @Nullable final RebuildBooklist rebuildBooklist) {
        this(bookshelfId, bookIdList, rebuildBooklist, null, null);
    }

    private BooksOnBookshelfInput(@Nullable final Long bookshelfId,
                                  @Nullable final List<Long> bookIdList,
                                  @Nullable final RebuildBooklist rebuildBooklist,
                                  @Nullable final LocalSearchCriteria criteria,
                                  @Nullable final Boolean proposeBackup) {
        this.bookshelfId = bookshelfId;
        this.bookIdList = bookIdList;
        this.rebuildBooklist = rebuildBooklist;
        this.criteria = criteria;
        this.proposeBackup = proposeBackup;
    }

    @SuppressWarnings("deprecation")
    @Nullable
    static BooksOnBookshelfInput fromBundle(@Nullable final Bundle args) {
        if (args == null) {
            return null;
        }

        @Nullable
        final Long bookshelfId;
        if (args.containsKey(DBKey.FK_BOOKSHELF)) {
            bookshelfId = args.getLong(DBKey.FK_BOOKSHELF, 0);
        } else {
            bookshelfId = null;
        }

        @Nullable
        final List<Long> bookIdlist = ParcelUtils.unwrap(args, Book.BKEY_BOOK_ID_LIST);
        @Nullable
        final RebuildBooklist rebuildBooklist = args.getParcelable(BKEY_LIST_REBUILD);
        @Nullable
        final LocalSearchCriteria searchCriteria = LocalSearchCriteria.fromBundle(args);

        @Nullable
        final Boolean proposeBackup;
        if (args.containsKey(BKEY_PROPOSE_BACKUP)) {
            proposeBackup = args.getBoolean(BKEY_PROPOSE_BACKUP, false);
        } else {
            proposeBackup = null;
        }

        return new BooksOnBookshelfInput(bookshelfId, bookIdlist, rebuildBooklist,
                                         searchCriteria, proposeBackup);
    }

    @NonNull
    public Bundle toBundle() {
        final Bundle args = new Bundle(3);

        if (bookshelfId != null) {
            args.putLong(DBKey.FK_BOOKSHELF, bookshelfId);
        }
        if (bookIdList != null) {
            args.putParcelable(Book.BKEY_BOOK_ID_LIST, ParcelUtils.wrap(bookIdList));
        }
        if (rebuildBooklist != null) {
            args.putParcelable(BKEY_LIST_REBUILD, rebuildBooklist);
        }
        if (criteria != null && !criteria.isEmpty()) {
            args.putAll(criteria.toBundle());
        }
        if (proposeBackup != null) {
            args.putBoolean(BKEY_PROPOSE_BACKUP, proposeBackup);
        }

        return args;
    }

    boolean isProposeBackup() {
        return proposeBackup != null ? proposeBackup : false;
    }

    @Nullable
    RebuildBooklist getRebuildBooklist() {
        return rebuildBooklist;
    }

    @NonNull
    Optional<Long> getBookshelfId() {
        return Optional.ofNullable(bookshelfId);
    }

    @NonNull
    Optional<List<Long>> getBookIdList() {
        return Optional.ofNullable(bookIdList);
    }

    /**
     * Get the criteria. If there are none, this method returns a new instance.
     *
     * @return criteria
     */
    @NonNull
    LocalSearchCriteria getCriteria() {
        return criteria != null ? criteria : new LocalSearchCriteria();
    }
}
