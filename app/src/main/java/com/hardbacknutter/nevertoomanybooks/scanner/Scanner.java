/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.scanner;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * Interface defining required methods for any external scanner interface.
 */
public interface Scanner {

    /**
     * Start the activity with the passed request code.
     *
     * @param fragment    calling fragment
     * @param requestCode which will be passed back to onActivityResult
     *
     * @return {@code true} if we could start the activity.
     */
    boolean startActivityForResult(@NonNull Fragment fragment,
                                   int requestCode);

    /**
     * Optional to implement. Meant specifically for the Google Play barcode scanner.
     * This makes coding access to a scanner uniform.
     * <p>
     * When receiving a {@code false}, the caller <strong>should</strong> retry.
     *
     * @return {@code true} if the scanner instance is ready for work.
     */
    default boolean isOperational() {
        return true;
    }

    /**
     * Get the barcode from the resulting intent of a scan action.
     *
     * @param context Current context
     * @param data    the intent as coming from {@link  Fragment#onActivityResult}
     *
     * @return barcode or {@code null}
     */
    @Nullable
    String getBarcode(@NonNull Context context,
                      @Nullable Intent data);
}
