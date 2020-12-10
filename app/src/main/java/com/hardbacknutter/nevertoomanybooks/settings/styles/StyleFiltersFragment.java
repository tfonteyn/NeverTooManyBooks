/*
 * @Copyright 2020 HardBackNutter
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
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.filters.Filters;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;

/**
 * Used/defined in xml/preferences_styles.xml
 */
@Keep
public class StyleFiltersFragment
        extends StyleBaseFragment {

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
        if (preference != null) {
            preference.setVisible(DBDefinitions.isUsed(global, DBDefinitions.KEY_READ));
        }
        preference = findPreference(Filters.PK_FILTER_SIGNED);
        if (preference != null) {
            preference.setVisible(DBDefinitions.isUsed(global, DBDefinitions.KEY_SIGNED));
        }
        preference = findPreference(Filters.PK_FILTER_TOC_BITMASK);
        if (preference != null) {
            preference.setVisible(DBDefinitions.isUsed(global, DBDefinitions.KEY_TOC_BITMASK));
        }
        preference = findPreference(Filters.PK_FILTER_LOANEE);
        if (preference != null) {
            preference.setVisible(DBDefinitions.isUsed(global, DBDefinitions.KEY_LOANEE));
        }
        preference = findPreference(Filters.PK_FILTER_EDITION_BITMASK);
        if (preference != null) {
            preference.setVisible(DBDefinitions.isUsed(global, DBDefinitions.KEY_EDITION_BITMASK));
        }
        preference = findPreference(Filters.PK_FILTER_ISBN);
        if (preference != null) {
            preference.setVisible(DBDefinitions.isUsed(global, DBDefinitions.KEY_ISBN));
        }
    }
}
