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
import android.text.InputType;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
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
    // category
    private static final String PSK_SYNC_OPTIONS = "psk_sync_options";

    private SwitchPreference pSyncEnabled;
    private SwitchPreference pLoginToSearch;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences_site_stripinfo, rootKey);

        initValidator(R.string.site_stripinfo_be);

        //noinspection DataFlowIssue
        findPreference("stripinfo.resolve.authors.bedetheque")
                .setEnabled(ServiceLocator.getInstance().isFieldEnabled(DBKey.AUTHOR_REAL_AUTHOR));

        // Hide the whole category if it's not included in the build.
        //noinspection DataFlowIssue
        findPreference(PSK_SYNC_OPTIONS)
                .setVisible(BuildConfig.ENABLE_STRIP_INFO_LOGIN);

        //noinspection DataFlowIssue
        pSyncEnabled = findPreference(StripInfoHandler.PK_ENABLED);
        //noinspection DataFlowIssue
        pLoginToSearch = findPreference(StripInfoAuth.PK_LOGIN_TO_SEARCH);

        if (BuildConfig.ENABLE_STRIP_INFO_LOGIN) {
            EditTextPreference etp;

            etp = findPreference(StripInfoAuth.PK_HOST_USER);
            //noinspection DataFlowIssue
            etp.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_CLASS_TEXT);
                editText.selectAll();
            });

            etp = findPreference(StripInfoAuth.PK_HOST_PASS);
            //noinspection DataFlowIssue
            etp.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_CLASS_TEXT
                                      | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                editText.selectAll();
            });
            etp.setSummaryProvider(preference -> {
                final String value = ((EditTextPreference) preference).getText();
                if (value == null || value.isEmpty()) {
                    return getString(R.string.preference_not_set);
                } else {
                    return "********";
                }
            });
        }
    }

    @Override
    protected boolean shouldProposeValidation() {
        return pLoginToSearch.isChecked()
               || pSyncEnabled.isChecked();
    }
}
