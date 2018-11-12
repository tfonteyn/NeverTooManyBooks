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

/**
 * Handles re-usable menu items; both to create and to handle
 */
public class MenuHandler {

    /**
     * Add SubMenu for book creation.
     *
     * Group: R.id.SUBMENU_BOOK_ADD
     *
     * @param menu Root menu
     */
    public static void addCreateBookSubMenu(final @NonNull Menu menu) {
        SubMenu subMenu = menu.addSubMenu(R.id.SUBMENU_BOOK_ADD, R.id.SUBMENU_BOOK_ADD,
                Menu.NONE,
                BookCatalogueApp.getResourceString(R.string.menu_add_book));

        subMenu.setIcon(R.drawable.ic_add)
                .getItem()
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        subMenu.add(R.id.SUBMENU_BOOK_ADD, R.id.MENU_BOOK_ADD_BY_SCAN, Menu.NONE, R.string.menu_add_book_by_barcode_scan)
                .setIcon(R.drawable.ic_add_a_photo);
        subMenu.add(R.id.SUBMENU_BOOK_ADD, R.id.MENU_BOOK_ADD_BY_SEARCH_ISBN, Menu.NONE, R.string.menu_add_book_by_isbn)
                .setIcon(R.drawable.ic_zoom_in);
        subMenu.add(R.id.SUBMENU_BOOK_ADD, R.id.MENU_BOOK_ADD_BY_SEARCH_TEXT, Menu.NONE, R.string.search_internet)
                .setIcon(R.drawable.ic_zoom_in);
        subMenu.add(R.id.SUBMENU_BOOK_ADD, R.id.MENU_BOOK_ADD_MANUALLY, Menu.NONE, R.string.menu_add_book_manually)
                .setIcon(R.drawable.ic_add);
    }

    /**
     * Add SubMenu for book creation.
     *
     * @param menu Root menu
     */
    public static void addAmazonSearchSubMenu(final @NonNull Menu menu) {
        SubMenu subMenu = menu.addSubMenu(R.id.SUBMENU_AMAZON_SEARCH, R.id.SUBMENU_AMAZON_SEARCH,
                Menu.NONE,
                BookCatalogueApp.getResourceString(R.string.amazon_ellipsis));

        subMenu.setIcon(R.drawable.ic_search);
        // we use the group to make the entry visible/invisible, hence it's == the actual id.
        subMenu.add(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR, Menu.NONE, R.string.menu_amazon_books_by_author)
                .setIcon(R.drawable.ic_search);
        subMenu.add(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES, Menu.NONE, R.string.menu_amazon_books_by_author_in_series)
                .setIcon(R.drawable.ic_search);
        subMenu.add(R.id.MENU_AMAZON_BOOKS_IN_SERIES, R.id.MENU_AMAZON_BOOKS_IN_SERIES, Menu.NONE, R.string.menu_amazon_books_in_series)
                .setIcon(R.drawable.ic_search);

    }

    /**
     * Handle the menu items created here.
     *
     * @param activity Calling activity
     * @param menuItem The item selected
     *
     * @return <tt>true</tt> if handled
     */
    public static boolean onOptionsItemSelectedBookSubMenu(final @NonNull Activity activity,
                                                           final @NonNull MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.MENU_BOOK_ADD_BY_SCAN: {
                Intent intent = new Intent(activity, BookSearchActivity.class);
                intent.putExtra(BookSearchActivity.REQUEST_BKEY_BY, BookSearchActivity.BY_SCAN);
                activity.startActivityForResult(intent, BookSearchActivity.REQUEST_CODE_SCAN); /* f1e0d846-852e-451b-9077-6daa5d94f37d */
                return true;
            }
            case R.id.MENU_BOOK_ADD_BY_SEARCH_ISBN: {
                Intent intent = new Intent(activity, BookSearchActivity.class);
                intent.putExtra(BookSearchActivity.REQUEST_BKEY_BY, BookSearchActivity.BY_ISBN);
                activity.startActivityForResult(intent, BookSearchActivity.REQUEST_CODE_SEARCH); /* 59fd9653-f033-40b5-bee8-f1dfa5b5be6b */
                return true;
            }
            case R.id.MENU_BOOK_ADD_BY_SEARCH_TEXT: {
                Intent intent = new Intent(activity, BookSearchActivity.class);
                intent.putExtra(BookSearchActivity.REQUEST_BKEY_BY, BookSearchActivity.BY_TEXT);
                activity.startActivityForResult(intent, BookSearchActivity.REQUEST_CODE_SEARCH); /* 59fd9653-f033-40b5-bee8-f1dfa5b5be6b */
                return true;
            }
            case R.id.MENU_BOOK_ADD_MANUALLY: {
                Intent intent = new Intent(activity, EditBookActivity.class);
                activity.startActivityForResult(intent, EditBookActivity.REQUEST_CODE); /* 88a6c414-2d3b-4637-9044-b7291b6b9100 */
                return true;
            }
        }
        return false;
    }
}
