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
package com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo;

import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreference;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.settings.ConnectionValidationBasePreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoHandler;

@Keep
public class StripInfoBePreferencesFragment
        extends ConnectionValidationBasePreferenceFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "StripInfoBePrefFrag";
    /** Category. */
    private static final String PSK_CREDENTIALS = "psk_credentials";

    private SwitchPreference pSyncEnabled;
    private SwitchPreference pLoginToSearch;
    private PreferenceCategory pcCredentials;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences_site_stripinfo, rootKey);

        //noinspection DataFlowIssue
        findPreference("stripinfo.resolve.authors.bedetheque")
                .setEnabled(ServiceLocator.getInstance().isFieldEnabled(DBKey.AUTHOR_REAL_AUTHOR));

        initLoginPrefs();

        if (BuildConfig.ENABLE_STRIP_INFO_LOGIN) {
            initValidator(R.string.site_stripinfo_be);
            initCredentialPreferences(StripInfoAuth.PK_HOST_USER,
                                      StripInfoAuth.PK_HOST_PASS);
        }
    }

    @SuppressWarnings("DataFlowIssue")
    private void initLoginPrefs() {
        pLoginToSearch = findPreference(StripInfoSearchEngine.PK_LOGIN_TO_SEARCH);
        pLoginToSearch.setVisible(BuildConfig.ENABLE_STRIP_INFO_LOGIN);
        pLoginToSearch.setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Boolean) {
                final boolean loginOn = (Boolean) newValue;
                final boolean syncOn = pSyncEnabled.isChecked();
                pcCredentials.setEnabled(loginOn || syncOn);
            }
            return true;
        });

        pSyncEnabled = findPreference(StripInfoHandler.PK_ENABLED);
        pSyncEnabled.setVisible(BuildConfig.ENABLE_STRIP_INFO_LOGIN);
        pSyncEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Boolean) {
                final boolean loginOn = pLoginToSearch.isChecked();
                final boolean syncOn = (Boolean) newValue;
                pcCredentials.setEnabled(loginOn || syncOn);
            }
            return true;
        });

        pcCredentials = findPreference(PSK_CREDENTIALS);
        pcCredentials.setVisible(BuildConfig.ENABLE_STRIP_INFO_LOGIN);
        pcCredentials.setEnabled(BuildConfig.ENABLE_STRIP_INFO_LOGIN
                                 && (pLoginToSearch.isChecked() || pSyncEnabled.isChecked()));
    }

    @Override
    protected boolean shouldProposeValidation() {
        if (BuildConfig.ENABLE_STRIP_INFO_LOGIN) {
            return pLoginToSearch.isChecked()
                   || pSyncEnabled.isChecked();
        } else {
            return false;
        }
    }
}
