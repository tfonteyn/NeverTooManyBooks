/*
 * @Copyright 2018-2023 HardBackNutter
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
import androidx.preference.PreferenceManager;
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

    public static final String PK_FIELD_VISIBILITY = "fields.visibility";

    @NonNull
    private final SwitchPreference[] pCovers = new SwitchPreference[2];
    private SettingsViewModel vm;
    private VSDataStore dataStore;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(SettingsViewModel.class);

        // redirect storage to a single long value
        // This MUST be done in onCreate/onCreatePreferences
        // and BEFORE we inflate the xml screen definition
        dataStore = new VSDataStore();
        //noinspection DataFlowIssue
        dataStore.load(PreferenceManager.getDefaultSharedPreferences(getContext()));
        getPreferenceManager().setPreferenceDataStore(dataStore);

        setPreferencesFromResource(R.xml.preferences_field_visibility, rootKey);

        pCovers[0] = findPreference(DBKey.COVER[0]);
        pCovers[1] = findPreference(DBKey.COVER[1]);

        pCovers[0].setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Boolean && !(Boolean) newValue) {
                pCovers[1].setChecked(false);
            }
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        //noinspection DataFlowIssue
        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getContext());
        dataStore.load(prefs);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        //noinspection DataFlowIssue
        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getContext());
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        dataStore.save(prefs);
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

        void load(@NonNull final SharedPreferences prefs) {
            fieldVisibility.setBitValue(prefs.getLong(PK_FIELD_VISIBILITY, Long.MAX_VALUE));
        }

        void save(@NonNull final SharedPreferences prefs) {
            prefs.edit()
                 .putLong(PK_FIELD_VISIBILITY, fieldVisibility.getBitValue())
                 .apply();
        }
    }
}
