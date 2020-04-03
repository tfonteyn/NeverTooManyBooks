/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

public class EditBookshelvesModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "EditBookshelvesModel";

    public static final String BKEY_CURRENT_BOOKSHELF = TAG + ":current";
    private final MutableLiveData<Integer> mSelectedPosition = new MutableLiveData<>();

    /** Database Access. */
    private DAO mDb;
    /** Shelf as set by the caller. Can be {@code 0}. */
    private long mInitialBookshelfId;
    @Nullable
    private Bookshelf mSelectedBookshelf;

    /** The list we're editing. */
    private ArrayList<Bookshelf> mList;

    @Override
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
        }
    }

    /**
     * Pseudo constructor.
     * <p>
     * Loads the book data upon first start.
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@Nullable final Bundle args) {
        if (mDb == null) {
            mDb = new DAO(TAG);
            mList = mDb.getBookshelves();
            if (args != null) {
                mInitialBookshelfId = args.getLong(BKEY_CURRENT_BOOKSHELF);
            }
        }
    }

    /**
     * The bookshelf we we're on when this activity was started from the main BoB screen.
     * Will be {@code 0} if started from somewhere else.
     *
     * @return initial shelf id, or {@code 0} for none.
     */
    public long getInitialBookshelfId() {
        return mInitialBookshelfId;
    }

    /**
     * Get the currently selected Bookshelf.
     *
     * @return bookshelf, or {@code null} if none selected (which should never happen... flw)
     */
    @Nullable
    public Bookshelf getSelectedBookshelf() {
        return mSelectedBookshelf;
    }

    public void setSelectedBookshelf(final int position) {
        mSelectedBookshelf = mList.get(position);
    }

    @NonNull
    public ArrayList<Bookshelf> getBookshelves() {
        return mList;
    }

    @NonNull
    public Bookshelf getBookshelf(final int position) {
        return mList.get(position);
    }

    @NonNull
    public Bookshelf createNewBookshelf(@NonNull final Context context) {
        return new Bookshelf("", BooklistStyle.getDefault(context, mDb));
    }

    /** Observable. */
    @NonNull
    public MutableLiveData<Integer> getSelectedPosition() {
        return mSelectedPosition;
    }

    public void reloadList(final long selectedBookshelfId) {
        mList.clear();
        mList.addAll(mDb.getBookshelves());

        for (int i = 0; i < mList.size(); i++) {
            final Bookshelf bookshelf = mList.get(i);
            if (bookshelf.getId() == selectedBookshelfId) {
                mSelectedPosition.setValue(i);
                mSelectedBookshelf = bookshelf;
                break;
            }
        }
    }

    public void purgeBLNS() {
        if (mSelectedBookshelf != null) {
            mDb.purgeNodeStatesByBookshelf(mSelectedBookshelf.getId());
        }
    }

    public void deleteBookshelf(@NonNull final Bookshelf bookshelf) {
        mDb.deleteBookshelf(bookshelf.getId());
        mList.remove(bookshelf);
    }
}
