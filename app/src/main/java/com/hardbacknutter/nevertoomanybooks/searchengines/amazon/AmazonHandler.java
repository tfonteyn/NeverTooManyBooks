/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searchengines.amazon;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolderUtils;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.utils.MenuHandler;

/**
 * Stateless.
 */
public class AmazonHandler
        implements MenuHandler {

    /**
     * The search url for books when opening a browser activity.
     * <p>
     * Fields that can be added to the /gp URL:
     * <ul>
     *      <li>&field-isbn</li>
     *      <li>&field-author</li>
     *      <li>&field-title</li>
     *      <li>&field-publisher</li>
     *      <li>&field-keywords</li>
     * </ul>
     *
     * ENHANCE: add "Find by ISBN" menu item;
     * ENHANCE: add "Find by Title+author" menu item
     *
     * @see <a href="https://www.amazon.co.uk/advanced-search/books/">
     *         www.amazon.co.uk/advanced-search/books</a>
     */
    private static final String ADV_SEARCH_BOOKS = "/gp/search?index=books";

    @Override
    public void onCreateMenu(@NonNull final Context context,
                             @NonNull final Menu menu,
                             @NonNull final MenuInflater inflater) {
        if (menu.findItem(R.id.SUBMENU_AMAZON_SEARCH) == null) {
            inflater.inflate(R.menu.sm_search_on_amazon, menu);
        }
    }

    @Override
    public void onPrepareMenu(@NonNull final Menu menu,
                              @NonNull final DataHolder rowData) {

        final MenuItem subMenuItem = menu.findItem(R.id.SUBMENU_AMAZON_SEARCH);
        if (subMenuItem == null) {
            return;
        }

        final boolean hasAuthor = DataHolderUtils.hasAuthor(rowData);
        final boolean hasSeries = DataHolderUtils.hasSeries(rowData);

        final boolean show = hasAuthor || hasSeries;
        subMenuItem.setVisible(show);
        if (show) {
            final SubMenu sm = subMenuItem.getSubMenu();
            //noinspection ConstantConditions
            sm.findItem(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR)
              .setVisible(hasAuthor);
            sm.findItem(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES)
              .setVisible(hasAuthor && hasSeries);
            sm.findItem(R.id.MENU_AMAZON_BOOKS_IN_SERIES)
              .setVisible(hasSeries);
        }
    }

    @Override
    public boolean onMenuItemSelected(@NonNull final Context context,
                                      @NonNull final MenuItem menuItem,
                                      @NonNull final DataHolder rowData) {

        final int itemId = menuItem.getItemId();

        if (itemId == R.id.MENU_AMAZON_BOOKS_BY_AUTHOR) {
            final Author author = DataHolderUtils.getAuthor(rowData);
            if (author != null) {
                startSearchActivity(context, author, null);
            }
            return true;

        } else if (itemId == R.id.MENU_AMAZON_BOOKS_IN_SERIES) {
            final Series series = DataHolderUtils.getSeries(rowData);
            if (series != null) {
                startSearchActivity(context, null, series);
            }
            return true;

        } else if (itemId == R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES) {
            final Author author = DataHolderUtils.getAuthor(rowData);
            final Series series = DataHolderUtils.getSeries(rowData);
            if (author != null && series != null) {
                startSearchActivity(context, author, series);
            }
            return true;
        }

        return false;
    }

    /**
     * Start an intent to search for an author and/or series on the Amazon website.
     *
     * @param context Current context from which the Activity will be started
     * @param author  to search for
     * @param series  to search for
     */
    private void startSearchActivity(@NonNull final Context context,
                                     @Nullable final Author author,
                                     @Nullable final Series series) {
        if (BuildConfig.DEBUG /* always */) {
            if (author == null && series == null) {
                throw new IllegalArgumentException("both author and series are null");
            }
        }

        String fields = "";

        if (author != null) {
            final String cAuthor = encodeSearchString(author.getFormattedName(true));
            if (!cAuthor.isEmpty()) {
                try {
                    fields += "&field-author="
                              + URLEncoder.encode(cAuthor, AmazonSearchEngine.CHARSET);
                } catch (@NonNull final UnsupportedEncodingException ignore) {
                    // ignore
                }
            }
        }
        if (series != null) {
            final String cSeries = encodeSearchString(series.getTitle());
            if (!cSeries.isEmpty()) {
                try {
                    fields += "&field-keywords="
                              + URLEncoder.encode(cSeries, AmazonSearchEngine.CHARSET);
                } catch (@NonNull final UnsupportedEncodingException ignore) {
                    // ignore
                }
            }
        }

        // Start the intent even if for some reason the fields string is empty.
        // If we don't the user will not see anything happen / we'd need to popup
        // an explanation why we cannot search.
        final String url = EngineId.Amazon.requireConfig().getHostUrl()
                           + ADV_SEARCH_BOOKS
                           + fields.trim();
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    @NonNull
    private String encodeSearchString(@Nullable final String search) {
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
