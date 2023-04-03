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

package com.hardbacknutter.nevertoomanybooks.searchengines.bol;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolderUtils;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.MenuHandler;

public class BolMenuHandler
        implements MenuHandler {
    @Override
    public void onCreateMenu(@NonNull final Context context,
                             @NonNull final Menu menu,
                             @NonNull final MenuInflater inflater) {
        if (menu.findItem(R.id.SUBMENU_BOL_SEARCH) == null) {
            inflater.inflate(R.menu.sm_search_on_bol, menu);
        }
    }

    @Override
    public void onPrepareMenu(@NonNull final Context context,
                              @NonNull final Menu menu,
                              @NonNull final DataHolder rowData) {
        final MenuItem subMenuItem = menu.findItem(R.id.SUBMENU_BOL_SEARCH);
        if (subMenuItem == null) {
            return;
        }

        boolean show = PreferenceManager.getDefaultSharedPreferences(context)
                                        .getBoolean(EngineId.Bol.getPreferenceKey()
                                                    + '.' + Prefs.pk_search_show_shopping_menu,
                                                    true);
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
            //noinspection ConstantConditions
            sm.findItem(R.id.MENU_BOL_BOOKS_BY_AUTHOR)
              .setVisible(hasAuthor);
            sm.findItem(R.id.MENU_BOL_BOOKS_BY_AUTHOR_IN_SERIES)
              .setVisible(hasAuthor && hasSeries);
            sm.findItem(R.id.MENU_BOL_BOOKS_IN_SERIES)
              .setVisible(hasSeries);
        }
    }

    @Override
    public boolean onMenuItemSelected(@NonNull final Context context,
                                      @NonNull final MenuItem menuItem,
                                      @NonNull final DataHolder rowData) {
        final int itemId = menuItem.getItemId();

        if (itemId == R.id.MENU_BOL_BOOKS_BY_AUTHOR) {
            if (DataHolderUtils.hasAuthor(rowData)) {
                final Author author = DataHolderUtils.requireAuthor(rowData);
                startSearchActivity(context, author, null);
                return true;
            }
        } else if (itemId == R.id.MENU_BOL_BOOKS_IN_SERIES) {
            if (DataHolderUtils.hasSeries(rowData)) {
                final Series series = DataHolderUtils.requireSeries(rowData);
                startSearchActivity(context, null, series);
                return true;
            }
        } else if (itemId == R.id.MENU_BOL_BOOKS_BY_AUTHOR_IN_SERIES) {
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
    private void startSearchActivity(@NonNull final Context context,
                                     @Nullable final Author author,
                                     @Nullable final Series series) {
        if (BuildConfig.DEBUG /* always */) {
            if (author == null && series == null) {
                throw new IllegalArgumentException("both author and series are null");
            }
        }

        final StringJoiner words = new StringJoiner(" ");

        if (author != null) {
            final String cAuthor = encodeSearchString(author.getFormattedName(true));
            if (!cAuthor.isEmpty()) {
                try {
                    words.add(URLEncoder.encode(cAuthor, BolSearchEngine.CHARSET));
                } catch (@NonNull final UnsupportedEncodingException ignore) {
                    // ignore
                }
            }
        }

        if (series != null) {
            final String cSeries = encodeSearchString(series.getTitle());
            if (!cSeries.isEmpty()) {
                try {
                    words.add(URLEncoder.encode(cSeries, BolSearchEngine.CHARSET));
                } catch (@NonNull final UnsupportedEncodingException ignore) {
                    // ignore
                }
            }
        }

        // Start the intent even if for some reason the fields string is empty.
        // If we don't the user will not see anything happen / we'd need to popup
        // an explanation why we cannot search.
        final String url = EngineId.Bol.requireConfig().getHostUrl(context)
                           + String.format(BolSearchEngine.BY_TEXT,
                                           BolSearchEngine.getCountry(context),
                                           words);
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
