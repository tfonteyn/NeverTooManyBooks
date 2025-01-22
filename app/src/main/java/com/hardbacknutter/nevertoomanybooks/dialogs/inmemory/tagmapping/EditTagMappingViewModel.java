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

package com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.tagmapping;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.entities.TagMapping;

@SuppressWarnings("WeakerAccess")
public class EditTagMappingViewModel
        extends ViewModel {

    @NonNull
    private TagMapping previousValue;
    @NonNull
    private TagMapping currentValue;
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

            // Read the BKEY_CURRENT and store it as the original value
            previousValue = Objects.requireNonNull(args.getParcelable(
                    EditTagMappingLauncher.BKEY_EDIT));
            // take a copy without copying the id
            currentValue = new TagMapping(previousValue);

            extras = args.getBundle(EditTagMappingLauncher.BKEY_EXTRAS);
        }
    }

    boolean isModified() {
        return !currentValue.equals(previousValue);
    }

    /**
     * The original, unmodified item.
     *
     * @return value
     */
    @NonNull
    TagMapping getPreviousValue() {
        return previousValue;
    }

    /**
     * The currently edited value.
     *
     * @return value
     */
    @NonNull
    TagMapping getCurrentValue() {
        return currentValue;
    }

    @Nullable
    Bundle getExtras() {
        return extras;
    }
}
