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

package com.hardbacknutter.prefslib.internal;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class VerticalSpaceItemDecoration
        extends RecyclerView.ItemDecoration {
    private final int pixelOffset;

    /**
     * Constructor.
     *
     * @param pixelOffset the height in pixels as an offset value.
     *                    i.e. a dimension fetched by
     *                    {@link android.content.res.Resources#getDimensionPixelOffset(int)}.
     */
    public VerticalSpaceItemDecoration(final int pixelOffset) {
        this.pixelOffset = pixelOffset;
    }

    @Override
    public void getItemOffsets(@NonNull final Rect outRect,
                               @NonNull final View view,
                               @NonNull final RecyclerView parent,
                               @NonNull final RecyclerView.State state) {
        // Add spacing to every item except the last one
        //noinspection DataFlowIssue
        if (parent.getChildAdapterPosition(view) != parent.getAdapter().getItemCount() - 1) {
            outRect.bottom = pixelOffset;
        }
    }
}
