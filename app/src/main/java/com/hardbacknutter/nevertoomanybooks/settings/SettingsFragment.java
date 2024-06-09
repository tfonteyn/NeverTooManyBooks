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
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.StartupViewModel;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SearchSitesAllListsContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SettingsContract;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.covers.CoverVolume;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.settings.styles.StyleViewModel;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreHandler;
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

    /** Fragment/Log tag. */
    public static final String TAG = "SettingsFragment";
    /** Passed in by the startup routines, indicating the storage device was not found. */
    public static final String BKEY_STORAGE_WAS_MISSING = TAG + ":swm";
    /** savedInstanceState key. */
    private static final String SIS_TITLE_ORDERBY = TAG + ":tob";
    private static final String SIS_VOLUME_INDEX = TAG + ":vol";

    private static final String PSK_SEARCH_SITE_ORDER = "psk_search_site_order";
    private static final String PSK_CALIBRE = "psk_calibre";

    private static final String PSK_STYLE_DEFAULTS = "psk_style_defaults";

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
    private TitleOrderByHelper titleOrderByHelper;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(SettingsViewModel.class);
        //noinspection DataFlowIssue
        vm.init(getContext());

        setPreferencesFromResource(R.xml.preferences, rootKey);

        final ListPreference pUiLocale = findPreference(Prefs.PK_UI_LOCALE);
        //noinspection DataFlowIssue
        pUiLocale.setEntries(vm.getUiLangNames());
        pUiLocale.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        pUiLocale.setOnPreferenceChangeListener((preference, newValue) -> {
            // Set the activity result so our caller will recreate itself
            vm.setOnBackRequiresActivityRecreation();
            getActivity().recreate();
            return true;
        });


        final Preference pUiTheme = findPreference(Prefs.PK_UI_DAY_NIGHT_MODE);
        //noinspection DataFlowIssue
        pUiTheme.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
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


        final Preference pFastscroller = findPreference(Prefs.PK_BOOKLIST_FASTSCROLLER_OVERLAY);
        //noinspection DataFlowIssue
        pFastscroller.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        pFastscroller.setOnPreferenceChangeListener((preference, newValue) -> {
            vm.setOnBackRequiresActivityRecreation();
            return true;
        });

        //noinspection DataFlowIssue
        findPreference(ISBN.PK_EDIT_BOOK_ISBN_CHECKS)
                .setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        //noinspection DataFlowIssue
        findPreference(Prefs.PK_BOOKLIST_REBUILD_STATE)
                .setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        //noinspection DataFlowIssue
        findPreference(Prefs.PK_BOOKLIST_CONTEXT_MENU)
                .setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

        //noinspection DataFlowIssue
        findPreference(PSK_SEARCH_SITE_ORDER).setOnPreferenceClickListener(p -> {
            editSitesLauncher.launch(null);
            return true;
        });

        titleOrderByPref = findPreference(ReorderHelper.PK_SORT_TITLE_REORDERED);
        titleOrderByHelper = new TitleOrderByHelper();
        //noinspection DataFlowIssue
        titleOrderByPref.setOnPreferenceChangeListener(titleOrderByHelper::onChanged);
        titleOrderByPref.setSummaryProvider(titleOrderByHelper);

        // Add flag to indicate we'll be editing the global-style when coming from here
        //noinspection DataFlowIssue
        findPreference(PSK_STYLE_DEFAULTS)
                .getExtras().putBoolean(StyleViewModel.BKEY_GLOBAL_STYLE, true);

        final Bundle args = getArguments();
        if (args != null) {
            storageWasMissing = args.getBoolean(BKEY_STORAGE_WAS_MISSING);
        }

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
        storageVolumePref.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        storageVolumePref.setEntries(entries);
        storageVolumePref.setEntryValues(entryValues);
        storageVolumePref.setOnPreferenceChangeListener(this::onStorageVolumeChange);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.lbl_settings);
        // erase any existing subtitle as we can come back here from a sub-fragment
        toolbar.setSubtitle("");

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
                throw new IllegalStateException(String.valueOf(volumeChangedOptionChosen));
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
                                          @Nullable final String key) {
        if (ReorderHelper.PK_SORT_TITLE_REORDERED.equals(key)) {
            // Set the activity result so our caller will recreate itself
            vm.setOnBackRequiresActivityRecreation();
        }
    }

    private void onProgress(@NonNull final LiveDataEvent<TaskProgress> message) {
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

    private void onMoveFinished(@NonNull final LiveDataEvent<Integer> message) {
        closeProgressDialog();

        message.process(volume -> {
            if (setStorageVolume(volume)) {
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

    private void onMoveFailure(@NonNull final LiveDataEvent<Throwable> message) {
        closeProgressDialog();

        message.process(e -> {
            //noinspection DataFlowIssue
            ErrorDialog.show(getContext(), TAG, e,
                             getString(R.string.lbl_moving_data),
                             getString(R.string.error_storage_not_accessible));
        });
    }

    private void onMoveCancelled(@NonNull final LiveDataEvent<Integer> message) {
        closeProgressDialog();

        message.process(ignored -> {
            // FIXME: need better msg + tell user to clean up the destination
            showMessageAndFinishActivity(getString(R.string.cancelled));
        });
    }

    private class TitleOrderByHelper
            implements Preference.SummaryProvider<Preference> {
        @NonNull
        @Override
        public CharSequence provideSummary(@NonNull final Preference preference) {
            String summary = titleOrderByPref.isChecked()
                             ? getString(R.string.ps_show_titles_reordered_on)
                             : getString(R.string.ps_show_titles_reordered_off);

            final Spannable spannable;
            // Use the 'schedulerKey' to get the condition!
            //noinspection DataFlowIssue
            if (preference.getSharedPreferences()
                          .getBoolean(StartupViewModel.PK_REBUILD_TITLE_OB, false)) {
                //noinspection DataFlowIssue
                @ColorInt
                final int color = AttrUtils.getColorInt(
                        getContext(), com.google.android.material.R.attr.colorError);

                // Add the warning
                final String warning = getString(R.string.warning_restart_required)
                        .toUpperCase(getContext().getResources().getConfiguration()
                                                 .getLocales().get(0));
                final int warningStart = summary.length() + 1;
                summary = summary + '\n' + warning;
                spannable = new SpannableString(summary);
                spannable.setSpan(new ForegroundColorSpan(color),
                                  warningStart, summary.length(), 0);
            } else {
                //noinspection DataFlowIssue
                @ColorInt
                final int color = AttrUtils.getColorInt(
                        getContext(), android.R.attr.textColorPrimary);
                spannable = new SpannableString(summary);
                spannable.setSpan(new ForegroundColorSpan(color), 0, summary.length(), 0);
            }

            return spannable;
        }

        boolean onChanged(@NonNull final Preference pref,
                          @NonNull final Object newValue) {
            final boolean checked = (Boolean) newValue;

            //noinspection DataFlowIssue
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_baseline_warning_24)
                    .setMessage(R.string.confirm_rebuild_orderby_columns)
                    // this dialog is important. Make sure the user pays some attention
                    .setCancelable(false)
                    // Cancelling will revert to the original value and remove any scheduling
                    .setNegativeButton(android.R.string.cancel, (d, w) -> {
                        titleOrderByPref.setChecked(storedTitleOrderBy);
                        StartupViewModel.schedule(getContext(),
                                                  StartupViewModel.PK_REBUILD_TITLE_OB,
                                                  false);
                        // Force the summary to redisplay by
                        // re-setting the provider will call the protected "notifyChanged()"
                        // as (of course..) Android does not allow an easier solution.
                        // Note to self: fork or replace the androidx.preference lib...
                        //noinspection unchecked,DataFlowIssue
                        titleOrderByPref.setSummaryProvider(this);
                    })
                    // Confirming will persist the new value and schedule the rebuild
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        titleOrderByPref.setChecked(checked);
                        StartupViewModel.schedule(getContext(),
                                                  StartupViewModel.PK_REBUILD_TITLE_OB,
                                                  true);
                    })
                    .create()
                    .show();
            // Do not let the system update the preference value.
            return false;
        }
    }
}
