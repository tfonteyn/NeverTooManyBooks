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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;

@Keep
public class SiteConfigPreferenceFragment
        extends BasePreferenceFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "SiteConfigPrefFrag";

    /** Preference key prefix for individual sites. */
    private static final String PSK_SEARCH_SITE = "psk_search_site_";

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences_site_searches, rootKey);

        for (final EngineId engineId : EngineId.values()) {
            final Preference preference = findPreference(
                    PSK_SEARCH_SITE + engineId.getPreferenceKey());
            if (preference != null) {
                preference.setVisible(engineId.isEnabled());
            }
        }
    }
}
