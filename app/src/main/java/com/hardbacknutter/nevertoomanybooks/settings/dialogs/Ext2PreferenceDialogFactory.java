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

package com.hardbacknutter.nevertoomanybooks.settings.dialogs;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;

import com.hardbacknutter.nevertoomanybooks.settings.DialogMode;

public final class Ext2PreferenceDialogFactory {

    private static final String ERROR_UNKNOWN_PREFERENCE_TYPE = "Unsupported Preference type: ";

    private Ext2PreferenceDialogFactory() {
    }

    /**
     * Create a new instance. The classes <strong>replace</strong> the androidx.preference
     * dialog classes, giving us M3 (floating) dialogs and full support for BottomSheets.
     * <p>
     * Fullscreen dialogs (for devices with small screens) are NOT implemented.
     *
     * @param preference to provide the dialog for
     *
     * @return new instance
     *
     * @throws IllegalArgumentException (debug) unsupported Preference class
     */
    @NonNull
    public static DialogFragment create(@NonNull final Preference preference) {
        final DialogFragment fragment;
        final DialogMode dialogMode = DialogMode.getMode(preference.getContext());
        switch (dialogMode) {
            case Dialog:
                if (preference instanceof EditTextPreference) {
                    fragment = new Ext2EditTextPreferenceDialogFragment();
                } else if (preference instanceof ListPreference) {
                    fragment = new Ext2ListPreferenceDialogFragment();
                } else if (preference instanceof MultiSelectListPreference) {
                    fragment = new Ext2MultiSelectListPreferenceDialogFragment();
                } else {
                    throw new IllegalArgumentException(
                            ERROR_UNKNOWN_PREFERENCE_TYPE + preference.getClass().getSimpleName());
                }
                break;

            case BottomSheet:
                if (preference instanceof EditTextPreference) {
                    fragment = new Ext2EditTextPreferenceBottomSheet();
                } else if (preference instanceof ListPreference) {
                    fragment = new Ext2ListPreferenceBottomSheet();
                } else if (preference instanceof MultiSelectListPreference) {
                    fragment = new Ext2MultiSelectListPreferenceBottomSheet();
                } else {
                    throw new IllegalArgumentException(
                            ERROR_UNKNOWN_PREFERENCE_TYPE + preference.getClass().getSimpleName());
                }
                break;
            default:
                throw new IllegalArgumentException("preference=" + preference.getKey()
                                                   + ", type=" + dialogMode);
        }

        final String key = preference.getKey();
        final Bundle b = new Bundle(1);
        // "key" is a required argument of PreferenceDialogFragmentCompat
        // We use the same in our Ext2*Delegate classes
        b.putString(Ext2PreferenceViewModel.ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }
}
