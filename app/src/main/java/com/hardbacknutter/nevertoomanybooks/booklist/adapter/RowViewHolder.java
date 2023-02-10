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

package com.hardbacknutter.nevertoomanybooks.booklist.adapter;

import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.ShowContextMenu;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.utils.WindowSizeClass;

/**
 * Base for all {@link BooklistGroup} ViewHolder classes.
 *
 * @param <T> the type of data to bind
 */
public abstract class RowViewHolder<T>
        extends RecyclerView.ViewHolder {

    /**
     * The view to install on-click listeners on. Can be the same as the itemView.
     * This is also the view where we can/should add tags,
     * as it is this View that will be passed to the onClick handlers.
     */
    @NonNull
    private final View onClickTargetView;

    @Nullable
    private final Button btnRowMenu;

    /**
     * Constructor.
     *
     * @param itemView the view specific for this holder
     */
    protected RowViewHolder(@NonNull final View itemView) {
        super(itemView);

        btnRowMenu = itemView.findViewById(R.id.btn_row_menu);

        // 2022-09-07: not used for now, but keeping for future usage
        // If present, redirect all clicks to this view, otherwise let the main view get them.
        onClickTargetView = Objects.requireNonNullElse(
                itemView.findViewById(R.id.ROW_ONCLICK_TARGET), itemView);
    }

    /**
     * Set the {@link OnRowClickListener} for a click on a row.
     *
     * @param listener to set
     */
    public void setOnRowClickListener(@Nullable final OnRowClickListener listener) {
        if (listener != null) {
            onClickTargetView.setOnClickListener(v -> listener
                    .onClick(v, getBindingAdapterPosition()));
        } else {
            onClickTargetView.setOnClickListener(null);
        }
    }

    /**
     * Setup the onClick listener for showing the context menu on a row.
     * <p>
     * Provides long-click on a row, and optionally a dedicated button for the same.
     *
     * @param listener        to receive clicks
     * @param contextMenuMode user preferred context menu mode
     */
    public void setOnRowShowContextMenuListener(@Nullable final OnRowClickListener listener,
                                                @Nullable final ShowContextMenu contextMenuMode) {
        if (listener != null && contextMenuMode != null) {
            onClickTargetView.setOnLongClickListener(v -> {
                listener.onClick(v, getBindingAdapterPosition());
                return true;
            });

            if (btnRowMenu != null) {
                btnRowMenu.setOnClickListener(
                        v -> listener.onClick(v, getBindingAdapterPosition()));

                switch (contextMenuMode) {
                    case Button: {
                        btnRowMenu.setVisibility(View.VISIBLE);
                        break;
                    }
                    case ButtonIfSpace: {
                        final WindowSizeClass size = WindowSizeClass.getWidth(
                                btnRowMenu.getContext());
                        if (size == WindowSizeClass.MEDIUM
                            || size == WindowSizeClass.EXPANDED) {
                            btnRowMenu.setVisibility(View.VISIBLE);
                        } else {
                            btnRowMenu.setVisibility(View.GONE);
                        }
                        break;
                    }
                    case NoButton: {
                        btnRowMenu.setVisibility(View.GONE);
                        break;
                    }
                }
            }
        } else {
            onClickTargetView.setOnLongClickListener(null);
            if (btnRowMenu != null) {
                btnRowMenu.setOnClickListener(null);
                btnRowMenu.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Bind the data to the views in the holder.
     *
     * @param position The position of the item within the adapter's data set.
     * @param data     to bind
     * @param style    to use (nullability depends on implementation)
     */
    public abstract void onBindViewHolder(int position,
                                          @NonNull T data,
                                          Style style);
}
