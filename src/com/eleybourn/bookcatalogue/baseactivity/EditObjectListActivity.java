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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.widgets.TouchListView;
import com.eleybourn.bookcatalogue.widgets.TouchListViewWithDropListener;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Base class for editing a list of objects. The inheritor must specify a view id and a row view
 * id to the constructor of this class. Each view can have the following sub-view IDs present
 * which will be automatically handled. Optional IDs are noted:
 *
 * Main View:
 * - cancel
 * - confirm
 * - add (OPTIONAL)
 *
 * Row View must have layout ID set to "@id/ROW" (defined in ids.xml)
 *
 * The row view is tagged using TAG_POSITION,, to save the rows position for
 * use when moving the row up/down or deleting it.
 *
 * Abstract methods are defined for specific tasks (Add, Save, Load etc). While would
 * be tempting to add local implementations the java generic model seems to prevent this.
 *
 * This Activity uses {@link TouchListViewWithDropListener} extended from {@link TouchListView}
 * from CommonsWare which is in turn based on Android code
 * for TouchInterceptor which was (reputedly) removed in Android 2.2.
 *
 * For this code to work, the  main view must contain:
 *
 * - a {@link TouchListViewWithDropListener} with id = @android:id/list
 * - the {@link TouchListViewWithDropListener} must have the following attributes:
 * tlv:ic_grabber="@+id/<SOME ID FOR AN IMAGE>" (eg. "@+id/ic_grabber")
 * tlv:remove_mode="none"
 * tlv:normal_height="64dip" ---- or some similar value
 *
 * Each row view must have:
 *
 * - an ID of @id/ROW
 * - an {@link ImageView} with an ID of "@+id/<SOME ID FOR AN IMAGE>" (eg. "@+id/ic_grabber")
 * - (OPTIONAL) a subview with an ID of "@+d/ROW_DETAILS"; when clicked, this will result
 * in the {@link #onRowClick} event. If not present, then the {@link #onRowClick} is set on the "@id/ROW"
 *
 * @param <T>
 *
 * @author Philip Warner
 */
abstract public class EditObjectListActivity<T extends Serializable> extends BaseListActivity {

    /** The key to use in the Bundle to get the array */
    @Nullable
    private final String mBKey;
    /** The resource ID for the base view */
    private final int mBaseViewId;
    /** The resource ID for the row view */
    private final int mRowViewId;
    /** the rows */
    protected ArrayList<T> mList = null;
    protected EditObjectListAdapter mAdapter;
    protected CatalogueDBAdapter mDb;
    @Nullable
    protected String mBookTitle;
    /** Row ID... mainly used (if list is from a book) to know if book is new. */
    protected long mRowId = 0;

    /**
     * Handle 'Cancel'
     */
    private final OnClickListener mCancelListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (onCancel())
                setResult(RESULT_CANCELED);
                finish();
        }
    };

    /**
     * Handle 'Add'
     */
    private final OnClickListener mAddListener = new OnClickListener() {
        @Override
        public void onClick(@NonNull View v) {
            onAdd(v);
            onListChanged();
        }
    };
    /**
     * Handle 'Save'
     */
    private final OnClickListener mSaveListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent();
            intent.putExtra(mBKey, mList);
            if (onSave(intent)) {
                setResult(RESULT_OK, intent);
                finish();
            }
        }
    };

    /**
     * Constructor
     *
     * @param bkey       The key to use in the Bundle to get the array
     * @param baseViewId Resource id of base view
     * @param rowViewId  Resource id of row view
     */
    protected EditObjectListActivity(@Nullable final String bkey, final int baseViewId, final int rowViewId) {
        mBKey = bkey;
        mBaseViewId = baseViewId;
        mRowViewId = rowViewId;
    }

    /**
     * Called when user clicks the 'Add' button (if present).
     *
     * @param target The view that was clicked ('add' button).
     */
    abstract protected void onAdd(@NonNull final View target);

    /**
     * Call to set up the row view.
     *
     * @param target The target row view object
     * @param object The object (or type T) from which to draw values.
     */
    abstract protected void onSetupView(@NonNull final View target, @NonNull final T object);

    protected void onListChanged() {
    }

    /**
     * Called when an otherwise inactive part of the row is clicked.
     * Optional to implement, by default does nothing.
     *
     * @param target The view clicked
     * @param object The object associated with this row
     */
    protected void onRowClick(@NonNull final View target, @NonNull final T object, final int position) {
    }

    /**
     * Called when an otherwise inactive part of the row is long clicked.
     *
     * @param target The view clicked
     * @param object The object associated with this row
     *
     * @return <tt>true</tt>if handled
     */
    @SuppressWarnings({"unused", "SameReturnValue"})
    @CallSuper
    protected boolean onRowLongClick(@NonNull final View target, @NonNull final T object, final int position) {
        return true;
    }
    /**
     *
     * @return  true if delete is allowed to happen
     */
    @SuppressWarnings({"unused", "SameReturnValue"})
    @CallSuper
    protected boolean onRowDelete(@NonNull final View target, @NonNull final T object, final int position) {
        return true;
    }

    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void onRowDown(@NonNull final View target, @NonNull final T object, final int position) {
    }

    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void onRowUp(@NonNull final View target, @NonNull final T object, final int position) {
    }
    /**
     * Called when user clicks the 'Save' button (if present). Primary task is
     * to return a boolean indicating it is OK to continue.
     * <p>
     * Can be overridden to perform other checks.
     *
     * @param intent A newly created Intent to store output if necessary.
     *
     * @return <tt>true</tt>if activity should exit, false to abort exit.
     */
    protected boolean onSave(@NonNull final Intent intent) {
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
        return true;
    }

    /**
     * Called to get the list if it was not in the intent.
     */
    @Nullable
    protected ArrayList<T> getList() {
        return null;
    }

    /**
     * Replace the current list
     */
    protected void setList(ArrayList<T> newList) {
        View listView = this.getListView().getChildAt(0);
        final int savedTop = listView == null ? 0 : listView.getTop();
        final int savedRow = this.getListView().getFirstVisiblePosition();

        this.mList = newList;
        // Set up list handling
        this.mAdapter = new EditObjectListAdapter(this, mRowViewId, mList);
        setListAdapter(this.mAdapter);

        getListView().post(new Runnable() {
            @Override
            public void run() {
                getListView().setSelectionFromTop(savedRow, savedTop);
            }
        });
    }

    @Override
    protected int getLayoutId() {
        return mBaseViewId;
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            // Setup the DB
            mDb = new CatalogueDBAdapter(this);
            mDb.open();

            // Add handlers for 'Save', 'Cancel' and 'Add' (if resources are defined)
            setOnClickListener(R.id.confirm, mSaveListener);
            setOnClickListener(R.id.cancel, mCancelListener);
            setOnClickListener(R.id.add, mAddListener);

            // Ask the subclass to setup the list; we need this before building the adapter.
            if (savedInstanceState != null && mBKey != null && savedInstanceState.containsKey(mBKey)) {
                mList = ArrayUtils.getListFromBundle(savedInstanceState, mBKey);
            }
            // not in bundle ? check the intent
            if (mList == null) {
                mList = ArrayUtils.getListFromIntentExtras(getIntent(), mBKey);
            }
            // still nothing ? then ask subclass explicitly
            if (mList == null) {
                mList = getList();
            }
            // give up if still null
            Objects.requireNonNull(mList, "Unable to find list key '" + mBKey + "' in passed intent extras");

            // Set up list handling
            this.mAdapter = new EditObjectListAdapter(this, mRowViewId, mList);
            setListAdapter(this.mAdapter);

            // Look for title and title_label
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                mRowId = extras.getLong(UniqueId.KEY_ID);

                mBookTitle = extras.getString(UniqueId.KEY_TITLE);
                setTextOrHideView(R.id.title, mBookTitle);
            }

        } catch (Exception e) {
            Logger.error(e);
        }
    }

    /**
     * Utility routine to setup a listener for the specified view id
     *
     * @param viewId Resource ID
     * @param listener  Listener
     */
    private void setOnClickListener(@IdRes final int viewId, @NonNull final OnClickListener listener) {
        View v = this.findViewById(viewId);
        if (v != null) {
            v.setOnClickListener(listener);
        }
    }

    /**
     * Utility routine to set a TextView to a string, or hide it.
     *
     * @param viewId View ID
     * @param value  String to set
     */
    protected void setTextOrHideView(@SuppressWarnings("SameParameterValue") @IdRes final int viewId, @Nullable final String value) {
        TextView textView = this.findViewById(viewId);
        if (textView == null) {
            return;
        }
        if (value == null || value.isEmpty()) {
            textView.setVisibility(View.GONE);
            return;
        }
        textView.setText(value);
    }

    /**
     * Ensure that the list is saved.
     */
    @Override
    @CallSuper
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        outState.putSerializable(mBKey, mList);

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
        super.onDestroy();
        if (mDb != null)
            mDb.close();
    }

    /**
     * bit of a kludge .. {@link SimpleListAdapter} is encapsulation here, and not extension.
     * So wrap it up in this dummy class.
     * probably needs an interface
     */
    protected class EditObjectListAdapter extends SimpleListAdapter<T> {
        EditObjectListAdapter(@NonNull final Context context, final int rowViewId, @NonNull final ArrayList<T> items) {
            super(context, rowViewId, items);
        }

        @Override
        protected void onSetupView(@NonNull final View convertView, @NonNull final T item) {
            EditObjectListActivity.this.onSetupView(convertView, item);
        }

        @Override
        protected void onRowClick(@NonNull final View target, @NonNull final T item, final int position) {
            EditObjectListActivity.this.onRowClick(target, item, position);
        }

        @Override
        @CallSuper
        protected boolean onRowLongClick(@NonNull final View target, @NonNull final T item, final int position) {
            super.onRowLongClick(target,item,position);
            return EditObjectListActivity.this.onRowLongClick(target, item, position);
        }

        @Override
        protected void onListChanged() {
            EditObjectListActivity.this.onListChanged();
        }

        @Override
        @CallSuper
        protected boolean onRowDelete(@NonNull final View target, @NonNull final T item, final int position) {
            super.onRowDelete(target,item,position);
            return EditObjectListActivity.this.onRowDelete(target, item, position);
        }

        @Override
        protected void onRowDown(@NonNull final View target, @NonNull final T item, final int position) {
            EditObjectListActivity.this.onRowDown(target, item, position);
        }

        @Override
        protected void onRowUp(@NonNull final View target, @NonNull final T item, final int position) {
            EditObjectListActivity.this.onRowUp(target, item, position);
        }
    }
}
