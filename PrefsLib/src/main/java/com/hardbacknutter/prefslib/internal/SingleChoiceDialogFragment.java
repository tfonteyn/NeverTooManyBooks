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

package com.hardbacknutter.prefslib.internal;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;

import java.util.Objects;

import com.hardbacknutter.prefslib.SettingsManagerViewModel;
import com.hardbacknutter.prefslib.SingleChoiceSetting;
import com.hardbacknutter.prefslib.databinding.PrefsLibDialogSingleChoiceBinding;

public class SingleChoiceDialogFragment
        extends DialogFragment {

    private SettingsManagerViewModel vm;

    private SingleChoiceSetting setting;

    @Nullable
    private String dialogMessage;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        final String key = Objects.requireNonNull(args.getString(DefaultDialogFactory.BKEY_KEY),
                                                  DefaultDialogFactory.BKEY_KEY);
        dialogMessage = args.getString(DefaultDialogFactory.BKEY_DIALOG_MESSAGE);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(SettingsManagerViewModel.class);
        setting = vm.requireSetting(key);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {

        final PrefsLibDialogSingleChoiceBinding dvb = PrefsLibDialogSingleChoiceBinding
                .inflate(requireActivity().getLayoutInflater(), null, false);

        if (dialogMessage == null || dialogMessage.isBlank()) {
            dvb.message.setVisibility(View.GONE);
        } else {
            dvb.message.setVisibility(View.VISIBLE);
            dvb.message.setText(dialogMessage);
        }

        final Context context = getContext();
        //noinspection DataFlowIssue
        final int heightPx = AttrUtils.getDimensionPixelSize(
                context, android.R.attr.listPreferredItemHeightSmall);

        final CharSequence[] entries = Objects.requireNonNull(
                setting.getEntries(), "setting.getEntries() was null");
        for (int i = 0; i < entries.length; i++) {
            final MaterialRadioButton rb = new MaterialRadioButton(context);
            // setOnCheckedChangeListener needs IDs to function
            rb.setId(View.generateViewId());
            rb.setMinimumHeight(heightPx);
            rb.setText(entries[i]);
            if (i == setting.getSelectedIndex()) {
                rb.setChecked(true);
            }
            dvb.settings.addView(rb);
        }

        dvb.settings.setOnCheckedChangeListener((group, checkedId) -> {
            final RadioButton rb = group.findViewById(checkedId);
            final int index = group.indexOfChild(rb);
            final CharSequence[] entryValues = Objects.requireNonNull(
                    setting.getEntryValues(), "setting.getEntryValues() was null");

            dismiss();
            vm.onChange(setting, entryValues[index]);
        });

        return new MaterialAlertDialogBuilder(context)
                .setTitle(setting.getTitle())
                .setIcon(setting.getIconResId())
                .setNegativeButton(setting.getNegativeButtonText(), null)
                .setView(dvb.getRoot())
                .create();
    }
}
