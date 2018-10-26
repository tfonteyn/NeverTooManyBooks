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
import android.support.annotation.DrawableRes;
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
    public MenuItem addItem(@NonNull final Menu menu, final int id, @StringRes final int resId, @DrawableRes final int icon) {
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
        SubMenu subMenu = menu.addSubMenu(0, R.id.SUBMENU_BOOK_ADD,
                mSort++,
                BookCatalogueApp.getResourceString(R.string.menu_insert) + "&hellip;");

        subMenu.setIcon(R.drawable.ic_add);
        subMenu.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        {
            subMenu.add(Menu.NONE, R.id.MENU_BOOK_ADD_BY_SCAN, mSort++, R.string.scan_barcode_isbn)
                    .setIcon(R.drawable.ic_add_a_photo);
            subMenu.add(Menu.NONE, R.id.MENU_BOOK_ADD_BY_SEARCH_ISBN, mSort++, R.string.enter_isbn)
                    .setIcon(R.drawable.ic_zoom_in);
            subMenu.add(Menu.NONE, R.id.MENU_BOOK_ADD_BY_SEARCH_TEXT, mSort++, R.string.search_internet)
                    .setIcon(R.drawable.ic_zoom_in);
            subMenu.add(Menu.NONE, R.id.MENU_BOOK_ADD_MANUALLY, mSort++, R.string.menu_add_book_manually)
                    .setIcon(R.drawable.ic_add);
        }
    }

    /**
     * Handle the default menu items
     *
     * @param activity Calling activity
     * @param menuItem     The item selected
     *
     * @return <tt>true</tt> if handled
     */
    public boolean onOptionsItemSelected(@NonNull final Activity activity, @NonNull final MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.MENU_BOOK_ADD_BY_SCAN:
                addBookByScanning(activity);
                return true;
            case R.id.MENU_BOOK_ADD_BY_SEARCH_ISBN:
                addBookBySearch(activity, BookSearchActivity.BY_ISBN);
                return true;
            case R.id.MENU_BOOK_ADD_BY_SEARCH_TEXT:
                addBookBySearch(activity, BookSearchActivity.BY_TEXT);
                return true;
            case R.id.MENU_BOOK_ADD_MANUALLY:
                addBookManually(activity);
                return true;
        }
        return false;
    }

    private void addBookByScanning(@NonNull final Activity activity) {
        Intent intent = new Intent(activity, BookSearchActivity.class);
        intent.putExtra(BookSearchActivity.REQUEST_KEY_BY, BookSearchActivity.BY_SCAN);
        activity.startActivityForResult(intent, BookSearchActivity.REQUEST_CODE_SCAN); /* f1e0d846-852e-451b-9077-6daa5d94f37d */
    }

    private void addBookBySearch(@NonNull final Activity activity, @NonNull final String by) {
        Intent intent = new Intent(activity, BookSearchActivity.class);
        intent.putExtra(BookSearchActivity.REQUEST_KEY_BY, by);
        activity.startActivityForResult(intent, BookSearchActivity.REQUEST_CODE_SEARCH); /* 59fd9653-f033-40b5-bee8-f1dfa5b5be6b */
    }

    private void addBookManually(@NonNull final Activity activity) {
        Intent intent = new Intent(activity, EditBookActivity.class);
        activity.startActivityForResult(intent, EditBookActivity.REQUEST_CODE); /* 88a6c414-2d3b-4637-9044-b7291b6b9100 */
    }
}
