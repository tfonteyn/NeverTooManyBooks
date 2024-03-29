/*
 * @Copyright 2018-2022 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.widgets.adapters;

import android.view.View;

import androidx.annotation.NonNull;

/**
 * Handle a click or long-click on a row.
 */
@FunctionalInterface
public interface OnRowClickListener {

    /**
     * User (long)clicked a row.
     *
     * @param v        View clicked
     * @param position The position of the item within the adapter's data set.
     */
    void onClick(@NonNull View v,
                 int position);
}
