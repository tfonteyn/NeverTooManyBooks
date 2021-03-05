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
package com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.admin;

import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.databinding.ActivityTaskQueueListBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.TQCursorAdapter;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.TQItem;

public abstract class BaseAdminActivity
        extends BaseActivity {

    /** Log tag. */
    private static final String TAG = "BaseAdminActivity";

    /** Database Access. */
    private BookDao mBookDao;

    /** The adapter for the list. */
    private TQCursorAdapter mListAdapter;

    /** Listener to handle changes made to the underlying cursor. */
    protected final QueueManager.OnChangeListener mOnChangeListener = this::refreshData;

    /**
     * Get a CursorAdapter returning the items we are interested in.
     *
     * @param bookDao Database Access
     *
     * @return CursorAdapter to use
     */
    @NonNull
    protected abstract TQCursorAdapter getListAdapter(@NonNull BookDao bookDao);

    protected ActivityTaskQueueListBinding mVb;

    @Override
    protected void onSetContentView() {
        mVb = ActivityTaskQueueListBinding.inflate(getLayoutInflater());
        setContentView(mVb.getRoot());
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        mBookDao = new BookDao(TAG);

        super.onCreate(savedInstanceState);

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
            TipManager.getInstance().display(this, ((TipManager.TipOwner) item).getTip(), () ->
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
        item.addContextMenuItems(this, menuItems, mBookDao);
        ContextDialogItem.showContextDialog(this, menuItems);
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
    protected void onResume() {
        super.onResume();
        refreshData();
    }

    protected void refreshData() {
        if (mListAdapter.getCursor().requery()) {
            mListAdapter.notifyDataSetChanged();
        }
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        if (mListAdapter.getCursor() != null) {
            mListAdapter.getCursor().close();
        }
        if (mBookDao != null) {
            mBookDao.close();
        }
        super.onDestroy();
    }
}
