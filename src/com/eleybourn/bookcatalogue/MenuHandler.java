/*
 * @copyright 2012 Philip Warner
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
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;

import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Class to make building menus simpler. Implements some default menu items and allows
 * selecting which need to be added as well as the addition of custom items.
 *
 * @author Philip Warner
 */
public class MenuHandler {

    private static final int MENU_ADD_BOOK = Menu.FIRST + 1;
    private static final int MENU_ITEM_ADD_BOOK_MANUAL = Menu.FIRST + 2;
    private static final int MENU_ITEM_ADD_BOOK_BARCODE = Menu.FIRST + 3;
    private static final int MENU_ITEM_ADD_BOOK_ISBN = Menu.FIRST + 4;
    private static final int MENU_ITEM_ADD_BOOK_NAMES = Menu.FIRST + 5;
    private static final int MENU_ITEM_SEARCH = Menu.FIRST + 6;

    // must have a higher offset then the last one listed above
    public static final int FIRST = Menu.FIRST + 13;

    /**
     * Construct & init.
     */
    public MenuHandler(Menu menu) {
        init(menu);
    }

    private int mSort = 0;

    /**
     * Called by the constructor.
     * Allows re-using the MenuHandler
     */
    private void init(Menu menu) {
        mSort = 0;
        menu.clear();
    }

//    public MenuItem addItem(Menu menu, int id, int stringId) {
//        return addItem(menu, id, stringId, 0);
//    }

    /**
     * Add a custom menu item.
     *
     * @param menu     Root menu
     * @param id       Menu item ID
     * @param stringId String ID to display
     * @param icon     (Optional) Icon for menu item, 0 for none
     *
     * @return The new item
     */
    public MenuItem addItem(Menu menu, int id, int stringId, int icon) {
        MenuItem item = menu.add(0, id, mSort++, stringId);
        if (icon != 0) {
            item.setIcon(icon);
        }
        return item;
    }

    /**
     * Add the default 'search' menu item
     *
     * @param menu	root menu
     */
    public MenuItem addSearchItem(Menu menu) {
        MenuItem search = menu.add(0, MENU_ITEM_SEARCH, mSort++, R.string.menu_search);
        search.setIcon(android.R.drawable.ic_menu_search);
        return search;
    }
    /**
     * Add menu and submenu for book creation.
     *
     * @param menu Root menu
     */
    public void addCreateBookSubMenu(Menu menu) {
        SubMenu addMenu = menu.addSubMenu(0, MENU_ADD_BOOK,
                mSort++,
                BookCatalogueApp.getResourceString(R.string.menu_insert) + "&hellip;");

        addMenu.setIcon(android.R.drawable.ic_menu_add);
        addMenu.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        {
            if (Utils.USE_BARCODE) {
                addMenu.add(0, MENU_ITEM_ADD_BOOK_BARCODE, mSort++, R.string.scan_barcode_isbn)
                        .setIcon(R.drawable.ic_menu_insert_barcode);
            }
            addMenu.add(0, MENU_ITEM_ADD_BOOK_ISBN, mSort++, R.string.enter_isbn)
                    .setIcon(android.R.drawable.ic_menu_zoom);

            addMenu.add(0, MENU_ITEM_ADD_BOOK_NAMES, mSort++, R.string.search_internet)
                    .setIcon(android.R.drawable.ic_menu_zoom);

            addMenu.add(0, MENU_ITEM_ADD_BOOK_MANUAL, mSort++, R.string.add_manually)
                    .setIcon(android.R.drawable.ic_menu_add);
        }
    }

    /**
     * Handle the default menu items
     *
     * @param a    Calling activity
     * @param item The item selected
     *
     * @return True, if handled
     */
    public boolean onOptionsItemSelected(Activity a, MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ITEM_ADD_BOOK_BARCODE:
                createBookScan(a);
                return true;
            case MENU_ITEM_ADD_BOOK_ISBN:
                createBookISBN(a, BookISBNSearch.BY_ISBN);
                return true;
            case MENU_ITEM_ADD_BOOK_NAMES:
                createBookISBN(a, BookISBNSearch.BY_NAME);
                return true;
            case MENU_ITEM_ADD_BOOK_MANUAL:
                createBook(a);
                return true;
            case MENU_ITEM_SEARCH:
                a.onSearchRequested();
                return true;
        }
        return false;
    }

    /**
     * Load the Search by ISBN Activity to begin scanning.
     */
    private void createBookScan(Activity a) {
        Intent i = new Intent(a, BookISBNSearch.class);
        i.putExtra(BookISBNSearch.BKEY_BY, BookISBNSearch.BY_SCAN);
        a.startActivityForResult(i, UniqueId.ACTIVITY_CREATE_BOOK_SCAN);
    }

    /**
     * Load the Search by ISBN Activity
     */
    private void createBookISBN(Activity a, String by) {
        Intent i = new Intent(a, BookISBNSearch.class);
        i.putExtra(BookISBNSearch.BKEY_BY, by);
        a.startActivityForResult(i, UniqueId.ACTIVITY_CREATE_BOOK_ISBN);
    }

    /**
     * Load the BookEdit Activity
     */
    private void createBook(Activity a) {
        Intent i = new Intent(a, BookEdit.class);
        a.startActivityForResult(i, UniqueId.ACTIVITY_CREATE_BOOK_MANUALLY);
    }

//    /**
//     * Load the EditBook activity based on the provided id. Also open to the provided tab
//     *
//     * @param id  The id of the book to edit
//     * @param tab Which tab to open first
//     */
//    public static void editBook(Activity a, long id, int tab) {
//        Intent i = new Intent(a, BookEdit.class);
//        i.putExtra(CatalogueDBAdapter.KEY_ROWID, id);
//        i.putExtra(BookEdit.TAB, tab);
//        a.startActivityForResult(i, UniqueId.ACTIVITY_EDIT_BOOK);
//        return;
//    }
}
