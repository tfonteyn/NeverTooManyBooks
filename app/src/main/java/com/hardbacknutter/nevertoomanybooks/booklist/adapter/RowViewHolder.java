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
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.utils.WindowSizeClass;

/**
 * Base for all {@link BooklistGroup} ViewHolder classes.
 */
public abstract class RowViewHolder
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
    RowViewHolder(@NonNull final View itemView) {
        super(itemView);

        btnRowMenu = itemView.findViewById(R.id.btn_row_menu);

        // 2022-09-07: not used for now, but keeping for future usage
        // If present, redirect all clicks to this view, otherwise let the main view get them.
        onClickTargetView = Objects.requireNonNullElse(
                itemView.findViewById(R.id.ROW_ONCLICK_TARGET), itemView);
    }

    void setOnClickListener(@Nullable final OnRowClickListener listener) {
        // test for the listener inside the lambda, this allows changing it if needed
        onClickTargetView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(v, getBindingAdapterPosition());
            }
        });
    }

    void setOnLongClickListener(@Nullable final OnRowClickListener listener,
                                final boolean embeddedMode,
                                @NonNull final ShowContextMenu contextMenuMode) {
        // Provide long-click support.
        onClickTargetView.setOnLongClickListener(v -> {
            if (listener != null) {
                return listener.onClick(v, getBindingAdapterPosition());
            }
            return false;
        });

        if (btnRowMenu != null) {
            btnRowMenu.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onClick(v, getBindingAdapterPosition());
                }
            });

            switch (contextMenuMode) {
                case Button: {
                    btnRowMenu.setVisibility(View.VISIBLE);
                    break;
                }
                case ButtonIfSpace: {
                    final WindowSizeClass size = WindowSizeClass.getWidth(
                            btnRowMenu.getContext());
                    final boolean hasSpace = !embeddedMode &&
                                             (size == WindowSizeClass.MEDIUM
                                              || size == WindowSizeClass.EXPANDED);
                    if (hasSpace) {
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
    }

    /**
     * Bind the data to the views in the holder.
     *
     * @param position The position of the item within the adapter's data set.
     * @param rowData  with data to bind
     * @param style    to use
     */
    public abstract void onBindViewHolder(int position,
                                          @NonNull DataHolder rowData,
                                          @NonNull Style style);
}
