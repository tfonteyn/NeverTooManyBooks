/*
 * @Copyright 2018-2021 HardBackNutter
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
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
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
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.StartupViewModel;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SearchSitesAllListsContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SettingsContract;
import com.hardbacknutter.nevertoomanybooks.covers.CoverDir;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.tasks.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;

/**
 * Global settings page.
 */
public class SettingsFragment
        extends BasePreferenceFragment {

    /** Fragment manager tag. */
    public static final String TAG = "SettingsFragment";
    public static final String BKEY_STORAGE_WAS_MISSING = TAG + ":swm";
    /** savedInstanceState key. */
    private static final String SIS_TITLE_ORDERBY = TAG + ":tob";
    private static final String SIS_VOLUME_INDEX = TAG + ":vol";

    private static final String PSK_SEARCH_SITE_ORDER = "psk_search_site_order";

    private final ActivityResultLauncher<Void> mEditSitesLauncher =
            registerForActivityResult(new SearchSitesAllListsContract(),
                                      success -> { /* ignore */ });

    /**
     * Used to be able to reset this pref to what it was when this fragment started.
     * Persisted with savedInstanceState.
     */
    private boolean mStoredTitleOrderBy;
    private SwitchPreference mTitleOrderByPref;

    /**
     * Used to be able to reset this pref to what it was when this fragment started.
     * Persisted with savedInstanceState.
     */
    private String mStoredVolumeIndex;
    private ListPreference mStorageVolumePref;

    SettingsViewModel mVm;

    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback mOnBackPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    //noinspection ConstantConditions
                    SettingsContract.setResultAndFinish(getActivity(),
                                                        mVm.getRequiresActivityRecreation());
                }
            };
    @Nullable
    private ProgressDelegate mProgressDelegate;
    private boolean mStorageWasMissing;
    private int mVolumeChangedOptionChosen;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences, rootKey);

        final ListPreference.SimpleSummaryProvider listSummaryProvider =
                ListPreference.SimpleSummaryProvider.getInstance();

        //noinspection ConstantConditions
        findPreference(Prefs.pk_ui_locale).setSummaryProvider(listSummaryProvider);
        //noinspection ConstantConditions
        findPreference(Prefs.pk_ui_theme).setSummaryProvider(listSummaryProvider);
        //noinspection ConstantConditions
        findPreference(Prefs.pk_edit_book_isbn_checks).setSummaryProvider(listSummaryProvider);
        //noinspection ConstantConditions
        findPreference(Prefs.pk_booklist_rebuild_state).setSummaryProvider(listSummaryProvider);
        //noinspection ConstantConditions
        findPreference(Prefs.pk_booklist_fastscroller_overlay)
                .setSummaryProvider(listSummaryProvider);

        //noinspection ConstantConditions
        findPreference(PSK_SEARCH_SITE_ORDER).setOnPreferenceClickListener(p -> {
            mEditSitesLauncher.launch(null);
            return true;
        });

        //noinspection ConstantConditions
        mTitleOrderByPref = findPreference(Prefs.pk_sort_title_reordered);
        //noinspection ConstantConditions
        setVisualIndicator(mTitleOrderByPref, StartupViewModel.PK_REBUILD_TITLE_OB);
        mTitleOrderByPref.setOnPreferenceChangeListener(this::onTitleOrderByChange);

        final Bundle args = getArguments();
        if (args != null) {
            mStorageWasMissing = args.getBoolean(BKEY_STORAGE_WAS_MISSING);
        }

        //noinspection ConstantConditions
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

        //noinspection ConstantConditions
        mStorageVolumePref = findPreference(Prefs.pk_storage_volume);
        //noinspection ConstantConditions
        mStorageVolumePref.setSummaryProvider(listSummaryProvider);
        mStorageVolumePref.setEntries(entries);
        mStorageVolumePref.setEntryValues(entryValues);
        mStorageVolumePref.setOnPreferenceChangeListener(this::onStorageVolumeChange);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);

        mVm = new ViewModelProvider(getActivity()).get(SettingsViewModel.class);
        mVm.onProgress().observe(getViewLifecycleOwner(), this::onProgress);
        mVm.onMoveCancelled().observe(getViewLifecycleOwner(), this::onMoveCancelled);
        mVm.onMoveFailure().observe(getViewLifecycleOwner(), this::onMoveFailure);
        mVm.onMoveFinished().observe(getViewLifecycleOwner(), this::onMoveFinished);

        final boolean currentSortTitleReordered = mTitleOrderByPref.isChecked();
        final String currentStorageVolume = mStorageVolumePref.getValue();

        if (savedInstanceState == null) {
            mStoredTitleOrderBy = currentSortTitleReordered;
            mStoredVolumeIndex = currentStorageVolume;
        } else {
            mStoredTitleOrderBy = savedInstanceState
                    .getBoolean(SIS_TITLE_ORDERBY, currentSortTitleReordered);
            mStoredVolumeIndex = savedInstanceState
                    .getString(SIS_VOLUME_INDEX, currentStorageVolume);
        }
    }

    private boolean onTitleOrderByChange(@NonNull final Preference pref,
                                         @NonNull final Object newValue) {
        // pref == mTitleOrderByPref
        final boolean isChecked = (Boolean) newValue;

        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setMessage(R.string.confirm_rebuild_orderby_columns)
                // this dialog is important. Make sure the user pays some attention
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, (d, w) -> {
                    // revert to the original value.
                    mTitleOrderByPref.setChecked(mStoredTitleOrderBy);
                    StartupViewModel.schedule(StartupViewModel.PK_REBUILD_TITLE_OB, false);
                    setVisualIndicator(mTitleOrderByPref, StartupViewModel.PK_REBUILD_TITLE_OB);
                })
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    // Persist the new value
                    mTitleOrderByPref.setChecked(isChecked);
                    StartupViewModel.schedule(StartupViewModel.PK_REBUILD_TITLE_OB, true);
                    setVisualIndicator(mTitleOrderByPref, StartupViewModel.PK_REBUILD_TITLE_OB);
                })
                .create()
                .show();
        // Do not let the system update the preference value.
        return false;
    }

    private boolean onStorageVolumeChange(@NonNull final Preference pref,
                                          @NonNull final Object newValue) {
        // pref == mStorageVolumePref
        final int newVolumeIndex = mStorageVolumePref.findIndexOfValue((String) newValue);
        final CharSequence newVolumeDesc = mStorageVolumePref.getEntries()[newVolumeIndex];

        if (mStorageWasMissing) {
            // The originally used volume is not available; there is nothing to move.
            // Handle this as a simple 'select'
            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_baseline_warning_24)
                    .setTitle(R.string.lbl_storage_volume)
                    // this dialog is important. Make sure the user pays some attention
                    .setCancelable(false)
                    .setMessage(getString(R.string.lbl_storage_select, newVolumeDesc))
                    .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(android.R.string.ok, (d, w) ->
                            setStorageVolume(newVolumeIndex))
                    .create()
                    .show();
        } else {
            final int oldVolumeIndex = mStorageVolumePref.findIndexOfValue(mStoredVolumeIndex);
            final CharSequence oldVolumeDesc = mStorageVolumePref.getEntries()[oldVolumeIndex];

            final CharSequence[] items = {
                    getString(R.string.lbl_storage_select, newVolumeDesc),
                    getString(R.string.info_moving_covers_from_x_to_y,
                              oldVolumeDesc, newVolumeDesc)};

            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_baseline_warning_24)
                    .setTitle(R.string.lbl_storage_volume)
                    // this dialog is important. Make sure the user pays some attention
                    .setCancelable(false)
                    .setSingleChoiceItems(items, 1, (d, w) -> mVolumeChangedOptionChosen = w)
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
        switch (mVolumeChangedOptionChosen) {
            case 0: {
                setStorageVolume(newVolumeIndex);
                break;
            }
            case 1: {
                // check space and start the task
                //noinspection ConstantConditions
                if (!mVm.moveData(getContext(), oldVolumeIndex, newVolumeIndex)) {
                    //noinspection ConstantConditions
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
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SIS_TITLE_ORDERBY, mStoredTitleOrderBy);
        outState.putString(SIS_VOLUME_INDEX, mStoredVolumeIndex);
    }

    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences preferences,
                                          @NonNull final String key) {
        switch (key) {
            case Prefs.pk_ui_locale:
            case Prefs.pk_ui_theme:
            case Prefs.pk_sort_title_reordered:
            case Prefs.pk_show_title_reordered:
            case Prefs.pk_booklist_fastscroller_overlay:
                mVm.setRequiresActivityRecreation();
                break;

            default:
                break;
        }

        super.onSharedPreferenceChanged(preferences, key);
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
        if (preference.getSharedPreferences().getBoolean(schedulerKey, false)) {
            attr = R.attr.appPreferenceAlertColor;
        } else {
            attr = R.attr.colorControlNormal;
        }

        final Drawable icon = preference.getIcon().mutate();
        //noinspection ConstantConditions
        icon.setTint(AttrUtils.getColorInt(getContext(), attr));
        preference.setIcon(icon);
    }

    private void onProgress(@NonNull final ProgressMessage message) {
        if (message.isNewEvent()) {
            if (mProgressDelegate == null) {
                //noinspection ConstantConditions
                mProgressDelegate = new ProgressDelegate(
                        getActivity().findViewById(R.id.progress_frame))
                        .setTitle(getString(R.string.lbl_moving_data))
                        .setPreventSleep(true)
                        .setIndeterminate(true)
                        .setOnCancelListener(v -> mVm.cancelTask(message.taskId))
                        .show(getActivity().getWindow());
            }
            mProgressDelegate.onProgress(message);
        }
    }

    private void closeProgressDialog() {
        if (mProgressDelegate != null) {
            //noinspection ConstantConditions
            mProgressDelegate.dismiss(getActivity().getWindow());
            mProgressDelegate = null;
        }
    }

    private void onMoveFinished(@NonNull final FinishedMessage<Integer> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            if (setStorageVolume(message.requireResult())) {
                //noinspection ConstantConditions
                Snackbar.make(getView(), R.string.action_done, Snackbar.LENGTH_LONG).show();
            }
        }
    }

    private boolean setStorageVolume(final int volume) {
        mStorageVolumePref.setValue(String.valueOf(volume));
        try {
            //noinspection ConstantConditions
            CoverDir.initVolume(getContext(), volume);
            return true;

        } catch (@NonNull final CoverStorageException e) {
            // This should never happen... flw
            StandardDialogs.showError(getContext(), R.string.error_storage_not_accessible);
            return false;
        }
    }

    private void onMoveFailure(@NonNull final FinishedMessage<Exception> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            final Context context = getContext();
            //noinspection ConstantConditions
            final String msg = ExMsg.map(context, message.requireResult())
                                    .orElse(getString(R.string.error_unknown_long,
                                                      getString(R.string.lbl_send_debug)));

            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_baseline_error_24)
                    .setTitle(R.string.lbl_moving_data)
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                    .create()
                    .show();
        }
    }

    private void onMoveCancelled(@NonNull final FinishedMessage<Integer> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            // FIXME: need better msg + tell user to clean up the destination
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.cancelled, Snackbar.LENGTH_LONG).show();
            //noinspection ConstantConditions
            getView().postDelayed(() -> getActivity().finish(), BaseActivity.ERROR_DELAY_MS);
        }
    }
}
