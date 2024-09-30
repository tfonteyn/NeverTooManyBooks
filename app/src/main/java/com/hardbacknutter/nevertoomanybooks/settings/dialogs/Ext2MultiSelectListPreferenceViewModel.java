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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.preference.MultiSelectListPreference;

import java.util.HashSet;
import java.util.Set;

public class Ext2MultiSelectListPreferenceViewModel
        extends Ext2PreferenceViewModel {

    private final Set<CharSequence> newValues = new HashSet<>();
    private boolean preferenceChanged;
    private CharSequence[] entries;
    private CharSequence[] entryValues;

    void init(@NonNull final Context context,
              @NonNull final MultiSelectListPreference preference) {
        super.init(context, preference);

        if (entries == null) {
            if (preference.getEntries() == null || preference.getEntryValues() == null) {
                throw new IllegalStateException(
                        "MultiSelectListPreference requires an entries array and "
                        + "an entryValues array.");
            }

            entries = preference.getEntries();
            entryValues = preference.getEntryValues();
            newValues.addAll(preference.getValues());
        }
    }

    @NonNull
    public CharSequence[] getEntries() {
        return entries;
    }

    @NonNull
    public CharSequence[] getEntryValues() {
        return entryValues;
    }

    @NonNull
    boolean[] getInitialSelection() {
        final int entryCount = entryValues.length;
        final boolean[] checkedItems = new boolean[entryCount];
        for (int i = 0; i < entryCount; i++) {
            checkedItems[i] = newValues.contains(entryValues[i].toString());
        }
        return checkedItems;
    }

    void add(@NonNull final CharSequence value) {
        this.preferenceChanged |= newValues.add(value.toString());
    }

    void remove(@NonNull final CharSequence value) {
        this.preferenceChanged |= newValues.remove(value.toString());
    }

    @NonNull
    Set<CharSequence> getNewValues() {
        return newValues;
    }

    boolean isPreferenceChanged() {
        return preferenceChanged;
    }
}
