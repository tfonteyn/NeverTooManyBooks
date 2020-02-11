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
package com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue;

import android.os.Bundle;
import android.widget.ListView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;

public abstract class BindableItemAdminActivity
        extends BaseActivity {

    /** Log tag. */
    private static final String TAG = "BindableItemAdminAct";

    /** Database Access. */
    private DAO mDb;

    /** The adapter for the list. */
    private BindableItemCursorAdapter mListAdapter;

    /** Listener to handle changes made to the underlying cursor. */
    protected final QueueManager.OnChangeListener mOnChangeListener = this::refreshData;

    @NonNull
    protected abstract BindableItemCursorAdapter getListAdapter(@NonNull DAO db);

    @Override
    protected int getLayoutId() {
        return R.layout.activity_message_queue_list;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        mDb = new DAO(TAG);
        super.onCreate(savedInstanceState);

        ListView listView = findViewById(android.R.id.list);

        mListAdapter = getListAdapter(mDb);

        listView.setAdapter(mListAdapter);
        listView.setOnItemClickListener(
                (parent, v, position, id) -> onItemClick(mListAdapter.getItem(position)
                                                                     .getBindableItem()));
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

    /**
     * Build a context menu dialogue when an item is clicked.
     *
     * @param item which was clicked
     */
    private void onItemClick(@NonNull final BindableItem item) {
        // If it owns a hint, display it first
        if (item instanceof TipManager.TipOwner) {
            TipManager.display(this, ((TipManager.TipOwner) item).getTip(), () ->
                    showContextDialog(item));
        } else {
            showContextDialog(item);
        }
    }

    private void showContextDialog(@NonNull final BindableItem item) {
        List<ContextDialogItem> menuItems = new ArrayList<>();
        // allow the parent Activity to add menu options
        addContextMenuItems(menuItems, item);
        // allow the selected item to add menu options
        item.addContextMenuItems(this, menuItems, mDb);
        ContextDialogItem.showContextDialog(this, menuItems);
    }

    protected void addContextMenuItems(@NonNull final List<ContextDialogItem> menuItems,
                                       @NonNull final BindableItem item) {
        // do nothing by default
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        if (mListAdapter.getCursor() != null) {
            mListAdapter.getCursor().close();
        }
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }
}
