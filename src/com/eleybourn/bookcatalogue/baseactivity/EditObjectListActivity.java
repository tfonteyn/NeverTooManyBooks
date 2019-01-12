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
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.utils.BundleUtils;
import com.eleybourn.bookcatalogue.widgets.TouchListView;

import java.util.ArrayList;

/**
 * ENHANCE: Ultimately, this should become a Fragment.
 *
 * Base class for editing a list of objects. The inheritor must specify a view id and a row view
 * id to the constructor of this class.
 *
 * This Activity uses {@link TouchListView} from CommonsWare which is in turn based on Android code
 * for TouchInterceptor which was (reputedly) removed in Android 2.2.
 *
 * Mandatory: {@link #createListAdapter}
 * needs to be implemented returning a suitable {@link SimpleListAdapter}
 * The method {@link SimpleListAdapter#onGetView} should be implemented.
 * Others are optional to override.
 *
 *
 * For this code to work, the main view must contain a {@link TouchListView}
 * <pre>
 *     id:
 *        android:id="@android:id/list"
 *     attributes:
 *        tlv:ic_grabber="@+id/<SOME ID FOR AN IMAGE>" (eg. "@+id/ic_grabber")
 *        tlv:remove_mode="none"
 *        tlv:normal_height="64dip" ---- or some similar value
 *  </pre>
 *
 * Main View buttons:
 * - R.id.cancel         calls {@link #onSave(Intent)}
 * - R.id.confirm        calls {@link #onCancel()}
 * - R.id.add (OPTIONAL) calls {@link #onAdd}
 *
 * Method {@link #onAdd} has an implementation that throws an {@link UnsupportedOperationException}
 * So if your list supports adding to the list, you must implement {@link #onAdd}.
 *
 * Moving an item in the list calls {@link #onListChanged()}
 *
 * Each row view must use id's as listed in {@link SimpleListAdapter} and in addition have
 * an {@link ImageView} with an ID of "@+id/<SOME ID FOR AN IMAGE>" matching the TLV one as above.
 *
 * @param <T> the object type as used in the List
 *
 * @author Philip Warner
 */
