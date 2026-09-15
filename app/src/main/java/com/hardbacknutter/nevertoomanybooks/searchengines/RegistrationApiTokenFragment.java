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

package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentRegistrationApiTokenBinding;
import com.hardbacknutter.nevertoomanybooks.utils.OrderedTextListFormatter;
import com.hardbacknutter.nevertoomanybooks.widgets.TilUtil;
import com.hardbacknutter.nevertoomanybooks.widgets.endicon.ExtClearTextEndIconDelegate;

public class RegistrationApiTokenFragment
        extends DialogFragment {

    /** View Binding. */
    private FragmentRegistrationApiTokenBinding vb;
    private RegistrationApiTokenViewModel vm;

    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    setResultsAndFinish();
                }
            };

    private void setResultsAndFinish() {
        final FragmentManager fm = getParentFragmentManager();
        fm.setFragmentResult(vm.getRequestKey(), vm.getRegistration().toBundle());
        // just pop, we're always called from a fragment
        fm.popBackStack();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentRegistrationApiTokenBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        final Context context = getContext();

        vm = new ViewModelProvider(this).get(RegistrationApiTokenViewModel.class);
        //noinspection DataFlowIssue
        vm.init(context, requireArguments());

        final OrderedTextListFormatter listFormatter =
                new OrderedTextListFormatter(context, 16, 24);

        vb.message.setText(listFormatter.format(vm.getMessage()));
        vb.message.setMovementMethod(LinkMovementMethod.getInstance());

        final int tokenLen = vm.getTokenLen();

        vb.lblApiToken.setCounterMaxLength(tokenLen);
        vb.apiToken.setEms(tokenLen);
        vb.apiToken.setText(vm.getApiToken());
        vb.apiToken.setFilters(new InputFilter[]{new InputFilter.LengthFilter(tokenLen)});
        ExtClearTextEndIconDelegate.attach(vb.lblApiToken, null);
        TilUtil.autoRemoveError(vb.apiToken, vb.lblApiToken);

        vb.btnSave.setOnClickListener(v -> {
            if (saveChanges()) {
                setResultsAndFinish();
            }
        });
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }

    private boolean saveChanges() {
        viewToModel();

        final boolean validated = vm.validate();
        if (validated) {
            vb.lblApiToken.setError(null);
        } else {
            vb.lblApiToken.setError(getString(R.string.vldt_exact_length_required,
                                              vm.getTokenLen()));
        }
        return validated;
    }

    private void viewToModel() {
        final Editable text = vb.apiToken.getText();
        vm.setApiToken(text != null ? text.toString().strip() : "");
    }
}
