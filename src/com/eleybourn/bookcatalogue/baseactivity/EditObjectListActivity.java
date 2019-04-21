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
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.EditBookTocFragment;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.widgets.TouchListView;

/**
 * ENHANCE: Ultimately, this should become a Fragment.
 * <p>
 * Base class for editing a list of objects. The inheritor must specify a view id and a row view
 * id to the constructor of this class.
 * <p>
 * This Activity uses {@link TouchListView} from CommonsWare which is in turn based on Android code
 * for TouchInterceptor which was (reputedly) removed in Android 2.2.
 * <p>
 * Mandatory: {@link #createListAdapter}
 * needs to be implemented returning a suitable {@link ArrayAdapter}
 * <p>
 * <p>
 * For this code to work, the main view must contain a {@link TouchListView}
 * <pre>
 *     id:
 *        android:id="@android:id/list"
 *     attributes:
 *        tlv:grabber="@+id/<SOME ID FOR AN IMAGE>" (eg. "@id/TLV_ROW_GRABBER")
 *  </pre>
 * <p>
 * Main View buttons:
 * - R.id.cancel         calls {@link #onSave(Intent)}
 * - R.id.confirm        calls {@link #onCancel()}
 * - R.id.add (OPTIONAL) calls {@link #onAdd}
 * <p>
 * Method {@link #onAdd} has an implementation that throws an {@link UnsupportedOperationException}
 * So if your list supports adding to the list, you must implement {@link #onAdd}.
 * <p>
 * Moving an item in the list calls {@link #onListChanged()}
 * <p>
 * Each row view must use id's as listed in {@link SimpleListAdapter} and in addition have
 * an {@link ImageView} with an ID of "@+id/<SOME ID FOR AN IMAGE>" matching the TLV one as above.
 *
 * @param <T> the object type as used in the List
 *
 * @author Philip Warner
 */
