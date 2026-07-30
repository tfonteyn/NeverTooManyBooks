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
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.prefslib.DialogInput;
import com.hardbacknutter.prefslib.MultiChoiceSetting;
import com.hardbacknutter.prefslib.SettingsManagerViewModel;
import com.hardbacknutter.prefslib.databinding.PrefsLibDialogMultiChoiceBinding;

public class MultiChoiceDialogFragment
        extends DialogFragment {

    private SettingsManagerViewModel vm;

    private MultiChoiceSetting setting;

    @Nullable
    private String dialogMessage;

    private PrefsLibDialogMultiChoiceBinding dvb;
    private boolean showClearButton;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final DialogInput args = DialogInput.fromBundle(requireArguments());
        final String key = args.getKey();
        dialogMessage = args.getDialogMessage();

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(SettingsManagerViewModel.class);
        setting = vm.requireSetting(key);
        showClearButton = setting.getClearButtonText() != null;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {

        dvb = PrefsLibDialogMultiChoiceBinding
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

        final boolean[] selectedItems = setting.getSelectedIndexes();
        final CharSequence[] entries = Objects.requireNonNull(
                setting.getEntries(), "setting.getEntries() was null");
        final CharSequence[] entryValues = Objects.requireNonNull(
                setting.getEntryValues(), "setting.getEntryValues() was null");

        for (int i = 0; i < entries.length; i++) {
            final MaterialCheckBox cb = new MaterialCheckBox(context);
            cb.setMinimumHeight(heightPx);
            cb.setText(entries[i]);
            cb.setChecked(selectedItems[i]);
            cb.setTag(entryValues[i]);
            dvb.settings.addView(cb);
        }

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(setting.getTitle())
                .setIcon(setting.getIconResId())
                .setView(dvb.getRoot())
                .setNegativeButton(setting.getNegativeButtonText(), null)
                .setPositiveButton(setting.getPositiveButtonText(), (d, which) -> {
                    final Set<String> newValue = new HashSet<>();
                    for (int i = 0; i < dvb.settings.getChildCount(); i++) {
                        final View child = dvb.settings.getChildAt(i);
                        if (child instanceof MaterialCheckBox) {
                            final MaterialCheckBox cb = (MaterialCheckBox) child;
                            if (cb.isChecked()) {
                                newValue.add((String) cb.getTag());
                            }
                        }
                    }
                    vm.onChange(setting, newValue);
                });

        if (showClearButton) {
            // see onStart for the action
            builder.setNeutralButton(setting.getClearButtonText(), null);
        }

        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (showClearButton) {
            final AlertDialog dialog = (AlertDialog) getDialog();
            if (dialog != null) {
                // A "standard" hack to keep the dialog open when the user taps "clear"
                final Button clearButton = dialog.getButton(Dialog.BUTTON_NEUTRAL);
                clearButton.setOnClickListener(v -> {
                    for (int i = 0; i < dvb.settings.getChildCount(); i++) {
                        final View child = dvb.settings.getChildAt(i);
                        if (child instanceof MaterialCheckBox) {
                            final MaterialCheckBox cb = (MaterialCheckBox) child;
                            cb.setChecked(false);
                        }
                    }
                });
            }
        }
    }
}
