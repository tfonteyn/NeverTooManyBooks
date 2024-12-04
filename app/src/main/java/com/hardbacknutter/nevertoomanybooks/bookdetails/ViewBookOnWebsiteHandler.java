/*
 * @Copyright 2018-2024 HardBackNutter
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
import android.net.Uri;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.utils.MenuHandler;

/**
 * Stateless.
 */
public class ViewBookOnWebsiteHandler
        implements MenuHandler {

    private final Map<Integer, EngineId> menuIds = new HashMap<>();

    @Override
    public void onCreateMenu(@NonNull final Context context,
                             @NonNull final Menu menu,
                             @NonNull final MenuInflater inflater) {
        if (menu.findItem(R.id.SUBMENU_VIEW_BOOK_AT_SITE) == null) {
            inflater.inflate(R.menu.sm_view_on_site, menu);
            menuIds.clear();

            final SubMenu subMenu = menu.findItem(R.id.SUBMENU_VIEW_BOOK_AT_SITE)
                                        .getSubMenu();
            Site.Type.ViewOnSite
                    .getSites()
                    .stream()
                    .map(Site::getEngineId)
                    // sort the engines by name to add to the submenu
                    // (Engine names are NOT translated)
                    .sorted(Comparator.comparing(Enum::name))
                    .forEach(engineId -> {
                        // generate a random id, and map it to the engine
                        final int menuItemId = View.generateViewId();
                        menuIds.put(menuItemId, engineId);

                        //noinspection DataFlowIssue
                        subMenu.add(R.id.MENU_GROUP_BOOK, menuItemId, 0, engineId.getLabelResId())
                               .setIcon(R.drawable.link_24px);
                    });
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
    public void onPrepareMenu(@NonNull final Context context,
                              @NonNull final Menu menu,
                              @NonNull final DataHolder rowData) {

        final MenuItem subMenuItem = menu.findItem(R.id.SUBMENU_VIEW_BOOK_AT_SITE);
        if (subMenuItem == null) {
            return;
        }

        final SubMenu subMenu = subMenuItem.getSubMenu();
        boolean subMenuVisible = false;
        //noinspection DataFlowIssue
        for (int i = 0; i < subMenu.size(); i++) {
            final MenuItem menuItem = subMenu.getItem(i);
            //noinspection DataFlowIssue
            final Domain domain = menuIds.get(menuItem.getItemId()).getExternalIdDomain();
            //noinspection DataFlowIssue
            final String externalId = rowData.getString(domain.getName());
            final boolean visible = !externalId.isEmpty() && !"0".equals(externalId);

            menuItem.setVisible(visible);
            if (visible) {
                subMenuVisible = true;
            }
        }
        subMenuItem.setVisible(subMenuVisible);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull final Context context,
                                      @IdRes final int menuItemId,
                                      @NonNull final DataHolder rowData) {

        final EngineId engineId = menuIds.get(menuItemId);
        // the engine will be not-null if the menuItemId was found; e.g. it's ours.
        if (engineId != null) {
            final Domain domain = engineId.getExternalIdDomain();
            // Sanity check
            if (domain != null) {
                final SearchEngine.ViewBookByExternalId searchEngine =
                        (SearchEngine.ViewBookByExternalId) engineId.createSearchEngine(context);

                final String externalId = rowData.getString(domain.getName());
                final String url = searchEngine.createBrowserUrl(context, externalId);
                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                return true;
            }
        }
        // Not our menuItemId
        return false;
    }
}
