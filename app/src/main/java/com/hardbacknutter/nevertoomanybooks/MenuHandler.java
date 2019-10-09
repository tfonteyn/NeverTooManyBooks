/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searches.amazon.AmazonManager;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.IsfdbManager;
import com.hardbacknutter.nevertoomanybooks.searches.librarything.LibraryThingManager;
import com.hardbacknutter.nevertoomanybooks.searches.openlibrary.OpenLibraryManager;
import com.hardbacknutter.nevertoomanybooks.searches.stripinfo.StripInfoManager;

/**
 * Handles re-usable menu items; both to create and to handle.
 * <p>
 * Defines some menu 'order' variables, to ensure certain menu's have a fixed spot.
 */
final class MenuHandler {

    static final int ORDER_HIDE_KEYBOARD = 1;

    static final int ORDER_SEARCH_SITES = 20;

    static final int ORDER_UPDATE_FIELDS = 50;
    static final int ORDER_LENDING = 60;

    static final int ORDER_SHARE = 70;

    static final int ORDER_SEND_TO_GOODREADS = 80;

    static final int ORDER_VIEW_BOOK_AT_SITE = 90;
    private static final int ORDER_AMAZON = 99;

    private MenuHandler() {
    }

    /**
     * Add SubMenu for viewing a book on external sites.
     * <p>
     * Normally called from your {@link Fragment#onCreateOptionsMenu}.
     *
     * @param menu Root menu
     */
    static void addViewBookSubMenu(@NonNull final Menu menu) {
        SubMenu subMenu = menu.addSubMenu(Menu.NONE, R.id.SUBMENU_VIEW_BOOK_AT_SITE,
                                          ORDER_VIEW_BOOK_AT_SITE,
                                          R.string.menu_view_book_at)
                              .setIcon(R.drawable.ic_link);
        //NEWTHINGS: add new site specific ID: add
        subMenu.add(Menu.NONE, R.id.MENU_VIEW_BOOK_AT_GOODREADS, 0, R.string.goodreads);
        subMenu.add(Menu.NONE, R.id.MENU_VIEW_BOOK_AT_LIBRARY_THING, 0, R.string.library_thing);
        subMenu.add(Menu.NONE, R.id.MENU_VIEW_BOOK_AT_STRIP_INFO_BE, 0, R.string.stripinfo);
        subMenu.add(Menu.NONE, R.id.MENU_VIEW_BOOK_AT_ISFDB, 0, R.string.isfdb);
        subMenu.add(Menu.NONE, R.id.MENU_VIEW_BOOK_AT_OPEN_LIBRARY, 0, R.string.open_library);
    }

    static void prepareViewBookSubMenu(@NonNull final Menu menu,
                                       @NonNull final Book book) {
        menu.findItem(R.id.SUBMENU_VIEW_BOOK_AT_SITE).setVisible(book.hasExternalId());
    }

