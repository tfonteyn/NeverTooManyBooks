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
package com.hardbacknutter.nevertoomanybooks.dialogs.date;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;

/**
 * Listener interface to receive notifications a date was picked.
 */
public interface DatePickerResultsListener
        extends FragmentResultListener {

    /* private. */ String YEAR = "year";
    /* private. */ String MONTH = "month";
    /* private. */ String DAY = "day";

    static void sendResult(@NonNull final Fragment fragment,
                           @NonNull final String requestKey,
                           final int year,
                           final int month,
                           final int day) {
        final Bundle result = new Bundle();
        result.putInt(YEAR, year);
        result.putInt(MONTH, month);
        result.putInt(DAY, day);

        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    @Override
    default void onFragmentResult(@NonNull final String requestKey,
                                  @NonNull final Bundle result) {
        onResult(result.getInt(YEAR), result.getInt(MONTH), result.getInt(MONTH));
    }

    /**
     * Callback handler with the user's selection.
     *
     * @param year  4 digit year, or {@code 0} for none
     * @param month 1..12 based, or {@code 0} for none
     * @param day   1..31 based, or {@code 0} for none
     */
    void onResult(int year,
                  int month,
                  int day);
}
