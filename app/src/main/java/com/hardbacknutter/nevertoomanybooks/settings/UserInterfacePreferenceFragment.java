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

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.theme.NightMode;
import com.hardbacknutter.nevertoomanybooks.utils.theme.ThemeColorController;

/**
 * Used/defined in xml/preferences.xml
 */
@Keep
public class UserInterfacePreferenceFragment
        extends BasePreferenceFragment {

    private static final int ANDROID_12 = 12;

    private SettingsViewModel vm;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(SettingsViewModel.class);
        //noinspection DataFlowIssue
        vm.init(getContext(), getArguments());

        setPreferencesFromResource(R.xml.preferences_user_interface, rootKey);

        initLanguage();
        initThemeMode();
        initThemeColors();
    }

    private void initLanguage() {
        final ListPreference pUiLocale = findPreference(Prefs.PK_UI_LOCALE);
        //noinspection DataFlowIssue
        pUiLocale.setDefaultValue(AppLocale.SYSTEM_LANGUAGE);
        pUiLocale.setEntries(vm.getUiLanguageEntries());
        pUiLocale.setEntryValues(vm.getUiLanguageEntryValues());
        pUiLocale.setSummaryProvider(new LanguageSummaryProvider());
        pUiLocale.setOnPreferenceChangeListener((preference, newValue) -> {
            // Set the activity result so our caller will recreate itself
            vm.setOnBackRequiresActivityRecreation();
            // and recreate the current activity so we get the new language immediately
            //noinspection DataFlowIssue
            getActivity().recreate();
            return true;
        });
    }

    private void initThemeMode() {
        final Preference pUiThemeMode = findPreference(NightMode.PK_UI_THEME_MODE);
        //noinspection DataFlowIssue
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
    }

    private void initThemeColors() {
        // We offer the standard Blue/Grey color scheme, or the Android 12 Dynamic Colors.
        // For simplicity, we just disable the setting when it's not 12+
        // If we (ever) add additional themes, then we'll need to ONLY enable/disable the DC option.
        final Preference pUiThemeColor = findPreference(ThemeColorController.PK_UI_THEME_COLOR);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            //noinspection DataFlowIssue
            pUiThemeColor.setEnabled(false);
            pUiThemeColor.setSummary(getString(R.string.warning_requires_android_x, ANDROID_12));
        } else {
            //noinspection DataFlowIssue
            pUiThemeColor.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
            pUiThemeColor.setOnPreferenceChangeListener((preference, newValue) -> {
                ThemeColorController.recreate();
                return true;
            });
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.lbl_settings);
        toolbar.setSubtitle("");
    }

    private static class LanguageSummaryProvider
            implements Preference.SummaryProvider<ListPreference> {

        LanguageSummaryProvider() {
        }

        @Nullable
        @Override
        public CharSequence provideSummary(@NonNull final ListPreference preference) {
            final Context context = preference.getContext();
            if (TextUtils.isEmpty(preference.getEntry())) {
                return (context.getString(R.string.pt_ui_system_locale));
            } else {
                final String value = preference.getValue();
                if (AppLocale.SYSTEM_LANGUAGE.equals(value)) {
                    return context.getString(R.string.pt_ui_system_locale);
                } else {
                    final Locale locale = ServiceLocator
                            .getInstance().getAppLocale()
                            .getLocale(context, value)
                            // We should never get here... flw
                            .orElseGet(() -> context.getResources().getConfiguration().getLocales()
                                                    .get(0));
                    // The NAME, i.e. including country, script,...
                    return locale.getDisplayName(locale);
                }
            }
        }
    }
}
