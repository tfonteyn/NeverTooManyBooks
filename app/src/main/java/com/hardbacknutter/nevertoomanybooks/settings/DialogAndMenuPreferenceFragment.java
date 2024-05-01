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

package com.hardbacknutter.nevertoomanybooks.settings;

import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Used/defined in xml/preferences.xml
 */
@Keep
public class DialogAndMenuPreferenceFragment
        extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences_dialog_and_menu_tuning, rootKey);

        //noinspection DataFlowIssue
        findPreference(Prefs.PK_UI_DIALOGS_MODE)
                .setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

        //noinspection DataFlowIssue
        findPreference(Prefs.PK_UI_CONTEXT_MENUS)
                .setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

    }
}
