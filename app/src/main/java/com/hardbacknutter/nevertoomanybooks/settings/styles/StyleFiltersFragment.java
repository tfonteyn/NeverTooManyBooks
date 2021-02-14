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
package com.hardbacknutter.nevertoomanybooks.settings.styles;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.filters.Filters;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.widgets.TriStateMultiSelectListPreference;

/**
 * Used/defined in xml/preferences_styles.xml
 */
@Keep
public class StyleFiltersFragment
        extends StyleBaseFragment {

    private static final String TAG = "StyleFiltersFragment";

    @Override
    @CallSuper
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences_style_filters, rootKey);

        //noinspection ConstantConditions
        final SharedPreferences global = PreferenceManager
                .getDefaultSharedPreferences(getContext());

        Preference preference;

        preference = findPreference(Filters.PK_FILTER_READ);
        //noinspection ConstantConditions
        preference.setVisible(DBDefinitions.isUsed(global, DBDefinitions.KEY_READ));
        preference.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

        preference = findPreference(Filters.PK_FILTER_SIGNED);
        //noinspection ConstantConditions
        preference.setVisible(DBDefinitions.isUsed(global, DBDefinitions.KEY_SIGNED));
        preference.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

        preference = findPreference(Filters.PK_FILTER_TOC_BITMASK);
        //noinspection ConstantConditions
        preference.setVisible(DBDefinitions.isUsed(global, DBDefinitions.KEY_TOC_BITMASK));
        preference.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

        preference = findPreference(Filters.PK_FILTER_LOANEE);
        //noinspection ConstantConditions
        preference.setVisible(DBDefinitions.isUsed(global, DBDefinitions.KEY_LOANEE));
        preference.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

        preference = findPreference(Filters.PK_FILTER_ISBN);
        //noinspection ConstantConditions
        preference.setVisible(DBDefinitions.isUsed(global, DBDefinitions.KEY_ISBN));
        preference.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

        preference = findPreference(Filters.PK_FILTER_EDITION_BITMASK);
        //noinspection ConstantConditions
        preference.setVisible(DBDefinitions.isUsed(global, DBDefinitions.KEY_EDITION_BITMASK));
        preference.setSummaryProvider(TriStateMultiSelectListPreference
                                              .SimpleSummaryProvider.getInstance());


        final MultiSelectListPreference bookshelves = findPreference(Filters.PK_FILTER_BOOKSHELVES);

        if (BuildConfig.ENABLE_STYLE_BOOKSHELF_FILTER) {
            //noinspection ConstantConditions
            bookshelves.setVisible(true);

            try (DAO db = new DAO(getContext(), TAG)) {
                final ArrayList<Bookshelf> list = db.getBookshelves();
                bookshelves.setEntryValues(
                        list.stream()
                            .map(bookshelf -> String.valueOf(bookshelf.getId()))
                            .toArray(String[]::new));

                bookshelves.setEntries(
                        list.stream().map(Bookshelf::getName)
                            .toArray(String[]::new));
            }
            bookshelves.setSummaryProvider(TriStateMultiSelectListPreference
                                                   .SimpleSummaryProvider.getInstance());

        } else {
            //noinspection ConstantConditions
            bookshelves.setVisible(false);
        }
    }
}
