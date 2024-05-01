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
package com.hardbacknutter.nevertoomanybooks.searchengines.amazon;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.ShoppingMenuHandler;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

/**
 * Stateless.
 */
public class AmazonMenuHandler
        extends ShoppingMenuHandler {

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
     * <p>
     * ENHANCE: add "Find by ISBN" menu item;
     * ENHANCE: add "Find by Title+author" menu item
     *
     * @see <a href="https://www.amazon.co.uk/advanced-search/books/">
     *         www.amazon.co.uk/advanced-search/books</a>
     */
    private static final String ADV_SEARCH_BOOKS = "/gp/search?index=books";

    /**
     * Constructor.
     */
    public AmazonMenuHandler() {
        super(R.menu.sm_search_on_amazon,
              R.id.SUBMENU_AMAZON_SEARCH,
              R.id.MENU_AMAZON_BOOKS_BY_AUTHOR,
              R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES,
              R.id.MENU_AMAZON_BOOKS_IN_SERIES);
    }

    @Override
    public boolean isShowMenu(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(EngineId.Amazon.getPreferenceKey()
                                            + '.' + Prefs.PK_SEARCH_SHOW_SHOPPING_MENU,
                                            true);

    }


    @Override
    protected void startSearchActivity(@NonNull final Context context,
                                       @Nullable final Author author,
                                       @Nullable final Series series) {
        if (BuildConfig.DEBUG /* always */) {
            if (author == null && series == null) {
                throw new IllegalArgumentException("both author and series are null");
            }
        }

        final StringJoiner fields = new StringJoiner("&");
        fields.add(ADV_SEARCH_BOOKS);

        if (author != null) {
            final String cAuthor = encodeSearchString(author.getFormattedName(true));
            if (!cAuthor.isEmpty()) {
                try {
                    fields.add("field-author="
                               + URLEncoder.encode(cAuthor, AmazonSearchEngine.CHARSET));
                } catch (@NonNull final UnsupportedEncodingException ignore) {
                    // ignore
                }
            }
        }
        if (series != null) {
            final String cSeries = encodeSearchString(series.getTitle());
            if (!cSeries.isEmpty()) {
                try {
                    fields.add("field-keywords="
                               + URLEncoder.encode(cSeries, AmazonSearchEngine.CHARSET));
                } catch (@NonNull final UnsupportedEncodingException ignore) {
                    // ignore
                }
            }
        }

        // Start the intent even if for some reason the fields string is empty.
        // If we don't the user will not see anything happen / we'd need to popup
        // an explanation why we cannot search.
        final String url = EngineId.Amazon.requireConfig().getHostUrl(context) + fields;
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }
}
