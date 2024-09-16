/*
 * @Copyright 2018-2024 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.core.widgets.adapters;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.annotation.Px;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Original code from
 * {@code com.google.android.material.divider.MaterialDividerItemDecoration}
 * in "com.google.android.material:material:1.12.0"
 * <p>
 * Modified to fully support the {@link GridLayoutManager}:
 * <ul>
 *     <li>{@link #dividerAfterLastColumn} is by default {@code false}</li>
 *     <li>{@link #dividerAfterLastRow} is by default {@code true}</li>
 *     <li>the vertical dividers will respect the {@link #dividerAfterLastColumn} properly.</li>
 *     <li>vertical and horizontal dividers can be drawn at the same time; i.e. a proper grid.</li>
 * </ul>
 * <p>
 * Only supported to be used directly from code.
 *
 * <p>For more information, see the <a
 * href="https://github.com/material-components/material-components-android/blob/master/docs/components/Divider.md">component
 * developer guidance</a> and <a href="https://material.io/components/divider/overview">design
 * guidelines</a>.
 */
@SuppressWarnings("WeakerAccess")
public class GridDividerItemDecoration
        extends RecyclerView.ItemDecoration {

    private final Rect tempRect = new Rect();
    @NonNull
    private final Drawable dividerDrawable;
    private final boolean horizontalDivider;
    private final boolean verticalDivider;
    @Px
    private int thickness;
    @ColorInt
    private int color;
    @Px
    private int insetStart;
    @Px
    private int insetEnd;
    private boolean dividerAfterLastColumn;
    private boolean dividerAfterLastRow = true;

    /**
     * Constructor.
     * <p>
     * The default color is {@code ?attr/colorOutline}.
     * The default thickness is {@code 1dp}.
     *
     * @param context           Current content
     * @param horizontalDivider whether a horizontal divider should be drawn between cells
     * @param verticalDivider   whether a vertical divider should be drawn between cells
     */
    public GridDividerItemDecoration(@NonNull final Context context,
                                     final boolean horizontalDivider,
                                     final boolean verticalDivider) {
        this.horizontalDivider = horizontalDivider;
        this.verticalDivider = verticalDivider;

        color = getColorInt(context, com.google.android.material.R.attr.colorOutline);

        thickness = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
                                                    context.getResources().getDisplayMetrics());

        dividerDrawable = new ShapeDrawable();
        setDividerColor(color);
    }

    /**
     * Get a color int value for the given attribute.
     *
     * @param context Current context
     * @param attr    attribute id to resolve
     *
     * @return A single color value in the form 0xAARRGGBB.
     *
     * @throws Resources.NotFoundException if the requested attribute/resource does not exist.
     */
    @ColorInt
    private static int getColorInt(@NonNull final Context context,
                                   @AttrRes final int attr)
            throws Resources.NotFoundException {
        final TypedArray a = context.obtainStyledAttributes(new int[]{attr});
        try {
            final int color = a.getColor(0, 0);
            if (color != 0) {
                return color;
            }
        } finally {
            a.recycle();
        }
        throw new Resources.NotFoundException(String.valueOf(attr));
    }

    /**
     * Sets the thickness of the divider.
     *
     * @param context     Current context
     * @param thicknessId The id of the thickness dimension resource to be set.
     *
     * @see #getDividerThickness()
     */
    public void setDividerThicknessResource(@NonNull final Context context,
                                            @DimenRes final int thicknessId) {
        setDividerThickness(context.getResources().getDimensionPixelSize(thicknessId));
    }

    /**
     * Returns the thickness set on the divider.
     *
     * @return pixels
     *
     * @see #setDividerThickness(int)
     */
    @Px
    public int getDividerThickness() {
        return thickness;
    }

    /**
     * Sets the thickness of the divider.
     *
     * @param thickness The thickness value to be set.
     *
     * @see #getDividerThickness()
     */
    public void setDividerThickness(@Px final int thickness) {
        this.thickness = thickness;
    }

    /**
     * Sets the color of the divider.
     *
     * @param context Current context
     * @param colorId The id of the color resource to be set.
     *
     * @see #getDividerColor()
     */
    public void setDividerColorResource(@NonNull final Context context,
                                        @ColorRes final int colorId) {
        setDividerColor(context.getColor(colorId));
    }

    /**
     * Returns the divider color.
     *
     * @return color int
     *
     * @see #setDividerColor(int)
     */
    @ColorInt
    public int getDividerColor() {
        return color;
    }

    /**
     * Sets the color of the divider.
     *
     * @param color The color to be set.
     *
     * @see #getDividerColor()
     */
    public void setDividerColor(@ColorInt final int color) {
        this.color = color;
        dividerDrawable.setTint(color);
    }

    /**
     * Sets the start inset of the divider.
     *
     * @param context      Current context
     * @param insetStartId The id of the inset dimension resource to be set.
     *
     * @see #getDividerInsetStart()
     */
    public void setDividerInsetStartResource(@NonNull final Context context,
                                             @DimenRes final int insetStartId) {
        setDividerInsetStart(context.getResources().getDimensionPixelOffset(insetStartId));
    }

    /**
     * Returns the divider's start inset.
     *
     * @return pixels
     *
     * @see #setDividerInsetStart(int)
     */
    @Px
    public int getDividerInsetStart() {
        return insetStart;
    }

    /**
     * Sets the start inset of the divider.
     *
     * @param insetStart The start inset to be set.
     *
     * @see #getDividerInsetStart()
     */
    public void setDividerInsetStart(@Px final int insetStart) {
        this.insetStart = insetStart;
    }

    /**
     * Sets the end inset of the divider.
     *
     * @param context    Current context
     * @param insetEndId The id of the inset dimension resource to be set.
     *
     * @see #getDividerInsetEnd()
     */
    public void setDividerInsetEndResource(@NonNull final Context context,
                                           @DimenRes final int insetEndId) {
        setDividerInsetEnd(context.getResources().getDimensionPixelOffset(insetEndId));
    }

    /**
     * Returns the divider's end inset.
     *
     * @return pixels
     *
     * @see #setDividerInsetEnd(int)
     */
    @Px
    public int getDividerInsetEnd() {
        return insetEnd;
    }

    /**
     * Sets the end inset of the divider.
     *
     * @param insetEnd The end inset to be set.
     *
     * @see #getDividerInsetEnd()
     */
    public void setDividerInsetEnd(@Px final int insetEnd) {
        this.insetEnd = insetEnd;
    }

    /**
     * Whether there's a divider after the last item of a {@link RecyclerView}.
     *
     * @return flag
     *
     * @see #setDividerAfterLastColumn(boolean)
     */
    public boolean isDividerAfterLastColumn() {
        return dividerAfterLastColumn;
    }

    /**
     * Sets whether the class should draw a divider after the last item of a {@link RecyclerView}.
     *
     * @param dividerAfterLastColumn whether there's a divider after the last item
     *                               of a recycler view.
     *
     * @see #isDividerAfterLastColumn()
     */
    public void setDividerAfterLastColumn(final boolean dividerAfterLastColumn) {
        this.dividerAfterLastColumn = dividerAfterLastColumn;
    }

    /**
     * Whether there's a divider after the last item of a {@link RecyclerView}.
     *
     * @return flag
     *
     * @see #setDividerAfterLastRow(boolean)
     */
    public boolean isDividerAfterLastRow() {
        return dividerAfterLastRow;
    }

    /**
     * Sets whether the class should draw a divider after the last item of a {@link RecyclerView}.
     *
     * @param dividerAfterLastRow whether there's a divider after the last item
     *                            of a recycler view.
     *
     * @see #isDividerAfterLastRow()
     */
    public void setDividerAfterLastRow(final boolean dividerAfterLastRow) {
        this.dividerAfterLastRow = dividerAfterLastRow;
    }

    @Override
    public void onDraw(@NonNull final Canvas canvas,
                       @NonNull final RecyclerView parent,
                       @NonNull final RecyclerView.State state) {
        if (!(parent.getLayoutManager() instanceof GridLayoutManager)) {
            return;
        }

        if (horizontalDivider) {
            drawHorizontalDivider(canvas, parent);
        }

        if (verticalDivider) {
            drawVerticalDivider(canvas, parent);
        }
    }

    private void drawHorizontalDivider(@NonNull final Canvas canvas,
                                       @NonNull final RecyclerView parent) {
        //noinspection DataFlowIssue
        final int spanCount = ((GridLayoutManager) parent.getLayoutManager()).getSpanCount();
        //noinspection DataFlowIssue
        final int childCount = Math.min(parent.getChildCount(), parent.getAdapter().getItemCount());
        final int dividerCount = childCount / spanCount
                                 + ((childCount % spanCount) > 0 ? 1 : 0)
                                 - (dividerAfterLastRow ? 0 : 1);


        if (dividerCount > 0) {
            canvas.save();

            int left;
            int right;
            if (parent.getClipToPadding()) {
                left = parent.getPaddingLeft();
                right = parent.getWidth() - parent.getPaddingRight();
                canvas.clipRect(left, parent.getPaddingTop(), right,
                                parent.getHeight() - parent.getPaddingBottom());
            } else {
                left = 0;
                right = parent.getWidth();
            }

            final boolean isRtl = parent.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            left += isRtl ? insetEnd : insetStart;
            right -= isRtl ? insetStart : insetEnd;

            for (int i = 0; i < dividerCount; i++) {
                final View child = parent.getChildAt(i * spanCount);
                parent.getDecoratedBoundsWithMargins(child, tempRect);
                // Take into consideration any translationY added to the view.
                final int bottom = tempRect.bottom + Math.round(child.getTranslationY());
                final int top = bottom - dividerDrawable.getIntrinsicHeight() - thickness;

                dividerDrawable.setBounds(left, top, right, bottom);
                final int alpha = Math.round(child.getAlpha() * 255);
                dividerDrawable.setAlpha(alpha);
                dividerDrawable.draw(canvas);
            }
            canvas.restore();
        }
    }

    private void drawVerticalDivider(@NonNull final Canvas canvas,
                                     @NonNull final RecyclerView parent) {
        //noinspection DataFlowIssue
        final int spanCount = ((GridLayoutManager) parent.getLayoutManager()).getSpanCount();
        final int dividerCount = Math.min(spanCount, parent.getChildCount())
                                 - (dividerAfterLastColumn ? 0 : 1);

        if (dividerCount > 0) {
            canvas.save();

            int top;
            int bottom;
            if (parent.getClipToPadding()) {
                top = parent.getPaddingTop();
                bottom = parent.getHeight() - parent.getPaddingBottom();
                canvas.clipRect(parent.getPaddingLeft(), top,
                                parent.getWidth() - parent.getPaddingRight(), bottom);
            } else {
                top = 0;
                bottom = parent.getHeight();
            }
            top += insetStart;
            bottom -= insetEnd;

            final boolean isRtl = parent.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;

            for (int d = 0; d < dividerCount; d++) {
                final View child = parent.getChildAt(d);
                parent.getLayoutManager().getDecoratedBoundsWithMargins(child, tempRect);
                // Take into consideration any translationX added to the view.c
                final int translationX = Math.round(child.getTranslationX());
                final int left;
                final int right;
                if (isRtl) {
                    left = tempRect.left + translationX;
                    right = left + dividerDrawable.getIntrinsicWidth() + thickness;
                } else {
                    right = tempRect.right + translationX;
                    left = right - dividerDrawable.getIntrinsicWidth() - thickness;
                }

                dividerDrawable.setBounds(left, top, right, bottom);
                final int alpha = Math.round(child.getAlpha() * 255);
                dividerDrawable.setAlpha(alpha);
                dividerDrawable.draw(canvas);
            }
            canvas.restore();
        }
    }

    @Override
    public void getItemOffsets(@NonNull final Rect outRect,
                               @NonNull final View view,
                               @NonNull final RecyclerView parent,
                               @NonNull final RecyclerView.State state) {
        outRect.set(0, 0, 0, 0);

        //noinspection DataFlowIssue
        final int spanCount = ((GridLayoutManager) parent.getLayoutManager()).getSpanCount();
        final int childLayoutPosition = parent.getChildLayoutPosition(view);

        if (horizontalDivider) {
            final int dividerCount = spanCount - (dividerAfterLastRow ? 0 : 1);
            if (dividerCount > 0) {
                if ((childLayoutPosition % dividerCount) != 0) {
                    outRect.bottom = dividerDrawable.getIntrinsicHeight() + thickness;
                }
            }
        }
        if (verticalDivider) {
            final int dividerCount = spanCount - (dividerAfterLastColumn ? 0 : 1);
            if (dividerCount > 0) {
                if ((childLayoutPosition % dividerCount) != 0) {
                    outRect.right = dividerDrawable.getIntrinsicWidth() + thickness;
                }
            }
        }
    }
}
