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
package com.hardbacknutter.nevertoomanybooks.searchengines.amazon;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolderUtils;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchSites;

public class AmazonHandler {

    @NonNull
    private final Context mContext;

    public AmazonHandler(@NonNull final Context context) {
        mContext = context;
    }

    private void prepareMenu(@NonNull final Menu menu,
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

    /**
     * Called from a details screen. i.e. the data comes from a {@link Book}.
     *
     * @param menu to add to
     * @param book data to use
     */
    public void prepareMenu(@NonNull final Menu menu,
                            @NonNull final Book book) {

        final boolean hasAuthor = !book.getParcelableArrayList(Book.BKEY_AUTHOR_LIST).isEmpty();
        final boolean hasSeries = !book.getParcelableArrayList(Book.BKEY_SERIES_LIST).isEmpty();

        prepareMenu(menu, hasAuthor, hasSeries);
    }

    /**
     * Called from a list screen. i.e. the data comes from a row {@link DataHolder}.
     *
     * @param menu    to add to
     * @param rowData data to use
     */
    public void prepareMenu(@NonNull final Menu menu,
                            @NonNull final DataHolder rowData) {

        final boolean hasAuthor;
        if (rowData.contains(DBKey.FK_AUTHOR)) {
            hasAuthor = rowData.getLong(DBKey.FK_AUTHOR) > 0;
        } else {
            hasAuthor = false;
        }

        final boolean hasSeries;
        if (rowData.contains(DBKey.FK_SERIES)) {
            hasSeries = rowData.getLong(DBKey.FK_SERIES) > 0;
        } else {
            hasSeries = false;
        }

        prepareMenu(menu, hasAuthor, hasSeries);
    }

    /**
     * Called from a details screen. i.e. the data comes from a {@link Book}.
     *
     * @param menuItemId to check
     * @param book       data to use
     */
    public boolean onItemSelected(@IdRes final int menuItemId,
                                  @NonNull final Book book) {

        if (menuItemId == R.id.MENU_AMAZON_BOOKS_BY_AUTHOR) {
            final Author author = book.getPrimaryAuthor();
            if (author != null) {
                startSearchActivity(author, null);
            }
            return true;

        } else if (menuItemId == R.id.MENU_AMAZON_BOOKS_IN_SERIES) {
            final Series series = book.getPrimarySeries();
            if (series != null) {
                startSearchActivity(null, series);
            }
            return true;

        } else if (menuItemId == R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES) {
            final Author author = book.getPrimaryAuthor();
            final Series series = book.getPrimarySeries();
            if (author != null && series != null) {
                startSearchActivity(author, series);
            }
            return true;
        }

        return false;
    }

    /**
     * Called from a list screen. i.e. the data comes from a row {@link DataHolder}.
     *
     * @param menuItemId to check
     * @param rowData    data to use
     */
    public boolean onItemSelected(@IdRes final int menuItemId,
                                  @NonNull final DataHolder rowData) {

        if (menuItemId == R.id.MENU_AMAZON_BOOKS_BY_AUTHOR) {
            final Author author = DataHolderUtils.getAuthor(rowData);
            if (author != null) {
                startSearchActivity(author, null);
            }
            return true;

        } else if (menuItemId == R.id.MENU_AMAZON_BOOKS_IN_SERIES) {
            final Series series = DataHolderUtils.getSeries(rowData);
            if (series != null) {
                startSearchActivity(null, series);
            }
            return true;

        } else if (menuItemId == R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES) {
            final Author author = DataHolderUtils.getAuthor(rowData);
            final Series series = DataHolderUtils.getSeries(rowData);
            if (author != null && series != null) {
                startSearchActivity(author, series);
            }
            return true;
        }

        return false;
    }

    /**
     * Start an intent to search for an author and/or series on the Amazon website.
     *
     * @param author to search for
     * @param series to search for
     */
    private void startSearchActivity(@Nullable final Author author,
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
                    fields += "&field-author=" + URLEncoder
                            .encode(cAuthor, AmazonSearchEngine.CHARSET);
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
        final String url = SearchEngineRegistry.getInstance()
                                               .getByEngineId(SearchSites.AMAZON)
                                               .getHostUrl()
                           + AmazonSearchEngine.SEARCH_SUFFIX
                           + fields.trim();
        mContext.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
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
