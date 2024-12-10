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

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolderUtils;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.MenuHandler;

public class SiteSearchMenuHandler
        implements MenuHandler {

    @IdRes
    private final int subMenuId;
    /** Search by author menu id. */
    @IdRes
    private final int midByAuthor;
    /** Search by both author and series menu id. */
    @IdRes
    private final int midByAuthorInSeries;
    /** Search by series menu id. */
    @IdRes
    private final int midBySeries;
    @NonNull
    private final EngineId engineId;
    @Nullable
    private SearchEngine.SearchOnSite searchEngine;

    /**
     * Constructor.
     *
     * @param engineId to search on
     */
    SiteSearchMenuHandler(@NonNull final EngineId engineId) {
        this.engineId = engineId;

        subMenuId = View.generateViewId();
        midByAuthor = View.generateViewId();
        midByAuthorInSeries = View.generateViewId();
        midBySeries = View.generateViewId();
    }

    private SearchEngine.SearchOnSite getSearchEngine(@NonNull final Context context) {
        if (searchEngine == null) {
            searchEngine = (SearchEngine.SearchOnSite) engineId.createSearchEngine(context);
        }
        return searchEngine;
    }

    @Override
    public void onCreateMenu(@NonNull final Context context,
                             @NonNull final Menu menu,
                             @NonNull final MenuInflater inflater) {
        // add the submenu if not there yet
        MenuItem menuItem = menu.findItem(R.id.SUBMENU_SEARCH_BOOKS_ON_SITE);
        if (menuItem == null) {
            inflater.inflate(R.menu.sm_search_books_on_site, menu);
            menuItem = menu.findItem(R.id.SUBMENU_SEARCH_BOOKS_ON_SITE);
        }

        // add THIS submenu if not there yet
        if (menu.findItem(subMenuId) == null) {
            final SubMenu parent = Objects.requireNonNull(menuItem.getSubMenu());

            final String menuTitle = context.getString(
                    R.string.ellipsize, context.getString(engineId.getLabelResId()));
            final SubMenu subMenu = parent.addSubMenu(0, subMenuId, 0, menuTitle)
                                          .setIcon(R.drawable.search_24px);

            subMenu.add(0, midByAuthor, 0,
                        R.string.option_search_books_by_author)
                   .setIcon(R.drawable.search_24px);
            subMenu.add(0, midByAuthorInSeries, 0,
                        R.string.option_search_books_by_author_in_series)
                   .setIcon(R.drawable.search_24px);
            subMenu.add(0, midBySeries, 0,
                        R.string.option_search_books_in_series)
                   .setIcon(R.drawable.search_24px);
        }
    }

    @Override
    public void onPrepareMenu(@NonNull final Context context,
                              @NonNull final Menu menu,
                              @NonNull final DataHolder rowData) {

        final MenuItem subMenuItem = menu.findItem(subMenuId);
        if (subMenuItem == null) {
            return;
        }

        boolean show = getSearchEngine(context).isShowSearchOnSiteMenu(context);
        if (!show) {
            subMenuItem.setVisible(false);
            return;
        }

        final boolean hasAuthor = DataHolderUtils.hasAuthor(rowData);
        final boolean hasSeries = DataHolderUtils.hasSeries(rowData);
        show = hasAuthor || hasSeries;

        subMenuItem.setVisible(show);
        if (show) {
            final SubMenu sm = subMenuItem.getSubMenu();
            //noinspection DataFlowIssue
            sm.findItem(midByAuthor)
              .setVisible(hasAuthor);
            sm.findItem(midByAuthorInSeries)
              .setVisible(hasAuthor && hasSeries);
            sm.findItem(midBySeries)
              .setVisible(hasSeries);
        }
    }

    @Override
    public boolean onMenuItemSelected(@NonNull final Context context,
                                      @IdRes final int menuItemId,
                                      @NonNull final DataHolder rowData) {

        if (menuItemId == midByAuthor) {
            if (DataHolderUtils.hasAuthor(rowData)) {
                final Author author = DataHolderUtils.requireAuthor(rowData);
                startSearchActivity(context, author, null);
                return true;
            }
        } else if (menuItemId == midBySeries) {
            if (DataHolderUtils.hasSeries(rowData)) {
                final Series series = DataHolderUtils.requireSeries(rowData);
                startSearchActivity(context, null, series);
                return true;
            }
        } else if (menuItemId == midByAuthorInSeries) {
            if (DataHolderUtils.hasAuthor(rowData)
                && DataHolderUtils.hasSeries(rowData)) {
                final Author author = DataHolderUtils.requireAuthor(rowData);
                final Series series = DataHolderUtils.requireSeries(rowData);
                startSearchActivity(context, author, series);
                return true;
            }
        }

        return false;
    }

    /**
     * Start an intent to search for an author and/or series on the website.
     *
     * @param context Current context from which the Activity will be started
     * @param author  to search for
     * @param series  to search for
     */
    private void startSearchActivity(@NonNull final Context context,
                                     @Nullable final Author author,
                                     @Nullable final Series series) {

        final String url = getSearchEngine(context)
                .createSearchOnSiteUrl(context, author, series);
        // Start the intent even if for some reason the fields string is empty.
        // If we don't the user will not see anything happen / we'd need to popup
        // an explanation why we cannot search.
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }
}
