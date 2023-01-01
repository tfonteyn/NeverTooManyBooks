/*
 * @Copyright 2018-2022 HardBackNutter
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

import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Used/defined in xml/preferences.xml
 */
@Keep
public class SearchInternetPreferenceFragment
        extends BasePreferenceFragment {

    private static final String PSK_SEARCH_SITE_ = "psk_search_site_";

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences_site_searches, rootKey);

        //noinspection ConstantConditions
        findPreference(PSK_SEARCH_SITE_ + "googlebooks")
                .setVisible(BuildConfig.ENABLE_GOOGLE_BOOKS);

        //noinspection ConstantConditions
        findPreference(PSK_SEARCH_SITE_ + "kbnl")
                .setVisible(BuildConfig.ENABLE_KB_NL);

        //noinspection ConstantConditions
        findPreference(PSK_SEARCH_SITE_ + "lastdodo")
                .setVisible(BuildConfig.ENABLE_LAST_DODO);

        //noinspection ConstantConditions
        findPreference(PSK_SEARCH_SITE_ + "stripinfo")
                .setVisible(BuildConfig.ENABLE_STRIP_INFO);

        //noinspection ConstantConditions
        findPreference(PSK_SEARCH_SITE_ + "bedetheque")
                .setVisible(BuildConfig.ENABLE_BEDETHEQUE);

        //noinspection ConstantConditions
        findPreference(PSK_SEARCH_SITE_ + "librarything")
                .setVisible(BuildConfig.ENABLE_LIBRARY_THING_ALT_ED);
    }
}
