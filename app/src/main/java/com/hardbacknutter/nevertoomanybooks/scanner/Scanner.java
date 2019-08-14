/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
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

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

/**
 * Interface defining required methods for any external scanner interface.
 * At least one of the {@link #startActivityForResult} methods must be implemented before using.
 */
public interface Scanner {

    /**
     * Start the activity with the passed request code.
     *
     * @param fragment    calling fragment
     * @param requestCode which will be passed back to onActivityResult
     */
    default void startActivityForResult(@NonNull Fragment fragment,
                                        int requestCode) {
        throw new IllegalStateException("must be implemented");
    }

    /**
     * Start the activity with the passed request code.
     *
     * @param activity    calling activity
     * @param requestCode which will be passed back to onActivityResult
     */
    default void startActivityForResult(@NonNull Activity activity,
                                        int requestCode) {
        throw new IllegalStateException("must be implemented");
    }

    /**
     * @return the barcode from the resulting intent of a scan action.
     */
    @NonNull
    String getBarcode(@NonNull Intent data);
}
