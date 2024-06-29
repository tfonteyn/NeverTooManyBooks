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
import androidx.preference.MultiSelectListPreference;

import java.util.Set;
import java.util.stream.Collectors;

class Ext2MultiSelectListPreferenceDelegate
        extends Ext2PreferenceDelegate<MultiSelectListPreference,
        Ext2MultiSelectListPreferenceViewModel> {

    Ext2MultiSelectListPreferenceDelegate(@NonNull final DialogFragment owner,
                                          @NonNull final Bundle args) {
        super(owner, args, Ext2MultiSelectListPreferenceViewModel.class);
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

    @NonNull
    boolean[] getInitialSelection() {
        return vm.getInitialSelection();
    }


    @NonNull
    Set<CharSequence> getNewValues() {
        return vm.getNewValues();
    }

    void onSelect(final int index,
                  final boolean isChecked) {
        onSelect(getEntryValues()[index], isChecked);
    }

    void onSelect(@NonNull final CharSequence item,
                  final boolean isChecked) {
        if (isChecked) {
            vm.add(item);
        } else {
            vm.remove(item);
        }
    }

    void saveValue() {
        if (vm.isPreferenceChanged()) {
            final MultiSelectListPreference p = getPreference();
            final Set<String> newValues = vm.getNewValues()
                                            .stream()
                                            .map(CharSequence::toString)
                                            .collect(Collectors.toSet());
            if (p.callChangeListener(newValues)) {
                p.setValues(newValues);
            }
        }
    }


}
