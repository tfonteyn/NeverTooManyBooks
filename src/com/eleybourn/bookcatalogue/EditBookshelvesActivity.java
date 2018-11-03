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

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.eleybourn.bookcatalogue.baseactivity.BaseListActivity;
import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.widgets.TouchListViewWithDropListener;

import java.util.ArrayList;


/**
 * Admin Activity where we list all bookshelves and can add/delete/edit them.
 *
 * refit with extends {@link EditObjectListActivity} ? => no point,
 * we don't want/need a {@link TouchListViewWithDropListener}
 */
public class EditBookshelvesActivity extends BaseListActivity {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_EDIT_BOOKSHELF_LIST;

    private CatalogueDBAdapter mDb;
    private ArrayList<Bookshelf> mList;

    protected int getLayoutId() {
        return R.layout.activity_edit_list_bookshelf;
    }

    @Override
    @CallSuper
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_manage_bs);

        mDb = new CatalogueDBAdapter(this)
                .open();
        populateList();
        registerForContextMenu(getListView());
    }

    private void populateList() {
        mList = mDb.getBookshelves();
        ArrayAdapter<Bookshelf> adapter = new ArrayAdapter<>(this, R.layout.row_bookshelf, R.id.row_bookshelves, mList);
        setListAdapter(adapter);
    }

    @Override
    @CallSuper
    public void onCreateContextMenu(final @NonNull ContextMenu menu, final View v, final ContextMenuInfo menuInfo) {
        menu.add(Menu.NONE, R.id.MENU_DELETE, 0, R.string.menu_delete)
                .setIcon(R.drawable.ic_delete);

        super.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    @CallSuper
    public boolean onContextItemSelected(final @NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_DELETE:
                AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
                Bookshelf bookshelf = mList.get(menuInfo.position);
                if (bookshelf.id != 1) {
                    mDb.deleteBookshelf(bookshelf.id);
                    populateList();
                } else {
                    //TODO: why not ? as long as we make sure there is another one left.. e.g. count > 2, then you can delete 'one'
                    StandardDialogs.showUserMessage(this, R.string.delete_1st_bs);
                }
                return true;
        }
        return super.onContextItemSelected(item);
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

        menu.add(Menu.NONE, R.id.MENU_INSERT, 0, R.string.menu_insert_bs)
                .setIcon(R.drawable.ic_add)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(final @NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_INSERT:
                Intent intent = new Intent(this, EditBookshelfActivity.class);
                startActivityForResult(intent, EditBookshelfActivity.REQUEST_CODE_CREATE); /* ed5e0eb7-6440-4e67-a253-41326bd5c8f4 */
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onListItemClick(final @NonNull ListView listView, final @NonNull View view, final int position, final long id) {
        Bookshelf bookshelf = mList.get(position);
        Intent intent = new Intent(this, EditBookshelfActivity.class);
        intent.putExtra(UniqueId.KEY_ID, bookshelf.id);
        startActivityForResult(intent, EditBookshelfActivity.REQUEST_CODE_EDIT); /* eabd012d-e5db-4c3b-ad65-876ed04b8eca */
    }

    @Override
    @CallSuper
    protected void onActivityResult(final int requestCode, final int resultCode, final @Nullable Intent data) {
        if (BuildConfig.DEBUG) {
            Logger.info(this,"onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
        }
        switch (requestCode) {
            case EditBookshelfActivity.REQUEST_CODE_EDIT: /* eabd012d-e5db-4c3b-ad65-876ed04b8eca */
            case EditBookshelfActivity.REQUEST_CODE_CREATE: /* ed5e0eb7-6440-4e67-a253-41326bd5c8f4 */
                // pass up
                setResult(resultCode, data); /* 41e84172-5833-4906-a891-8df302ecc190 */

                // regardless of activity, rebuild
                populateList();
                return;
        }

        super.onActivityResult(requestCode, resultCode, data);
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
