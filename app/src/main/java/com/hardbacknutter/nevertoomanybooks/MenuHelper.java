/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searches.Site;

public final class MenuHelper {

    private MenuHelper() {
    }

    /**
     * Called from a details screen. i.e. the data comes from a {@link Book}.
     *
     * @param menu to add to
     * @param book data to use
     */
    public static void prepareOptionalMenus(@NonNull final Menu menu,
                                            @NonNull final Book book) {

        final boolean hasAuthor = !book.getParcelableArrayList(Book.BKEY_AUTHOR_LIST).isEmpty();
        final boolean hasSeries = !book.getParcelableArrayList(Book.BKEY_SERIES_LIST).isEmpty();

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

        final boolean hasAuthor;
        if (rowData.contains(DBDefinitions.KEY_FK_AUTHOR)) {
            hasAuthor = rowData.getLong(DBDefinitions.KEY_FK_AUTHOR) > 0;
        } else {
            hasAuthor = false;
        }

        final boolean hasSeries;
        if (rowData.contains(DBDefinitions.KEY_FK_SERIES)) {
            hasSeries = rowData.getLong(DBDefinitions.KEY_FK_SERIES) > 0;
        } else {
            hasSeries = false;
        }

        prepareSearchOnAmazonMenu(menu, hasAuthor, hasSeries);
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

    public static void setupSearchActionView(@NonNull final Activity activity,
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

    /**
     * Populate the OpenOnWebsiteMenu sub menu (if present) for a book
     * with the sites for which the book has a valid external-id.
     *
     * @param menu       root menu
     * @param dataHolder the row data
     */
    public static void prepareViewBookOnWebsiteMenu(@NonNull final Menu menu,
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

            final Optional<SearchEngineRegistry.Config> oConfig = SearchEngineRegistry
                    .getByMenuId(menuItem.getItemId());
            if (oConfig.isPresent()) {
                final Domain domain = oConfig.get().getExternalIdDomain();
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

    public static boolean handleViewBookOnWebsiteMenu(@NonNull final Context context,
                                                      @IdRes final int menuItemId,
                                                      @NonNull final DataHolder rowData) {

        final Optional<SearchEngineRegistry.Config> oConfig = SearchEngineRegistry
                .getByMenuId(menuItemId);
        if (oConfig.isPresent()) {
            final Domain domain = oConfig.get().getExternalIdDomain();
            if (domain != null) {
                final SearchEngine.ByExternalId searchEngine = (SearchEngine.ByExternalId)
                        Site.Type.Data.getSite(oConfig.get().getEngineId())
                                      .getSearchEngine(context);

                final String url = searchEngine.createUrl(rowData.getString(domain.getName()));
                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                return true;
            }

        }
        return false;
    }
}
