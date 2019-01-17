package com.eleybourn.bookcatalogue.baseactivity;

import android.app.Activity;
import android.app.ListActivity;
import android.os.Bundle;
import android.os.Handler;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.dialogs.SimpleDialog;

import java.util.Objects;

/**
 * This has now become a copy from {@link ListActivity} but extending {@link BaseActivity}.
 * <p>
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
public abstract class BaseListActivity
        extends BaseActivity
        implements
        AdapterView.OnItemClickListener,
        SimpleDialog.ListViewContextMenu {

    private final Handler mHandler = new Handler();
    protected Menu mListViewContextMenu;
    private ListAdapter mListAdapter;
    private ListView mListView;
    private final Runnable mRequestFocus = new Runnable() {
        public void run() {
            mListView.focusableViewAvailable(mListView);
        }
    };
    private boolean mFinishedStart;

    @Override
    protected int getLayoutId() {
        return R.layout.list_activity;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // enable context menu for the list view
        initContextMenuOnListView();
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
     * <p>
     * {@link #onContentChanged} enables 'this' as the listener for our ListView
     *
     * @param parent   The AdapterView where the click happened.
     * @param view     The view within the AdapterView that was clicked (this
     *                 will be a view provided by the adapter)
     * @param position The position of the view in the adapter.
     * @param id       The row id of the item that was clicked.
     */
    @Override
    public void onItemClick(@NonNull final AdapterView<?> parent,
                            @NonNull final View view,
                            final int position,
                            final long id) {
    }

    /**
     * Set the currently selected list item to the specified position with the adapter's data.
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
    protected void setListAdapter(@NonNull final ListAdapter adapter) {
        synchronized (this) {
            mListAdapter = adapter;
            mListView.setAdapter(adapter);
        }
    }

    /**
     * Using {@link SimpleDialog#showContextMenu} for context menus.
     */
    @Override
    public void initContextMenuOnListView() {
        //do nothing, example code you can copy to overriding methods:

//        getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
//            @Override
//            public boolean onItemLongClick(@NonNull final AdapterView<?> parent,
//                                           @NonNull final View view,
//                                           final int position, final long id) {
//
//                // SET THE MENU TITLE
//                String menuTitle = mList.get(position).getTitle();
//
//                // legal trick to get an instance of Menu.
//                mListViewContextMenu = new PopupMenu(view.getContext(), null).getMenu();
//                // custom menuInfo
//                SimpleDialog.ContextMenuInfo menuInfo =
//                    new SimpleDialog.ContextMenuInfo(menuTitle, view, position);
//
//                // POPULATE THE MENU
//                mListViewContextMenu.add(Menu.NONE, R.id.MENU_DELETE, 0,
//                                         R.string.menu_delete_something)
//                        .setIcon(R.drawable.ic_delete);
//
//                // display
//                onCreateListViewContextMenu(mListViewContextMenu, view, menuInfo);
//                return true;
//            }
//        });
    }

    /**
     * Using {@link SimpleDialog#showContextMenu} for context menus.
     * <p>
     * Replaces: {@link #onCreateContextMenu(ContextMenu, View, ContextMenu.ContextMenuInfo)}
     */
    @Override
    public final void onCreateListViewContextMenu(@NonNull final View view,
                                                  @NonNull final Menu menu,
                                                  @NonNull final SimpleDialog.ContextMenuInfo
                                                          menuInfo) {
        if (menu.size() > 0) {
            SimpleDialog.showContextMenu(
                    getLayoutInflater(), menuInfo.title, menu,
                    new SimpleDialog.OnClickListener() {
                        @Override
                        public void onClick(@NonNull final SimpleDialog.SimpleDialogItem item) {
                            MenuItem menuItem =
                                    ((SimpleDialog.SimpleDialogMenuItem) item).getMenuItem();
                            if (menuItem.hasSubMenu()) {
                                // bring up sub-menu
                                menuInfo.title = menuItem.getTitle().toString();
                                onCreateListViewContextMenu(view, menuItem.getSubMenu(), menuInfo);
                            } else {
                                onListViewContextItemSelected(menuItem, menuInfo.position);
                            }
                        }
                    });
        }
    }

    /**
     * Using {@link SimpleDialog#showContextMenu} for context menus.
     */
    @SuppressWarnings("UnusedReturnValue")
    @Override
    public boolean onListViewContextItemSelected(@NonNull final MenuItem menuItem,
                                                 final int position) {
        return false;
    }
}
