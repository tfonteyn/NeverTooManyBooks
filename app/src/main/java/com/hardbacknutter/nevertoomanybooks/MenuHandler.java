/*
 * @Copyright 2020 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.NameValueHolder;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.amazon.AmazonSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.IsfdbSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.librarything.LibraryThingSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.openlibrary.OpenLibrarySearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.stripinfo.StripInfoSearchEngine;

final class MenuHandler {

    private MenuHandler() {
    }

    static void prepareOptionalMenus(@NonNull final Menu menu,
                                     @NonNull final Book book) {

        boolean hasAuthor = !book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY).isEmpty();
        boolean hasSeries = !book.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY).isEmpty();

        prepareOpenOnWebsiteMenu(menu, book.getNativeIds());
        prepareSearchOnAmazonMenu(menu, hasAuthor, hasSeries);
    }

    static void prepareOptionalMenus(@NonNull final Menu menu,
                                     @NonNull final CursorRow cursorRow) {

        SparseArray<String> nativeIds = new SparseArray<>();
        for (String key : DBDefinitions.NATIVE_ID_KEYS) {
            String value = cursorRow.getString(key);
            if (!value.isEmpty() && !"0".equals(value)) {
                nativeIds.put(SearchSites.getSiteIdFromDBDefinitions(key), value);
            }
        }

        boolean hasAuthor = cursorRow.contains(DBDefinitions.KEY_FK_AUTHOR)
                            && cursorRow.getLong(DBDefinitions.KEY_FK_AUTHOR) > 0;
        boolean hasSeries = cursorRow.contains(DBDefinitions.KEY_FK_SERIES)
                            && cursorRow.getLong(DBDefinitions.KEY_FK_SERIES) > 0;

        prepareOpenOnWebsiteMenu(menu, nativeIds);
        prepareSearchOnAmazonMenu(menu, hasAuthor, hasSeries);
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

    static boolean handleOpenOnWebsiteMenus(@NonNull final Context context,
                                            @NonNull final MenuItem menuItem,
                                            @NonNull final NameValueHolder holder) {
        switch (menuItem.getItemId()) {
            case R.id.MENU_VIEW_BOOK_AT_AMAZON:
                AmazonSearchEngine.openWebsite(
                        context, holder.getString(DBDefinitions.KEY_ISBN));
                return true;

            case R.id.MENU_VIEW_BOOK_AT_GOODREADS:
                GoodreadsSearchEngine.openWebsite(
                        context, holder.getLong(DBDefinitions.KEY_EID_GOODREADS_BOOK));
                return true;

            case R.id.MENU_VIEW_BOOK_AT_ISFDB:
                IsfdbSearchEngine.openWebsite(
                        context, holder.getLong(DBDefinitions.KEY_EID_ISFDB));
                return true;

            case R.id.MENU_VIEW_BOOK_AT_LIBRARY_THING:
                LibraryThingSearchEngine.openWebsite(
                        context, holder.getLong(DBDefinitions.KEY_EID_LIBRARY_THING));
                return true;

            case R.id.MENU_VIEW_BOOK_AT_OPEN_LIBRARY:
                OpenLibrarySearchEngine.openWebsite(
                        context, holder.getString(DBDefinitions.KEY_EID_OPEN_LIBRARY));
                return true;

            case R.id.MENU_VIEW_BOOK_AT_STRIP_INFO_BE:
                StripInfoSearchEngine.openWebsite(
                        context, holder.getLong(DBDefinitions.KEY_EID_STRIP_INFO_BE));
                return true;

            //NEWTHINGS: add new site specific ID: add case

            default:
                return false;
        }
    }

    private static void prepareSearchOnAmazonMenu(@NonNull final Menu menu,
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
}
