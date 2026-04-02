/*
 * @Copyright 2018-2025 HardBackNutter
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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.dialogs.Tip;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;

/**
 * Main settings editor for a single style.
 */
public class StyleFragment
        extends StyleBaseFragment {

    /** Fragment manager tag. */
    private static final String TAG = "StylePreferenceFragment";
    private static final String SIS_NAME_SET = TAG + ":nameSet";

    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    //noinspection DataFlowIssue
                    final StyleViewModel.Saved dbResult = vm.insertOrUpdateStyle(getContext());
                    if (dbResult.isSuccess()) {
                        final Intent resultIntent = EditStyleContract
                                .createResult(vm.getTemplateUuid(),
                                              dbResult.isModified(),
                                              vm.getStyle().getUuid());

                        //noinspection DataFlowIssue
                        getActivity().setResult(Activity.RESULT_OK, resultIntent);
                        getActivity().finish();
                    }
                }
            };

    /** Flag: prompt for the name of cloned styles; managed by savedInstanceState. */
    private boolean nameSet;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        if (savedInstanceState != null) {
            nameSet = savedInstanceState.getBoolean(SIS_NAME_SET);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Style style = vm.getStyle();

        final Toolbar toolbar = getToolbar();
        if (style.getId() == 0) {
            toolbar.setTitle(R.string.lbl_clone_style);
        } else {
            toolbar.setTitle(R.string.lbl_edit_style);
        }
        //noinspection DataFlowIssue
        toolbar.setSubtitle(style.getLabel(getContext()));

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        if (savedInstanceState == null) {
            TipManager.getInstance().show(getContext(), Tip.STYLE_PROPERTIES);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // for new (i.e. cloned) styles, auto-popup the name field for the user to change it.
        // We only do this once.
        if (vm.getStyle().getId() == 0 && !nameSet) {
            nameSet = true;
            getPreferenceManager().showDialog(pName);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SIS_NAME_SET, nameSet);
    }
}
