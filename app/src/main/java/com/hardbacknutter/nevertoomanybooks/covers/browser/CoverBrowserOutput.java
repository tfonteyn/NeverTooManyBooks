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

package com.hardbacknutter.nevertoomanybooks.covers.browser;

import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.dialogs.LauncherOutput;

class CoverBrowserOutput
        implements LauncherOutput {
    private static final String COVER_FILE_SPEC = "fileSpec";

    @NonNull
    private final String fileSpec;

    CoverBrowserOutput(@NonNull final String fileSpec) {
        this.fileSpec = fileSpec;
    }

    @NonNull
    static String fromBundle(@NonNull final Bundle args) {
        return Objects.requireNonNull(args.getString(COVER_FILE_SPEC), COVER_FILE_SPEC);
    }

    @NonNull
    @Override
    public Bundle toBundle() {
        final Bundle args = new Bundle(1);
        args.putString(COVER_FILE_SPEC, fileSpec);
        return args;
    }

    @Override
    @NonNull
    public String toString() {
        return "CoverBrowserOutput{"
               + "fileSpec='" + fileSpec + '\''
               + '}';
    }
}
