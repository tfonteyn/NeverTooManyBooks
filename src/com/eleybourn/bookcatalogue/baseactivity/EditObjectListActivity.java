/*
 * @copyright 2011 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.baseactivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.widgets.RecyclerViewAdapterBase;
import com.eleybourn.bookcatalogue.widgets.ddsupport.OnStartDragListener;
import com.eleybourn.bookcatalogue.widgets.ddsupport.SimpleItemTouchHelperCallback;

/**
 * Base class for editing a list of objects.
 * <p>
 * {@link #createListAdapter} needs to be implemented returning a suitable RecyclerView adapter.
 * <p>
 * Main View buttons:
 * - R.id.confirm       calls {@link #onSave(Intent)}
 * - R.id.cancel        calls {@link #onCancel()}
 * - R.id.add           calls {@link #onAdd}
 * <p>
 * Method {@link #onAdd} has an implementation that throws an {@link UnsupportedOperationException}
 * So if your list supports adding to the list, you must implement {@link #onAdd}.
 *
 * @param <T> the object type as used in the List
 *
 * @author Philip Warner
 */
public abstract class EditObjectListActivity<T extends Parcelable>
        extends BaseActivity {

    /** The key to use in the Bundle to get the array. */
    @NonNull
    private final String mBKey;

    /** Database access. */
    protected DAO mDb;

    /** the rows. */
    protected ArrayList<T> mList;

    /** The adapter for the list. */
    protected RecyclerViewAdapterBase mListAdapter;

    /** The View for the list. */
    protected RecyclerView mListView;

    protected LinearLayoutManager mLayoutManager;

    @Nullable
    protected String mBookTitle;
    /** Row ID... mainly used (if list is from a book) to know if the object is new. */
    protected long mRowId = 0;

    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;

    /**
     * Constructor.
     *
     * @param bkey The key to use in the Bundle to get the list
     */
    protected EditObjectListActivity(@NonNull final String bkey) {
        mBKey = bkey;
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO();

        // Look for id and title
        Bundle args = savedInstanceState != null ? savedInstanceState : getIntent().getExtras();
        if (args != null) {
            mRowId = args.getLong(DBDefinitions.KEY_ID);
            mBookTitle = args.getString(DBDefinitions.KEY_TITLE);

            mList = args.getParcelableArrayList(mBKey);
        }

        mListView = findViewById(android.R.id.list);
        mLayoutManager = new LinearLayoutManager(this);
        mListView.setLayoutManager(mLayoutManager);
        mListView.setHasFixedSize(true);

        // setup the adapter
        mListAdapter = createListAdapter(mList,
                                         (viewHolder) -> mItemTouchHelper.startDrag(viewHolder));
        mListView.setAdapter(mListAdapter);

        SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mListView);

        setTextOrHideView(R.id.title, mBookTitle);

        // Add handlers for 'Save', 'Cancel' and 'Add' (if resources are defined)
        setOnClickListener(R.id.confirm, v -> {
            Intent data = new Intent().putExtra(mBKey, mList);
            if (onSave(data)) {
                finish();
            }
        });
        setOnClickListener(R.id.cancel, v -> {
            if (onCancel()) {
                finish();
            }
        });
        setOnClickListener(R.id.add, this::onAdd);
    }

    /**
     * get the specific list adapter from the child class.
     */
    protected abstract RecyclerViewAdapterBase
    createListAdapter(@NonNull ArrayList<T> list,
                      @NonNull OnStartDragListener dragStartListener);

    /**
     * Called when user clicks the 'Add' button (if present).
     *
     * @param target The view that was clicked ('add' button).
     */
    protected abstract void onAdd(@NonNull final View target);

    /**
     * Called when user clicks the 'Save' button (if present). Primary task is
     * to return a boolean indicating it is OK to continue.
     * <p>
     * Can be overridden to perform other checks.
     * <p>
     * IMPORTANT: Individual items on the list might have been saved to the database
     * depending on the child class needs.
     * The list itself is (normally) NOT SAVED -> we only return it in the result.
     *
     * @param data A newly created Intent to store output if necessary.
     *             Comes pre-populated with data.putExtra(mBKey, mList);
     *
     * @return {@code true} if activity should exit, {@code false} to abort exit.
     */
    protected boolean onSave(@NonNull final Intent data) {
        setResult(Activity.RESULT_OK, data);
        return true;
    }

    /**
     * Called when user presses 'Cancel' button if present. Primary task is
     * return a boolean indicating it is OK to continue.
     * <p>
     * Can be overridden to perform other checks.
     *
     * @return {@code true} if activity should exit, {@code false} to abort exit.
     */
    @SuppressWarnings("SameReturnValue")
    protected boolean onCancel() {
        setResult(Activity.RESULT_CANCELED);
        return true;
    }

    /**
     * Setup a listener for the specified view id if such id exist.
     *
     * @param viewId   Resource ID
     * @param listener Listener
     */
    private void setOnClickListener(@IdRes final int viewId,
                                    @NonNull final OnClickListener listener) {
        View view = findViewById(viewId);
        if (view != null) {
            view.setOnClickListener(listener);
        }
    }

    /**
     * Set a TextView to a string, or hide it.
     *
     * @param viewId View ID
     * @param value  String to set
     */
    protected void setTextOrHideView(@SuppressWarnings("SameParameterValue")
                                     @IdRes final int viewId,
                                     @Nullable final String value) {
        TextView textView = findViewById(viewId);
        if (textView == null) {
            return;
        }

        if (value == null || value.isEmpty()) {
            textView.setVisibility(View.GONE);
        } else {
            textView.setText(value);
        }
    }

    /**
     * Ensure that the list is saved.
     */
    @Override
    @CallSuper
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelableArrayList(mBKey, mList);
        outState.putLong(DBDefinitions.KEY_ID, mRowId);
        outState.putString(DBDefinitions.KEY_TITLE, mBookTitle);
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }
}
