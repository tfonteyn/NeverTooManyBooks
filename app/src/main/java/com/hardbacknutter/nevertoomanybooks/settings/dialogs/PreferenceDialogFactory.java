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

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;

public final class PreferenceDialogFactory {

    private PreferenceDialogFactory() {
    }

    /**
     * Create a new instance.
     *
     * @param preference to provide the dialog for
     *
     * @return new instance
     */
    @NonNull
    public static DialogFragment create(@NonNull final Preference preference) {
        final DialogFragment fragment;
        if (preference instanceof EditTextPreference) {
            fragment = new ExtEditTextPreferenceDialogFragment();
        } else if (preference instanceof ListPreference) {
            fragment = new ExtListPreferenceDialogFragment();
        } else if (preference instanceof MultiSelectListPreference) {
            fragment = new ExtMultiSelectListPreferenceDialogFragment();
        } else {
            throw new IllegalArgumentException(
                    "Unknown Preference type: " + preference.getClass().getSimpleName());
        }

        final String key = preference.getKey();
        final Bundle b = new Bundle(1);
        b.putString(Ext2PreferenceViewModel.ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }
}
