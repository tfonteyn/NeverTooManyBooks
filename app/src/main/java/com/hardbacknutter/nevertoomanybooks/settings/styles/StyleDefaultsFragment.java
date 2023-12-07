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
package com.hardbacknutter.nevertoomanybooks.settings.styles;

import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsViewModel;

@Keep
public class StyleDefaultsFragment
        extends StyleBaseFragment {

    public static final String TAG = "StyleDefaultsFragment";

    private SettingsViewModel settingsViewModel;

    /** This fragment was swapped in, so we only need to store any modifications. */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    //noinspection DataFlowIssue
                    final boolean modified = vm.insertOrUpdateStyle(getContext());
                    if (modified) {
                        settingsViewModel.setForceRebuildBooklist();
                    }

                    // just pop, we're always called from a fragment
                    getParentFragmentManager().popBackStack();
                }
            };

    @NonNull
    public static Fragment create() {
        final Fragment fragment = new StyleDefaultsFragment();
        final Bundle args = new Bundle(1);
        args.putBoolean(StyleViewModel.BKEY_GLOBAL_STYLE, true);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        //noinspection DataFlowIssue
        settingsViewModel = new ViewModelProvider(getActivity()).get(SettingsViewModel.class);

        super.onCreatePreferences(savedInstanceState, rootKey);

        pName.setVisible(false);
        pGroups.setVisible(false);
        pExpansionLevel.setVisible(false);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        // Don't change the title; it's "Settings".
        // Style name as the subtitle
        //noinspection DataFlowIssue
        toolbar.setSubtitle(vm.getStyle().getLabel(getContext()));

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        if (savedInstanceState == null) {
            TipManager.getInstance()
                      .display(getContext(), R.string.tip_booklist_style_defaults, null);
        }
    }
}
