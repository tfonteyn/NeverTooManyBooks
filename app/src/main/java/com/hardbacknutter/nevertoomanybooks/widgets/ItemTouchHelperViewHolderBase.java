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
package com.hardbacknutter.nevertoomanybooks.widgets;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;
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
    public final CompoundButton checkableButton;
    /** The details part of the row (or the row itself). */
    @NonNull
    public final View rowDetailsView;
    /** optional drag handle button for drag/drop support. */
    @Nullable
    final ImageView dragHandleView;
    /** optional row delete button. */
    @Nullable
    final View deleteButton;

    /** used while dragging. */
    @ColorInt
    private final int itemDraggedBackgroundColor;
    /** preserves the original background while dragging. */
    private Drawable originalItemSelectedBackground;

    protected ItemTouchHelperViewHolderBase(@NonNull final View itemView) {
        super(itemView);

        // Don't enable the whole row, so buttons keep working
        final View rd = itemView.findViewById(R.id.ROW_ONCLICK_TARGET);
        // but if we did not define a details row subview, use the row itself anyhow.
        rowDetailsView = rd != null ? rd : itemView;
        rowDetailsView.setFocusable(false);

        // optional
        deleteButton = itemView.findViewById(R.id.ROW_DELETE_BTN);
        checkableButton = itemView.findViewById(R.id.ROW_CHECKABLE_BTN);
        dragHandleView = itemView.findViewById(R.id.ROW_GRABBER_ICON);

        itemDraggedBackgroundColor = AttrUtils.getColorInt(
                itemView.getContext(), com.google.android.material.R.attr.colorPrimary);
    }

    @Override
    public void onItemDragStarted() {
        originalItemSelectedBackground = itemView.getBackground();
        itemView.setBackgroundColor(itemDraggedBackgroundColor);
    }

    @Override
    public void onItemDragFinished() {
        //2022-03-28: this used to work by just calling 'setBackground'
        // It seems we now ALSO need to call 'setBackgroundColor' ??
        itemView.setBackgroundColor(Color.TRANSPARENT);
        itemView.setBackground(originalItemSelectedBackground);
    }
}
