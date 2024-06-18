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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.ArrayRes;
import androidx.annotation.CallSuper;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.StartupViewModel;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SearchSitesAllListsContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SettingsContract;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.covers.CoverVolume;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.settings.styles.StyleViewModel;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreHandler;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;
import com.hardbacknutter.nevertoomanybooks.utils.theme.NightMode;
import com.hardbacknutter.nevertoomanybooks.utils.theme.ThemeColorController;

/**
 * Global settings page.
 * <p>
 * TODO: add per-app locale:
 *  https://developer.android.com/guide/topics/resources/app-languages
 *  {@link Settings#ACTION_APP_LOCALE_SETTINGS}
 * TODO: add link: {@link Settings#ACTION_APPLICATION_SETTINGS}
 *  to allow access to storage etc...
 */
public class SettingsFragment
        extends BasePreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    /** Fragment/Log tag. */
    public static final String TAG = "SettingsFragment";

    private static final String PSK_USER_INTERFACE = "psk_user_interface";
    private static final String PSK_SEARCH_SITE_ORDER = "psk_search_site_order";
    private static final String PSK_STYLE_DEFAULTS = "psk_style_defaults";
    private static final String PSK_CALIBRE = "psk_calibre";

    private final ActivityResultLauncher<Void> editSitesLauncher =
            registerForActivityResult(new SearchSitesAllListsContract(),
                                      success -> { /* ignore */ });

    private SettingsViewModel vm;

    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    final Intent resultIntent = SettingsContract.createResult(
                            vm.isRequiresActivityRecreation(),
                            vm.isForceRebuildBooklist());
                    //noinspection DataFlowIssue
                    getActivity().setResult(Activity.RESULT_OK, resultIntent);
                    getActivity().finish();
                }
            };

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private TitleOrderByHelper titleOrderByHelper;
    private StorageVolumeHelper storageVolumeHelper;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(SettingsViewModel.class);
        //noinspection DataFlowIssue
        vm.init(getContext(), getArguments());

        setPreferencesFromResource(R.xml.preferences, rootKey);

        final ListPreference pUiLocale = findPreference(Prefs.PK_UI_LOCALE);
        //noinspection DataFlowIssue
        pUiLocale.setEntries(vm.getUiLangNames());
        pUiLocale.setOnPreferenceChangeListener((preference, newValue) -> {
            // Set the activity result so our caller will recreate itself
            vm.setOnBackRequiresActivityRecreation();
            getActivity().recreate();
            return true;
        });

        final Preference pFastscroller = findPreference(Prefs.PK_BOOKLIST_FASTSCROLLER_OVERLAY);
        //noinspection DataFlowIssue
        pFastscroller.setOnPreferenceChangeListener((preference, newValue) -> {
            vm.setOnBackRequiresActivityRecreation();
            return true;
        });

        //noinspection DataFlowIssue
        findPreference(PSK_SEARCH_SITE_ORDER).setOnPreferenceClickListener(p -> {
            editSitesLauncher.launch(null);
            return true;
        });

        // Add flag to indicate we'll be editing the global-style when coming from here
        //noinspection DataFlowIssue
        findPreference(PSK_STYLE_DEFAULTS)
                .getExtras().putBoolean(StyleViewModel.BKEY_GLOBAL_STYLE, true);

        //noinspection DataFlowIssue
        titleOrderByHelper = new TitleOrderByHelper(
                getContext(), findPreference(ReorderHelper.PK_SORT_TITLE_REORDERED));

        //noinspection DataFlowIssue
        storageVolumeHelper = new StorageVolumeHelper(
                getContext(), findPreference(Prefs.PK_STORAGE_VOLUME));
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.lbl_settings);
        toolbar.setSubtitle("");

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        vm.onProgress().observe(getViewLifecycleOwner(), storageVolumeHelper::onProgress);
        vm.onMoveCancelled().observe(getViewLifecycleOwner(), storageVolumeHelper::onMoveCancelled);
        vm.onMoveFailure().observe(getViewLifecycleOwner(), storageVolumeHelper::onMoveFailure);
        vm.onMoveFinished().observe(getViewLifecycleOwner(), storageVolumeHelper::onMoveFinished);
    }

    @Override
    public void onResume() {
        super.onResume();
        final SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();
        //noinspection DataFlowIssue
        prefs.registerOnSharedPreferenceChangeListener(this);

        updateThemeSummary();

        //noinspection DataFlowIssue
        findPreference(PSK_CALIBRE).setSummary(CalibreHandler.isSyncEnabled(getContext())
                                               ? R.string.enabled : R.string.disabled);
    }

    private void updateThemeSummary() {
        final Context context = getContext();
        //noinspection DataFlowIssue
        final Resources res = context.getResources();

        final String themeMode = getModeSummary(res, R.array.pe_ui_theme_mode,
                                                NightMode.getSetting(context));

        final String themeColors = getModeSummary(res, R.array.pe_ui_theme_colors,
                                                  ThemeColorController.getSetting(context));

        // Hardcoded ';' as separator... oh well...
        // Also note we don't display the other UI settings (e.g. dialogs etc...)
        final String uiSummary = themeMode + "; " + themeColors;

        //noinspection DataFlowIssue
        findPreference(PSK_USER_INTERFACE).setSummary(uiSummary);
    }

    @NonNull
    private static String getModeSummary(@NonNull final Resources res,
                                         @ArrayRes final int resId,
                                         final int value) {
        final String[] modes = res.getStringArray(resId);
        int mode = value;
        // sanity check
        if (mode > modes.length) {
            mode = 0;
        }
        return modes[mode];
    }

    @Override
    public void onPause() {
        //noinspection DataFlowIssue
        getPreferenceScreen().getSharedPreferences()
                             .unregisterOnSharedPreferenceChangeListener(this);

        super.onPause();
    }

    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences prefs,
                                          @Nullable final String key) {
        if (ReorderHelper.PK_SORT_TITLE_REORDERED.equals(key)) {
            // Set the activity result so our caller will recreate itself
            vm.setOnBackRequiresActivityRecreation();
        }
    }

    /**
     * Encapsulates all the code to handle the
     * {@link Prefs#PK_STORAGE_VOLUME} preference.
     */
    private class StorageVolumeHelper {
        @NonNull
        private final Context context;
        private final ListPreference storageVolumePref;
        @Nullable
        private ProgressDelegate progressDelegate;
        private int volumeChangedOptionChosen;

        StorageVolumeHelper(@NonNull final Context context,
                            @NonNull final ListPreference preference) {
            this.context = context;
            this.storageVolumePref = preference;

            final StorageManager storage = (StorageManager)
                    this.context.getSystemService(Context.STORAGE_SERVICE);

            final List<StorageVolume> storageVolumes =
                    storage.getStorageVolumes()
                           .stream()
                           .filter(sv -> Environment.MEDIA_MOUNTED.equals(sv.getState()))
                           .collect(Collectors.toList());

            final int max = storageVolumes.size();
            final CharSequence[] entries = new CharSequence[max];
            final CharSequence[] entryValues = new CharSequence[max];

            for (int i = 0; i < max; i++) {
                final StorageVolume sv = storageVolumes.get(i);
                entries[i] = sv.getDescription(context);
                entryValues[i] = String.valueOf(i);
            }

            storageVolumePref.setEntries(entries);
            storageVolumePref.setEntryValues(entryValues);
            storageVolumePref.setOnPreferenceChangeListener(this::onStorageVolumeChange);
        }

        private boolean onStorageVolumeChange(@NonNull final Preference pref,
                                              @NonNull final Object newValue) {
            final int newVolumeIndex = storageVolumePref.findIndexOfValue((String) newValue);
            final CharSequence newVolumeDesc = storageVolumePref.getEntries()[newVolumeIndex];

            if (vm.isMissingStorageVolume()) {
                // The originally used volume is not available; there is nothing to move.
                // Handle this as a simple 'select'
                new MaterialAlertDialogBuilder(context)
                        .setIcon(R.drawable.ic_baseline_warning_24)
                        .setTitle(R.string.lbl_storage_settings)
                        // this dialog is important. Make sure the user pays some attention
                        .setCancelable(false)
                        .setMessage(context.getString(R.string.option_storage_select,
                                                      newVolumeDesc))
                        .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                        .setPositiveButton(android.R.string.ok, (d, w) ->
                                setStorageVolume(newVolumeIndex))
                        .create()
                        .show();
            } else {
                final int oldVolumeIndex = vm.getStoredVolumeIndex();
                final CharSequence oldVolumeDesc = storageVolumePref.getEntries()[oldVolumeIndex];

                final CharSequence[] items = {
                        context.getString(R.string.option_storage_select, newVolumeDesc),
                        context.getString(R.string.option_moving_covers_from_x_to_y,
                                          oldVolumeDesc, newVolumeDesc)};
                // default to option_moving_covers_from_x_to_y
                volumeChangedOptionChosen = 1;

                new MaterialAlertDialogBuilder(context)
                        .setIcon(R.drawable.ic_baseline_warning_24)
                        .setTitle(R.string.lbl_storage_settings)
                        // this dialog is important. Make sure the user pays some attention
                        .setCancelable(false)
                        .setSingleChoiceItems(items, volumeChangedOptionChosen,
                                              (d, w) -> volumeChangedOptionChosen = w)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                        .setPositiveButton(android.R.string.ok, (d, w) ->
                                onVolumeChangedOptionChosen(oldVolumeIndex, newVolumeIndex))
                        .create()
                        .show();
            }

            // Do not let the system update the preference value.
            return false;
        }

        private void onVolumeChangedOptionChosen(final int oldVolumeIndex,
                                                 final int newVolumeIndex) {
            switch (volumeChangedOptionChosen) {
                case 0: {
                    setStorageVolume(newVolumeIndex);
                    break;
                }
                case 1: {
                    // check space and start the task
                    if (!vm.moveData(context, oldVolumeIndex, newVolumeIndex)) {
                        //noinspection DataFlowIssue
                        Snackbar.make(getView(), R.string.error_storage_not_writable,
                                      Snackbar.LENGTH_LONG).show();
                    }
                    break;
                }
                default:
                    throw new IllegalStateException(String.valueOf(volumeChangedOptionChosen));
            }
        }

        private boolean setStorageVolume(final int volume) {
            storageVolumePref.setValue(String.valueOf(volume));
            //noinspection OverlyBroadCatchBlock
            try {
                CoverVolume.initVolume(context, volume);
                return true;

            } catch (@NonNull final StorageException e) {
                // This should never happen... flw
                ErrorDialog.show(context, TAG, e);
                return false;
            }
        }

        void onProgress(@NonNull final LiveDataEvent<TaskProgress> message) {
            message.process(progress -> {
                if (progressDelegate == null) {
                    //noinspection DataFlowIssue
                    progressDelegate = new ProgressDelegate(getProgressFrame())
                            .setTitle(R.string.lbl_moving_data)
                            .setPreventSleep(true)
                            .setIndeterminate(true)
                            .setOnCancelListener(v -> vm.cancelTask(progress.taskId))
                            .show(() -> getActivity().getWindow());
                }
                progressDelegate.onProgress(progress);
            });
        }

        private void closeProgressDialog() {
            if (progressDelegate != null) {
                //noinspection DataFlowIssue
                progressDelegate.dismiss(getActivity().getWindow());
                progressDelegate = null;
            }
        }

        void onMoveFinished(@NonNull final LiveDataEvent<Integer> message) {
            closeProgressDialog();

            message.process(volume -> {
                if (setStorageVolume(volume)) {
                    //noinspection DataFlowIssue
                    Snackbar.make(getView(), R.string.action_done, Snackbar.LENGTH_LONG).show();
                }
            });
        }

        void onMoveFailure(@NonNull final LiveDataEvent<Throwable> message) {
            closeProgressDialog();

            message.process(e -> {
                //noinspection DataFlowIssue
                ErrorDialog.show(getContext(), TAG, e,
                                 getString(R.string.lbl_moving_data),
                                 getString(R.string.error_storage_not_accessible));
            });
        }

        void onMoveCancelled(@NonNull final LiveDataEvent<Integer> message) {
            closeProgressDialog();

            message.process(ignored -> {
                // FIXME: need better msg + tell user to clean up the destination
                showMessageAndFinishActivity(getString(R.string.cancelled));
            });
        }
    }

    /**
     * Encapsulates all the code to handle the
     * {@link ReorderHelper#PK_SORT_TITLE_REORDERED} preference.
     */
    private class TitleOrderByHelper {

        @NonNull
        private final Context context;
        private final SwitchPreference titleOrderByPref;
        private final PreferenceSummaryProvider summaryProvider;

        TitleOrderByHelper(@NonNull final Context context,
                           @NonNull final SwitchPreference preference) {
            this.context = context;
            this.titleOrderByPref = preference;

            titleOrderByPref.setOnPreferenceChangeListener(this::onChanged);
            summaryProvider = new PreferenceSummaryProvider(context);
            titleOrderByPref.setSummaryProvider(summaryProvider);
        }

        boolean onChanged(@NonNull final Preference pref,
                          @NonNull final Object newValue) {
            final boolean checked = (Boolean) newValue;

            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_baseline_warning_24)
                    .setMessage(R.string.confirm_rebuild_orderby_columns)
                    // this dialog is important. Make sure the user pays some attention
                    .setCancelable(false)
                    // Cancelling will revert to the original value and remove any scheduling
                    .setNegativeButton(android.R.string.cancel, (d, w) -> {
                        StartupViewModel.schedule(context, StartupViewModel.PK_REBUILD_TITLE_OB,
                                                  false);
                        titleOrderByPref.setChecked(vm.getStoredTitleOrderBy());
                        // Force the summary to redisplay by
                        // re-setting the provider will call the protected "notifyChanged()"
                        // as (of course..) Android does not allow an easier solution.
                        // Note to self: fork or replace the androidx.preference lib...
                        titleOrderByPref.setSummaryProvider(summaryProvider);
                    })
                    // Confirming will persist the new value and schedule the rebuild
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        StartupViewModel.schedule(context, StartupViewModel.PK_REBUILD_TITLE_OB,
                                                  true);
                        titleOrderByPref.setChecked(checked);
                    })
                    .create()
                    .show();
            // Do not let the system update the preference value.
            return false;
        }

        private class PreferenceSummaryProvider
                implements Preference.SummaryProvider<SwitchPreference> {
            private final Context context;

            PreferenceSummaryProvider(@NonNull final Context context) {
                this.context = context;
            }

            @NonNull
            @Override
            public CharSequence provideSummary(@NonNull final SwitchPreference preference) {
                String summary = preference.isChecked()
                                 ? context.getString(R.string.ps_show_titles_reordered_on)
                                 : context.getString(R.string.ps_show_titles_reordered_off);

                final Spannable spannable;
                // Use the 'schedulerKey' to get the condition!
                //noinspection DataFlowIssue
                if (preference.getSharedPreferences()
                              .getBoolean(StartupViewModel.PK_REBUILD_TITLE_OB, false)) {
                    @ColorInt
                    final int color = AttrUtils.getColorInt(
                            context, com.google.android.material.R.attr.colorError);

                    final int warningStart = summary.length() + 1;
                    // Add the warning
                    final Locale locale = context.getResources().getConfiguration()
                                                 .getLocales().get(0);
                    summary += '\n' + context.getString(R.string.warning_restart_required)
                                             .toUpperCase(locale);
                    spannable = new SpannableString(summary);
                    spannable.setSpan(new ForegroundColorSpan(color),
                                      warningStart, summary.length(), 0);
                } else {
                    @ColorInt
                    final int color = AttrUtils.getColorInt(
                            context, android.R.attr.textColorPrimary);
                    spannable = new SpannableString(summary);
                    spannable.setSpan(new ForegroundColorSpan(color), 0, summary.length(), 0);
                }

                return spannable;
            }
        }
    }
}