public abstract class EditObjectListActivity<T extends Parcelable>
        extends BaseListActivity
        implements TouchListView.OnDropListener {

    /** The key to use in the Bundle to get the array. */
    @Nullable
    private final String mBKey;
    /** The resource ID for the base view. */
    @LayoutRes
    private final int mBaseViewId;
    /** The resource ID for the row view. */
    @LayoutRes
    private final int mRowViewId;

    /**
     * Handle 'Cancel'.
     */
    private final OnClickListener mCancelListener = new OnClickListener() {
        @Override
        public void onClick(@NonNull final View v) {
            if (onCancel()) {
                finish();
            }
        }
    };
    /** the rows. */
    protected ArrayList<T> mList;
    /**
     * Handle 'Save'.
     *
     * TEST: setResult(Activity.RESULT_OK although we might not have made any.
     */
    private final OnClickListener mSaveListener = new OnClickListener() {
        @Override
        public void onClick(@NonNull final View v) {
            Intent data = new Intent();
            data.putExtra(mBKey, mList);
            if (onSave(data)) {
                finish();
            }
        }
    };
    protected SimpleListAdapter<T> mListAdapter;
    /**
     * Handle 'Add'.
     */
    private final OnClickListener mOnAddListener = new OnClickListener() {
        @Override
        public void onClick(@NonNull final View v) {
            onAdd(v);
            onListChanged();
        }
    };
    protected DBA mDb;
    @Nullable
    protected String mBookTitle;
    /** Row ID... mainly used (if list is from a book) to know if book is new. */
    protected long mRowId = 0;

    /**
     * Constructor.
     *
     * @param baseViewId Resource id of base view
     * @param rowViewId  Resource id of row view
     * @param bkey       The key to use in the Bundle to get the list
     */
    protected EditObjectListActivity(@LayoutRes final int baseViewId,
                                     @LayoutRes final int rowViewId,
                                     @Nullable final String bkey) {
        mBaseViewId = baseViewId;
        mRowViewId = rowViewId;
        mBKey = bkey;
    }

    @Override
    protected int getLayoutId() {
        return mBaseViewId;
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);

        mDb = new DBA(this);

        // see getList for full details as to where we "get" the list from
        mList = getList(mBKey, savedInstanceState);
        // setup the adapter
        mListAdapter = createListAdapter(mRowViewId, mList);
        setListAdapter(mListAdapter);

        // Look for id and title
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mRowId = extras.getLong(UniqueId.KEY_ID);
            mBookTitle = extras.getString(UniqueId.KEY_TITLE);
            setTextOrHideView(R.id.title, mBookTitle);
        }

        // Add handlers for 'Save', 'Cancel' and 'Add' (if resources are defined)
        setOnClickListener(R.id.confirm, mSaveListener);
        setOnClickListener(R.id.cancel, mCancelListener);
        setOnClickListener(R.id.add, mOnAddListener);

        // Add handler for 'onDrop' from the TouchListView
        ((TouchListView) getListView()).setOnDropListener(this);

        Tracker.exitOnCreate(this);
    }

    /**
     * try to load the list from:
     * 1. savedInstanceState ?
     * 2. intent extras ?
     * 3. getList() from child ?
     * 4. throw FATAL error
     */
    @NonNull
    private ArrayList<T> getList(@NonNull final String key,
                                 @Nullable final Bundle savedInstanceState) {
        ArrayList<T> list = null;

        if (key != null) {
            list = BundleUtils.getParcelableArrayList(key, savedInstanceState,
                                                      getIntent().getExtras());
        }
        if (list != null) {
            return list;
        }

        // no list yet ? Then ask the subclass to setup the list
        return getList();
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
        View listView = getListView().getChildAt(0);
        final int savedTop = listView != null ? listView.getTop() : 0;
        final int savedRow = getListView().getFirstVisiblePosition();

        mList = newList;
        mListAdapter = createListAdapter(mRowViewId, mList);
        setListAdapter(mListAdapter);

        getListView().post(new Runnable() {
            @Override
            public void run() {
                getListView().setSelectionFromTop(savedRow, savedTop);
            }
        });
    }

    /**
     * get the specific list adapter from the child class.
     */
    protected abstract SimpleListAdapter<T> createListAdapter(@LayoutRes int rowViewId,
                                                              @NonNull ArrayList<T> list);

    /**
     * Called when user clicks the 'Add' button (if present).
     *
     * @param target The view that was clicked ('add' button).
     */
    protected void onAdd(@NonNull final View target) {
        throw new UnsupportedOperationException("Must be overridden");
    }

    /**
     * Handle drop events; also preserves current position.
     */
    @Override
    @CallSuper
    public void onDrop(final int fromPosition,
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

        final ListView listView = getListView();
        final int firstVisiblePosition = listView.getFirstVisiblePosition();
        final int newFirst;
        if (toPosition > fromPosition && fromPosition < firstVisiblePosition) {
            newFirst = firstVisiblePosition - 1;
        } else {
            newFirst = firstVisiblePosition;
        }

        View firstView = listView.getChildAt(0);
        final int offset = firstView.getTop();

        // re-position the list
        listView.post(new Runnable() {
            @Override
            public void run() {
                listView.requestFocusFromTouch();
                listView.setSelectionFromTop(newFirst, offset);
                listView.post(new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; ; i++) {
                            View c = listView.getChildAt(i);
                            if (c == null) {
                                break;
                            }
                            if (listView.getPositionForView(c) == toPosition) {
                                listView.setSelectionFromTop(toPosition, c.getTop());
                                //c.requestFocusFromTouch();
                                break;
                            }
                        }
                    }
                });
            }
        });
    }

    /**
     * Called when user clicks the 'Save' button (if present). Primary task is
     * to return a boolean indicating it is OK to continue.
     *
     * Can be overridden to perform other checks.
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
     *
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
        TextView textView = this.findViewById(viewId);
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
        outState.putParcelableArrayList(mBKey, mList);

        super.onSaveInstanceState(outState);
    }

    /**
     * This is totally bizarre. Without this piece of code, under Android 1.6, the
     * native onRestoreInstanceState() fails to restore custom classes, throwing
     * a ClassNotFoundException, when the activity is resumed.
     * <p>
     * To test this, remove this line, edit a custom style, and save it. App will
     * crash in AVD under Android 1.6.
     * <p>
     * It is not entirely clear how this happens but since the Bundle has a classLoader
     * it is fair to surmise that the code that creates the bundle determines the class
     * loader to use based (somehow) on the class being called, and if we don't implement
     * this method, then in Android 1.6, the class is a basic android class NOT and app
     * class.
     */
    @Override
    @CallSuper
    public void onRestoreInstanceState(@Nullable final Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        Tracker.enterOnDestroy(this);
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
        Tracker.exitOnDestroy(this);
    }

}
