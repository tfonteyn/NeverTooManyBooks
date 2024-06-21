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

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;

import com.hardbacknutter.nevertoomanybooks.settings.DialogMode;

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
        final DialogMode dialogMode = DialogMode.getMode(preference.getContext());
        switch (dialogMode) {
            case Dialog:
                return createDialog(preference);
            case BottomSheet:
                // TODO: add BottomSheet support
                return createDialog(preference);
            default:
                throw new IllegalArgumentException("preference=" + preference.getKey()
                                                   + ", type=" + dialogMode);
        }
    }

    @NonNull
    private static DialogFragment createDialog(@NonNull final Preference preference) {
        final DialogFragment f;

        if (preference instanceof EditTextPreference) {
            f = ExtEditTextPreferenceDialogFragment.newInstance(preference.getKey());
        } else if (preference instanceof ListPreference) {
            f = ExtListPreferenceDialogFragment.newInstance(preference.getKey());
        } else if (preference instanceof MultiSelectListPreference) {
            f = ExtMultiSelectListPreferenceDialogFragment.newInstance(preference.getKey());
        } else {
            throw new IllegalArgumentException(
                    "Cannot display dialog for an unknown Preference type: "
                    + preference.getClass().getSimpleName()
                    + ". Make sure to implement onPreferenceDisplayDialog() to handle "
                    + "displaying a custom dialog for this Preference.");
        }

        return f;
    }
}
