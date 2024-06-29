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
import androidx.preference.ListPreference;

import java.util.Objects;

public class Ext2ListPreferenceViewModel
        extends Ext2PreferenceViewModel {

    private String initialSelection;

    private CharSequence[] entries;
    private CharSequence[] entryValues;

    public void init(@NonNull final Context context,
                     @NonNull final ListPreference preference) {
        super.init(context, preference);

        if (entries == null) {
            if (preference.getEntries() == null || preference.getEntryValues() == null) {
                throw new IllegalStateException(
                        "ListPreference requires an entries array and an entryValues array.");
            }
            entries = preference.getEntries();
            entryValues = preference.getEntryValues();
            initialSelection = Objects.requireNonNull(preference.getValue());
        }
    }

    @NonNull
    CharSequence[] getEntries() {
        return entries;
    }

    @NonNull
    CharSequence[] getEntryValues() {
        return entryValues;
    }

    @NonNull
    CharSequence getInitialSelection() {
        return initialSelection;
    }
}
