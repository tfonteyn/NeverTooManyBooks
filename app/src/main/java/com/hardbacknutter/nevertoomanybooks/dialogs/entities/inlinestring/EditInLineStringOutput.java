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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities.inlinestring;

import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.dialogs.LauncherOutput;

class EditInLineStringOutput
        implements LauncherOutput {

    private static final String TAG = "EditInLineStringOutput";
    private static final String BKEY_ORIGINAL = TAG + ":original";
    private static final String BKEY_EDIT = TAG + ":edit";

    @NonNull
    private final String original;
    @NonNull
    private final String edited;

    /**
     * Constructor.
     *
     * @param original the previous value
     * @param edited   the new value
     */
    EditInLineStringOutput(@NonNull final String original,
                           @NonNull final String edited) {
        this.original = original;
        this.edited = edited;
    }

    @NonNull
    static EditInLineStringOutput fromBundle(@NonNull final Bundle args) {
        final String previousValue = Objects.requireNonNull(
                args.getString(BKEY_ORIGINAL), BKEY_ORIGINAL);
        final String currentValue = Objects.requireNonNull(
                args.getString(BKEY_EDIT), BKEY_EDIT);

        return new EditInLineStringOutput(previousValue, currentValue);
    }

    @NonNull
    public Bundle toBundle() {
        final Bundle result = new Bundle(2);
        result.putString(BKEY_ORIGINAL, original);
        result.putString(BKEY_EDIT, edited);

        return result;
    }

    @NonNull
    String getOriginal() {
        return original;
    }

    @NonNull
    String getEdited() {
        return edited;
    }

    @Override
    @NonNull
    public String toString() {
        return "EditInLineStringOutput{"
               + "original='" + original + '\''
               + ", edited='" + edited + '\''
               + '}';
    }
}
