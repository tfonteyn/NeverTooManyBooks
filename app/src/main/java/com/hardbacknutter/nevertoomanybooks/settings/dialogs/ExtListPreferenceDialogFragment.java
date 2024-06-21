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

package com.hardbacknutter.nevertoomanybooks.settings.dialogs;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;
import androidx.preference.ListPreferenceDialogFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;

public class ExtListPreferenceDialogFragment
        extends ListPreferenceDialogFragmentCompat {

    /**
     * Constructor.
     *
     * @param key for the preference
     *
     * @return new instance
     */
    @NonNull
    public static ExtListPreferenceDialogFragment newInstance(@NonNull final String key) {
        final ExtListPreferenceDialogFragment fragment =
                new ExtListPreferenceDialogFragment();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {

        final DialogPreference.TargetFragment fragment = Objects.requireNonNull(
                (DialogPreference.TargetFragment) getTargetFragment());
        final String key = Objects.requireNonNull(requireArguments().getString(ARG_KEY));
        final DialogPreference preference = Objects.requireNonNull(fragment.findPreference(key));

        // equivalent of: mWhichButtonClicked = DialogInterface.BUTTON_NEGATIVE;
        //noinspection DataFlowIssue
        onClick(null, DialogInterface.BUTTON_NEGATIVE);

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(preference.getDialogTitle())
                .setIcon(preference.getDialogIcon())
                .setPositiveButton(preference.getPositiveButtonText(), this)
                .setNegativeButton(preference.getNegativeButtonText(), this);

        final View contentView = onCreateDialogView(requireContext());
        if (contentView != null) {
            onBindDialogView(contentView);
            builder.setView(contentView);
        } else {
            builder.setMessage(preference.getDialogMessage());
        }

        onPrepareDialogBuilder(builder);

        return builder.create();
    }
}
