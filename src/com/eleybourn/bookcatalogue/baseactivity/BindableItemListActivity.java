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

package com.eleybourn.bookcatalogue.baseactivity;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;

import com.eleybourn.bookcatalogue.dialogs.ContextDialogItem;
import com.eleybourn.bookcatalogue.taskqueue.BindableItemCursor;
import com.eleybourn.bookcatalogue.adapters.BindableItemCursorAdapter;
import com.eleybourn.bookcatalogue.adapters.BindableItemCursorAdapter.BindableItemBinder;

import java.util.List;

/**
 * @author pjw
 */
abstract public class BindableItemListActivity extends BaseListActivity implements BindableItemBinder {
    /** The resource ID for the base view */
    private final int mBaseViewId;
    /** Cursor of book IDs */
    private BindableItemCursor mBindableItems;
    /** Adapter for list */
    private BindableItemCursorAdapter mListAdapter;

    /**
     * Constructor; this will be called by the subclass to set the resource IDs.
     *
     * @param baseViewId Resource id of base view
     */
    public BindableItemListActivity(final int baseViewId) {
        mBaseViewId = baseViewId;
    }

    /**
     * Subclass MUST implement to return the cursor that will be used to select TaskNotes to
     * display. This is called from onCreate().
     *
     * @param savedInstanceState state info passed to onCreate()
     *
     * @return TaskNotesCursor to use
     */
    @NonNull
    protected abstract BindableItemCursor getBindableItemCursor(@Nullable final Bundle savedInstanceState);

    @Override
    protected int getLayoutId() {
        return mBaseViewId;
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBindableItems = getBindableItemCursor(savedInstanceState);

        // Set up list handling
        mListAdapter = new BindableItemCursorAdapter(this, this, mBindableItems);

        setListAdapter(mListAdapter);

        ListView lv = this.findViewById(android.R.id.list);

        lv.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(@NonNull final AdapterView<?> parent, @NonNull final View v, final int position, final long id) {
                BindableItemListActivity.this.onListItemClick(parent, v, position, id);
            }
        });
        lv.setOnItemLongClickListener(new OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(@NonNull final AdapterView<?> parent, @NonNull final View v, final int position, final long id) {
                return BindableItemListActivity.this.onListItemLongClick(parent, v, position, id);
            }
        });
    }

    protected void refreshData() {
        mBindableItems.requery();
        mListAdapter.notifyDataSetChanged();
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (mBindableItems != null) {
                mBindableItems.close();
                mBindableItems = null;
            }
        } catch (Exception ignore) {
        }
    }

    protected void onListItemClick(@NonNull final AdapterView<?> parent, @NonNull final View v, final int position, final long id) {
    }

    @SuppressWarnings("SameReturnValue")
    @CallSuper
    private boolean onListItemLongClick(@SuppressWarnings("unused") @NonNull final AdapterView<?> parent,
                                        @SuppressWarnings("unused") @NonNull final View v,
                                        @SuppressWarnings("unused") final int position,
                                        @SuppressWarnings("unused") final long id) {
        return false;
    }

    /**
     * Utility routine to display an array of ContextDialogItems in an alert.
     *
     * @param title Title of Alert
     * @param items Items to display
     */
    protected void showContextDialogue(@SuppressWarnings("SameParameterValue") @StringRes final int title,
                                       @NonNull final List<ContextDialogItem> items) {
        if (items.size() > 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setIconAttribute(android.R.attr.alertDialogIcon);

            final ContextDialogItem[] itemArray = new ContextDialogItem[items.size()];
            items.toArray(itemArray);

            builder.setItems(itemArray, new DialogInterface.OnClickListener() {
                public void onClick(final DialogInterface dialog, final int item) {
                    itemArray[item].handler.run();
                }
            });
            builder.create().show();
        }
    }


}
