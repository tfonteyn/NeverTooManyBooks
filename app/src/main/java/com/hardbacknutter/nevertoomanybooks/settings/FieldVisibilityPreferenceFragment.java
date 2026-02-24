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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceDataStore;
import androidx.preference.SwitchPreference;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

/**
 * Used/defined in xml/preferences.xml
 */
@Keep
public class FieldVisibilityPreferenceFragment
        extends BasePreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    @NonNull
    private final SwitchPreference[] pCovers = new SwitchPreference[DBKey.NR_OF_BOOK_COVERS];
    private SettingsViewModel vm;
    private VSDataStore dataStore;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(SettingsViewModel.class);

        // redirect storage to a single long value
        // This MUST be done in onCreate/onCreatePreferences,
        // and BEFORE we inflate the XML screen definition
        dataStore = new VSDataStore();
        dataStore.load();
        getPreferenceManager().setPreferenceDataStore(dataStore);

        setPreferencesFromResource(R.xml.preferences_field_visibility, rootKey);

        for (int cIdx = 0; cIdx < DBKey.NR_OF_BOOK_COVERS; cIdx++) {
            pCovers[cIdx] = findPreference(DBKey.COVER[cIdx]);
        }

        //noinspection DataFlowIssue
        pCovers[0].setOnPreferenceChangeListener((preference, newValue) -> {
            // Setting cover 0 to false
            if (newValue instanceof Boolean && !(Boolean) newValue) {
                // Set all others to false as well
                for (int cIdx = 1; cIdx < DBKey.NR_OF_BOOK_COVERS; cIdx++) {
                    pCovers[cIdx].setChecked(false);
                }
            }
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        dataStore.load();
        ServiceLocator.getInstance().getSharedPreferences()
                      .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        ServiceLocator.getInstance().getSharedPreferences()
                      .unregisterOnSharedPreferenceChangeListener(this);
        dataStore.save();
        super.onPause();
    }

    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences prefs,
                                          @Nullable final String key) {
        // Changing ANY field visibility will usually require recreating the activity
        vm.setOnBackRequiresActivityRecreation();
    }

    private static class VSDataStore
            extends PreferenceDataStore {

        @NonNull
        private final FieldVisibility fieldVisibility;

        VSDataStore() {
            fieldVisibility = ServiceLocator.getInstance().getGlobalFieldVisibility();
        }

        @Override
        public void putBoolean(@NonNull final String key,
                               final boolean value) {
            fieldVisibility.setVisible(key, value);
        }

        @Override
        public boolean getBoolean(@NonNull final String key,
                                  final boolean defValue) {
            return fieldVisibility.isVisible(key).orElse(true);
        }

        void load() {
            fieldVisibility.load();
        }

        void save() {
            fieldVisibility.save();
        }
    }
}
