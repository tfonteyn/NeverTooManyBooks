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

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.cursors.CursorMapper;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searches.amazon.AmazonManager;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.IsfdbManager;
import com.hardbacknutter.nevertoomanybooks.searches.librarything.LibraryThingManager;
import com.hardbacknutter.nevertoomanybooks.searches.openlibrary.OpenLibraryManager;
import com.hardbacknutter.nevertoomanybooks.searches.stripinfo.StripInfoManager;

/**
 * Method isolated here to make it easier to add NEWTHINGS: add new site specific ID
 */
final class MenuHandler {

    private MenuHandler() {
    }

    static void prepareOptionalMenus(@NonNull final Menu menu,
                                     @NonNull final Book book) {

        boolean hasIsfdbId = 0 != book.getLong(DBDefinitions.KEY_EID_ISFDB);
        boolean hasGoodreadsId = 0 != book.getLong(DBDefinitions.KEY_EID_GOODREADS_BOOK);
        boolean hasLibraryThingId = 0 != book.getLong(DBDefinitions.KEY_EID_LIBRARY_THING);
        boolean hasStripInfoBeId = 0 != book.getLong(DBDefinitions.KEY_EID_STRIP_INFO_BE);
        boolean hasOpenLibraryId = !book.getString(DBDefinitions.KEY_EID_OPEN_LIBRARY).isEmpty();

        boolean hasAuthor = !book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY).isEmpty();
        boolean hasSeries = !book.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY).isEmpty();

        prepareOpenOnWebsiteMenu(menu, hasIsfdbId, hasGoodreadsId, hasLibraryThingId,
                                 hasStripInfoBeId, hasOpenLibraryId);
        prepareOpenOnWebsiteAmazonMenu(menu, hasAuthor, hasSeries);
    }

    static void prepareOptionalMenus(@NonNull final Menu menu,
                                     @NonNull final CursorMapper row) {

        boolean hasIsfdbId = 0 != row.getLong(DBDefinitions.KEY_EID_ISFDB);
        boolean hasGoodreadsId = 0 != row.getLong(DBDefinitions.KEY_EID_GOODREADS_BOOK);
        boolean hasLibraryThingId = 0 != row.getLong(DBDefinitions.KEY_EID_LIBRARY_THING);
        boolean hasStripInfoBeId = 0 != row.getLong(DBDefinitions.KEY_EID_STRIP_INFO_BE);
        boolean hasOpenLibraryId = !row.getString(DBDefinitions.KEY_EID_OPEN_LIBRARY).isEmpty();

        boolean hasAuthor = row.contains(DBDefinitions.KEY_FK_AUTHOR)
                            && row.getLong(DBDefinitions.KEY_FK_AUTHOR) > 0;
        boolean hasSeries = row.contains(DBDefinitions.KEY_FK_SERIES)
                            && row.getLong(DBDefinitions.KEY_FK_SERIES) > 0;


        prepareOpenOnWebsiteMenu(menu, hasIsfdbId, hasGoodreadsId, hasLibraryThingId,
                                 hasStripInfoBeId, hasOpenLibraryId);
        prepareOpenOnWebsiteAmazonMenu(menu, hasAuthor, hasSeries);
    }

    private static void prepareOpenOnWebsiteMenu(@NonNull final Menu menu,
                                                 final boolean hasIsfdbId,
                                                 final boolean hasGoodreadsId,
                                                 final boolean hasLibraryThingId,
                                                 final boolean hasStripInfoBeId,
                                                 final boolean hasOpenLibraryId) {

        MenuItem subMenu = menu.findItem(R.id.SUBMENU_VIEW_BOOK_AT_SITE);
        if (subMenu == null) {
            return;
        }

        subMenu.setVisible(hasIsfdbId
                           || hasGoodreadsId
                           || hasLibraryThingId
                           || hasOpenLibraryId
                           || hasStripInfoBeId);

        menu.findItem(R.id.MENU_VIEW_BOOK_AT_ISFDB).setVisible(hasIsfdbId);
        menu.findItem(R.id.MENU_VIEW_BOOK_AT_GOODREADS).setVisible(hasGoodreadsId);
        menu.findItem(R.id.MENU_VIEW_BOOK_AT_LIBRARY_THING).setVisible(hasLibraryThingId);
        menu.findItem(R.id.MENU_VIEW_BOOK_AT_OPEN_LIBRARY).setVisible(hasOpenLibraryId);
        menu.findItem(R.id.MENU_VIEW_BOOK_AT_STRIP_INFO_BE).setVisible(hasStripInfoBeId);
    }

    private static void prepareOpenOnWebsiteAmazonMenu(@NonNull final Menu menu,
                                                       final boolean hasAuthor,
                                                       final boolean hasSeries) {

        MenuItem subMenu = menu.findItem(R.id.SUBMENU_AMAZON_SEARCH);
        if (subMenu == null) {
            return;
        }

        subMenu.setVisible(hasAuthor || hasSeries);

        menu.findItem(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR)
            .setVisible(hasAuthor);
        menu.findItem(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES)
            .setVisible(hasAuthor && hasSeries);
        menu.findItem(R.id.MENU_AMAZON_BOOKS_IN_SERIES)
            .setVisible(hasSeries);
    }

    static boolean handleOpenOnWebsiteMenus(@NonNull final Context context,
                                            @NonNull final MenuItem menuItem,
                                            @NonNull final Book book) {
        switch (menuItem.getItemId()) {
            case R.id.MENU_VIEW_BOOK_AT_GOODREADS:
                GoodreadsManager
                        .openWebsite(context, book.getLong(DBDefinitions.KEY_EID_GOODREADS_BOOK));
                return true;

            case R.id.MENU_VIEW_BOOK_AT_ISFDB:
                IsfdbManager.openWebsite(context, book.getLong(DBDefinitions.KEY_EID_ISFDB));
                return true;

            case R.id.MENU_VIEW_BOOK_AT_LIBRARY_THING:
                LibraryThingManager
                        .openWebsite(context, book.getLong(DBDefinitions.KEY_EID_LIBRARY_THING));
                return true;

            case R.id.MENU_VIEW_BOOK_AT_OPEN_LIBRARY:
                OpenLibraryManager
                        .openWebsite(context, book.getString(DBDefinitions.KEY_EID_OPEN_LIBRARY));
                return true;

            case R.id.MENU_VIEW_BOOK_AT_STRIP_INFO_BE:
                StripInfoManager
                        .openWebsite(context, book.getLong(DBDefinitions.KEY_EID_STRIP_INFO_BE));
                return true;


//            case R.id.SUBMENU_AMAZON_SEARCH: {
//                // after the user selects the submenu, we make individual items visible/hidden.
//                SubMenu menu = menuItem.getSubMenu();
//                boolean hasAuthor = !book.getParcelableArrayList(
//                        UniqueId.BKEY_AUTHOR_ARRAY).isEmpty();
//                boolean hasSeries = !book.getParcelableArrayList(
//                        UniqueId.BKEY_SERIES_ARRAY).isEmpty();
//
//                menu.findItem(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR)
//                    .setVisible(hasAuthor);
//                menu.findItem(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES)
//                    .setVisible(hasAuthor && hasSeries);
//                menu.findItem(R.id.MENU_AMAZON_BOOKS_IN_SERIES)
//                    .setVisible(hasSeries);
//                // let the normal call flow go on, it will display the submenu
//                return false;
//            }
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
