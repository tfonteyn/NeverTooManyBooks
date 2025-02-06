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
package com.hardbacknutter.nevertoomanybooks.search;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.StylesHelper;

@SuppressWarnings("WeakerAccess")
public class SearchBookByTextViewModel
        extends ViewModel {

    /**
     * A list of names we have already searched for in this session.
     */
    @NonNull
    private final Collection<String> recentAuthorNames = new ArrayList<>();

    /**
     * A list of names we have already searched for in this session.
     */
    @NonNull
    private final Collection<String> recentSeriesNames = new ArrayList<>();

    /**
     * A list of names we have already searched for in this session.
     */
    @NonNull
    private final Collection<String> recentPublisherNames = new ArrayList<>();
    @NonNull
    private final EditBookOutput resultData = new EditBookOutput();

    private Style style;

    private static boolean addName(@NonNull final Collection<String> recentNames,
                                   @NonNull final String searchText) {
        if (recentNames.stream().noneMatch(s -> s.equalsIgnoreCase(searchText))) {
            recentNames.add(searchText);
            return true;
        }
        return false;
    }

    /**
     * Build a combined list of the passed in names + the database.
     *
     * @param context     Current context
     * @param dbNames     the list from the database (will be modified, and returned as the result).
     * @param recentNames the in-memory list
     *
     * @return combined list
     */
    @NonNull
    private static List<String> combineNames(@NonNull final Context context,
                                             @NonNull final List<String> dbNames,
                                             @NonNull final Collection<String> recentNames) {
        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);

        final Collection<String> uniqueNames = new HashSet<>(dbNames.size());
        for (final String s : dbNames) {
            uniqueNames.add(s.toLowerCase(userLocale));
        }

        // Add the names the user has already tried (to handle errors and mistakes)
        for (final String s : recentNames) {
            if (!uniqueNames.contains(s.toLowerCase(userLocale))) {
                dbNames.add(s);
            }
        }

        return dbNames;
    }

    @NonNull
    Intent createResultIntent() {
        return resultData.createResultIntent();
    }

    void onBookEditingDone(@NonNull final EditBookOutput data) {
        resultData.update(data);
    }

    /**
     * Pseudo constructor.
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    void init(@NonNull final Bundle args) {
        if (style == null) {
            // Lookup the provided style or use the default if not found.
            final String styleUuid = args.getString(Style.BKEY_UUID);
            final StylesHelper stylesHelper = ServiceLocator.getInstance().getStyles();
            style = stylesHelper.getStyle(styleUuid).orElseGet(stylesHelper::getDefault);
        }
    }

    @NonNull
    Style getStyle() {
        Objects.requireNonNull(style, "style");
        return style;
    }

    boolean addAuthorName(@NonNull final String searchText) {
        return addName(recentAuthorNames, searchText);
    }

    @NonNull
    List<String> getAuthorNames(@NonNull final Context context) {
        // Uses {@link DBDefinitions#KEY_AUTHOR_FORMATTED_GIVEN_FIRST} as not all
        // search sites can cope with the formatted version.
        final List<String> dbNames =
                ServiceLocator.getInstance().getAuthorDao()
                              .getNames(DBKey.AUTHOR.FORMATTED_FULL_NAME_GIVEN_FIRST);
        return combineNames(context, dbNames, recentAuthorNames);
    }

    boolean addSeriesName(@NonNull final String searchText) {
        return addName(recentSeriesNames, searchText);
    }

    @NonNull
    List<String> getSeriesNames(@NonNull final Context context) {
        final List<String> dbNames = ServiceLocator.getInstance().getSeriesDao().getNames();
        return combineNames(context, dbNames, recentSeriesNames);
    }

    boolean addPublisherName(@NonNull final String searchText) {
        return addName(recentPublisherNames, searchText);
    }

    @NonNull
    List<String> getPublisherNames(@NonNull final Context context) {
        final List<String> dbNames = ServiceLocator.getInstance().getPublisherDao().getNames();
        return combineNames(context, dbNames, recentPublisherNames);
    }
}
