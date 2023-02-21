/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.bookdetails;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.utils.MenuHandler;

/**
 * Stateless.
 */
public class ViewBookOnWebsiteHandler
        implements MenuHandler {

    @Override
    public void onCreateMenu(@NonNull final Context context,
                             @NonNull final Menu menu,
                             @NonNull final MenuInflater inflater) {
        if (menu.findItem(R.id.SUBMENU_VIEW_BOOK_AT_SITE) == null) {
            inflater.inflate(R.menu.sm_view_on_site, menu);

            final MenuItem subMenuItem = menu.findItem(R.id.SUBMENU_VIEW_BOOK_AT_SITE);
            final SubMenu subMenu = subMenuItem.getSubMenu();
            final Resources res = context.getResources();

            for (final Site site : Site.Type.ViewOnSite.getSites()) {
                final EngineId engineId = site.getEngineId();
                final SearchEngineConfig config = Objects.requireNonNull(engineId.getConfig());
                subMenu.add(R.id.MENU_GROUP_BOOK,
                            config.getDomainMenuResId(),
                            res.getInteger(config.getDomainMenuOrderResId()),
                            engineId.getLabelResId())
                       .setIcon(R.drawable.ic_baseline_link_24);
            }
        }
    }

    /**
     * Populate the OpenOnWebsiteMenu sub menu (if present) for a book
     * with the sites for which the book has a valid external-id.
     *
     * @param menu    root menu
     * @param rowData the row data
     */
    @Override
    public void onPrepareMenu(@NonNull final Menu menu,
                              @NonNull final DataHolder rowData) {

        final MenuItem subMenuItem = menu.findItem(R.id.SUBMENU_VIEW_BOOK_AT_SITE);
        if (subMenuItem == null) {
            return;
        }

        final SubMenu subMenu = subMenuItem.getSubMenu();
        boolean subMenuVisible = false;
        //noinspection ConstantConditions
        for (int i = 0; i < subMenu.size(); i++) {
            final MenuItem menuItem = subMenu.getItem(i);
            final boolean visible =
                    SearchEngineConfig.getByMenuId(menuItem.getItemId())
                                      .map(SearchEngineConfig::getExternalIdDomain)
                                      .filter(Objects::nonNull)
                                      .map(domain -> rowData.getString(domain.getName()))
                                      .filter(value -> !value.isEmpty() && !"0".equals(value))
                                      .isPresent();

            menuItem.setVisible(visible);
            if (visible) {
                subMenuVisible = true;
            }
        }
        subMenuItem.setVisible(subMenuVisible);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull final Context context,
                                      @NonNull final MenuItem menuItem,
                                      @NonNull final DataHolder rowData) {
        final Optional<SearchEngineConfig> oConfig =
                SearchEngineConfig.getByMenuId(menuItem.getItemId());
        if (oConfig.isPresent()) {
            final Domain domain = oConfig.get().getExternalIdDomain();
            if (domain != null) {
                final EngineId engineId = oConfig.get().getEngineId();
                // sanity check
                if (Site.Type.ViewOnSite.contains(engineId)) {
                    final SearchEngine.ViewBookByExternalId searchEngine =
                            (SearchEngine.ViewBookByExternalId) engineId.createSearchEngine();

                    final String externalId = rowData.getString(domain.getName());
                    final String url = searchEngine.createBrowserUrl(externalId);
                    context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                }
            }
        }
        return false;
    }
}
