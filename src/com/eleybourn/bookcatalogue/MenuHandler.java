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
import android.support.annotation.NonNull;
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
    public MenuHandler(@NonNull final Menu menu) {
        init(menu);
    }

    private int mSort = 0;

    /**
     * Called by the constructor.
     * Allows re-using the MenuHandler
     */
    private void init(@NonNull final Menu menu) {
        mSort = 0;
        menu.clear();
    }

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
    public MenuItem addItem(@NonNull final Menu menu, final int id, final int stringId, final int icon) {
        MenuItem item = menu.add(0, id, mSort++, stringId);
        if (icon != 0) {
            item.setIcon(icon);
        }
        return item;
    }

    /**
     * Add menu and submenu for book creation.
     *
     * @param menu Root menu
     */
    public void addCreateBookSubMenu(@NonNull final Menu menu) {
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
    public boolean onOptionsItemSelected(@NonNull final Activity a, @NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ITEM_ADD_BOOK_BARCODE:
                createBookScan(a);
                return true;
            case MENU_ITEM_ADD_BOOK_ISBN:
                createBookISBN(a, BookISBNSearchActivity.BY_ISBN);
                return true;
            case MENU_ITEM_ADD_BOOK_NAMES:
                createBookISBN(a, BookISBNSearchActivity.BY_NAME);
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
    private void createBookScan(@NonNull final Activity a) {
        Intent i = new Intent(a, BookISBNSearchActivity.class);
        i.putExtra(BookISBNSearchActivity.BKEY_BY, BookISBNSearchActivity.BY_SCAN);
        a.startActivityForResult(i, UniqueId.ACTIVITY_CREATE_BOOK_SCAN);
    }

    /**
     * Load the Search by ISBN Activity
     */
    private void createBookISBN(@NonNull final Activity a, String by) {
        Intent i = new Intent(a, BookISBNSearchActivity.class);
        i.putExtra(BookISBNSearchActivity.BKEY_BY, by);
        a.startActivityForResult(i, UniqueId.ACTIVITY_CREATE_BOOK_ISBN);
    }

    /**
     * Load the BookDetailsActivity Activity
     */
    private void createBook(@NonNull final Activity a) {
        Intent i = new Intent(a, BookDetailsActivity.class);
        a.startActivityForResult(i, UniqueId.ACTIVITY_CREATE_BOOK_MANUALLY);
    }

//    /**
//     * Load the BookDetailsActivity activity based on the provided id. Also open to the provided tab
//     *
//     * @param id  The id of the book to edit
//     * @param tab Which tab to open first
//     */
//    public static void startEditMode(@NonNull final Activity a, final long id, final int tab) {
//        Intent i = new Intent(a, BookDetailsActivity.class);
//        i.putExtra(DatabaseDefinitions.KEY_ID, id);
//        i.putExtra(BookDetailsActivity.TAB, tab);
//        a.startActivityForResult(i, UniqueId.ACTIVITY_EDIT_BOOK);
//        return;
//    }
}
