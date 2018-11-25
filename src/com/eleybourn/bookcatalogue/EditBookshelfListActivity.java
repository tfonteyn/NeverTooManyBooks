/*
 * @copyright 2010 Evan Leybourn
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

package com.eleybourn.bookcatalogue;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.PopupMenu;

import com.eleybourn.bookcatalogue.baseactivity.BaseListActivity;
import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.SelectOneDialog;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditBookshelfDialog;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.widgets.TouchListViewWithDropListener;

import java.util.ArrayList;


/**
 * Admin Activity where we list all bookshelves and can add/delete/edit them.
 *
 * refit with extends {@link EditObjectListActivity} ? => no point,
 * we don't want/need a {@link TouchListViewWithDropListener}
 */
public class EditBookshelfListActivity extends BaseListActivity {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_EDIT_BOOKSHELF_LIST;

    private CatalogueDBAdapter mDb;
    private ArrayList<Bookshelf> mList;

    protected int getLayoutId() {
        return R.layout.activity_edit_list_bookshelf;
    }

    @Override
    @CallSuper
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_edit_bookshelves);

        mDb = new CatalogueDBAdapter(this);

        populateList();
        Tracker.exitOnCreate(this);
    }

    private void populateList() {
        mList = mDb.getBookshelves();
        ArrayAdapter<Bookshelf> adapter = new ArrayAdapter<>(this, R.layout.row_bookshelf, R.id.row_bookshelves, mList);
        setListAdapter(adapter);
    }

    /**
     * Using {@link SelectOneDialog#showContextMenuDialog} for context menus
     */
    @Override
    public void initListViewContextMenuListener(final @NonNull Context context) {
        getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(final AdapterView<?> parent, final View view, final int position, final long id) {
                String menuTitle = mList.get(position).name;

                // legal trick to get an instance of Menu.
                mListViewContextMenu = new PopupMenu(context, null).getMenu();
                // custom menuInfo
                SelectOneDialog.SimpleDialogMenuInfo menuInfo = new SelectOneDialog.SimpleDialogMenuInfo(menuTitle, view, position);
                // populate the menu
                mListViewContextMenu.add(Menu.NONE, R.id.MENU_EDIT, 0, R.string.menu_edit_bookshelf)
                        .setIcon(R.drawable.ic_mode_edit);
                mListViewContextMenu.add(Menu.NONE, R.id.MENU_DELETE, 0, R.string.menu_delete_bookshelf)
                        .setIcon(R.drawable.ic_delete);
                // display
                onCreateListViewContextMenu(mListViewContextMenu, view, menuInfo);
                return true;
            }
        });
    }

    /**
     * Using {@link SelectOneDialog#showContextMenuDialog} for context menus
     */
    @Override
    public boolean onListViewContextItemSelected(final @NonNull MenuItem menuItem,
                                                 final @NonNull SelectOneDialog.SimpleDialogMenuInfo menuInfo) {
        switch (menuItem.getItemId()) {
            case R.id.MENU_EDIT: {
                Bookshelf bookshelf = mList.get(menuInfo.position);
                doEditDialog(bookshelf);
                return true;
            }
            case R.id.MENU_DELETE: {
                Bookshelf bookshelf = mList.get(menuInfo.position);
                if (bookshelf.id != 1) {
                    mDb.deleteBookshelf(bookshelf.id);
                    populateList();
                } else {
                    //TODO: why not ? as long as we make sure there is another one left.. e.g. count > 2, then you can delete '1'
                    StandardDialogs.showUserMessage(this, R.string.warning_cannot_delete_1st_bs);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * @param menu The options menu in which you place your items.
     *
     * @return super.onCreateOptionsMenu(menu);
     *
     * @see #onPrepareOptionsMenu
     * @see #onOptionsItemSelected
     */
    @Override
    @CallSuper
    public boolean onCreateOptionsMenu(final @NonNull Menu menu) {

        menu.add(Menu.NONE, R.id.MENU_INSERT, 0, R.string.menu_add_bookshelf)
                .setIcon(R.drawable.ic_add)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(final @NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_INSERT: {
                doEditDialog(new Bookshelf(""));
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Listen for clicks on items in our list
     *
     * {@link BaseListActivity} enables 'this' as the listener
     */
    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
        Bookshelf bookshelf = mList.get(position);
        doEditDialog(bookshelf);
    }

    private void doEditDialog(final @NonNull Bookshelf bookshelf) {
        EditBookshelfDialog d = new EditBookshelfDialog(this, mDb, new Runnable() {
            @Override
            public void run() {
                populateList();
                setChangesMade(true);
            }
        });
        d.edit(bookshelf);
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
