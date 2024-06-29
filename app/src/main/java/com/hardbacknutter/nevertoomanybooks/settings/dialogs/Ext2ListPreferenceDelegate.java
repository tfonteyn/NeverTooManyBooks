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
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.preference.ListPreference;

class Ext2ListPreferenceDelegate
        extends Ext2PreferenceDelegate<ListPreference, Ext2ListPreferenceViewModel> {

    Ext2ListPreferenceDelegate(@NonNull final DialogFragment owner,
                               @NonNull final Bundle args) {
        super(owner, args, Ext2ListPreferenceViewModel.class);
        // See comment in Ext2PreferenceDelegate-constructor
        //noinspection DataFlowIssue
        vm.init(owner.getContext(), getPreference());
    }

    @NonNull
    CharSequence[] getEntries() {
        return vm.getEntries();
    }

    @NonNull
    CharSequence[] getEntryValues() {
        return vm.getEntryValues();
    }

    // RadioGroupRecyclerAdapter requires the value
    @Nullable
    CharSequence getInitialSelection() {
        return vm.getInitialSelection();
    }

    // MaterialAlertDialogBuilder requires the index
    int getInitialSelectedIndex() {
        return getPreference().findIndexOfValue(vm.getInitialSelection().toString());
    }

    void saveValue(final int index) {
        saveValue(getEntryValues()[index]);
    }

    void saveValue(@Nullable final CharSequence value) {
        if (value != null) {
            final ListPreference p = getPreference();
            final String selection = value.toString();
            if (p.callChangeListener(selection)) {
                p.setValue(selection);
            }

            owner.dismiss();
        }
    }
}
