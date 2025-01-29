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
import androidx.annotation.Nullable;

import java.util.EnumMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolderUtils;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.MenuHandler;

/**
 * Collects all sites supporting {@link SearchEngine.SearchOnSite}
 * and builds/displays a menu suitable for a given book-list node.
 * <p>
 * We handle all engines in a single instance as we need to hide the entire submenu
 * if there are no relevant engines.
 */
class SiteSearchMenuHandler
        implements MenuHandler {

    private final Map<EngineId, Integer> submenuIds = new EnumMap<>(EngineId.class);
    private final Map<EngineId, Integer> menuIdsByAuthor = new EnumMap<>(EngineId.class);
    private final Map<EngineId, Integer> menuIdsByAuthorAndSeries = new EnumMap<>(EngineId.class);
    private final Map<EngineId, Integer> menuIdsBySeries = new EnumMap<>(EngineId.class);

    private final Map<EngineId, SearchEngine.SearchOnSite> engines = new EnumMap<>(EngineId.class);

    @Override
    public void onCreateMenu(@NonNull final Context context,
                             @NonNull final Menu menu,
                             @NonNull final MenuInflater inflater,
                             @NonNull final DataHolder rowData) {
        // Sanity check
        MenuItem menuItem = menu.findItem(R.id.SUBMENU_SEARCH_BOOKS_ON_SITE);
        if (menuItem == null) {
            inflater.inflate(R.menu.sm_search_books_on_site, menu);
            menuItem = menu.findItem(R.id.SUBMENU_SEARCH_BOOKS_ON_SITE);
            menuIdsByAuthor.clear();
            menuIdsByAuthorAndSeries.clear();
            menuIdsBySeries.clear();

            final SubMenu parent = menuItem.getSubMenu();

            EngineId.getSearchOnSite().forEach(engineId -> {
                final String menuTitle = context.getString(
                        R.string.ellipsize, context.getString(engineId.getLabelResId()));
                @IdRes
                final int engineMenuId = View.generateViewId();
                submenuIds.put(engineId, engineMenuId);
                //noinspection DataFlowIssue
                final SubMenu engineMenu = parent.addSubMenu(0, engineMenuId, 0, menuTitle)
                                                 .setIcon(R.drawable.search_24px);

                @IdRes
                final int midByAuthor = View.generateViewId();
                menuIdsByAuthor.put(engineId, midByAuthor);
                engineMenu.add(0, midByAuthor, 0,
                               R.string.option_search_books_by_author)
                          .setIcon(R.drawable.search_24px);

                @IdRes
                final int midByAuthorInSeries = View.generateViewId();
                menuIdsByAuthorAndSeries.put(engineId, midByAuthorInSeries);
                engineMenu.add(0, midByAuthorInSeries, 0,
                               R.string.option_search_books_by_author_in_series)
                          .setIcon(R.drawable.search_24px);

                @IdRes
                final int midBySeries = View.generateViewId();
                menuIdsBySeries.put(engineId, midBySeries);
                engineMenu.add(0, midBySeries, 0,
                               R.string.option_search_books_in_series)
                          .setIcon(R.drawable.search_24px);
            });
        }
    }

    @Override
    public void onPrepareMenu(@NonNull final Context context,
                              @NonNull final Menu menu,
                              @NonNull final DataHolder rowData) {

        final MenuItem subMenuItem = menu.findItem(R.id.SUBMENU_SEARCH_BOOKS_ON_SITE);
        // Sanity check
        if (subMenuItem == null) {
            return;
        }

        boolean subMenuVisible = false;

        // Set the visibility of each menu item.
        // If all items are invisible, make the submenu invisible as well
        for (final Map.Entry<EngineId, Integer> entry : submenuIds.entrySet()) {
            final EngineId engineId = entry.getKey();
            final Integer engineMenuId = entry.getValue();

            final SearchEngine.SearchOnSite searchEngine = (SearchEngine.SearchOnSite)
                    engineId.createSearchEngine(context);
            engines.put(engineId, searchEngine);

            final MenuItem engineMenu = menu.findItem(engineMenuId);
            boolean visible = searchEngine.isShowSearchOnSiteMenu(context);
            if (visible) {
                final boolean hasAuthor = DataHolderUtils.hasAuthor(rowData);
                final boolean hasSeries = DataHolderUtils.hasSeries(rowData);
                visible = hasAuthor || hasSeries;

                engineMenu.setVisible(visible);
                if (visible) {
                    final SubMenu sm = engineMenu.getSubMenu();
                    //noinspection DataFlowIssue
                    sm.findItem(menuIdsByAuthor.get(engineId))
                      .setVisible(hasAuthor);
                    //noinspection DataFlowIssue
                    sm.findItem(menuIdsByAuthorAndSeries.get(engineId))
                      .setVisible(hasAuthor && hasSeries);
                    //noinspection DataFlowIssue
                    sm.findItem(menuIdsBySeries.get(engineId))
                      .setVisible(hasSeries);

                    // at least one menu item is visible, show the menu
                    subMenuVisible = true;
                }

            } else {
                engineMenu.setVisible(false);
            }
        }

        subMenuItem.setVisible(subMenuVisible);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull final Context context,
                                      @IdRes final int menuItemId,
                                      @NonNull final DataHolder rowData) {

        for (final EngineId engineId : submenuIds.keySet()) {

            Integer id;
            id = menuIdsByAuthor.get(engineId);
            if (id != null && id == menuItemId) {
                startSearchActivity(context, engineId,
                                    DataHolderUtils.requireAuthor(rowData),
                                    null);
                return true;
            }

            id = menuIdsBySeries.get(engineId);
            if (id != null && id == menuItemId) {
                startSearchActivity(context, engineId,
                                    null,
                                    DataHolderUtils.requireSeries(rowData));
                return true;
            }

            id = menuIdsByAuthorAndSeries.get(engineId);
            if (id != null && id == menuItemId) {
                startSearchActivity(context, engineId,
                                    DataHolderUtils.requireAuthor(rowData),
                                    DataHolderUtils.requireSeries(rowData));
                return true;
            }
        }
        return false;
    }

    /**
     * Start an intent to search for an author and/or series on the website.
     *
     * @param context  Current context from which the Activity will be started
     * @param engineId to use
     * @param author   to search for
     * @param series   to search for
     */
    private void startSearchActivity(@NonNull final Context context,
                                     @NonNull final EngineId engineId,
                                     @Nullable final Author author,
                                     @Nullable final Series series) {

        //noinspection DataFlowIssue
        final String url = engines.get(engineId).createSearchOnSiteUrl(context, author, series);
        // Start the intent even if for some reason the fields string is empty.
        // If we don't the user will not see anything happen / we'd need to popup
        // an explanation why we cannot search.
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }
}
