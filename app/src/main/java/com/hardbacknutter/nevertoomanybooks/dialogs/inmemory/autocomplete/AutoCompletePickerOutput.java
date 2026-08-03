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

package com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.autocomplete;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.dialogs.LauncherOutput;

class AutoCompletePickerOutput
        implements LauncherOutput {

    private static final String TAG = "AutoCompletePickerOutput";
    private static final String BKEY_ORIGINAL = TAG + ":original";
    private static final String BKEY_EDIT = TAG + ":edit";
    private static final String BKEY_EXTRAS = TAG + ":extras";

    @Nullable
    private final String original;
    @NonNull
    private final String edited;
    @Nullable
    private final Bundle extras;

    /**
     * Constructor.
     *
     * @param original the previous value
     * @param edited   the new value
     * @param extras   (optional) Bundle provided as input
     */
    AutoCompletePickerOutput(@Nullable final String original,
                             @NonNull final String edited,
                             @Nullable final Bundle extras) {
        this.original = original;
        this.edited = edited;
        this.extras = extras;
    }

    @NonNull
    static AutoCompletePickerOutput fromBundle(@NonNull final Bundle args) {
        final String previousValue = args.getString(BKEY_ORIGINAL);
        final String currentValue = Objects.requireNonNull(
                args.getString(BKEY_EDIT), BKEY_EDIT);
        final Bundle extras = args.getBundle(BKEY_EXTRAS);

        return new AutoCompletePickerOutput(previousValue, currentValue, extras);
    }

    @NonNull
    public Bundle toBundle() {
        final Bundle result = new Bundle(3);
        if (original != null && !original.isBlank()) {
            result.putString(BKEY_ORIGINAL, original);
        }
        result.putString(BKEY_EDIT, edited);
        if (extras != null && !extras.isEmpty()) {
            result.putBundle(BKEY_EXTRAS, extras);
        }

        return result;
    }

    @Nullable
    String getOriginal() {
        return original;
    }

    @NonNull
    String getEdited() {
        return edited;
    }

    @Nullable
    Bundle getExtras() {
        return extras;
    }

    @Override
    @NonNull
    public String toString() {
        return "AutoCompletePickerOutput{"
               + "original='" + original + '\''
               + ", edited='" + edited + '\''
               + ", extras=" + extras
               + '}';
    }
}
