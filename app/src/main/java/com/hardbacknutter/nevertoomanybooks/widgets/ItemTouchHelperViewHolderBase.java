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
package com.hardbacknutter.nevertoomanybooks.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.ItemTouchHelperViewHolder;

/**
 * Row ViewHolder for each row in a RecyclerView.
 *
 * <ul>Extends the original with support for:
 *      <li>{@link ItemTouchHelperViewHolder}</li>
 *      <li>a 'delete' button</li>
 *      <li>a 'checkable' button</li>
 * </ul>
 * <ul>Uses pre-defined ID's:
 *      <li>R.id.TLV_ROW_DETAILS</li>
 *      <li>R.id.TLV_ROW_DELETE</li>
 *      <li>R.id.TLV_ROW_CHECKABLE</li>
 *      <li>R.id.TLV_ROW_GRABBER</li>
 * </ul>
 */
public abstract class ItemTouchHelperViewHolderBase
        extends RecyclerView.ViewHolder
        implements ItemTouchHelperViewHolder {

    /** optional row checkable button. */
    @Nullable
    public final CompoundButton mCheckableButton;
    /** The details part of the row (or the row itself). */
    @NonNull
    public final View rowDetailsView;
    /** optional drag handle button for drag/drop support. */
    @Nullable
    final ImageView mDragHandleView;
    /** optional row delete button. */
    @Nullable
    final View mDeleteButton;

    /** used while dragging. */
    @ColorInt
    private final int mItemDraggedBackgroundColor;
    /** preserves the original background while dragging. */
    private Drawable mOriginalItemSelectedBackground;

    protected ItemTouchHelperViewHolderBase(@NonNull final View itemView) {
        super(itemView);

        // Don't enable the whole row, so buttons keep working
        final View rd = itemView.findViewById(R.id.ROW_ONCLICK_TARGET);
        // but if we did not define a details row subview, use the row itself anyhow.
        rowDetailsView = rd != null ? rd : itemView;
        rowDetailsView.setFocusable(false);

        // optional
        mDeleteButton = itemView.findViewById(R.id.ROW_DELETE_BTN);
        mCheckableButton = itemView.findViewById(R.id.ROW_CHECKABLE_BTN);
        mDragHandleView = itemView.findViewById(R.id.ROW_GRABBER_ICON);

        mItemDraggedBackgroundColor = getColorInt(itemView.getContext(), R.attr.colorPrimary);
    }

    /**
     * Get a color int value for the given attribute.
     *
     * @param context Current context
     * @param attr    attribute id to resolve
     *
     * @return A single color value in the form 0xAARRGGBB.
     */
    @ColorInt
    private static int getColorInt(@NonNull final Context context,
                                   @SuppressWarnings("SameParameterValue") @AttrRes final int attr)
            throws Resources.NotFoundException {
        final Resources.Theme theme = context.getTheme();
        final TypedValue tv = new TypedValue();
        theme.resolveAttribute(attr, tv, true);
        return context.getResources().getColor(tv.resourceId, theme);
    }

    @Override
    public void onItemDragStarted() {
        mOriginalItemSelectedBackground = itemView.getBackground();
        itemView.setBackgroundColor(mItemDraggedBackgroundColor);
    }

    @Override
    public void onItemDragFinished() {
        itemView.setBackground(mOriginalItemSelectedBackground);
    }
}
