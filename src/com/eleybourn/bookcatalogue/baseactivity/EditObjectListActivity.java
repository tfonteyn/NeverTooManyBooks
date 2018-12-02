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
import android.support.annotation.CallSuper;
import android.support.annotation.IdRes;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.adapters.SimpleListAdapterRowActionListener;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.utils.BundleUtils;
import com.eleybourn.bookcatalogue.widgets.TouchListView;
import com.eleybourn.bookcatalogue.widgets.TouchListViewWithDropListener;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Base class for editing a list of objects. The inheritor must specify a view id and a row view
 * id to the constructor of this class.
 *
 * This Activity uses {@link TouchListViewWithDropListener} extended from {@link TouchListView}
 * from CommonsWare which is in turn based on Android code
 * for TouchInterceptor which was (reputedly) removed in Android 2.2.
 *
 * {@link #createListAdapter} needs to be implemented returning a suitable {@link SimpleListAdapter}
 * which implements {@link SimpleListAdapterRowActionListener}.
 * The method {@link SimpleListAdapterRowActionListener<T>#onGetView} should be implemented.
 * Others are optional to override.
 *
 * Method {@link #onAdd} has an implementation that throws an {@link UnsupportedOperationException}
 * So if your list supports adding to, you must implement {@link #onAdd}.
 *
 * For this code to work, the main view must contain a {@link TouchListViewWithDropListener}
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
 * - R.id.cancel
 * - R.id.confirm
 * - R.id.add (OPTIONAL) see above for {@link #onAdd}
 *
 * Each row view must use id's as listed in {@link SimpleListAdapter} and in addition have
 * an {@link ImageView} with an ID of "@+id/<SOME ID FOR AN IMAGE>" matching the TLV one as above.
 *
 * @param <T> the object type as used in the List
 *
 * @author Philip Warner
 */
abstract public class EditObjectListActivity<T extends Parcelable> extends BaseListActivity {

    /** The key to use in the Bundle to get the array */
    @Nullable
    private final String mBKey;
    /** The resource ID for the base view */
    @LayoutRes
    private final int mBaseViewId;
    /** The resource ID for the row view */
    @LayoutRes
    private final int mRowViewId;
    /**
     * Handle 'Cancel'
     */
    private final OnClickListener mCancelListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (onCancel()) {
                finish();
            }
        }
    };
    /** the rows */
    protected ArrayList<T> mList = null;

    /**
     * Handle 'Save'
     *
     * TEST: setResult(Activity.RESULT_OK although we might not have made any.
     */
    private final OnClickListener mSaveListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent data = new Intent();
            data.putExtra(mBKey, mList);
            if (onSave(data)) {
                finish();
            }
        }
    };
    protected SimpleListAdapter<T> mListAdapter;
    /**
     * Handle 'Add'
     */
    private final OnClickListener mAddListener = new OnClickListener() {
        @Override
        public void onClick(@NonNull View v) {
            onAdd(v);
            mListAdapter.onListChanged();
        }
    };
    protected CatalogueDBAdapter mDb;
    @Nullable
    protected String mBookTitle;
    /** Row ID... mainly used (if list is from a book) to know if book is new. */
    protected long mRowId = 0;

    /**
     * Constructor
     *
     * @param baseViewId Resource id of base view
     * @param rowViewId  Resource id of row view
     * @param bkey       The key to use in the Bundle to get the list
     */
    protected EditObjectListActivity(final @LayoutRes int baseViewId,
                                     final @LayoutRes int rowViewId,
                                     final @Nullable String bkey) {
        mBKey = bkey;
        mBaseViewId = baseViewId;
        mRowViewId = rowViewId;
    }

    @Override
    protected int getLayoutId() {
        return mBaseViewId;
    }

    @Override
    @CallSuper
    protected void onCreate(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);

        mDb = new CatalogueDBAdapter(this);

        // see getList for full details as to where we "get" the list from
        mList = getList(mBKey, savedInstanceState);
        initListAdapter(mList);

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
        setOnClickListener(R.id.add, mAddListener);

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
    private ArrayList<T> getList(String key, final @Nullable Bundle savedInstanceState) {
        ArrayList<T> list = null;

        // we need the ArrayList before building the adapter.
        if (key != null) {
            list = BundleUtils.getParcelableArrayList(key, savedInstanceState, getIntent().getExtras());
        }
        // nothing ? Then ask the subclass to setup the list
        if (list == null) {
            list = getList();
        }
        // give up if still null
        Objects.requireNonNull(list, "Unable to find list for key '" + key + "'");

        return list;
    }

    /**
     * Called to get the list if it was not in the intent.
     * Override to make it do something
     */
    @Nullable
    protected ArrayList<T> getList() {
        return null;
    }

    /**
     * Replace the current list
     */
    protected void setList(final @NonNull ArrayList<T> newList) {
        View listView = this.getListView().getChildAt(0);
        final int savedTop = listView == null ? 0 : listView.getTop();
        final int savedRow = this.getListView().getFirstVisiblePosition();

        mList = newList;
        initListAdapter(mList);

        getListView().post(new Runnable() {
            @Override
            public void run() {
                getListView().setSelectionFromTop(savedRow, savedTop);
            }
        });
    }

    /**
     * Set up list handling
     */
    private void initListAdapter(ArrayList<T> list) {
        this.mListAdapter = createListAdapter(mRowViewId, list);
        setListAdapter(this.mListAdapter);
    }

    /**
     * get the specific list adapter from the child class
     */
    abstract protected SimpleListAdapter<T> createListAdapter(final @LayoutRes int rowViewId, final @NonNull ArrayList<T> list);


    /**
     * Called when user clicks the 'Add' button (if present).
     *
     * @param target The view that was clicked ('add' button).
     */
    protected void onAdd(final @NonNull View target) {
        throw new UnsupportedOperationException("Must be overridden");
    }

    /**
     * Called when user clicks the 'Save' button (if present). Primary task is
     * to return a boolean indicating it is OK to continue.
     *
     * Can be overridden to perform other checks.
     *
     * @param data A newly created Intent to store output if necessary.
     *            Comes pre-populated with data.putExtra(mBKey, mList);
     *
     * @return <tt>true</tt>if activity should exit, false to abort exit.
     */
    protected boolean onSave(final @NonNull Intent data) {
        setResult(Activity.RESULT_OK, data); /* bca659b6-dfb9-4a97-b651-5b05ad102400,
                 dd74343a-50ff-4ce9-a2e4-a75f7bcf9e36, 3f210502-91ab-4b11-b165-605e09bb0c17
                 13854efe-e8fd-447a-a195-47678c0d87e7 */

        return true;
    }

    /**
     * Called when user presses 'Cancel' button if present. Primary task is
     * return a boolean indicating it is OK to continue.
     * <p>
     * Can be overridden to perform other checks.
     *
     * @return <tt>true</tt>if activity should exit, false to abort exit.
     */
    @SuppressWarnings("SameReturnValue")
    protected boolean onCancel() {
        setResult(Activity.RESULT_CANCELED); /* bca659b6-dfb9-4a97-b651-5b05ad102400,
                dd74343a-50ff-4ce9-a2e4-a75f7bcf9e36, 3f210502-91ab-4b11-b165-605e09bb0c17,
                13854efe-e8fd-447a-a195-47678c0d87e7 */
        return true;
    }

    /**
     * Utility routine to setup a listener for the specified view id
     *
     * @param viewId   Resource ID
     * @param listener Listener
     */
    private void setOnClickListener(final @IdRes int viewId, final @NonNull OnClickListener listener) {
        View view = this.findViewById(viewId);
        if (view != null) {
            view.setOnClickListener(listener);
        }
    }

    /**
     * Utility routine to set a TextView to a string, or hide it.
     *
     * @param viewId View ID
     * @param value  String to set
     */
    protected void setTextOrHideView(@SuppressWarnings("SameParameterValue") final @IdRes int viewId, final @Nullable String value) {
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
    protected void onSaveInstanceState(final @NonNull Bundle outState) {
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
    public void onRestoreInstanceState(final Bundle state) {
        super.onRestoreInstanceState(state);
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
