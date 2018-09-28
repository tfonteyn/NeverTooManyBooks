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
import android.support.annotation.StringRes;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;

/**
 * Class to make building menus simpler. Implements some default menu items and allows
 * selecting which need to be added as well as the addition of custom items.
 *
 * @author Philip Warner
 */
public class MenuHandler {

    private int mSort = 0;

    public MenuHandler(@NonNull final Menu menu) {
        init(menu);
    }

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
     * @param menu  Root menu
     * @param id    Menu item ID
     * @param resId String ID to display
     * @param icon  (Optional) Icon for menu item, 0 for none
     *
     * @return The new item
     */
    public MenuItem addItem(@NonNull final Menu menu, final int id, @StringRes final int resId, final int icon) {
        MenuItem item = menu.add(Menu.NONE, id, mSort++, resId);
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
        SubMenu subMenu = menu.addSubMenu(0, R.id.SUBMENU_ADD_BOOK,
                mSort++,
                BookCatalogueApp.getResourceString(R.string.menu_insert) + "&hellip;");

        subMenu.setIcon(R.drawable.ic_add);
        subMenu.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        {
            subMenu.add(Menu.NONE, R.id.MENU_ADD_BOOK_BARCODE, mSort++, R.string.scan_barcode_isbn)
                    .setIcon(R.drawable.ic_add_a_photo);
            subMenu.add(Menu.NONE, R.id.MENU_ADD_BOOK_ISBN, mSort++, R.string.enter_isbn)
                    .setIcon(R.drawable.ic_zoom_in);
            subMenu.add(Menu.NONE, R.id.MENU_ADD_BOOK_NAMES, mSort++, R.string.search_internet)
                    .setIcon(R.drawable.ic_zoom_in);
            subMenu.add(Menu.NONE, R.id.MENU_ADD_BOOK_MANUALLY, mSort++, R.string.add_manually)
                    .setIcon(R.drawable.ic_add);
        }
    }

    /**
     * Handle the default menu items
     *
     * @param activity Calling activity
     * @param item     The item selected
     *
     * @return True, if handled
     */
    public boolean onOptionsItemSelected(@NonNull final Activity activity, @NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_ADD_BOOK_BARCODE:
                createBookScan(activity);
                return true;
            case R.id.MENU_ADD_BOOK_ISBN:
                createBookISBN(activity, BookISBNSearchActivity.BY_ISBN);
                return true;
            case R.id.MENU_ADD_BOOK_NAMES:
                createBookISBN(activity, BookISBNSearchActivity.BY_NAME);
                return true;
            case R.id.MENU_ADD_BOOK_MANUALLY:
                createBook(activity);
                return true;
            // not used for now, calls activity.onSearchRequested(); but not implemented (yet?)
            case R.id.MENU_SEARCH:
                activity.onSearchRequested();
                return true;
        }
        return false;
    }

    /**
     * Load the Search by ISBN Activity to begin scanning.
     */
    private void createBookScan(@NonNull final Activity activity) {
        Intent intent = new Intent(activity, BookISBNSearchActivity.class);
        intent.putExtra(BookISBNSearchActivity.BKEY_BY, BookISBNSearchActivity.BY_SCAN);
        activity.startActivityForResult(intent, UniqueId.ACTIVITY_REQUEST_CODE_ADD_BOOK_SCAN);
    }

    /**
     * Load the Search by ISBN Activity
     */
    private void createBookISBN(@NonNull final Activity activity, String by) {
        Intent intent = new Intent(activity, BookISBNSearchActivity.class);
        intent.putExtra(BookISBNSearchActivity.BKEY_BY, by);
        activity.startActivityForResult(intent, UniqueId.ACTIVITY_REQUEST_CODE_ADD_BOOK_ISBN);
    }

    /**
     * Load the BookDetailsActivity Activity
     */
    private void createBook(@NonNull final Activity activity) {
        Intent intent = new Intent(activity, BookDetailsActivity.class);
        activity.startActivityForResult(intent, UniqueId.ACTIVITY_REQUEST_CODE_ADD_BOOK_MANUALLY);
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
//        a.startActivityForResult(i, UniqueId.ACTIVITY_REQUEST_CODE_EDIT_BOOK);
//        return;
//    }
}
