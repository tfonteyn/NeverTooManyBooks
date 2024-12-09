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
package com.hardbacknutter.nevertoomanybooks.searchengines.bertrandpt;

import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.SwitchPreference;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.settings.BasePreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

@Keep
public class BertrandPtPreferencesFragment
        extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences_site_bertrandpt, rootKey);

        initSearchMenuPref(EngineId.BertrandPt);
    }

    /**
     * Set this manually, as the default depends on the user language.
     *
     * @param engineId to use
     */
    @SuppressWarnings("DataFlowIssue")
    private void initSearchMenuPref(@NonNull final EngineId engineId) {
        final SearchEngine.SearchOnSite searchEngine = (SearchEngine.SearchOnSite)
                engineId.createSearchEngine(getContext());
        final SwitchPreference preference = findPreference(
                engineId.getPreferenceKey() + '.' + Prefs.PK_SEARCH_WEBSITE_MENU);
        preference.setChecked(searchEngine.isShowSearchOnSiteMenu(getContext()));
    }
}
