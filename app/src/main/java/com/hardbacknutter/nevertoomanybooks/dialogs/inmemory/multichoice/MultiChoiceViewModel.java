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

package com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.multichoice;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("WeakerAccess")
public class MultiChoiceViewModel
        extends ViewModel {

    @NonNull
    private final Set<Long> previousSelection = new HashSet<>();
    @NonNull
    private final Set<Long> currentSelection = new HashSet<>();
    private boolean preferenceChanged;
    @Nullable
    private Bundle extras;

    private boolean initDone;

    /**
     * Pseudo constructor.
     *
     * @param args all arguments
     */
    void init(@NonNull final MultiChoiceInput args) {
        if (!initDone) {
            initDone = true;

            previousSelection.addAll(args.getSelectedIds());
            currentSelection.addAll(previousSelection);

            extras = args.getExtras();
        }
    }

    void setSelection(@NonNull final Long value,
                      final boolean checked) {
        // duh... FIRST add/remove, THEN 'or'...
        if (checked) {
            this.preferenceChanged = currentSelection.add(value) || this.preferenceChanged;
        } else {
            this.preferenceChanged = currentSelection.remove(value) || this.preferenceChanged;
        }
    }

    void setSelection(@NonNull final Set<Long> selection) {
        if (selection.equals(currentSelection)) {
            return;
        }
        currentSelection.clear();
        currentSelection.addAll(selection);
        this.preferenceChanged = true;
    }

    boolean isModified() {
        return preferenceChanged;
    }

    @NonNull
    Set<Long> getPreviousSelection() {
        return previousSelection;
    }

    @NonNull
    Set<Long> getCurrentSelection() {
        return currentSelection;
    }

    /**
     * Get the optional/additional payload.
     *
     * @return extras Bundle
     */
    @Nullable
    Bundle getExtras() {
        return extras;
    }
}
