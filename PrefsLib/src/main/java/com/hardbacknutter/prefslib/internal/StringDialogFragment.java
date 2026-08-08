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
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import com.hardbacknutter.prefslib.SettingsManagerViewModel;
import com.hardbacknutter.prefslib.StringSetting;
import com.hardbacknutter.prefslib.databinding.PrefsLibDialogEditStringBinding;

public class StringDialogFragment
        extends DialogFragment {

    private SettingsManagerViewModel vm;

    private StringSetting setting;

    @Nullable
    private String dialogMessage;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final DialogInput args = DialogInput.fromBundle(requireArguments());
        final String key = args.getKey();
        dialogMessage = args.getDialogMessage();

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(SettingsManagerViewModel.class);
        setting = vm.requireSetting(key);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {

        final PrefsLibDialogEditStringBinding dvb = PrefsLibDialogEditStringBinding
                .inflate(requireActivity().getLayoutInflater(), null, false);

        bindMessageView(dvb.message);
        bindEditText(dvb.edit);

        @SuppressWarnings("DataFlowIssue")
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
                .setTitle(setting.getTitle())
                .setIcon(setting.getIconResId())
                .setView(dvb.getRoot())
                .setPositiveButton(setting.getPositiveButtonText(), (d, which) -> {
                    //noinspection DataFlowIssue
                    final String newValue = dvb.edit.getText().toString();
                    vm.onChange(setting, newValue);
                });

        if (setting.getNegativeButtonText() != null) {
            builder.setNegativeButton(setting.getNegativeButtonText(), null);
        }
        if (setting.getNotSetButtonText() != null) {
            builder.setNeutralButton(setting.getNotSetButtonText(),
                                     (dialog, which) -> vm.onChange(setting, null));
        }

        return builder.create();
    }

    private void bindMessageView(@Nullable final TextView messageView) {
        if (messageView != null) {
            if (dialogMessage == null || dialogMessage.isEmpty()) {
                messageView.setVisibility(View.GONE);
            } else {
                messageView.setText(dialogMessage);
                messageView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void bindEditText(@NonNull final TextInputEditText editText) {
        final int inputType = setting.getInputType();
        if (inputType != 0) {
            editText.setInputType(inputType);
        }

        editText.setText(setting.getValue());
        // Place cursor at the end
        //noinspection DataFlowIssue
        editText.setSelection(editText.getText().length());
        editText.requestFocus();
    }
}
