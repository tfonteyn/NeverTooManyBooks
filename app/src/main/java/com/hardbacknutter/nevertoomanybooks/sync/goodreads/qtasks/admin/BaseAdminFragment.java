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
package com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.admin;

import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentGoodreadsAdminListviewBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue.TQCursorAdapter;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue.TQItem;

public abstract class BaseAdminFragment
        extends BaseFragment {

    public static final String TAG = "BaseAdminFragment";

    /** Database Access. */
    private BookDao mBookDao;

    /** The adapter for the list. */
    private TQCursorAdapter mListAdapter;

    /** Listener to handle changes made to the underlying cursor. */
    final QueueManager.OnChangeListener mOnChangeListener = this::refreshData;
    /** View Binding. */
    private FragmentGoodreadsAdminListviewBinding mVb;

    /**
     * Get a CursorAdapter returning the items we are interested in.
     *
     * @param bookDao Database Access
     *
     * @return CursorAdapter to use
     */
    @NonNull
    protected abstract TQCursorAdapter getListAdapter(@NonNull BookDao bookDao);

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentGoodreadsAdminListviewBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setTitle(R.string.site_goodreads);

        mBookDao = new BookDao(TAG);

        mListAdapter = getListAdapter(mBookDao);

        mVb.itemList.setAdapter(mListAdapter);
        mVb.itemList.setOnItemClickListener((parent, v, position, id) -> onItemClick(
                mListAdapter.getTQItem(v.getContext(), position)));
    }

    /**
     * Build a context menu dialogue when an item is clicked.
     *
     * @param item which was clicked
     */
    private void onItemClick(@NonNull final TQItem item) {
        // If it owns a hint, display it first
        if (item instanceof TipManager.TipOwner) {
            //noinspection ConstantConditions
            TipManager.getInstance().display(getContext(),
                                             ((TipManager.TipOwner) item).getTip(), () ->
                                                     showContextDialog(item));
        } else {
            showContextDialog(item);
        }
    }

    private void showContextDialog(@NonNull final TQItem item) {
        final List<ContextDialogItem> menuItems = new ArrayList<>();
        // allow the parent Activity to add menu options
        addContextMenuItems(menuItems, item);
        // allow the selected item to add menu options
        //noinspection ConstantConditions
        item.addContextMenuItems(getContext(), menuItems, mBookDao);
        ContextDialogItem.showContextDialog(getContext(), menuItems);
    }

    protected void addContextMenuItems(@NonNull final List<ContextDialogItem> menuItems,
                                       @NonNull final TQItem item) {
        // nothing to add by default
    }

    /**
     * Refresh data; some other activity may have changed relevant data (e.g. a book)
     */
    @Override
    @CallSuper
    public void onResume() {
        super.onResume();
        refreshData();
    }

    void refreshData() {
        final Cursor cursor = mListAdapter.getCursor();
        if (cursor.requery()) {
//            Log.d(TAG,"requery called, count=" + cursor.getCount());
            mListAdapter.notifyDataSetChanged();
        }
    }

    @Override
    @CallSuper
    public void onDestroy() {
        if (mListAdapter.getCursor() != null) {
            mListAdapter.getCursor().close();
        }
        if (mBookDao != null) {
            mBookDao.close();
        }
        super.onDestroy();
    }
}
