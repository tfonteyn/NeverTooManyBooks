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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("WeakerAccess")
public class MultiChoiceViewModel
        extends ViewModel {

    @NonNull
    private final Set<Long> previousSelection = new HashSet<>();
    @SuppressWarnings("NotNullFieldNotInitialized")
    @NonNull
    private Set<Long> currentSelection;
    private boolean preferenceChanged;
    @Nullable
    private Bundle extras;

    private boolean initDone;

    /**
     * Pseudo constructor.
     *
     * @param args {@link Fragment#requireArguments()}
     */
    void init(@NonNull final Bundle args) {
        if (!initDone) {
            initDone = true;

            @Nullable
            final long[] selected = args.getLongArray(MultiChoiceLauncher.BKEY_EDIT);
            if (selected != null) {
                previousSelection.addAll(Arrays.stream(selected)
                                              .boxed()
                                              .collect(Collectors.toSet()));
            }
            currentSelection = new HashSet<>(previousSelection);

            extras = args.getBundle(MultiChoiceLauncher.BKEY_EXTRAS);
        }
    }

    void add(@NonNull final Long value) {
        this.preferenceChanged = this.preferenceChanged || currentSelection.add(value);
    }

    void remove(@NonNull final Long value) {
        this.preferenceChanged = this.preferenceChanged || currentSelection.remove(value);
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
