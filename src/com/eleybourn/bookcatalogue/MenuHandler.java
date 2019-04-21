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

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.searches.amazon.AmazonSearchPage;

/**
 * Handles re-usable menu items; both to create and to handle.
 *
 * Defines some menu 'order' variables, to ensure certain menu's have a fixed spot.
 */
final class MenuHandler {

    public static final int MENU_ORDER_LENDING = 90;
    public static final int MENU_ORDER_SHARE = 95;
    private static final int MENU_ORDER_AMAZON = 99;

    private MenuHandler() {
    }

    /**
     * Add SubMenu for book creation.
     * <p>
     * Group: R.id.SUBMENU_BOOK_ADD
     *
     * @param menu Root menu
     */
    static void addCreateBookSubMenu(@NonNull final Menu menu) {
        SubMenu subMenu = menu.addSubMenu(R.id.SUBMENU_BOOK_ADD, R.id.SUBMENU_BOOK_ADD,
                                          0, R.string.menu_add_book)
                              .setIcon(R.drawable.ic_add);

        subMenu.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        subMenu.add(R.id.SUBMENU_BOOK_ADD, R.id.MENU_BOOK_ADD_BY_SCAN, 0,
                    R.string.menu_add_book_by_barcode_scan)
               .setIcon(R.drawable.ic_add_a_photo);
        subMenu.add(R.id.SUBMENU_BOOK_ADD, R.id.MENU_BOOK_ADD_BY_SEARCH_ISBN, 0,
                    R.string.menu_add_book_by_isbn)
               .setIcon(R.drawable.ic_zoom_in);
        subMenu.add(R.id.SUBMENU_BOOK_ADD, R.id.MENU_BOOK_ADD_BY_SEARCH_TEXT, 0,
                    R.string.menu_search_internet)
               .setIcon(R.drawable.ic_zoom_in);
        subMenu.add(R.id.SUBMENU_BOOK_ADD, R.id.MENU_BOOK_ADD_MANUALLY, 0,
                    R.string.menu_add_book_manually)
               .setIcon(R.drawable.ic_keyboard);
    }

    /**
     * Handle the menu items created by {@link #addCreateBookSubMenu(Menu)}.
     *
     * @param activity Calling activity
     * @param menuItem The item selected
     *
     * @return <tt>true</tt> if handled
     */
    static boolean handleBookSubMenu(@NonNull final Activity activity,
                                     @NonNull final MenuItem menuItem) {
        Intent intent;
        switch (menuItem.getItemId()) {
            case R.id.MENU_BOOK_ADD_BY_SCAN:
                intent = new Intent(activity, BookSearchActivity.class)
                        .putExtra(UniqueId.BKEY_FRAGMENT_TAG, BookSearchByIsbnFragment.TAG)
                        .putExtra(BookSearchByIsbnFragment.BKEY_IS_SCAN_MODE, true);
                activity.startActivityForResult(intent, UniqueId.REQ_BOOK_SEARCH);
                return true;

            case R.id.MENU_BOOK_ADD_BY_SEARCH_ISBN:
                intent = new Intent(activity, BookSearchActivity.class)
                        .putExtra(UniqueId.BKEY_FRAGMENT_TAG, BookSearchByIsbnFragment.TAG);
                activity.startActivityForResult(intent, UniqueId.REQ_BOOK_SEARCH);
                return true;

            case R.id.MENU_BOOK_ADD_BY_SEARCH_TEXT:
                intent = new Intent(activity, BookSearchActivity.class)
                        .putExtra(UniqueId.BKEY_FRAGMENT_TAG, BookSearchByTextFragment.TAG);
                activity.startActivityForResult(intent, UniqueId.REQ_BOOK_SEARCH);
                return true;

            case R.id.MENU_BOOK_ADD_MANUALLY:
                intent = new Intent(activity, EditBookActivity.class);
                activity.startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
                return true;

            default:
                return false;
        }
    }

    /**
     * Add SubMenu for Amazon searches.
     * <p>
     * Normally called from your {@link Fragment#onCreateOptionsMenu}.
     *
     * @param menu Root menu
     *
     * @return the submenu for further manipulation if needed.
     */
    static SubMenu addAmazonSearchSubMenu(@NonNull final Menu menu) {
        SubMenu subMenu = menu.addSubMenu(R.id.SUBMENU_AMAZON_SEARCH, R.id.SUBMENU_AMAZON_SEARCH,
                                          MENU_ORDER_AMAZON, R.string.amazon_ellipsis)
                              .setIcon(R.drawable.ic_search)
                              .setHeaderIcon(R.drawable.ic_search);

        // we use the group to make the entry visible/invisible, hence it's == the actual id.
        subMenu.add(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR, 0,
                    R.string.menu_amazon_books_by_author);
        subMenu.add(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES,
                    R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES, 0,
                    R.string.menu_amazon_books_by_author_in_series);
        subMenu.add(R.id.MENU_AMAZON_BOOKS_IN_SERIES, R.id.MENU_AMAZON_BOOKS_IN_SERIES, 0,
                    R.string.menu_amazon_books_in_series);

        return subMenu;
    }

    /**
     * Set visibility of the Amazon sub menu itself.
     * <p>
     * After the user selects the submenu, individual menu items are made visible/hidden in:
     * {@link #handleAmazonSearchSubMenu}.
     * <p>
     * Normally called from your {@link Fragment#onPrepareOptionsMenu(Menu)}.
     *
     * @param menu Root menu
     * @param book the current book
     */
    static void prepareAmazonSearchSubMenu(final Menu menu,
                                           final Book book) {
        boolean hasAuthor = !book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY).isEmpty();
        boolean hasSeries = !book.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY).isEmpty();
        menu.setGroupVisible(R.id.SUBMENU_AMAZON_SEARCH, hasAuthor || hasSeries);
    }

    /**
     * Handle the menu items created by {@link #addAmazonSearchSubMenu(Menu)}.
     *
     * @param activity Calling activity
     * @param menuItem The item selected
     * @param book     the book upon to act
     *
     * @return <tt>true</tt> if handled
     */
    static boolean handleAmazonSearchSubMenu(@NonNull final Activity activity,
                                             @NonNull final MenuItem menuItem,
                                             @NonNull final Book book) {
        switch (menuItem.getItemId()) {
            case R.id.SUBMENU_AMAZON_SEARCH:
                // after the user selects the submenu, we make individual items visible/hidden.
                SubMenu menu = menuItem.getSubMenu();
                boolean hasAuthor = !book.getParcelableArrayList(
                        UniqueId.BKEY_AUTHOR_ARRAY).isEmpty();
                boolean hasSeries = !book.getParcelableArrayList(
                        UniqueId.BKEY_SERIES_ARRAY).isEmpty();

                menu.setGroupVisible(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR, hasAuthor);
                menu.setGroupVisible(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES,
                                     hasAuthor && hasSeries);
                menu.setGroupVisible(R.id.MENU_AMAZON_BOOKS_IN_SERIES, hasSeries);
                // let the normal call flow go on, it will display the submenu
                return false;

            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR:
                AmazonSearchPage.open(activity, book.getPrimaryAuthor(), null);
                return true;

            case R.id.MENU_AMAZON_BOOKS_IN_SERIES:
                AmazonSearchPage.open(activity, null, book.getPrimarySeries());
                return true;

            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES:
                AmazonSearchPage.open(activity, book.getPrimaryAuthor(), book.getPrimarySeries());
                return true;

            default:
                return false;
        }
    }
}
