/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDAO;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

public class EditBookshelvesViewModel
        extends ViewModel
        implements ResultIntent {

    /** Log tag. */
    private static final String TAG = "EditBookshelvesViewModel";

    public static final String BKEY_CURRENT_BOOKSHELF = TAG + ":current";
    private final MutableLiveData<Void> mSelectedPositionChanged = new MutableLiveData<>();

    /** Accumulate all data that will be send in {@link Activity#setResult}. */
    @NonNull
    private final Intent mResultIntent = new Intent();

    /** Database Access. */
    private DAO mDb;

    /** Currently selected row. */
    private int mSelectedPosition = RecyclerView.NO_POSITION;

    /** The list we're editing. */
    private ArrayList<Bookshelf> mList;

    @Override
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
        }
    }

    /**
     * {@link DBDefinitions#KEY_PK_ID} the selected bookshelf id, can be {@code 0} for none.
     */
    @NonNull
    @Override
    public Intent getResultIntent() {
        return mResultIntent;
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
                final long bookshelfId = args.getLong(BKEY_CURRENT_BOOKSHELF);
                mSelectedPosition = findSelectedPosition(bookshelfId);
                // set as the initial result
                mResultIntent.putExtra(DBDefinitions.KEY_PK_ID, bookshelfId);
            }
        }
    }

    public void reloadListAndSetSelectedPosition(final long bookshelfId) {
        mList.clear();
        mList.addAll(mDb.getBookshelves());
        setSelectedPosition(findSelectedPosition(bookshelfId));
    }

    private int findSelectedPosition(final long bookshelfId) {
        for (int i = 0; i < mList.size(); i++) {
            final Bookshelf bookshelf = mList.get(i);
            if (bookshelf.getId() == bookshelfId) {
                return i;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    @NonNull
    public ArrayList<Bookshelf> getBookshelves() {
        return mList;
    }

    @NonNull
    public Bookshelf getBookshelf(final int position) {
        return Objects.requireNonNull(mList.get(position), String.valueOf(position));
    }

    public int getSelectedPosition() {
        return mSelectedPosition;
    }

    public void setSelectedPosition(final int position) {
        mSelectedPosition = position;
        // update the fragment -> it will update the adapter
        mSelectedPositionChanged.setValue(null);
        // update the activity result.
        mResultIntent.putExtra(DBDefinitions.KEY_PK_ID, getBookshelf(mSelectedPosition).getId());
    }

    @NonNull
    public MutableLiveData<Void> onSelectedPositionChanged() {
        return mSelectedPositionChanged;
    }

    @NonNull
    public Bookshelf createNewBookshelf(@NonNull final Context context) {
        return new Bookshelf("", StyleDAO.getDefault(context, mDb));
    }

    public void deleteBookshelf(final int position) {
        final Bookshelf bookshelf = mList.get(position);
        mDb.delete(bookshelf);
        mList.remove(bookshelf);
    }

    public void purgeBLNS() {
        mDb.purgeNodeStatesByBookshelf(mList.get(mSelectedPosition).getId());
    }
}
