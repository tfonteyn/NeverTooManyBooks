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
package com.hardbacknutter.nevertoomanybooks.searchengines.isfdb;

import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreference;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.settings.ConnectionValidationBasePreferenceFragment;

@Keep
public class IsfdbPreferencesFragment
        extends ConnectionValidationBasePreferenceFragment {

    // category
    private static final String PSK_CREDENTIALS = "psk_credentials";

    private SwitchPreference pLoginToSearch;
    private PreferenceCategory pcCredentials;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences_site_isfdb, rootKey);

        initLoginPrefs();

        if (BuildConfig.ENABLE_ISFDB_LOGIN) {
            initValidator(R.string.site_isfdb);
            initCredentialPreferences(IsfdbAuth.PK_HOST_USER,
                                      IsfdbAuth.PK_HOST_PASS);
        }
    }

    @SuppressWarnings("DataFlowIssue")
    private void initLoginPrefs() {
        pLoginToSearch = findPreference(IsfdbSearchEngine.PK_LOGIN_TO_SEARCH);
        pLoginToSearch.setVisible(BuildConfig.ENABLE_ISFDB_LOGIN);
        pLoginToSearch.setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Boolean) {
                final boolean loginOn = (Boolean) newValue;
                pcCredentials.setEnabled(loginOn);
            }
            return true;
        });

        pcCredentials = findPreference(PSK_CREDENTIALS);
        pcCredentials.setVisible(BuildConfig.ENABLE_ISFDB_LOGIN);
        pcCredentials.setEnabled(BuildConfig.ENABLE_ISFDB_LOGIN
                                 && pLoginToSearch.isChecked());
    }

    @Override
    protected boolean shouldProposeValidation() {
        if (BuildConfig.ENABLE_ISFDB_LOGIN) {
            return pLoginToSearch.isChecked();
        } else {
            return false;
        }
    }
}
