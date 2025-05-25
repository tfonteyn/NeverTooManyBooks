/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.booklist.adapter;

import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import com.hardbacknutter.nevertoomanybooks.R;

class BookDebugRowIdView {

    /** Only active when running in debug mode; displays the "position/rowId" for a book. */
    @NonNull
    private final TextView dbgRowIdView;

    BookDebugRowIdView(@NonNull final ConstraintLayout parentLayout) {
        // Add a text view to display the "position/rowId" for a book.
        // Displayed on top of the image so the layout is not changed.
        dbgRowIdView = new TextView(parentLayout.getContext());
        dbgRowIdView.setId(View.generateViewId());
        dbgRowIdView.setTextColor(Color.BLUE);
        dbgRowIdView.setBackgroundColor(Color.WHITE);
        dbgRowIdView.setZ(5);
        dbgRowIdView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);

        parentLayout.addView(dbgRowIdView, 0);

        final ConstraintSet set = new ConstraintSet();
        set.clone(parentLayout);
        set.connect(dbgRowIdView.getId(), ConstraintSet.TOP,
                    R.id.cover_image_0, ConstraintSet.TOP);
        set.connect(dbgRowIdView.getId(), ConstraintSet.START,
                    R.id.cover_image_0, ConstraintSet.START);
        set.setVerticalBias(dbgRowIdView.getId(), 1.0f);

        set.applyTo(parentLayout);
    }

    void onBind(final int bindingAdapterPosition,
                final long rowId) {
        final String txt = String.valueOf(bindingAdapterPosition) + '/' + rowId;
        dbgRowIdView.setText(txt);
    }
}
