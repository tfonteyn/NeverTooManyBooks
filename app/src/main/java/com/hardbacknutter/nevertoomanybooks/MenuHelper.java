/*
 * @Copyright 2018-2021 HardBackNutter
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
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.widget.SearchView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;

public final class MenuHelper {

    private MenuHelper() {
    }

    static void setupSearchActionView(@NonNull final Activity activity,
                                      @NonNull final Menu menu) {
        final MenuItem searchItem = menu.findItem(R.id.MENU_SEARCH);
        if (searchItem != null) {
            // Reminder: we let the SearchView handle its own icons.
            // The hint text is defined in xml/searchable.xml
            final SearchView searchView = (SearchView) searchItem.getActionView();
            final SearchManager searchManager = (SearchManager)
                    activity.getSystemService(Context.SEARCH_SERVICE);
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

        final SearchEngineRegistry registry = SearchEngineRegistry.getInstance();
        final SubMenu subMenu = subMenuItem.getSubMenu();
        boolean subMenuVisible = false;
        for (int i = 0; i < subMenu.size(); i++) {
            final MenuItem menuItem = subMenu.getItem(i);
            boolean visible = false;

            final Optional<SearchEngineConfig> oConfig =
                    registry.getByMenuId(menuItem.getItemId());
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

        final Optional<SearchEngineConfig> oConfig = SearchEngineRegistry
                .getInstance().getByMenuId(menuItemId);
        if (oConfig.isPresent()) {
            final Domain domain = oConfig.get().getExternalIdDomain();
            if (domain != null) {
                final SearchEngine.ByExternalId searchEngine = (SearchEngine.ByExternalId)
                        Site.Type.Data.getSite(oConfig.get().getEngineId()).getSearchEngine();

                final String url = searchEngine.createUrl(rowData.getString(domain.getName()));
                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                return true;
            }

        }
        return false;
    }

    /**
     * Customize the given menu item title to give it the same look as preference categories.
     * The color is set to 'colorAccent' + the text is scaled 0.88 (16sp versus default 18sp).
     *
     * @param context Current context
     * @param menu    hosting menu
     * @param itemId  menu item id
     */
    public static void customizeMenuGroupTitle(@NonNull final Context context,
                                               @NonNull final Menu menu,
                                               @IdRes final int itemId) {
        final MenuItem item = menu.findItem(itemId);
        final SpannableString title = new SpannableString(item.getTitle());
        final int color = AttrUtils.getColorInt(context, R.attr.colorAccent);
        title.setSpan(new ForegroundColorSpan(color), 0, title.length(), 0);
        title.setSpan(new RelativeSizeSpan(0.88f), 0, title.length(), 0);
        item.setTitle(title);

        // can be set in xml, but here for paranoia
        item.setCheckable(false);
        item.setEnabled(false);
    }
}
