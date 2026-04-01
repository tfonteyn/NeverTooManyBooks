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

package com.hardbacknutter.nevertoomanybooks.settings;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.theme.NightMode;
import com.hardbacknutter.nevertoomanybooks.utils.theme.ThemeColorController;
import com.hardbacknutter.prefslib.Setting;
import com.hardbacknutter.prefslib.SettingsDataStore;
import com.hardbacknutter.prefslib.SettingsManager;
import com.hardbacknutter.prefslib.SharedPreferencesDataStore;
import com.hardbacknutter.prefslib.SingleChoiceSetting;

@Keep
public class UserInterfacePreferenceFragment
        extends BaseSettingsFragment {

    private SettingsViewModel vm;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(SettingsViewModel.class);
    }

    @NonNull
    @Override
    protected SettingsManager.Builder onCreateSettings() {
        final SettingsDataStore store = new SharedPreferencesDataStore(
                ServiceLocator.getInstance().getSharedPreferences());
        //noinspection DataFlowIssue
        final SettingsManager.Builder factory = new SettingsManager.Builder(getContext(), store);

        factory.header(R.string.pc_ui);

        factory.singleChoice(AppLocale.PK_UI_LOCALE,
                             R.string.pt_ui_language,
                             this::onChangeUiLocale, p -> {
                    p.setIcon(R.drawable.language_24px);
                    p.setSummaryProvider(c -> getLanguageSummary(p));
                    p.setEntries(vm.getUiLanguageEntries());
                    p.setEntryValues(vm.getUiLanguageEntryValues());
                    p.setSelectedIndex(0);
                });

        factory.singleChoice(NightMode.PK_UI_THEME_MODE,
                             R.string.pt_ui_theme,
                             R.array.pe_ui_theme_mode,
                             R.array.pv_ui_theme_mode,
                             this::onChangeTheme, p -> {
                    p.setIcon(R.drawable.settings_brightness_24px);
                    p.setSelectedIndex(0);
                });

        factory.singleChoice(ThemeColorController.PK_UI_THEME_COLOR,
                             R.string.pt_ui_theme_colors,
                             R.array.pe_ui_theme_colors,
                             R.array.pv_ui_theme_colors,
                             this::onChangeThemeColor, p -> {
                    p.setIcon(R.drawable.palette_24px);
                    p.setSelectedIndex(0);
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        p.setSummary(getString(R.string.warning_requires_android_x, 12));
                    }
                });

        factory.singleChoice(BaseActivity.PK_UI_TOP_MENU,
                             R.string.pt_ui_screen_systembars_behavior,
                             R.array.pe_ui_screen_systembars,
                             R.array.pv_ui_screen_systembars,
                             this::onChangeUiTopMenuScrolling, p -> {
                    p.setSelectedIndex(0);
                });

        factory.singleChoice(DialogMode.PK_UI_DIALOGS_MODE,
                             R.string.pt_ui_dialogs_mode,
                             R.array.pe_ui_dialog_mode,
                             R.array.pv_ui_dialogs_mode, null, p -> {
                    // Default: by screen size.
                    p.setSelectedIndex(2);
                });

        factory.singleChoice(MenuMode.PK_UI_CONTEXT_MENUS,
                             R.string.pc_context_menus,
                             R.array.pe_ui_context_menu_mode,
                             R.array.pv_ui_menus_context, null, p -> {
                    // Default: by menu and screen size.
                    p.setSelectedIndex(2);
                });

        factory.singleChoice(FastScrollerMode.PK_DRAG_HANDLE,
                             R.string.lbl_fastscroller_draghandle,
                             R.array.pe_fastscroller_drag_handle,
                             R.array.pv_fastscroller_drag_handle,
                             this::onChangeDragHandle, p -> {
                    p.setSelectedIndex(0);
                });

        return factory;
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        //noinspection DataFlowIssue
        vm.init(getContext(), getArguments());
        // init the vm BEFORE calling the super, as onCreateSettings uses it.
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.lbl_settings);
        toolbar.setSubtitle("");

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // We offer the standard Blue/Grey colour scheme, or the Android 12 Dynamic Colours.
            // For simplicity, we just disable the setting when it's not 12+
            // If we (ever) add additional themes, then we'll need to
            // ONLY enable/disable the Dynamic Colours option.
            getSettingsManager().setEnabled(false, ThemeColorController.PK_UI_THEME_COLOR);
        }
    }

    private boolean onChangeUiTopMenuScrolling(@NonNull final Setting setting,
                                               @Nullable final Object newValue) {
        // Set the activity result so our caller will recreate itself
        vm.setOnBackRequiresActivityRecreation();
        // and recreate the current activity
        //noinspection DataFlowIssue
        getActivity().recreate();
        return true;
    }

    private boolean onChangeUiLocale(@NonNull final Setting setting,
                                     @Nullable final Object newValue) {
        // Set the activity result so our caller will recreate itself
        vm.setOnBackRequiresActivityRecreation();
        // and recreate the current activity so we get the new language immediately
        //noinspection DataFlowIssue
        getActivity().recreate();
        return true;
    }

    private boolean onChangeDragHandle(@NonNull final Setting setting,
                                       @Nullable final Object newValue) {
        // Set the activity result so our caller will recreate itself
        vm.setOnBackRequiresActivityRecreation();
        return true;
    }

    private boolean onChangeThemeColor(@NonNull final Setting setting,
                                       @Nullable final Object newValue) {
        // The controller will also restart the current Activity.
        ThemeColorController.recreate();
        return true;
    }

    private boolean onChangeTheme(@NonNull final Setting setting,
                                  @Nullable final Object newValue) {
        // we should never have an invalid setting in the prefs... flw
        try {
            final int mode;
            if (newValue == null) {
                mode = 0;
            } else {
                mode = Integer.parseInt(String.valueOf(newValue));
            }
            NightMode.apply(mode);
        } catch (@NonNull final NumberFormatException ignore) {
            NightMode.apply(0);
        }
        return true;
    }

    @NonNull
    private CharSequence getLanguageSummary(@NonNull final SingleChoiceSetting p) {
        final Context context = getContext();
        // not-set, or index 0
        final CharSequence value = p.getValue();
        if (value == null) {
            //noinspection DataFlowIssue
            return context.getString(R.string.pt_ui_system_locale);
        } else {
            //noinspection DataFlowIssue
            final Locale userLocale =
                    context.getResources().getConfiguration().getLocales().get(0);
            final Locale locale = ServiceLocator
                    .getInstance().getAppLocale()
                    .getLocale(value.toString(), userLocale)
                    // We should never get here... flw
                    .orElse(userLocale);
            // The NAME, i.e. including country, script,...
            return locale.getDisplayName(locale);
        }
    }
}
