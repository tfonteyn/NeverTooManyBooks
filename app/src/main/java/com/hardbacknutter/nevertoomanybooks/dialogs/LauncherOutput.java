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

package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;

@FunctionalInterface
public interface LauncherOutput {

    /**
     * Encode the output as a Bundle.
     *
     * @return bundle
     */
    @NonNull
    Bundle toBundle();

    /**
     * Send the output/results to the {@link FragmentResultListener}.
     *
     * @param fragment   the calling DialogFragment
     * @param requestKey to use
     */
    default void send(@NonNull final Fragment fragment,
                      @NonNull final String requestKey) {
        fragment.getParentFragmentManager().setFragmentResult(requestKey, toBundle());
    }
}
