package com.eleybourn.bookcatalogue.baseactivity;

import android.app.Activity;
import android.app.ListActivity;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.eleybourn.bookcatalogue.R;

/**
 * This has now become a copy from {@link ListActivity} but extending {@link BookCatalogueActivity}
 *
 * You must have a layout with the file name
 * res/layout/list_activity.xml
 * and containing something like this:
 * <pre>
 * <?xml version="1.0" encoding="utf-8"?>
 * <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
 * android:layout_width="match_parent"
 * android:layout_height="0dp"
 * android:layout_weight="1"
 * android:orientation="vertical">
 *
 * <ListView
 * android:id="@android:id/list"
 * android:layout_width="match_parent"
 * android:layout_height="match_parent" />
 *
 * <TextView
 * android:id="@android:id/empty"
 * android:layout_width="wrap_content"
 * android:layout_height="wrap_content"
 * android:visibility="gone" />
 * </FrameLayout>
 * </pre>
 */
abstract public class BookCatalogueListActivity extends BookCatalogueActivity {

    private final Handler mHandler = new Handler();
    private final AdapterView.OnItemClickListener mOnClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(final AdapterView<?> parent, final View v, final int position, final long id) {
            onListItemClick((ListView) parent, v, position, id);
        }
    };
    private ListAdapter mAdapter;
    private ListView mList;
    private final Runnable mRequestFocus = new Runnable() {
        public void run() {
            mList.focusableViewAvailable(mList);
        }
    };
    private boolean mFinishedStart = false;

    @Override
    protected int getLayoutId() {
        return R.layout.list_activity;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    /**
     * This method will be called when an item in the list is selected.
     * Subclasses should override. Subclasses can call
     * getListView().getItemAtPosition(position) if they need to access the
     * data associated with the selected item.
     *
     * @param l        The ListView where the click happened
     * @param v        The view that was clicked within the ListView
     * @param position The position of the view in the list
     * @param id       The row id of the item that was clicked
     */
    protected void onListItemClick(@NonNull final ListView l, @NonNull final View v, final int position, final long id) {
    }

    /**
     * Ensures the list view has been created before Activity restores all
     * of the view states.
     *
     * @see Activity#onRestoreInstanceState(Bundle)
     */
    @Override
    protected void onRestoreInstanceState(final Bundle state) {
        super.onRestoreInstanceState(state);
    }

    /**
     * @see Activity#onDestroy()
     */
    @Override
    protected void onDestroy() {
        mHandler.removeCallbacks(mRequestFocus);
        super.onDestroy();
    }

    /**
     * Updates the screen state (current list and other views) when the
     * content changes.
     *
     * @see Activity#onContentChanged()
     */
    @Override
    public void onContentChanged() {
        super.onContentChanged();
        View emptyView = findViewById(android.R.id.empty);
        mList = findViewById(android.R.id.list);
        if (mList == null) {
            throw new RuntimeException(
                    "Your content must have a ListView whose id attribute is '@id/android:list'");
        }
        if (emptyView != null) {
            mList.setEmptyView(emptyView);
        }
        mList.setOnItemClickListener(mOnClickListener);
        if (mFinishedStart) {
            setListAdapter(mAdapter);
        }
        mHandler.post(mRequestFocus);
        mFinishedStart = true;
    }

    /**
     * Set the currently selected list item to the specified position with the adapter's data
     */
    @SuppressWarnings("unused")
    public void setSelection(final int position) {
        mList.setSelection(position);
    }

    /**
     * Get the position of the currently selected list item.
     */
    @SuppressWarnings("unused")
    public int getSelectedItemPosition() {
        return mList.getSelectedItemPosition();
    }

    /**
     * Get the cursor row ID of the currently selected list item.
     */
    @SuppressWarnings("unused")
    public long getSelectedItemId() {
        return mList.getSelectedItemId();
    }

    /**
     * Get the activity's list view widget.
     */
    protected ListView getListView() {
        return mList;
    }

    /**
     * Get the ListAdapter associated with this activity's ListView.
     */
    @SuppressWarnings("unused")
    public ListAdapter getListAdapter() {
        return mAdapter;
    }

    /**
     * Provide the cursor for the list view.
     */
    protected void setListAdapter(@NonNull final ListAdapter adapter) {
        synchronized (this) {
            mAdapter = adapter;
            mList.setAdapter(adapter);
        }
    }
}