    static boolean handleViewBookSubMenu(@NonNull final Context context,
                                         @NonNull final MenuItem menuItem,
                                         @NonNull final Book book) {
        switch (menuItem.getItemId()) {
            case R.id.SUBMENU_VIEW_BOOK_AT_SITE: {
                // after the user selects the submenu, we make individual items visible/hidden.
                SubMenu menu = menuItem.getSubMenu();
                //NEWTHINGS: add new site specific ID: add
                menu.findItem(R.id.MENU_VIEW_BOOK_AT_GOODREADS)
                    .setVisible(0 != book.getLong(DBDefinitions.KEY_GOODREADS_BOOK_ID));
                menu.findItem(R.id.MENU_VIEW_BOOK_AT_LIBRARY_THING)
                    .setVisible(0 != book.getLong(DBDefinitions.KEY_LIBRARY_THING_ID));
                menu.findItem(R.id.MENU_VIEW_BOOK_AT_STRIP_INFO_BE)
                    .setVisible(0 != book.getLong(DBDefinitions.KEY_STRIP_INFO_BE_ID));
                menu.findItem(R.id.MENU_VIEW_BOOK_AT_ISFDB)
                    .setVisible(0 != book.getLong(DBDefinitions.KEY_ISFDB_ID));
                menu.findItem(R.id.MENU_VIEW_BOOK_AT_OPEN_LIBRARY)
                    .setVisible(!book.getString(DBDefinitions.KEY_OPEN_LIBRARY_ID).isEmpty());
                // let the normal call flow go on, it will display the submenu
                return false;
            }
            case R.id.MENU_VIEW_BOOK_AT_ISFDB:
                IsfdbManager.openWebsite(context, book.getLong(DBDefinitions.KEY_ISFDB_ID));
                return true;

            case R.id.MENU_VIEW_BOOK_AT_GOODREADS:
                GoodreadsManager.openWebsite(context,
                                             book.getLong(DBDefinitions.KEY_GOODREADS_BOOK_ID));
                return true;

            case R.id.MENU_VIEW_BOOK_AT_LIBRARY_THING:
                LibraryThingManager.openWebsite(context,
                                                book.getLong(DBDefinitions.KEY_LIBRARY_THING_ID));
                return true;

            case R.id.MENU_VIEW_BOOK_AT_OPEN_LIBRARY:
                OpenLibraryManager.openWebsite(context,
                                               book.getString(DBDefinitions.KEY_OPEN_LIBRARY_ID));
                return true;

            case R.id.MENU_VIEW_BOOK_AT_STRIP_INFO_BE:
                StripInfoManager.openWebsite(context,
                                             book.getLong(DBDefinitions.KEY_STRIP_INFO_BE_ID));
                return true;

            //NEWTHINGS: add new site specific ID: add case

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
                                          ORDER_AMAZON, R.string.amazon_ellipsis)
                              .setIcon(R.drawable.ic_search);

        // we use the group to make the entry visible/invisible, hence it's == the actual ID.
        subMenu.add(Menu.NONE, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR, 0,
                    R.string.menu_amazon_books_by_author);
        subMenu.add(Menu.NONE, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES, 0,
                    R.string.menu_amazon_books_by_author_in_series);
        subMenu.add(Menu.NONE, R.id.MENU_AMAZON_BOOKS_IN_SERIES, 0,
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
    static void prepareAmazonSearchSubMenu(@NonNull final Menu menu,
                                           @NonNull final Book book) {
        boolean hasAuthor = !book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY).isEmpty();
        boolean hasSeries = !book.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY).isEmpty();
        menu.findItem(R.id.SUBMENU_AMAZON_SEARCH).setVisible(hasAuthor || hasSeries);
    }

    /**
     * Handle the menu items created by {@link #addAmazonSearchSubMenu(Menu)}.
     *
     * @param context  Current context
     * @param menuItem The item selected
     * @param book     the book upon to act
     *
     * @return {@code true} if handled
     */
    static boolean handleAmazonSearchSubMenu(@NonNull final Context context,
                                             @NonNull final MenuItem menuItem,
                                             @NonNull final Book book) {
        switch (menuItem.getItemId()) {
            case R.id.SUBMENU_AMAZON_SEARCH: {
                // after the user selects the submenu, we make individual items visible/hidden.
                SubMenu menu = menuItem.getSubMenu();
                boolean hasAuthor = !book.getParcelableArrayList(
                        UniqueId.BKEY_AUTHOR_ARRAY).isEmpty();
                boolean hasSeries = !book.getParcelableArrayList(
                        UniqueId.BKEY_SERIES_ARRAY).isEmpty();

                menu.findItem(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR)
                    .setVisible(hasAuthor);
                menu.findItem(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES)
                    .setVisible(hasAuthor && hasSeries);
                menu.findItem(R.id.MENU_AMAZON_BOOKS_IN_SERIES)
                    .setVisible(hasSeries);
                // let the normal call flow go on, it will display the submenu
                return false;
            }
            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR:
                AmazonManager.openWebsite(context, book.getPrimaryAuthor(context), null);
                return true;

            case R.id.MENU_AMAZON_BOOKS_IN_SERIES:
                AmazonManager.openWebsite(context, null, book.getPrimarySeries());
                return true;

            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES:
                AmazonManager.openWebsite(context,
                                          book.getPrimaryAuthor(context), book.getPrimarySeries());
                return true;

            default:
                return false;
        }
    }
}
