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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.dialogs.LauncherOutput;

public class EditInPlaceParcelableOutput<T extends Parcelable>
        implements LauncherOutput {

    private static final String BKEY_MODIFIED = "modified";

    @NonNull
    private final T data;

    public EditInPlaceParcelableOutput(@NonNull final T data) {
        this.data = data;
    }

    @NonNull
    static <T extends Parcelable> T fromBundle(@NonNull final Bundle args) {
        //noinspection deprecation
        return Objects.requireNonNull(args.getParcelable(BKEY_MODIFIED));
    }

    @NonNull
    @Override
    public Bundle toBundle() {
        final Bundle args = new Bundle(1);
        args.putParcelable(BKEY_MODIFIED, data);
        return args;
    }

    @Override
    @NonNull
    public String toString() {
        return "EditInPlaceParcelableOutput{"
               + "data=" + data
               + '}';
    }
}
