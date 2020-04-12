/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.RequestCode;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * Used/defined in xml/preferences.xml
 */
public class WebSearchesPreferenceFragment
        extends BasePreferenceFragment {

    /** Log tag. */
    private static final String TAG = "WebSearchesPreferenceFr";

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState,
                                    final String rootKey) {

        setPreferencesFromResource(R.xml.preferences_site_searches, rootKey);

        initListeners();
    }

    /**
     * Hook up specific listeners/preferences.
     */
    private void initListeners() {
        Preference preference;

        preference = findPreference(Prefs.PSK_SEARCH_SITE_ORDER);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                Intent intent = new Intent(getContext(), SearchAdminActivity.class);
                startActivityForResult(intent, RequestCode.PREFERRED_SEARCH_SITES);
                return true;
            });
        }
    }

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }

        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            case RequestCode.PREFERRED_SEARCH_SITES:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    mResultDataModel.putResultData(data);
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }
}
