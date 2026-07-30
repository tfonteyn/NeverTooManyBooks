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

import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.dialogs.Tip;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsViewModel;

@Keep
public class StyleDefaultsFragment
        extends StyleBaseFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "StyleDefaultsFragment";

    private SettingsViewModel settingsViewModel;

    /** This fragment was swapped in, so we only need to store any modifications. */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    //noinspection DataFlowIssue
                    final StyleViewModel.Saved dbResult = vm.insertOrUpdateStyle(getContext());
                    if (dbResult.isSuccess()) {
                        if (dbResult.isModified()) {
                            settingsViewModel.setForceRebuildBooklist();
                        }

                        // just pop, we're always called from a fragment
                        getParentFragmentManager().popBackStack();
                    }
                }
            };

    @NonNull
    public static Fragment create() {
        final Fragment fragment = new StyleDefaultsFragment();
        fragment.setArguments(EditStyleInput.editDefaults().toBundle());
        return fragment;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //noinspection DataFlowIssue
        settingsViewModel = new ViewModelProvider(getActivity()).get(SettingsViewModel.class);

    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        getSettingsManager().setVisible(Map.of(
                StyleDataStore.PK_NAME, false,
                StyleDataStore.PK_GROUPS, false,
                StyleDataStore.PK_EXPANSION_LEVEL, false
        ));

        final Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.lbl_styles_long);
        //noinspection DataFlowIssue
        toolbar.setSubtitle(vm.getStyle().getLabel(getContext()));

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        if (savedInstanceState == null) {
            TipManager.getInstance().show(getContext(), Tip.STYLE_DEFAULTS);
        }
    }
}
