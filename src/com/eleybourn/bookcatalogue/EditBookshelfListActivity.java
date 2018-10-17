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
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Bookshelf;

import java.util.ArrayList;


/**
 * Admin Activity where we list all bookshelves and can add/delete/edit them.
 *
 * ENHANCE: refit with  extends EditObjectListActivity<Bookshelf>
 */
public class EditBookshelfListActivity extends BaseListActivity
{
    private static final int ACTIVITY_CREATE = 0;
    private static final int ACTIVITY_EDIT = 1;

    private CatalogueDBAdapter mDb;
    private ArrayList<Bookshelf> mList;

    protected int getLayoutId() {
        return R.layout.activity_edit_list_bookshelf;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_manage_bs);

        mDb = new CatalogueDBAdapter(this);
        mDb.open();
        populateList();
        registerForContextMenu(getListView());
    }

    private void populateList() {
        mList = mDb.getBookshelves();
        ArrayAdapter<Bookshelf> adapter = new ArrayAdapter<>(this, R.layout.row_bookshelf, R.id.row_bookshelf, mList);
        setListAdapter(adapter);
    }

    @Override
    @CallSuper
    public boolean onCreateOptionsMenu(@NonNull final Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(Menu.NONE, R.id.MENU_INSERT, 0, R.string.menu_insert_bs)
                .setIcon(R.drawable.ic_add)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_INSERT:
                Intent intent = new Intent(this, EditBookshelfActivity.class);
                startActivityForResult(intent, ACTIVITY_CREATE);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    @CallSuper
    public void onCreateContextMenu(@NonNull final ContextMenu menu, final View v, final ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(Menu.NONE, R.id.MENU_DELETE, 0, R.string.menu_delete_bs)
                .setIcon(R.drawable.ic_mode_edit);
    }

    @Override
    @CallSuper
    public boolean onContextItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_DELETE:
                AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
                Bookshelf bookshelf = mList.get(menuInfo.position);
                if (bookshelf.id != 1) {
                    mDb.deleteBookshelf(bookshelf.id);
                    populateList();
                } else {
                    //TODO: why not ? as long as we make sure there is another one left.. e.g. count > 2, then you can delete 'one'
                    StandardDialogs.showBriefMessage(this, R.string.delete_1st_bs);
                }
                return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onListItemClick(@NonNull final ListView listView, @NonNull final View view, final int position, final long id) {
        Bookshelf bookshelf = mList.get(position);
        Intent intent = new Intent(this, EditBookshelfActivity.class);
        intent.putExtra(UniqueId.KEY_ID, bookshelf.id);
        startActivityForResult(intent, ACTIVITY_EDIT);
    }

    @Override
    @CallSuper
    protected void onActivityResult(final int requestCode, final int resultCode, @Nullable final Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        populateList();
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        super.onDestroy();
        mDb.close();
    }
}
