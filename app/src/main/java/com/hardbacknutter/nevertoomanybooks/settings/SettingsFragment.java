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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.AttrRes;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.FragmentHostActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SearchSitesAllListsContract;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;
import com.hardbacknutter.nevertoomanybooks.viewmodels.StartupViewModel;

/**
 * Global settings page.
 */
public class SettingsFragment
        extends BasePreferenceFragment {

    /** Fragment manager tag. */
    public static final String TAG = "SettingsFragment";
    /** savedInstanceState key. */
    private static final String SIS_CURRENT_SORT_TITLE_REORDERED = TAG + ":cSTR";
    private static final String PSK_SEARCH_SITE_ORDER = "psk_search_site_order";

    private final ActivityResultLauncher<Void> mEditSitesLauncher =
            registerForActivityResult(new SearchSitesAllListsContract(),
                                      success -> { /* ignore */ });
    /**
     * Used to be able to reset this pref to what it was when this fragment started.
     * Persisted with savedInstanceState.
     */
    private boolean mCurrentSortTitleReordered;
    /** The Activity results. */
    private SettingsViewModel mVm;
    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback mOnBackPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    //noinspection ConstantConditions
                    ResultContract.setResultAndFinish(getActivity(),
                                                      mVm.getRequiresActivityRecreation());
                }
            };

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences, rootKey);

        //noinspection ConstantConditions
        mVm = new ViewModelProvider(getActivity()).get(SettingsViewModel.class);

        //noinspection ConstantConditions
        findPreference(Prefs.pk_ui_locale)
                .setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

        //noinspection ConstantConditions
        findPreference(Prefs.pk_ui_theme)
                .setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

        //noinspection ConstantConditions
        findPreference(Prefs.pk_edit_book_isbn_checks)
                .setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

        //noinspection ConstantConditions
        findPreference(Prefs.pk_booklist_rebuild_state)
                .setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

        //noinspection ConstantConditions
        findPreference(Prefs.pk_booklist_fastscroller_overlay)
                .setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

        //noinspection ConstantConditions
        findPreference(PSK_SEARCH_SITE_ORDER)
                .setOnPreferenceClickListener(p -> {
                    mEditSitesLauncher.launch(null);
                    return true;
                });

        onCreateSortTitleReorderedPreference(savedInstanceState);
    }

    private void onCreateSortTitleReorderedPreference(
            @Nullable final Bundle savedInstanceState) {
        final Preference preference = findPreference(Prefs.pk_sort_title_reordered);
        //noinspection ConstantConditions
        final boolean currentValue = preference
                .getSharedPreferences().getBoolean(Prefs.pk_sort_title_reordered, true);

        if (savedInstanceState == null) {
            mCurrentSortTitleReordered = currentValue;
        } else {
            mCurrentSortTitleReordered = savedInstanceState
                    .getBoolean(SIS_CURRENT_SORT_TITLE_REORDERED, currentValue);
        }

        setVisualIndicator(preference, StartupViewModel.PK_REBUILD_ORDERBY_COLUMNS);

        preference.setOnPreferenceChangeListener((pref, newValue) -> {
            final SwitchPreference p = (SwitchPreference) pref;
            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_warning)
                    .setMessage(R.string.confirm_rebuild_orderby_columns)
                    // this dialog is important. Make sure the user pays some attention
                    .setCancelable(false)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> {
                        p.setChecked(mCurrentSortTitleReordered);
                        StartupViewModel.scheduleOrderByRebuild(getContext(), false);
                        setVisualIndicator(p, StartupViewModel.PK_REBUILD_ORDERBY_COLUMNS);
                    })
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        p.setChecked(!p.isChecked());
                        StartupViewModel.scheduleOrderByRebuild(getContext(), true);
                        setVisualIndicator(p, StartupViewModel.PK_REBUILD_ORDERBY_COLUMNS);
                    })
                    .create()
                    .show();
            // Do not let the system update the preference value.
            return false;
        });
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);
    }

    @Override
    public void onResume() {
        super.onResume();
        mToolbar.setTitle(R.string.lbl_settings);
        mToolbar.setSubtitle("");
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SIS_CURRENT_SORT_TITLE_REORDERED, mCurrentSortTitleReordered);
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
     * Check if its's possible to overlay the icon with another icon (showing e.g. a clock)
     *
     * @param preference   to modify
     * @param schedulerKey to reflect
     */
    private void setVisualIndicator(@NonNull final Preference preference,
                                    @SuppressWarnings("SameParameterValue")
                                    @NonNull final String schedulerKey) {
        @AttrRes
        final int attr;
        if (getPreferenceManager().getSharedPreferences().getBoolean(schedulerKey, false)) {
            attr = R.attr.appPreferenceAlertColor;
        } else {
            attr = R.attr.colorControlNormal;
        }

        final Drawable icon = preference.getIcon().mutate();
        //noinspection ConstantConditions
        icon.setTint(AttrUtils.getColorInt(getContext(), attr));
        preference.setIcon(icon);
    }

    public static class ResultContract
            extends ActivityResultContract<String, Boolean> {

        /** Something changed (or not) that requires a recreation of the caller Activity. */
        private static final String BKEY_RECREATE_ACTIVITY = TAG + ":recreate";

        static void setResultAndFinish(@NonNull final Activity activity,
                                       final boolean requiresRecreation) {
            final Intent resultIntent = new Intent()
                    .putExtra(BKEY_RECREATE_ACTIVITY, requiresRecreation);
            activity.setResult(Activity.RESULT_OK, resultIntent);
            activity.finish();
        }

        @NonNull
        @Override
        public Intent createIntent(@NonNull final Context context,
                                   @Nullable final String scrollToKey) {
            final Intent intent = new Intent(context, SettingsHostActivity.class)
                    .putExtra(FragmentHostActivity.BKEY_FRAGMENT_TAG, TAG);
            if (scrollToKey != null) {
                intent.putExtra(BasePreferenceFragment.BKEY_AUTO_SCROLL_TO_KEY, scrollToKey);
            }
            return intent;
        }

        @Override
        @NonNull
        public Boolean parseResult(final int resultCode,
                                   @Nullable final Intent intent) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                Logger.d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
            }

            if (intent == null || resultCode != Activity.RESULT_OK) {
                return false;
            }
            return intent.getBooleanExtra(BKEY_RECREATE_ACTIVITY, false);
        }
    }
}
