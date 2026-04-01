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

package com.hardbacknutter.nevertoomanybooks.settings.dialogs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import java.util.HashSet;
import java.util.Set;

import com.hardbacknutter.prefslib.MultiChoiceSetting;

@SuppressWarnings("WeakerAccess")
public class MultiChoiceViewModel
        extends ViewModel {

    @Nullable
    private Set<CharSequence> newValue;

    /**
     * Pseudo constructor.
     *
     * @param setting to use
     */
    void init(@NonNull final MultiChoiceSetting setting) {
        if (newValue == null) {
            newValue = new HashSet<>(setting.getValue());
        }
    }

    void add(@NonNull final CharSequence value) {
        //noinspection DataFlowIssue
        newValue.add(value);
    }

    void remove(@NonNull final CharSequence value) {
        //noinspection DataFlowIssue
        newValue.remove(value);
    }

    @Nullable
    Set<CharSequence> getNewValue() {
        return newValue;
    }
}
