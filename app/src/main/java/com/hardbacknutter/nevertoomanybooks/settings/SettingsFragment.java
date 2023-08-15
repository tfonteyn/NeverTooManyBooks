/*
 * @Copyright 2018-2023 HardBackNutter
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
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.Settings;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.AttrRes;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.StartupViewModel;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SearchSitesAllListsContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SettingsContract;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.covers.CoverVolume;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreHandler;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;
import com.hardbacknutter.nevertoomanybooks.utils.NightMode;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;

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

    /** Fragment manager tag. */
    public static final String TAG = "SettingsFragment";
    public static final String BKEY_STORAGE_WAS_MISSING = TAG + ":swm";
    /** savedInstanceState key. */
    private static final String SIS_TITLE_ORDERBY = TAG + ":tob";
    private static final String SIS_VOLUME_INDEX = TAG + ":vol";

    private static final String PSK_SEARCH_SITE_ORDER = "psk_search_site_order";
    private static final String PSK_CALIBRE = "psk_calibre";

    private final ActivityResultLauncher<Void> editSitesLauncher =
            registerForActivityResult(new SearchSitesAllListsContract(),
                                      success -> { /* ignore */ });

    /**
     * Used to be able to reset this pref to what it was when this fragment started.
     * Persisted with savedInstanceState.
     */
    private boolean storedTitleOrderBy;
    private SwitchPreference titleOrderByPref;

    /**
     * Used to be able to reset this pref to what it was when this fragment started.
     * Persisted with savedInstanceState.
     */
    private String storedVolumeIndex;
    private ListPreference storageVolumePref;

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
    @Nullable
    private ProgressDelegate progressDelegate;
    private boolean storageWasMissing;
    private int volumeChangedOptionChosen;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(SettingsViewModel.class);

        setPreferencesFromResource(R.xml.preferences, rootKey);

        final ListPreference.SimpleSummaryProvider listSummaryProvider =
                ListPreference.SimpleSummaryProvider.getInstance();


        final Preference pUiLocale = findPreference(Prefs.pk_ui_locale);
        //noinspection DataFlowIssue
        pUiLocale.setSummaryProvider(listSummaryProvider);
        pUiLocale.setOnPreferenceChangeListener((preference, newValue) -> {
            // Set the activity result so our caller will recreate itself
            vm.setOnBackRequiresActivityRecreation();
            getActivity().recreate();
            return true;
        });


        final Preference pUiTheme = findPreference(Prefs.pk_ui_theme);
        //noinspection DataFlowIssue
        pUiTheme.setSummaryProvider(listSummaryProvider);
        pUiTheme.setOnPreferenceChangeListener((preference, newValue) -> {
            // we should never have an invalid setting in the prefs... flw
            try {
                final int mode = Integer.parseInt(String.valueOf(newValue));
                NightMode.apply(mode);
            } catch (@NonNull final NumberFormatException ignore) {
                NightMode.apply(0);
            }

            return true;
        });


        final Preference pFastscroller = findPreference(Prefs.pk_booklist_fastscroller_overlay);
        //noinspection DataFlowIssue
        pFastscroller.setSummaryProvider(listSummaryProvider);
        pFastscroller.setOnPreferenceChangeListener((preference, newValue) -> {
            vm.setOnBackRequiresActivityRecreation();
            return true;
        });

        //noinspection DataFlowIssue
        findPreference(ISBN.PK_EDIT_BOOK_ISBN_CHECKS).setSummaryProvider(listSummaryProvider);
        //noinspection DataFlowIssue
        findPreference(Prefs.pk_booklist_rebuild_state).setSummaryProvider(listSummaryProvider);
        //noinspection DataFlowIssue
        findPreference(Prefs.pk_booklist_context_menu).setSummaryProvider(listSummaryProvider);


        //noinspection DataFlowIssue
        findPreference(PSK_SEARCH_SITE_ORDER).setOnPreferenceClickListener(p -> {
            editSitesLauncher.launch(null);
            return true;
        });

        titleOrderByPref = findPreference(ReorderHelper.PK_SORT_TITLE_REORDERED);
        //noinspection DataFlowIssue
        setVisualIndicator(titleOrderByPref, StartupViewModel.PK_REBUILD_TITLE_OB);
        titleOrderByPref.setOnPreferenceChangeListener(this::onTitleOrderByChange);

        final Bundle args = getArguments();
        if (args != null) {
            storageWasMissing = args.getBoolean(BKEY_STORAGE_WAS_MISSING);
        }

        //noinspection DataFlowIssue
        final StorageManager storage = (StorageManager)
                getContext().getSystemService(Context.STORAGE_SERVICE);

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
            entries[i] = sv.getDescription(getContext());
            entryValues[i] = String.valueOf(i);
        }

        storageVolumePref = findPreference(Prefs.pk_storage_volume);
        //noinspection DataFlowIssue
        storageVolumePref.setSummaryProvider(listSummaryProvider);
        storageVolumePref.setEntries(entries);
        storageVolumePref.setEntryValues(entryValues);
        storageVolumePref.setOnPreferenceChangeListener(this::onStorageVolumeChange);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        vm.onProgress().observe(getViewLifecycleOwner(), this::onProgress);
        vm.onMoveCancelled().observe(getViewLifecycleOwner(), this::onMoveCancelled);
        vm.onMoveFailure().observe(getViewLifecycleOwner(), this::onMoveFailure);
        vm.onMoveFinished().observe(getViewLifecycleOwner(), this::onMoveFinished);

        final boolean currentSortTitleReordered = titleOrderByPref.isChecked();
        final String currentStorageVolume = storageVolumePref.getValue();

        if (savedInstanceState == null) {
            storedTitleOrderBy = currentSortTitleReordered;
            storedVolumeIndex = currentStorageVolume;
        } else {
            storedTitleOrderBy = savedInstanceState
                    .getBoolean(SIS_TITLE_ORDERBY, currentSortTitleReordered);
            storedVolumeIndex = savedInstanceState
                    .getString(SIS_VOLUME_INDEX, currentStorageVolume);
        }
    }

    private boolean onTitleOrderByChange(@NonNull final Preference pref,
                                         @NonNull final Object newValue) {
        final boolean checked = (Boolean) newValue;

        //noinspection DataFlowIssue
        new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setMessage(R.string.confirm_rebuild_orderby_columns)
                // this dialog is important. Make sure the user pays some attention
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, (d, w) -> {
                    // revert to the original value.
                    titleOrderByPref.setChecked(storedTitleOrderBy);
                    StartupViewModel.schedule(getContext(),
                                              StartupViewModel.PK_REBUILD_TITLE_OB, false);
                    setVisualIndicator(titleOrderByPref, StartupViewModel.PK_REBUILD_TITLE_OB);
                })
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    // Persist the new value
                    titleOrderByPref.setChecked(checked);
                    StartupViewModel.schedule(getContext(),
                                              StartupViewModel.PK_REBUILD_TITLE_OB, true);
                    setVisualIndicator(titleOrderByPref, StartupViewModel.PK_REBUILD_TITLE_OB);
                })
                .create()
                .show();
        // Do not let the system update the preference value.
        return false;
    }

    private boolean onStorageVolumeChange(@NonNull final Preference pref,
                                          @NonNull final Object newValue) {
        final int newVolumeIndex = storageVolumePref.findIndexOfValue((String) newValue);
        final CharSequence newVolumeDesc = storageVolumePref.getEntries()[newVolumeIndex];

        if (storageWasMissing) {
            // The originally used volume is not available; there is nothing to move.
            // Handle this as a simple 'select'
            //noinspection DataFlowIssue
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_baseline_warning_24)
                    .setTitle(R.string.lbl_storage_settings)
                    // this dialog is important. Make sure the user pays some attention
                    .setCancelable(false)
                    .setMessage(getString(R.string.option_storage_select, newVolumeDesc))
                    .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(android.R.string.ok, (d, w) ->
                            setStorageVolume(newVolumeIndex))
                    .create()
                    .show();
        } else {
            final int oldVolumeIndex = storageVolumePref.findIndexOfValue(storedVolumeIndex);
            final CharSequence oldVolumeDesc = storageVolumePref.getEntries()[oldVolumeIndex];

            final CharSequence[] items = {
                    getString(R.string.option_storage_select, newVolumeDesc),
                    getString(R.string.option_moving_covers_from_x_to_y,
                              oldVolumeDesc, newVolumeDesc)};

            //noinspection DataFlowIssue
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_baseline_warning_24)
                    .setTitle(R.string.lbl_storage_settings)
                    // this dialog is important. Make sure the user pays some attention
                    .setCancelable(false)
                    .setSingleChoiceItems(items, 1, (d, w) -> volumeChangedOptionChosen = w)
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
                //noinspection DataFlowIssue
                if (!vm.moveData(getContext(), oldVolumeIndex, newVolumeIndex)) {
                    //noinspection DataFlowIssue
                    Snackbar.make(getView(), R.string.error_storage_not_writable,
                                  Snackbar.LENGTH_LONG).show();
                }
                break;
            }
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        final SharedPreferences global = getPreferenceScreen().getSharedPreferences();
        //noinspection DataFlowIssue
        global.registerOnSharedPreferenceChangeListener(this);

        final boolean enabled = global.getBoolean(CalibreHandler.PK_ENABLED, false);
        //noinspection DataFlowIssue
        findPreference(PSK_CALIBRE).setSummary(enabled ? R.string.enabled : R.string.disabled);
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SIS_TITLE_ORDERBY, storedTitleOrderBy);
        outState.putString(SIS_VOLUME_INDEX, storedVolumeIndex);
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
                                          @NonNull final String key) {
        //TODO: once these are on the style level, the below can be removed as well.
        switch (key) {
            case ReorderHelper.PK_SORT_TITLE_REORDERED:
            case ReorderHelper.PK_SHOW_TITLE_REORDERED: {
                // Set the activity result so our caller will recreate itself
                vm.setOnBackRequiresActivityRecreation();
                break;
            }
            default:
                break;
        }
    }

    /**
     * Change the icon color depending on the preference being scheduled for change on restart.
     * <p>
     * TODO: this is not ideal as it does not explain to the user WHY the color is changed
     * Check if it's possible to overlay the icon with another icon (showing e.g. a clock)
     *
     * @param preference   to modify
     * @param schedulerKey to reflect
     */
    private void setVisualIndicator(@NonNull final Preference preference,
                                    @SuppressWarnings("SameParameterValue")
                                    @NonNull final String schedulerKey) {
        @AttrRes
        final int attr;
        //careful: we use the pref to get SharedPreferences... but we need the 'schedulerKey' !
        //noinspection DataFlowIssue
        if (preference.getSharedPreferences().getBoolean(schedulerKey, false)) {
            attr = R.attr.appPreferenceAlertColor;
        } else {
            attr = com.google.android.material.R.attr.colorControlNormal;
        }

        //noinspection DataFlowIssue
        final Drawable icon = preference.getIcon().mutate();
        //noinspection DataFlowIssue
        icon.setTint(AttrUtils.getColorInt(getContext(), attr));
        preference.setIcon(icon);
    }

    private void onProgress(@NonNull final LiveDataEvent<TaskProgress> message) {
        message.getData().ifPresent(data -> {
            if (progressDelegate == null) {
                //noinspection DataFlowIssue
                progressDelegate = new ProgressDelegate(getProgressFrame())
                        .setTitle(R.string.lbl_moving_data)
                        .setPreventSleep(true)
                        .setIndeterminate(true)
                        .setOnCancelListener(v -> vm.cancelTask(data.taskId))
                        .show(() -> getActivity().getWindow());
            }
            progressDelegate.onProgress(data);
        });
    }

    private void closeProgressDialog() {
        if (progressDelegate != null) {
            //noinspection DataFlowIssue
            progressDelegate.dismiss(getActivity().getWindow());
            progressDelegate = null;
        }
    }

    private void onMoveFinished(@NonNull final LiveDataEvent<TaskResult<Integer>> message) {
        closeProgressDialog();

        message.getData()
               .map(data -> Objects.requireNonNull(data.getResult()))
               .ifPresent(result -> {
                   if (setStorageVolume(result)) {
                       //noinspection DataFlowIssue
                       Snackbar.make(getView(), R.string.action_done, Snackbar.LENGTH_LONG).show();
                   }
               });
    }

    private boolean setStorageVolume(final int volume) {
        storageVolumePref.setValue(String.valueOf(volume));
        //noinspection OverlyBroadCatchBlock
        try {
            //noinspection DataFlowIssue
            CoverVolume.initVolume(getContext(), volume);
            return true;

        } catch (@NonNull final StorageException e) {
            // This should never happen... flw
            ErrorDialog.show(getContext(), TAG, e);
            return false;
        }
    }

    private void onMoveFailure(@NonNull final LiveDataEvent<TaskResult<Throwable>> message) {
        closeProgressDialog();

        message.getData().map(TaskResult::getResult).filter(Objects::nonNull).ifPresent(e -> {
            //noinspection DataFlowIssue
            ErrorDialog.show(getContext(), e,
                             getString(R.string.lbl_moving_data),
                             getString(R.string.error_storage_not_accessible));
        });
    }

    private void onMoveCancelled(@NonNull final LiveDataEvent<TaskResult<Integer>> message) {
        closeProgressDialog();

        message.getData().ifPresent(data -> {
            // FIXME: need better msg + tell user to clean up the destination
            showMessageAndFinishActivity(getString(R.string.cancelled));
        });
    }
}
