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

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.dialogs.LauncherOutput;

class MultiChoiceOutput
        implements LauncherOutput {

    private static final String TAG = "MultiChoiceOutput";
    private static final String BKEY_ORIGINAL = TAG + ":original";
    private static final String BKEY_EDIT = TAG + ":edit";
    private static final String BKEY_EXTRAS = TAG + ":extras";

    @NonNull
    private final Set<Long> original;
    @NonNull
    private final Set<Long> edited;
    @Nullable
    private final Bundle extras;

    /**
     * Constructor.
     *
     * @param original the previous value
     * @param edited   the new value
     * @param extras   (optional) Bundle provided as input
     */
    MultiChoiceOutput(@NonNull final Set<Long> original,
                      @NonNull final Set<Long> edited,
                      @Nullable final Bundle extras) {
        this.original = original;
        this.edited = edited;
        this.extras = extras;
    }

    @NonNull
    static MultiChoiceOutput fromBundle(@NonNull final Bundle args) {
        final Set<Long> previousSelection =
                Arrays.stream(Objects.requireNonNull(
                              args.getLongArray(BKEY_ORIGINAL), BKEY_ORIGINAL))
                      .boxed()
                      .collect(Collectors.toSet());

        final Set<Long> currentSelection =
                Arrays.stream(Objects.requireNonNull(
                              args.getLongArray(BKEY_EDIT), BKEY_EDIT))
                      .boxed()
                      .collect(Collectors.toSet());

        final Bundle extras = args.getBundle(BKEY_EXTRAS);
        return new MultiChoiceOutput(previousSelection, currentSelection, extras);
    }

    @NonNull
    public Bundle toBundle() {
        final Bundle result = new Bundle(3);
        result.putLongArray(BKEY_ORIGINAL,
                            original.stream().mapToLong(o -> o).toArray());
        result.putLongArray(BKEY_EDIT,
                            edited.stream().mapToLong(o -> o).toArray());
        if (extras != null && !extras.isEmpty()) {
            result.putBundle(BKEY_EXTRAS, extras);
        }

        return result;
    }

    @NonNull
    Set<Long> getOriginal() {
        return original;
    }

    @NonNull
    Set<Long> getEdited() {
        return edited;
    }

    @Nullable
    Bundle getExtras() {
        return extras;
    }

    @Override
    @NonNull
    public String toString() {
        return "MultiChoiceOutput{"
               + "original=" + original
               + ", edited=" + edited
               + ", extras=" + extras
               + '}';
    }
}
