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
package com.hardbacknutter.nevertoomanybooks.goodreads.admin;

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
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TQCursorAdapter;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TQItem;

public abstract class BaseAdminActivity
        extends BaseActivity {

    /** Log tag. */
    private static final String TAG = "BaseAdminActivity";

    /** Database Access. */
    private DAO mDb;

    /** The adapter for the list. */
    private TQCursorAdapter mListAdapter;

    /** Listener to handle changes made to the underlying cursor. */
    protected final QueueManager.OnChangeListener mOnChangeListener = this::refreshData;

    /**
     * Get a CursorAdapter returning the items we are interested in.
     *
     * @param db Database Access
     *
     * @return CursorAdapter to use
     */
    @NonNull
    protected abstract TQCursorAdapter getListAdapter(@NonNull DAO db);

    @Override
    protected void onSetContentView() {
        setContentView(R.layout.activity_message_queue_list);
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        mDb = new DAO(TAG);
        super.onCreate(savedInstanceState);

        mListAdapter = getListAdapter(mDb);

        final ListView listView = findViewById(R.id.item_list);
        listView.setAdapter(mListAdapter);
        listView.setOnItemClickListener((parent, v, position, id) -> onItemClick(
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
            TipManager.display(this, ((TipManager.TipOwner) item).getTip(), () ->
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
        item.addContextMenuItems(this, menuItems, mDb);
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
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }
}
