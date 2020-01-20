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
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.BindableItemCursorAdapter.BindableItemBinder;

abstract class BindableItemListActivity
        extends BaseActivity
        implements BindableItemBinder {

    private static final String TAG = "BindableItemListAct";

    /** Database Access. */
    DAO mDb;

    /** Cursor for list. */
    private BindableItemCursor mBindableItems;
    /** The adapter for the list. */
    private BindableItemCursorAdapter mListAdapter;
    /**
     * Listener to handle add/change/delete.
     */
    final QueueManager.ChangeListener mChangeListener = this::refreshData;

    /**
     * Subclass MUST implement to return the cursor that will be used to display items.
     * This is called from onCreate().
     *
     * @return Cursor to use
     */
    @NonNull
    protected abstract BindableItemCursor getBindableItemCursor();


    @Override
    protected int getLayoutId() {
        return R.layout.activity_message_queue_list;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        mDb = new DAO(TAG);
        super.onCreate(savedInstanceState);

        // The View for the list.
        ListView listView = findViewById(android.R.id.list);

        mBindableItems = getBindableItemCursor();
        mListAdapter = new BindableItemCursorAdapter(this, this, mBindableItems);
        listView.setAdapter(mListAdapter);

        listView.setOnItemClickListener(this::onListItemClick);
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

    /**
     * Paranoid overestimate of the number of types we use.
     */
    @Override
    public int getBindableItemTypeCount() {
        return 50;
    }

    private void refreshData() {
        if (mBindableItems != null) {
            mBindableItems.close();
        }
        mBindableItems = getBindableItemCursor();
        mListAdapter.notifyDataSetChanged();
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        if (mBindableItems != null) {
            mBindableItems.close();
        }
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }

    protected abstract void onListItemClick(@NonNull AdapterView<?> parent,
                                            @NonNull View v,
                                            int position,
                                            long id);
}
