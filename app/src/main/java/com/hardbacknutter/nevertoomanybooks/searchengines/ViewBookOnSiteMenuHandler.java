/*
 * @Copyright 2018-2025 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searchengines;

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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolderUtils;
import com.hardbacknutter.nevertoomanybooks.utils.MenuHandler;

/**
 * Collects all sites supporting {@link SearchEngine.ViewBookByExternalId}
 * and builds/displays a menu suitable for a given {@link Book}.
 * <p>
 * We handle all engines in a single instance as we need to hide the entire submenu
 * if there are no relevant engines (i.e. external book ids).
 */
class ViewBookOnSiteMenuHandler
        implements MenuHandler {

    private final Map<Integer, EngineId> menuIds = new HashMap<>();

    @NonNull
    private static Optional<String> getExternalId(@NonNull final DataHolder rowData,
                                                  final String identifierKey) {
        final Optional<String> oid = DataHolderUtils.getExternalId(rowData, identifierKey);
        if (oid.isPresent()) {
            // found it
            return oid;
        }

        //URGENT: is this a good idea? The browser/amazon gives a 404 if the isbn is not found
        // When looking for the Amazon ASIN, fallback on an Isbn code if possible
//        if (Identifier.SID_ASIN.equals(identifierKey)
//            && rowData.contains(DBKey.BOOK_ISBN)) {
//            final String isbnStr = rowData.getString(DBKey.BOOK_ISBN);
//            final ISBN isbn = new ISBN(isbnStr, true);
//            if (isbn.isValid(true)) {
//                final String asin = isbn.isIsbn10Compat() ? isbn.asText(ISBN.Type.Isbn10)
//                                                          : isbn.asText();
//                return Optional.of(asin);
//            }
//        }
        return oid;
    }

    @Override
    public void onCreateMenu(@NonNull final Context context,
                             @NonNull final Menu menu,
                             @NonNull final MenuInflater inflater) {
        // Sanity check
        MenuItem menuItem = menu.findItem(R.id.SUBMENU_VIEW_BOOK_AT_SITE);
        if (menuItem == null) {
            inflater.inflate(R.menu.sm_view_on_site, menu);
            menuItem = menu.findItem(R.id.SUBMENU_VIEW_BOOK_AT_SITE);
            menuIds.clear();

            final SubMenu subMenu = menuItem.getSubMenu();

            // The menu will have options for ALL engines!
            // Visibility is set in onPrepareMenu.
            EngineId.getViewOnSite().forEach(engineId -> {
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
            // Not ours to handle
            return;
        }

        final SubMenu subMenu = subMenuItem.getSubMenu();
        boolean subMenuVisible = false;
        // Set the visibility of each menu item.
        // If all items are invisible, make the submenu invisible as well
        //noinspection DataFlowIssue
        for (int i = 0; i < subMenu.size(); i++) {
            final MenuItem menuItem = subMenu.getItem(i);
            //noinspection DataFlowIssue
            final String identifierKey = menuIds.get(menuItem.getItemId()).getIdentifierKey();
            //noinspection DataFlowIssue
            final boolean visible = getExternalId(rowData, identifierKey).isPresent();

            menuItem.setVisible(visible);
            if (visible) {
                // at least one menu item is visible, show the menu
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
        if (engineId == null) {
            // Not ours to handle
            return false;
        }

        final String identifierKey = engineId.getIdentifierKey();
        // Sanity check
        if (identifierKey == null) {
            return false;
        }

        getExternalId(rowData, identifierKey).ifPresent(sid -> {
            final SearchEngine.ViewBookByExternalId searchEngine =
                    (SearchEngine.ViewBookByExternalId) engineId.createSearchEngine(context);
            final String url = searchEngine.createViewOnSiteUrl(context, sid);
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        });
        return true;
    }
}
