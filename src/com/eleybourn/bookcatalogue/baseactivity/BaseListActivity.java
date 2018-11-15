package com.eleybourn.bookcatalogue.baseactivity;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.dialogs.SelectOneDialog;

import java.util.Objects;

/**
 * This has now become a copy from {@link ListActivity} but extending {@link BaseActivity}
 *
 * You must have a layout with the file name
 * res/layout/list_activity.xml
 * and containing something like this:
 * <pre>
 * <?xml version="1.0" encoding="utf-8"?>
 * <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
 * android:layout_width="match_parent"
 * android:layout_height="match_parent"
 * android:orientation="vertical">
 *
 * <FrameLayout
 * android:id="@+id/header"
 * android:layout_width="match_parent"
 * android:layout_height="wrap_content"
 * android:orientation="vertical" />
 *
 * <FrameLayout
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
 *
 * <FrameLayout
 * android:id="@+id/footer"
 * android:layout_width="match_parent"
 * android:layout_height="wrap_content"
 * android:orientation="vertical" />
 * </LinearLayout>
 * </pre>
 */
abstract public class BaseListActivity extends BaseActivity implements
        AdapterView.OnItemClickListener,
        SelectOneDialog.hasListViewContextMenu {

    private final Handler mHandler = new Handler();
    protected Menu mListViewContextMenu;
    private ListAdapter mListAdapter;
    private ListView mListView;
    private final Runnable mRequestFocus = new Runnable() {
        public void run() {
            mListView.focusableViewAvailable(mListView);
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
        // enable context menu for the list view
        initListViewContextMenuListener(this);
    }

    /**
     * @see Activity#onDestroy()
     */
    @Override
    @CallSuper
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
    @CallSuper
    public void onContentChanged() {
        super.onContentChanged();
        mListView = findViewById(android.R.id.list);
        Objects.requireNonNull(mListView, "Layout must have a ListView whose id attribute is '@android:id/list'");

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
     * Listen for clicks on items in our list
     *
     * {@link #onContentChanged} enables 'this' as the listener for our ListView
     */
    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
    }

    /**
     * Set the currently selected list item to the specified position with the adapter's data
     */
    @SuppressWarnings("unused")
    public void setSelection(final int position) {
        mListView.setSelection(position);
    }

    /**
     * Get the position of the currently selected list item.
     */
    @SuppressWarnings("unused")
    public int getSelectedItemPosition() {
        return mListView.getSelectedItemPosition();
    }

    /**
     * Get the cursor row ID of the currently selected list item.
     */
    @SuppressWarnings("unused")
    public long getSelectedItemId() {
        return mListView.getSelectedItemId();
    }

    /**
     * Get the activity's list view widget.
     */
    protected ListView getListView() {
        return mListView;
    }

    /**
     * Get the ListAdapter associated with this activity's ListView.
     */
    @SuppressWarnings("unused")
    public ListAdapter getListAdapter() {
        return mListAdapter;
    }

    /**
     * Provide the cursor for the list view.
     */
    protected void setListAdapter(final @NonNull ListAdapter adapter) {
        synchronized (this) {
            mListAdapter = adapter;
            mListView.setAdapter(adapter);
        }
    }

    /**
     * Using {@link SelectOneDialog#showContextMenuDialog} for context menus
     */
    @Override
    public void initListViewContextMenuListener(@NonNull final Context context) {
        //do nothing, example code you can copy to overriding methods:

//        getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
//            @Override
//            public boolean onItemLongClick(final AdapterView<?> parent, final View view, final int position, final long id) {
//
//                // SET THE MENU TITLE
//                String menuTitle = mList.get(position).getTitle();
//
//                // legal trick to get an instance of Menu.
//                mListViewContextMenu = new PopupMenu(context, null).getMenu();
//                // custom menuInfo
//                SelectOneDialog.SimpleDialogMenuInfo menuInfo = new SelectOneDialog.SimpleDialogMenuInfo(menuTitle, view, position);
//
//                // POPULATE THE MENU
//                mListViewContextMenu.add(Menu.NONE, R.id.MENU_DELETE, 0, R.string.menu_delete_something)
//                        .setIcon(R.drawable.ic_delete);
//
//                // display
//                onCreateListViewContextMenu(mListViewContextMenu, view, menuInfo);
//                return true;
//            }
//        });
    }

    /**
     * Using {@link SelectOneDialog#showContextMenuDialog} for context menus
     *
     * Replaces: {@link #onCreateContextMenu(ContextMenu, View, ContextMenu.ContextMenuInfo)}
     */
    @Override
    public final void onCreateListViewContextMenu(final @NonNull Menu menu,
                                                  final @NonNull View view,
                                                  final @NonNull SelectOneDialog.SimpleDialogMenuInfo menuInfo) {
        if (menu.size() > 0) {
            SelectOneDialog.showContextMenuDialog(getLayoutInflater(), menuInfo, menu,
                    new SelectOneDialog.SimpleDialogOnClickListener() {
                        @Override
                        public void onClick(final @NonNull SelectOneDialog.SimpleDialogItem item) {
                            MenuItem menuItem = ((SelectOneDialog.SimpleDialogMenuItem) item).getMenuItem();
                            if (menuItem.hasSubMenu()) {
                                menuInfo.title = menuItem.getTitle().toString();
                                onCreateListViewContextMenu(menuItem.getSubMenu(), view, menuInfo);
                            } else {
                                onListViewContextItemSelected(menuItem, menuInfo);
                            }
                        }
                    });
        }
    }

    /**
     * Using {@link SelectOneDialog#showContextMenuDialog} for context menus
     */
    @SuppressWarnings("UnusedReturnValue")
    @Override
    public boolean onListViewContextItemSelected(final @NonNull MenuItem menuItem,
                                                 final @NonNull SelectOneDialog.SimpleDialogMenuInfo menuInfo) {
        return false;
    }

}
