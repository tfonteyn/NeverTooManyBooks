/*
 * @Copyright 2018-2026 HardBackNutter
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

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.settings.BaseSettingsFragment;
import com.hardbacknutter.prefslib.SettingsDataStore;
import com.hardbacknutter.prefslib.SettingsManager;
import com.hardbacknutter.prefslib.SharedPreferencesDataStore;

@Keep
public class SiteConfigPreferenceFragment
        extends BaseSettingsFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "SiteConfigPrefFrag";

    @NonNull
    @Override
    protected SettingsManager.Builder onCreateSettings() {
        final SettingsDataStore store = new SharedPreferencesDataStore(
                ServiceLocator.getInstance().getSharedPreferences());
        //noinspection DataFlowIssue
        final SettingsManager.Builder factory = new SettingsManager.Builder(getContext(), store);

        factory.header(R.string.lbl_settings);

        for (final EngineId engineId : EngineId.values()) {
            final Class<? extends Fragment> clazz = engineId.getPreferenceFragmentClazz();
            if (clazz != null && engineId.isEnabled()) {
                factory.fragment(engineId.getPreferenceKey(),
                                 engineId.getLabelResId(),
                                 clazz.getName(), R.id.content_frame, null);
            }
        }

        return factory;
    }
}
