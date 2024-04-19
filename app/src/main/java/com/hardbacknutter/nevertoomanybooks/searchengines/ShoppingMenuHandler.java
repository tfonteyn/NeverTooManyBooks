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
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;

import androidx.annotation.IdRes;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolderUtils;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.MenuHandler;

public abstract class ShoppingMenuHandler
        implements MenuHandler {

    @MenuRes
    private final int menuResId;
    @IdRes
    private final int subMenuId;
    @IdRes
    private final int midByAuthor;
    @IdRes
    private final int midByAuthorInSeries;
    @IdRes
    private final int midBySeries;

    /**
     * Constructor.
     *
     * @param menuResId           the menu layout id
     * @param subMenuId           the submenu
     * @param midByAuthor         search by author menu id
     * @param midByAuthorInSeries search by both author and series menu id
     * @param midBySeries         search by series menu id
     */
    protected ShoppingMenuHandler(@MenuRes final int menuResId,
                                  @IdRes final int subMenuId,
                                  @IdRes final int midByAuthor,
                                  @IdRes final int midByAuthorInSeries,
                                  @IdRes final int midBySeries) {
        this.menuResId = menuResId;
        this.subMenuId = subMenuId;
        this.midByAuthor = midByAuthor;
        this.midByAuthorInSeries = midByAuthorInSeries;
        this.midBySeries = midBySeries;
    }

    public abstract boolean isShowMenu(@NonNull Context context);

    @Override
    public void onCreateMenu(@NonNull final Context context,
                             @NonNull final Menu menu,
                             @NonNull final MenuInflater inflater) {
        if (menu.findItem(subMenuId) == null) {
            inflater.inflate(menuResId, menu);
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

        boolean show = isShowMenu(context);
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
        } else if (menuItemId == midByAuthorInSeries) {
            if (DataHolderUtils.hasSeries(rowData)) {
                final Series series = DataHolderUtils.requireSeries(rowData);
                startSearchActivity(context, null, series);
                return true;
            }
        } else if (menuItemId == midBySeries) {
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
     * Start an intent to search for an author and/or series on the BOL website.
     *
     * @param context Current context from which the Activity will be started
     * @param author  to search for
     * @param series  to search for
     */
    protected abstract void startSearchActivity(@NonNull Context context,
                                                @Nullable Author author,
                                                @Nullable Series series);

    @NonNull
    protected String encodeSearchString(@Nullable final String search) {
        if (search == null || search.isEmpty()) {
            return "";
        }

        final StringBuilder out = new StringBuilder(search.length());
        char prev = ' ';
        for (final char curr : search.toCharArray()) {
            if (Character.isLetterOrDigit(curr)) {
                out.append(curr);
                prev = curr;
            } else {
                if (!Character.isWhitespace(prev)) {
                    out.append(' ');
                }
                prev = ' ';
            }
        }
        return out.toString().trim();
    }
}
