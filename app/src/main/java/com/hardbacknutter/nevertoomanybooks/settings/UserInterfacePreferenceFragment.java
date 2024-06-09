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

import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.theme.NightMode;
import com.hardbacknutter.nevertoomanybooks.utils.theme.ThemeColorController;

/**
 * Used/defined in xml/preferences.xml
 */
@Keep
public class UserInterfacePreferenceFragment
        extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences_user_interface, rootKey);

        final Preference pUiThemeMode = findPreference(NightMode.PK_UI_THEME_MODE);
        //noinspection DataFlowIssue
        pUiThemeMode.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        pUiThemeMode.setOnPreferenceChangeListener((preference, newValue) -> {
            // we should never have an invalid setting in the prefs... flw
            try {
                final int mode = Integer.parseInt(String.valueOf(newValue));
                NightMode.apply(mode);
            } catch (@NonNull final NumberFormatException ignore) {
                NightMode.apply(0);
            }

            return true;
        });

        final Preference pUiThemeColor = findPreference(ThemeColorController.PK_UI_THEME_COLOR);
        if (Build.VERSION.SDK_INT < 33) {
            //noinspection DataFlowIssue
            pUiThemeColor.setEnabled(false);
            pUiThemeColor.setSummary(R.string.warning_requires_android_12);
        } else {
            //noinspection DataFlowIssue
            pUiThemeColor.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
            pUiThemeColor.setOnPreferenceChangeListener((preference, newValue) -> {
                ThemeColorController.recreate();
                return true;
            });
        }


        //noinspection DataFlowIssue
        findPreference(DialogMode.PK_UI_DIALOGS_MODE)
                .setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

        //noinspection DataFlowIssue
        findPreference(MenuMode.PK_UI_CONTEXT_MENUS)
                .setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.lbl_settings);
        toolbar.setSubtitle("");
    }
}
