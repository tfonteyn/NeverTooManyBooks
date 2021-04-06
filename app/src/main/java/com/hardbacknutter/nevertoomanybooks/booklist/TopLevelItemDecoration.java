/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.Objects;

/**
 * Stripped down copy of {@link DividerItemDecoration} which only draws the decorator
 * on top-level (==1) items. Orientation is vertical only.
 */
public class TopLevelItemDecoration
        extends RecyclerView.ItemDecoration {

    private static final int[] ATTRS = new int[]{android.R.attr.listDivider};
    private final Rect mBounds = new Rect();
    @NonNull
    private final Drawable mDivider;

    /**
     * Creates a divider {@link RecyclerView.ItemDecoration} that can be used with a
     * {@link LinearLayoutManager}.
     *
     * @param context Current context@param context\s+[^c ]
     */
    public TopLevelItemDecoration(@NonNull final Context context) {
        final TypedArray ta = context.getTheme().obtainStyledAttributes(ATTRS);
        mDivider = Objects.requireNonNull(ta.getDrawable(0), Arrays.toString(ATTRS));
        ta.recycle();
    }

    @Override
    public void onDraw(@NonNull final Canvas canvas,
                       @NonNull final RecyclerView parent,
                       @NonNull final RecyclerView.State state) {
        if (parent.getLayoutManager() == null) {
            return;
        }

        //noinspection rawtypes
        final RecyclerView.Adapter adapter = parent.getAdapter();
        if (!(adapter instanceof BooklistAdapter)) {
            return;
        }

        final BooklistAdapter booklistAdapter = (BooklistAdapter) adapter;
        if (!booklistAdapter.hasCursor()) {
            return;
        }

        canvas.save();
        final int left;
        final int right;
        if (parent.getClipToPadding()) {
            left = parent.getPaddingLeft();
            right = parent.getWidth() - parent.getPaddingRight();
            canvas.clipRect(left, parent.getPaddingTop(), right,
                            parent.getHeight() - parent.getPaddingBottom());
        } else {
            left = 0;
            right = parent.getWidth();
        }

        final int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = parent.getChildAt(i);
            // level 1 only
            if (booklistAdapter.getLevel(parent.getChildAdapterPosition(child)) == 1) {
                parent.getDecoratedBoundsWithMargins(child, mBounds);
                final int bottom = mBounds.bottom + Math.round(child.getTranslationY());
                final int top = bottom - mDivider.getIntrinsicHeight();
                mDivider.setBounds(left, top, right, bottom);
                mDivider.draw(canvas);
            }
        }
        canvas.restore();
    }

    @Override
    public void getItemOffsets(@NonNull final Rect outRect,
                               @NonNull final View view,
                               @NonNull final RecyclerView parent,
                               @NonNull final RecyclerView.State state) {
        outRect.set(0, 0, 0, mDivider.getIntrinsicHeight());
    }
}

