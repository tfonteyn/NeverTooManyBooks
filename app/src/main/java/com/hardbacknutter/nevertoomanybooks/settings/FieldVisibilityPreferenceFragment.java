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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.SwitchPreference;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

/**
 * Used/defined in xml/preferences.xml
 */
@Keep
public class FieldVisibilityPreferenceFragment
        extends BasePreferenceFragment {

    private SettingsViewModel mVm;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        //noinspection ConstantConditions
        mVm = new ViewModelProvider(getActivity()).get(SettingsViewModel.class);

        setPreferencesFromResource(R.xml.preferences_field_visibility, rootKey);
    }

    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences preferences,
                                          @NonNull final String key) {

        if (DBKey.PREFS_COVER_VISIBILITY_KEY[0].equals(key)
            && !preferences.getBoolean(key, false)) {
            // Setting cover 0 to false -> set cover 1 to false as well
            final SwitchPreference cover = findPreference(DBKey.PREFS_COVER_VISIBILITY_KEY[1]);
            //noinspection ConstantConditions
            cover.setChecked(false);
        }

        // Changing ANY field visibility will usually require recreating the activity which
        // was active when the prefs screen was called.
        // TODO: make this fine-grained
        mVm.setOnBackRequiresActivityRecreation();

        super.onSharedPreferenceChanged(preferences, key);
    }
}
