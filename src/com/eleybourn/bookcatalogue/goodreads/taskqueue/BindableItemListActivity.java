/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * TaskQueue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TaskQueue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.goodreads.taskqueue;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.baseactivity.BaseListActivity;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.BindableItemCursorAdapter.BindableItemBinder;

/**
 * @author pjw
 */
abstract class BindableItemListActivity
        extends BaseListActivity
        implements BindableItemBinder {

    /** Cursor for list. */
    private BindableItemCursor mBindableItems;
    /** Adapter for list. */
    private BindableItemCursorAdapter mListAdapter;

    /**
     * Listener to handle add/change/delete.
     */
    protected final QueueManager.OnChangeListener mOnChangeListener = this::refreshData;

    /**
     * Subclass MUST implement to return the cursor that will be used to display items.
     * This is called from onCreate().
     *
     * @return Cursor to use
     */
    @NonNull
    protected abstract BindableItemCursor getBindableItemCursor();

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        mDb = new DBA(this);
        super.onCreate(savedInstanceState);

        mBindableItems = getBindableItemCursor();
        mListAdapter = new BindableItemCursorAdapter(this, this, mBindableItems);
        setListAdapter(mListAdapter);

        getListView().setOnItemClickListener(this::onListItemClick);
    }

    /**
     * Paranoid overestimate of the number of types we use.
     */
    @Override
    public int getBindableItemTypeCount() {
        return 50;
    }

    /**
     * Refresh data; some other activity may have changed relevant data (eg. a book)
     */
    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();
        refreshData();
    }

    private void refreshData() {
        closeCursor(mBindableItems);
        mBindableItems = getBindableItemCursor();
        mListAdapter.notifyDataSetChanged();
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        closeCursor(mBindableItems);
        super.onDestroy();
    }

    protected abstract void onListItemClick(@NonNull final AdapterView<?> parent,
                                            @NonNull final View v,
                                            final int position,
                                            final long id);

}
