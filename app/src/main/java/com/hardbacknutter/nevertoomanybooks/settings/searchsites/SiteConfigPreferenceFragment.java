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
package com.hardbacknutter.nevertoomanybooks.settings.searchsites;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.settings.BasePreferenceFragment;

@Keep
public class SiteConfigPreferenceFragment
        extends BasePreferenceFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "SiteConfigPrefFrag";

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences_site_searches, rootKey);

        final Context context = getContext();

        final PreferenceCategory root = findPreference("psk_root_cat");

        for (final EngineId engineId : EngineId.values()) {
            final Class<? extends Fragment> clazz = engineId.getPreferenceFragmentClazz();
            if (clazz != null && engineId.isEnabled()) {
                //noinspection DataFlowIssue
                final Preference preference = new Preference(context);
                preference.setTitle(engineId.getLabelResId());
                preference.setFragment(clazz.getName());
                preference.setKey(engineId.getPreferenceKey());
                //noinspection DataFlowIssue
                root.addPreference(preference);
            }
        }
    }
}
