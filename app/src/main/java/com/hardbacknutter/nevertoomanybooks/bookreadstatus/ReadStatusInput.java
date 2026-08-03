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

package com.hardbacknutter.nevertoomanybooks.bookreadstatus;

import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.Objects;

final class ReadStatusInput {

    private static final String TAG = "ReadStatusInput";
    private static final String BKEY_MODE = TAG + ":mode";
    private static final String BKEY_EMBEDDED = TAG + ":bd-embedded";

    @NonNull
    private final ReadStatusFragmentFactory.Mode mode;
    private final boolean embedded;

    ReadStatusInput(@NonNull final ReadStatusFragmentFactory.Mode mode,
                    final boolean embedded) {
        this.mode = mode;
        this.embedded = embedded;
    }

    @NonNull
    static ReadStatusInput fromBundle(@NonNull final Bundle args) {
        @SuppressWarnings("deprecation")
        final ReadStatusFragmentFactory.Mode mode =
                Objects.requireNonNull(args.getParcelable(BKEY_MODE));
        final boolean embedded = args.getBoolean(BKEY_EMBEDDED, false);

        return new ReadStatusInput(mode, embedded);
    }

    @NonNull
    Bundle toBundle() {
        final Bundle args = new Bundle(2);
        args.putParcelable(BKEY_MODE, mode);
        args.putBoolean(BKEY_EMBEDDED, embedded);

        return args;
    }

    @NonNull
    ReadStatusFragmentFactory.Mode getMode() {
        return mode;
    }

    boolean isEmbedded() {
        return embedded;
    }

    @Override
    @NonNull
    public String toString() {
        return "ReadStatusInput{"
               + "mode=" + mode
               + ", embedded=" + embedded
               + '}';
    }
}
