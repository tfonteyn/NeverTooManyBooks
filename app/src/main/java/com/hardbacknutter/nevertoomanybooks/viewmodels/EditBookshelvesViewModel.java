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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

public class EditBookshelvesViewModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "EditBookshelvesViewModel";

    public static final String BKEY_CURRENT_BOOKSHELF = TAG + ":current";

    private final MutableLiveData<Void> mSelectedPositionChanged = new MutableLiveData<>();

    /** Currently selected row. */
    private int mSelectedPosition = RecyclerView.NO_POSITION;

    /** The list we're editing. */
    private ArrayList<Bookshelf> mList;
    /** the selected bookshelf id, can be {@code 0} for none. */
    private long mSelectedBookshelfId;

    public long getSelectedBookshelfId() {
        return mSelectedBookshelfId;
    }

    /**
     * Pseudo constructor.
     * <p>
     * Loads the book data upon first start.
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@Nullable final Bundle args) {
        if (mList == null) {
            mList = ServiceLocator.getInstance().getBookshelfDao().getAll();
            if (args != null) {
                // set as the initial result
                mSelectedBookshelfId = args.getLong(BKEY_CURRENT_BOOKSHELF);
                mSelectedPosition = findSelectedPosition(mSelectedBookshelfId);
            }
        }
    }

    public void reloadListAndSetSelectedPosition(final long bookshelfId) {
        mList.clear();
        mList.addAll(ServiceLocator.getInstance().getBookshelfDao().getAll());
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
        mSelectedBookshelfId = getBookshelf(mSelectedPosition).getId();
    }

    @NonNull
    public LiveData<Void> onSelectedPositionChanged() {
        return mSelectedPositionChanged;
    }

    @NonNull
    public Bookshelf createNewBookshelf(@NonNull final Context context) {
        return new Bookshelf("", ServiceLocator.getInstance().getStyles()
                                               .getDefault(context));
    }

    public void deleteBookshelf(final int position) {
        final Bookshelf bookshelf = mList.get(position);
        ServiceLocator.getInstance().getBookshelfDao().delete(bookshelf);
        mList.remove(bookshelf);
    }

    public void purgeBLNS() {
        ServiceLocator.getInstance().getBookshelfDao()
                      .purgeNodeStates(mList.get(mSelectedPosition).getId());
    }
}
