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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.app.Activity;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.ComponentName;
import android.content.Context;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.SearchView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf;
import com.hardbacknutter.nevertoomanybooks.R;

public final class MenuUtils {

    private MenuUtils() {
    }

    public static void setupSearchActionView(@NonNull final Activity activity,
                                             @NonNull final MenuInflater inflater,
                                             @NonNull final Menu menu) {
        final MenuItem searchItem = menu.findItem(R.id.MENU_SEARCH);
        if (searchItem == null) {
            inflater.inflate(R.menu.sav_search, menu);
        }

        setupSearchActionView(activity, menu);
    }

    public static void setupSearchActionView(@NonNull final Activity activity,
                                             @NonNull final Menu menu) {
        final MenuItem searchItem = Objects.requireNonNull(menu.findItem(R.id.MENU_SEARCH),
                                                           "Missing search menu item");

        // Reminder: we let the SearchView handle its own icons.
        // The hint text is defined in xml/searchable.xml
        final SearchView searchView = (SearchView) searchItem.getActionView();
        final SearchManager searchManager = (SearchManager)
                activity.getSystemService(Context.SEARCH_SERVICE);
        final SearchableInfo si = searchManager.getSearchableInfo(
                new ComponentName(activity, BooksOnBookshelf.class.getName()));
        //noinspection ConstantConditions
        searchView.setSearchableInfo(si);
    }

    /**
     * Customize the given menu item title to give it the same look as preference categories.
     * The color is set to 'colorAccent' + the text is scaled 0.88 (16sp versus default 18sp).
     *
     * @param context Current context
     * @param menu    hosting menu
     * @param itemId  menu item id
     */
    public static void customizeMenuGroupTitle(@NonNull final Context context,
                                               @NonNull final Menu menu,
                                               @IdRes final int itemId) {
        final MenuItem item = menu.findItem(itemId);
        final SpannableString title = new SpannableString(item.getTitle());
        final int color = AttrUtils
                .getColorInt(context, androidx.appcompat.R.attr.colorAccent);
        title.setSpan(new ForegroundColorSpan(color), 0, title.length(), 0);
        title.setSpan(new RelativeSizeSpan(0.88f), 0, title.length(), 0);
        item.setTitle(title);

        // can be set in xml, but here for paranoia
        item.setCheckable(false);
        item.setEnabled(false);
    }
}
