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

package com.hardbacknutter.nevertoomanybooks.searchengines.bertrandpt;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.StringJoiner;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.ShoppingMenuHandler;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;

public class BertrandMenuHandler
        extends ShoppingMenuHandler {

    @NonNull
    private final Supplier<Languages> languagesSupplier;

    /**
     * Constructor.
     *
     * @param languagesSupplier deferred supplier for the {@link Languages}
     */
    public BertrandMenuHandler(@NonNull final Supplier<Languages> languagesSupplier) {
        super(R.menu.sm_search_on_bertrand,
              R.id.SUBMENU_BERTRAND_SEARCH,
              R.id.MENU_BERTRAND_BOOKS_BY_AUTHOR,
              R.id.MENU_BERTRAND_BOOKS_BY_AUTHOR_IN_SERIES,
              R.id.MENU_BERTRAND_BOOKS_IN_SERIES);
        this.languagesSupplier = languagesSupplier;
    }

    @Override
    public boolean isShowMenu(@NonNull final Context context) {
        final String key = EngineId.BertrandPt.getPreferenceKey()
                           + '.' + Prefs.pk_search_show_shopping_menu;

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.contains(key)) {
            return prefs.getBoolean(key, false);
        } else {
            return languagesSupplier.get().isUserLanguage(context, "por");
        }
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

        final StringJoiner fields = new StringJoiner(" ");

        if (author != null) {
            final String cAuthor = encodeSearchString(author.getFormattedName(true));
            if (!cAuthor.isEmpty()) {
                try {
                    fields.add(URLEncoder.encode(cAuthor, BertrandPtSearchEngine.CHARSET));
                } catch (@NonNull final UnsupportedEncodingException ignore) {
                    // ignore
                }
            }
        }

        if (series != null) {
            final String cSeries = encodeSearchString(series.getTitle());
            if (!cSeries.isEmpty()) {
                try {
                    fields.add(URLEncoder.encode(cSeries, BertrandPtSearchEngine.CHARSET));
                } catch (@NonNull final UnsupportedEncodingException ignore) {
                    // ignore
                }
            }
        }

        // Start the intent even if for some reason the fields string is empty.
        // If we don't the user will not see anything happen / we'd need to popup
        // an explanation why we cannot search.
        final String url = EngineId.BertrandPt.requireConfig().getHostUrl(context)
                           + BertrandPtSearchEngine.SEARCH + fields;
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }
}
