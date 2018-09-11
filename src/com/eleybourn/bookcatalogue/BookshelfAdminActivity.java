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
import android.database.Cursor;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;
import com.eleybourn.bookcatalogue.baseactivity.BookCatalogueListActivity;

import static com.eleybourn.bookcatalogue.database.ColumnInfo.KEY_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.ColumnInfo.KEY_ID;

/**
 * Admin Activity where we list all bookshelves and can add/delete/edit them.
 *
 * ENHANCE:
 * - isDirty functionality
 */
public class BookshelfAdminActivity extends BookCatalogueListActivity
{
    private static final int ACTIVITY_CREATE = 0;
    private static final int ACTIVITY_EDIT = 1;
    private static final int INSERT_ID = Menu.FIRST;
    private static final int DELETE_ID = Menu.FIRST + 1;

    private CatalogueDBAdapter mDb;

    protected int getLayoutId() {
        return R.layout.activity_edit_list_bookshelf;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_manage_bs);

        mDb = new CatalogueDBAdapter(this);
        mDb.open();
        fillBookshelves();
        registerForContextMenu(getListView());
    }

    private void fillBookshelves() {
        //FIXME: https://www.androiddesignpatterns.com/2012/07/loaders-and-loadermanager-background.html
        //FIXME Use the new {@link android.content.CursorLoader} class with {@link LoaderManager} instead
        Cursor bookshelfCursor = mDb.fetchAllBookshelves();
        startManagingCursor(bookshelfCursor);

        // Now create a simple cursor adapter and set it to display
        String[] fieldsToDisplay = new String[]{KEY_BOOKSHELF, KEY_ID};
        int[] fieldsToBindTo = new int[]{R.id.row_bookshelf};
        SimpleCursorAdapter books = new SimpleCursorAdapter(this, R.layout.row_bookshelf,
                bookshelfCursor, fieldsToDisplay, fieldsToBindTo);
        this.setListAdapter(books);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, INSERT_ID, 0,
                R.string.menu_insert_bs)
                .setIcon(android.R.drawable.ic_menu_add)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case INSERT_ID:
                Intent i = new Intent(this, BookshelfEditActivity.class);
                startActivityForResult(i, ACTIVITY_CREATE);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(0, DELETE_ID, 0, R.string.menu_delete_bs);
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        switch (item.getItemId()) {
            case DELETE_ID:
                AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
                if (info.id != 1) {
                    mDb.deleteBookshelf(info.id);
                    fillBookshelves();
                } else {
                    Toast.makeText(this, R.string.delete_1st_bs, Toast.LENGTH_LONG).show();
                }
                return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        Intent i = new Intent(this, BookshelfEditActivity.class);
        i.putExtra(KEY_ID, id);
        startActivityForResult(i, ACTIVITY_EDIT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        fillBookshelves();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDb.close();
    }
}
