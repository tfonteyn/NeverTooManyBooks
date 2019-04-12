package com.eleybourn.bookcatalogue.baseactivity;

import android.app.Activity;
import android.app.ListActivity;
import android.os.Handler;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.util.Objects;

import com.eleybourn.bookcatalogue.database.DBA;

/**
 * This is a modified copy of {@link ListActivity} extending our own {@link BaseActivity}.
 */
public abstract class BaseListActivity
        extends BaseActivity
        implements AdapterView.OnItemClickListener {

    private final Handler mHandler = new Handler();

    /**
     * The database. It's up to the child classes to initialise it,
     * but most if not all need one. This base class DOES take care of closing it in
     * {@link #onDestroy()}
     */
    protected DBA mDb;

    /**
     * The adapter for the list.
     */
    private ListAdapter mListAdapter;
    /**
     * the View for the list.
     */
    private ListView mListView;

    private final Runnable mRequestFocus = new Runnable() {
        public void run() {
            mListView.focusableViewAvailable(mListView);
        }
    };
    private boolean mFinishedStart;

    /**
     * @see Activity#onDestroy()
     */
    @Override
    @CallSuper
    protected void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        mHandler.removeCallbacks(mRequestFocus);
        super.onDestroy();
    }

    /**
     * Updates the screen state (current list and other views) when the content changes.
     *
     * @see Activity#onContentChanged()
     */
    @Override
    @CallSuper
    public void onContentChanged() {
        super.onContentChanged();
        mListView = findViewById(android.R.id.list);
        Objects.requireNonNull(mListView, "Layout must have a ListView whose id"
                + " attribute is '@android:id/list'");

        View emptyView = findViewById(android.R.id.empty);
        if (emptyView != null) {
            mListView.setEmptyView(emptyView);
        }
        mListView.setOnItemClickListener(this);

        if (mFinishedStart) {
            setListAdapter(mListAdapter);
        }
        mHandler.post(mRequestFocus);
        mFinishedStart = true;
    }

    /**
     * Listen for clicks on items in our list.
     * {@link #onContentChanged} enables 'this' as the listener for our ListView
     * <p>
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void onItemClick(@NonNull final AdapterView<?> parent,
                            @NonNull final View view,
                            final int position,
                            final long id) {
    }

    /**
     * Get the activity's list view widget.
     */
    protected ListView getListView() {
        return mListView;
    }

    /**
     * Provide the cursor for the list view.
     */
    protected void setListAdapter(@NonNull final ListAdapter adapter) {
        synchronized (this) {
            mListAdapter = adapter;
            mListView.setAdapter(adapter);
        }
    }
}