public abstract class EditObjectListActivity<T extends Parcelable>
        extends BaseActivity {

    /** tag. */
    private static final String TAG = EditObjectListActivity.class.getSimpleName();

    /** if there was no key passed in, use this one for the savedInstance and return value */
    private static final String BKEY_LIST = TAG + ":tmpList";

    /** The key to use in the Bundle to get the array. */
    @Nullable
    private final String mBKey;

    protected DBA mDb;

    /** the rows. */
    protected ArrayList<T> mList;
    /**
     * Handle 'Save'.
     * <p>
     * TEST: setResult(Activity.RESULT_OK although we might not have made any.
     */
    private final OnClickListener mSaveListener = new OnClickListener() {
        @Override
        public void onClick(@NonNull final View v) {
            Intent data = new Intent().putExtra(mBKey != null ? mBKey : BKEY_LIST, mList);
            if (onSave(data)) {
                finish();
            }
        }
    };
    /** The adapter for the list. */
    protected ArrayAdapter<T> mListAdapter;
    @Nullable
    protected String mBookTitle;
    /** Row ID... mainly used (if list is from a book) to know if the object is new. */
    protected long mRowId = 0;
    /** The View for the list. */
    protected TouchListView mListView;

    /**
     * Constructor.
     *
     * @param bkey The key to use in the Bundle to get the list
     */
    protected EditObjectListActivity(@Nullable final String bkey) {
        mBKey = bkey;
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DBA(this);

        // Look for id and title
        Bundle args = savedInstanceState != null ? savedInstanceState : getIntent().getExtras();
        if (args != null) {
            mRowId = args.getLong(DBDefinitions.KEY_ID);
            mBookTitle = args.getString(DBDefinitions.KEY_TITLE);
        }

        // see getList for full details as to where we "get" the list from
        mList = getList(savedInstanceState);

        mListView = findViewById(android.R.id.list);
        View emptyView = findViewById(android.R.id.empty);
        if (emptyView != null) {
            mListView.setEmptyView(emptyView);
        }

        // setup the adapter
        mListAdapter = createListAdapter(mList);
        mListView.setAdapter(mListAdapter);

        setTextOrHideView(R.id.title, mBookTitle);

        // Add handlers for 'Save', 'Cancel' and 'Add' (if resources are defined)
        setOnClickListener(R.id.confirm, mSaveListener);
        setOnClickListener(R.id.cancel, v -> {
            if (onCancel()) {
                finish();
            }
        });
        setOnClickListener(R.id.add, v -> {
            onAdd(v);
            onListChanged();
        });

        // Handle drop events; also preserves current position.
        mListView.setOnDropListener(this::onDrop);
    }

    /**
     * TOMF: code nearly identical with {@link EditBookTocFragment} #onDrop
     *
     * @param fromPosition original position of the row
     * @param toPosition   where the row was dropped
     */
    private void onDrop(final int fromPosition,
                        final int toPosition) {
        // Check if nothing to do; also avoids the nasty case where list size == 1
        if (fromPosition == toPosition) {
            return;
        }

        // update the list
        T item = mListAdapter.getItem(fromPosition);
        mListAdapter.remove(item);
        mListAdapter.insert(item, toPosition);
        onListChanged();

        final int firstVisiblePosition = mListView.getFirstVisiblePosition();
        final int newFirst;
        if (toPosition > fromPosition && fromPosition < firstVisiblePosition) {
            newFirst = firstVisiblePosition - 1;
        } else {
            newFirst = firstVisiblePosition;
        }

        View firstView = mListView.getChildAt(0);
        final int offset = firstView.getTop();

        // re-position the list
        mListView.post(() -> {
            mListView.requestFocusFromTouch();
            mListView.setSelectionFromTop(newFirst, offset);
            mListView.post(() -> {
                for (int i = 0; ; i++) {
                    View c = mListView.getChildAt(i);
                    if (c == null) {
                        break;
                    }
                    if (mListView.getPositionForView(c) == toPosition) {
                        mListView.setSelectionFromTop(toPosition, c.getTop());
                        //c.requestFocusFromTouch();
                        break;
                    }
                }
            });
        });
    }

    /**
     * try to load the list from:
     * 1. savedInstanceState using the key
     * 2. intent extras using the key
     *
     * 3. getList() from child ?
     *
     * 4. throw FATAL error in the default {@link #getList()} method
     */
    @NonNull
    private ArrayList<T> getList(@Nullable final Bundle savedInstanceState) {
        ArrayList<T> list = null;

        String key = mBKey != null ? mBKey : BKEY_LIST;
        if (savedInstanceState != null) {
            list = savedInstanceState.getParcelableArrayList(key);
        }

        if (list == null) {
            list = getIntent().getParcelableArrayListExtra(key);
        }
        // no list yet ? Then ask the subclass to setup the list
        return list != null ? list : getList();
    }

    /**
     * Called to get the list if it was not in the intent.
     * Override to make it do something
     */
    @NonNull
    protected ArrayList<T> getList() {
        throw new IllegalStateException();
    }

    /**
     * Replace the current list.
     */
    protected void setList(@NonNull final ArrayList<T> newList) {
        View view = mListView.getChildAt(0);
        final int savedTop = view != null ? view.getTop() : 0;
        final int savedRow = mListView.getFirstVisiblePosition();

        mList = newList;
        mListAdapter = createListAdapter(mList);
        mListView.setAdapter(mListAdapter);

        mListView.post(() -> mListView.setSelectionFromTop(savedRow, savedTop));
    }

    /**
     * get the specific list adapter from the child class.
     */
    protected abstract ArrayAdapter<T> createListAdapter(@NonNull ArrayList<T> list);

    /**
     * Called when user clicks the 'Add' button (if present).
     *
     * @param target The view that was clicked ('add' button).
     */
    protected void onAdd(@NonNull final View target) {
        throw new UnsupportedOperationException("Must be overridden");
    }

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
     * @return <tt>true</tt> if activity should exit, <tt>false</tt> to abort exit.
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
     * @return <tt>true</tt> if activity should exit, <tt>false</tt> to abort exit.
     */
    @SuppressWarnings("SameReturnValue")
    protected boolean onCancel() {
        setResult(Activity.RESULT_CANCELED);
        return true;
    }

    /**
     * Called when the list had been modified in some way.
     * By default, tells the adapter that the list was changed
     * <p>
     * Child classes should override when needed and call super FIRST
     */
    @CallSuper
    protected void onListChanged() {
        mListAdapter.notifyDataSetChanged();
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

        outState.putParcelableArrayList(mBKey != null ? mBKey : BKEY_LIST, mList);
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
