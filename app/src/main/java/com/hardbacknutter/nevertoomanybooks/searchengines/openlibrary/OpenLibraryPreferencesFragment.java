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
package com.hardbacknutter.nevertoomanybooks.searchengines.openlibrary;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreference;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.settings.BasePreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.settings.ConnectionValidationHelper;

@Keep
public class OpenLibraryPreferencesFragment
        extends BasePreferenceFragment {

    // category
    private static final String PSK_CREDENTIALS = "psk_credentials";

    private SwitchPreference pLoginToSearch;
    private PreferenceCategory pcCredentials;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences_site_openlibrary, rootKey);

        initLoginPrefs();
        initCredentialPreferences(OpenLibraryAuth.PK_HOST_USER, OpenLibraryAuth.PK_HOST_PASS);
    }

    @SuppressWarnings("DataFlowIssue")
    private void initLoginPrefs() {
        pLoginToSearch = findPreference(OpenLibrarySearchEngine.PK_LOGIN_TO_SEARCH);
        pLoginToSearch.setVisible(BuildConfig.ENABLE_OPEN_LIBRARY_LOGIN);
        pLoginToSearch.setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Boolean) {
                final boolean loginOn = (Boolean) newValue;
                pcCredentials.setEnabled(loginOn);
            }
            return true;
        });

        pcCredentials = findPreference(PSK_CREDENTIALS);
        pcCredentials.setVisible(BuildConfig.ENABLE_OPEN_LIBRARY_LOGIN);
        pcCredentials.setEnabled(BuildConfig.ENABLE_OPEN_LIBRARY_LOGIN
                                 && pLoginToSearch.isChecked());
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        new ConnectionValidationHelper(
                R.string.site_open_library, this, getProgressFrame(), () -> {
            if (BuildConfig.ENABLE_OPEN_LIBRARY_LOGIN) {
                return pLoginToSearch.isChecked();
            } else {
                return false;
            }
        }, this::popBackStackOrFinish);
    }
}
