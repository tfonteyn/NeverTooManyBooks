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

package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class MultiChoiceViewModel
        extends ViewModel {

    /** The selected items. */
    private Set<Long> selectedItems;

    /**
     * Pseudo constructor.
     *
     * @param args {@link Fragment#requireArguments()}
     */
    void init(@NonNull final Bundle args) {
        if (selectedItems == null) {
            final long[] items = Objects.requireNonNull(
                    args.getLongArray(MultiChoiceLauncher.BKEY_SELECTED_ITEMS),
                    MultiChoiceLauncher.BKEY_SELECTED_ITEMS);
            selectedItems = Arrays.stream(items).boxed().collect(Collectors.toSet());
        }
    }

    @NonNull
    Set<Long> getSelectedItems() {
        return selectedItems;
    }
}
