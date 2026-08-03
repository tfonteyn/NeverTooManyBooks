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

package com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.partialdate;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.dialogs.LauncherOutput;

class PartialDatePickerOutput
        implements LauncherOutput {
    private static final String TAG = "PartialDatePickerOutput";
    private static final String BKEY_ORIGINAL = TAG + ":original";
    private static final String BKEY_EDIT = TAG + ":edit";
    private static final String BKEY_EXTRAS = TAG + ":extras";

    @NonNull
    private final PartialDate original;
    @NonNull
    private final PartialDate edited;
    @Nullable
    private final Bundle extras;

    /**
     * Constructor.
     *
     * @param original the previous value
     * @param edited   the new value
     * @param extras   (optional) Bundle provided as input
     */
    PartialDatePickerOutput(@NonNull final PartialDate original,
                            @NonNull final PartialDate edited,
                            @Nullable final Bundle extras) {
        this.original = original;
        this.edited = edited;
        this.extras = extras;
    }

    @SuppressWarnings("deprecation")
    @NonNull
    static PartialDatePickerOutput fromBundle(@NonNull final Bundle args) {
        final PartialDate previousValue = Objects.requireNonNull(
                args.getParcelable(BKEY_ORIGINAL), BKEY_ORIGINAL);
        final PartialDate currentValue = Objects.requireNonNull(
                args.getParcelable(BKEY_EDIT), BKEY_EDIT);
        final Bundle extras = args.getBundle(BKEY_EXTRAS);

        return new PartialDatePickerOutput(previousValue, currentValue, extras);
    }

    @NonNull
    public Bundle toBundle() {
        final Bundle result = new Bundle(3);
        result.putParcelable(BKEY_ORIGINAL, original);
        result.putParcelable(BKEY_EDIT, edited);
        if (extras != null && !extras.isEmpty()) {
            result.putBundle(BKEY_EXTRAS, extras);
        }

        return result;
    }

    @NonNull
    PartialDate getOriginal() {
        return original;
    }

    @NonNull
    PartialDate getEdited() {
        return edited;
    }

    @Nullable
    Bundle getExtras() {
        return extras;
    }

    @Override
    @NonNull
    public String toString() {
        return "PartialDatePickerOutput{"
               + "original=" + original
               + ", edited=" + edited
               + ", extras=" + extras
               + '}';
    }
}
