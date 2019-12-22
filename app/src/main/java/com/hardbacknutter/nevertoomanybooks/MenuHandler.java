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
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.cursors.CursorMapper;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.amazon.AmazonManager;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.IsfdbManager;
import com.hardbacknutter.nevertoomanybooks.searches.librarything.LibraryThingManager;
import com.hardbacknutter.nevertoomanybooks.searches.openlibrary.OpenLibraryManager;
import com.hardbacknutter.nevertoomanybooks.searches.stripinfo.StripInfoManager;

final class MenuHandler {

    private MenuHandler() {
    }

    static void prepareOptionalMenus(@NonNull final Menu menu,
                                     @NonNull final Book book) {

        boolean hasAuthor = !book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY).isEmpty();
        boolean hasSeries = !book.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY).isEmpty();

        prepareOpenOnWebsiteMenu(menu, book.getNativeIds());
        prepareOpenOnWebsiteAmazonMenu(menu, hasAuthor, hasSeries);
    }

    static void prepareOptionalMenus(@NonNull final Menu menu,
                                     @NonNull final CursorMapper row) {

        SparseArray<String> nativeIds = new SparseArray<>();
        for (String key : DBDefinitions.NATIVE_ID_KEYS) {
            String value = row.getString(key);
            if (!value.isEmpty() && !"0".equals(value)) {
                nativeIds.put(SearchSites.getSiteIdFromDBDefinitions(key), value);
            }
        }

        boolean hasAuthor = row.contains(DBDefinitions.KEY_FK_AUTHOR)
                            && row.getLong(DBDefinitions.KEY_FK_AUTHOR) > 0;
        boolean hasSeries = row.contains(DBDefinitions.KEY_FK_SERIES)
                            && row.getLong(DBDefinitions.KEY_FK_SERIES) > 0;

        prepareOpenOnWebsiteMenu(menu, nativeIds);
        prepareOpenOnWebsiteAmazonMenu(menu, hasAuthor, hasSeries);
    }

    private static void prepareOpenOnWebsiteMenu(@NonNull final Menu menu,
                                                 @NonNull final SparseArray<String> nativeIds) {

        MenuItem subMenuItem = menu.findItem(R.id.SUBMENU_VIEW_BOOK_AT_SITE);
        if (subMenuItem == null) {
            return;
        }

        boolean show = nativeIds.size() > 0;
        subMenuItem.setVisible(show);
        if (show) {
            SubMenu sm = subMenuItem.getSubMenu();
            // display/hide menu items on their presence in the nativeIds list.
            for (int item = 0; item < sm.size(); item++) {
                int menuId = sm.getItem(item).getItemId();
                sm.findItem(menuId)
                  .setVisible(nativeIds.get(SearchSites.getSiteIdFromResId(menuId)) != null);
            }
        }
    }

    private static void prepareOpenOnWebsiteAmazonMenu(@NonNull final Menu menu,
                                                       final boolean hasAuthor,
                                                       final boolean hasSeries) {

        MenuItem subMenuItem = menu.findItem(R.id.SUBMENU_AMAZON_SEARCH);
        if (subMenuItem == null) {
            return;
        }

        boolean show = hasAuthor || hasSeries;
        subMenuItem.setVisible(show);
        if (show) {
            SubMenu sm = subMenuItem.getSubMenu();
            sm.findItem(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR)
              .setVisible(hasAuthor);
            sm.findItem(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES)
              .setVisible(hasAuthor && hasSeries);
            sm.findItem(R.id.MENU_AMAZON_BOOKS_IN_SERIES)
              .setVisible(hasSeries);
        }
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
                IsfdbManager
                        .openWebsite(context, book.getLong(DBDefinitions.KEY_EID_ISFDB));
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

            //NEWTHINGS: add new site specific ID: add case

            /* ********************************************************************************** */
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

    static boolean handleOpenOnWebsiteMenus(@NonNull final Context context,
                                            @NonNull final MenuItem menuItem,
                                            @NonNull final CursorMapper row) {
        switch (menuItem.getItemId()) {
            case R.id.MENU_VIEW_BOOK_AT_GOODREADS:
                GoodreadsManager
                        .openWebsite(context, row.getLong(DBDefinitions.KEY_EID_GOODREADS_BOOK));
                return true;

            case R.id.MENU_VIEW_BOOK_AT_ISFDB:
                IsfdbManager
                        .openWebsite(context, row.getLong(DBDefinitions.KEY_EID_ISFDB));
                return true;

            case R.id.MENU_VIEW_BOOK_AT_LIBRARY_THING:
                LibraryThingManager
                        .openWebsite(context, row.getLong(DBDefinitions.KEY_EID_LIBRARY_THING));
                return true;

            case R.id.MENU_VIEW_BOOK_AT_OPEN_LIBRARY:
                OpenLibraryManager
                        .openWebsite(context, row.getString(DBDefinitions.KEY_EID_OPEN_LIBRARY));
                return true;

            case R.id.MENU_VIEW_BOOK_AT_STRIP_INFO_BE:
                StripInfoManager
                        .openWebsite(context, row.getLong(DBDefinitions.KEY_EID_STRIP_INFO_BE));
                return true;

            //NEWTHINGS: add new site specific ID: add case

            default:
                return false;
        }
    }
}
