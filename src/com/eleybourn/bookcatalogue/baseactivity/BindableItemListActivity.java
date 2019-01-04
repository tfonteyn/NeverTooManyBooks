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
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;

import com.eleybourn.bookcatalogue.adapters.BindableItemCursorAdapter;
import com.eleybourn.bookcatalogue.adapters.BindableItemCursorAdapter.BindableItemBinder;
import com.eleybourn.bookcatalogue.database.cursors.BindableItemCursor;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.ContextDialogItem;

import java.util.List;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

/**
 * @author pjw
 */
public abstract class BindableItemListActivity extends BaseListActivity implements BindableItemBinder {

    /** Cursor of book IDs */
    private BindableItemCursor mBindableItems;
    /** Adapter for list */
    private BindableItemCursorAdapter mListAdapter;

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
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);

        mBindableItems = getBindableItemCursor(savedInstanceState);

        // Set up list handling
        mListAdapter = new BindableItemCursorAdapter(this, this, mBindableItems);

        setListAdapter(mListAdapter);

        ListView lv = getListView();

        lv.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(@NonNull final AdapterView<?> parent, @NonNull final View view, final int position, final long id) {
                BindableItemListActivity.this.onListItemClick(parent, view, position, id);
            }
        });
        lv.setOnItemLongClickListener(new OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(@NonNull final AdapterView<?> parent, @NonNull final View view, final int position, final long id) {
                return BindableItemListActivity.this.onListItemLongClick(parent, view, position, id);
            }
        });
        Tracker.exitOnCreate(this);
    }

    protected void refreshData() {
        mBindableItems.requery();
        mListAdapter.notifyDataSetChanged();
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        Tracker.enterOnDestroy(this);
        try {
            if (mBindableItems != null) {
                mBindableItems.close();
            }
        } catch (RuntimeException ignore) {
        }
        super.onDestroy();
        Tracker.exitOnDestroy(this);
    }

    protected void onListItemClick(@NonNull final AdapterView<?> parent, @NonNull final View v, final int position, final long id) {
    }

    @SuppressWarnings("SameReturnValue")
    @CallSuper
    protected boolean onListItemLongClick(@NonNull final AdapterView<?> parent,
                                          @NonNull final View v,
                                          final int position,
                                          final long id) {
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
            final ContextDialogItem[] itemArray = new ContextDialogItem[items.size()];
            items.toArray(itemArray);

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setItems(itemArray, new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int which) {
                            itemArray[which].handler.run();
                        }
                    }).create();

            dialog.show();
        }
    }


}
