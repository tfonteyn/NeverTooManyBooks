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
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;

/**
 * Used/defined in xml/preferences.xml
 */
@Keep
public class FieldVisibilityPreferenceFragment
        extends BasePreferenceFragment {

    private static final String[] PREFS_COVER_VISIBILITY_KEY = new String[]{
            // fields.visibility.thumbnail.0
            DBDefinitions.PREFS_PREFIX_FIELD_VISIBILITY + DBDefinitions.PREFS_IS_USED_COVER + ".0",
            // fields.visibility.thumbnail.1
            DBDefinitions.PREFS_PREFIX_FIELD_VISIBILITY + DBDefinitions.PREFS_IS_USED_COVER + ".1",
            };

    /** The Activity results. */
    private SettingsViewModel mVm;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences_field_visibility, rootKey);

        //noinspection ConstantConditions
        mVm = new ViewModelProvider(getActivity()).get(SettingsViewModel.class);
    }

    @Override
    public void onResume() {
        super.onResume();
        mToolbar.setTitle(R.string.lbl_settings);
        mToolbar.setSubtitle(R.string.pg_ui_field_visibility);
    }

    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences preferences,
                                          @NonNull final String key) {

        if (PREFS_COVER_VISIBILITY_KEY[0].equals(key)
            && !preferences.getBoolean(key, false)) {
            // Setting cover 0 to false -> set cover 1 to false as well
            final SwitchPreference cover = findPreference(PREFS_COVER_VISIBILITY_KEY[1]);
            //noinspection ConstantConditions
            cover.setChecked(false);
        }

        // Changing ANY field visibility will usually require recreating the activity which
        // was active when the prefs screen was called.
        // TODO: make this fine-grained
        mVm.setRequiresActivityRecreation();

        super.onSharedPreferenceChanged(preferences, key);
    }
}
