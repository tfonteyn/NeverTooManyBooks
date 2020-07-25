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

import android.app.Activity;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.widget.SearchView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.Site;

final class MenuHandler {

    private MenuHandler() {
    }

    /**
     * Called from a details screen. i.e. the data comes from a {@link Book}.
     *
     * @param menu to add to
     * @param book data to use
     */
    static void prepareOptionalMenus(@NonNull final Menu menu,
                                     @NonNull final Book book) {

        final boolean hasAuthor = !book.getParcelableArrayList(Book.BKEY_AUTHOR_ARRAY)
                                       .isEmpty();
        final boolean hasSeries = !book.getParcelableArrayList(Book.BKEY_SERIES_ARRAY)
                                       .isEmpty();

        prepareOpenOnWebsiteMenu(menu, book);
        prepareSearchOnAmazonMenu(menu, hasAuthor, hasSeries);
    }

    /**
     * Called from a list screen. i.e. the data comes from a row {@link DataHolder}.
     *
     * @param menu    to add to
     * @param rowData data to use
     */
    static void prepareOptionalMenus(@NonNull final Menu menu,
                                     @NonNull final DataHolder rowData) {

        final boolean hasAuthor = rowData.contains(DBDefinitions.KEY_FK_AUTHOR)
                                  && rowData.getLong(DBDefinitions.KEY_FK_AUTHOR) > 0;
        final boolean hasSeries = rowData.contains(DBDefinitions.KEY_FK_SERIES)
                                  && rowData.getLong(DBDefinitions.KEY_FK_SERIES) > 0;

        prepareOpenOnWebsiteMenu(menu, rowData);
        prepareSearchOnAmazonMenu(menu, hasAuthor, hasSeries);
    }

    private static void prepareOpenOnWebsiteMenu(@NonNull final Menu menu,
                                                 @NonNull final DataHolder dataHolder) {

        final MenuItem subMenuItem = menu.findItem(R.id.SUBMENU_VIEW_BOOK_AT_SITE);
        if (subMenuItem == null) {
            return;
        }

        final SubMenu subMenu = subMenuItem.getSubMenu();
        boolean subMenuVisible = false;
        for (int i = 0; i < subMenu.size(); i++) {
            final MenuItem menuItem = subMenu.getItem(i);
            boolean visible = false;

            final Site.Config config = Site
                    .getConfigByMenuId(menuItem.getItemId());
            if (config != null) {
                final Domain domain = config.getExternalIdDomain();
                if (domain != null) {
                    final String value = dataHolder.getString(domain.getName());
                    if (!value.isEmpty() && !"0".equals(value)) {
                        visible = true;
                    }
                }
            }

            menuItem.setVisible(visible);
            if (visible) {
                subMenuVisible = true;
            }
        }
        subMenuItem.setVisible(subMenuVisible);
    }

    static boolean handleOpenOnWebsiteMenus(@NonNull final Context context,
                                            @IdRes final int menuItemId,
                                            @NonNull final DataHolder rowData) {

        final Site.Config config = Site.getConfigByMenuId(menuItemId);
        // sanity check
        if (config != null) {
            final SearchEngine searchEngine = config.createSearchEngine();
            // sanity check
            if (searchEngine instanceof SearchEngine.ByExternalId) {
                final Domain domain = config.getExternalIdDomain();
                //noinspection ConstantConditions
                final String url = ((SearchEngine.ByExternalId) searchEngine)
                        .createUrl(rowData.getString(domain.getName()));
                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                return true;
            }
        }

        return false;
    }

    private static void prepareSearchOnAmazonMenu(@NonNull final Menu menu,
                                                  final boolean hasAuthor,
                                                  final boolean hasSeries) {

        final MenuItem subMenuItem = menu.findItem(R.id.SUBMENU_AMAZON_SEARCH);
        if (subMenuItem == null) {
            return;
        }

        final boolean show = hasAuthor || hasSeries;
        subMenuItem.setVisible(show);
        if (show) {
            final SubMenu sm = subMenuItem.getSubMenu();
            sm.findItem(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR)
              .setVisible(hasAuthor);
            sm.findItem(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES)
              .setVisible(hasAuthor && hasSeries);
            sm.findItem(R.id.MENU_AMAZON_BOOKS_IN_SERIES)
              .setVisible(hasSeries);
        }
    }

    static void setupSearch(@NonNull final Activity activity,
                            @NonNull final Menu menu) {
        final MenuItem searchItem = menu.findItem(R.id.MENU_SEARCH);
        if (searchItem != null) {
            // Reminder: we let the SearchView handle its own icons.
            // The hint text is defined in xml/searchable.xml
            final SearchView searchView = (SearchView) searchItem.getActionView();
            final SearchManager searchManager = (SearchManager)
                    activity.getSystemService(Context.SEARCH_SERVICE);
            //noinspection ConstantConditions
            final SearchableInfo si = searchManager.getSearchableInfo(
                    new ComponentName(activity, BooksOnBookshelf.class.getName()));
            searchView.setSearchableInfo(si);
        }
    }
}
