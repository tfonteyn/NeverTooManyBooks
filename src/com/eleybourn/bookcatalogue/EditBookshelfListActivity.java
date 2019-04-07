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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.PopupMenu;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.adapters.BookshelfAdapter;
import com.eleybourn.bookcatalogue.baseactivity.BaseListActivity;
import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.dialogs.SimpleDialog;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditBookshelfDialogFragment;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.utils.UserMessage;


/**
 * Admin Activity where we list all bookshelves and can add/delete/edit them.
 * <p>
 * refit with extends {@link EditObjectListActivity} ? => no point,
 * we don't want/need a TouchListView
 */
public class EditBookshelfListActivity
        extends BaseListActivity
        implements EditBookshelfDialogFragment.OnBookshelfChangedListener {

    /** the list we're editing. */
    private ArrayList<Bookshelf> mList;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_edit_list_bookshelf;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_edit_bookshelves);
        mDb = new DBA(this);

        populateList();
    }

    /**
     * populate / refresh the list view.
     */
    private void populateList() {
        mList = mDb.getBookshelves();
        ArrayAdapter<Bookshelf> adapter = new BookshelfAdapter(this,
                                                               R.layout.row_bookshelf, mList);
        setListAdapter(adapter);
    }

    /**
     * Using {@link SimpleDialog#showContextMenu} for context menus.
     */
    @Override
    public void initContextMenuOnListView() {
        getListView().setOnItemLongClickListener((parent, view, position, id) -> {
            String menuTitle = mList.get(position).getName();

            // legal trick to get an instance of Menu.
            mListViewContextMenu = new PopupMenu(view.getContext(), null).getMenu();
            // custom menuInfo
            SimpleDialog.ContextMenuInfo menuInfo =
                    new SimpleDialog.ContextMenuInfo(menuTitle, position);
            // populate the menu
            mListViewContextMenu.add(Menu.NONE, R.id.MENU_EDIT, 0, R.string.menu_edit_bookshelf)
                                .setIcon(R.drawable.ic_edit);
            mListViewContextMenu.add(Menu.NONE, R.id.MENU_DELETE, 0,
                                     R.string.menu_delete_bookshelf)
                                .setIcon(R.drawable.ic_delete);
            // display
            onCreateListViewContextMenu(view, mListViewContextMenu, menuInfo);
            return true;
        });
    }

    /**
     * Using {@link SimpleDialog#showContextMenu} for context menus.
     */
    @Override
    public boolean onListViewContextItemSelected(@NonNull final MenuItem menuItem,
                                                 final int position) {

        Bookshelf bookshelf = mList.get(position);
        switch (menuItem.getItemId()) {
            case R.id.MENU_EDIT:
                doEditDialog(bookshelf);
                return true;

            case R.id.MENU_DELETE:
                if (!bookshelf.isDefault()) {
                    mDb.deleteBookshelf(bookshelf.getId());
                    populateList();
                } else {
                    //TODO: why not ? as long as we make sure there is another one left..
                    // e.g. count > 2, then you can delete '1'
                    UserMessage.showUserMessage(this, R.string.warning_cannot_delete_1st_bs);
                }
                return true;

            default:
                return false;
        }
    }

    @Override
    @CallSuper
    public boolean onCreateOptionsMenu(@NonNull final Menu menu) {
        menu.add(Menu.NONE, R.id.MENU_INSERT, 0, R.string.menu_add_bookshelf)
            .setIcon(R.drawable.ic_add)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_INSERT:
                doEditDialog(new Bookshelf("", BooklistStyles.getDefaultStyle(mDb).getId()));
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Listen for clicks on items in our list.
     * <p>
     * {@link BaseListActivity} enables 'this' as the listener
     */
    @Override
    public void onItemClick(@NonNull final AdapterView<?> parent,
                            @NonNull final View view,
                            final int position,
                            final long id) {
        Bookshelf bookshelf = mList.get(position);
        doEditDialog(bookshelf);
    }

    /**
     * @param bookshelf to edit
     */
    private void doEditDialog(@NonNull final Bookshelf bookshelf) {
        EditBookshelfDialogFragment.show(getSupportFragmentManager(), bookshelf);

    }

    @Override
    public void onBookshelfChanged(final long bookshelfId,
                                   final int booksMoved) {
        populateList();
        Intent data = new Intent().putExtra(DBDefinitions.KEY_ID, bookshelfId);
        setResult(Activity.RESULT_OK, data);
    }
}
