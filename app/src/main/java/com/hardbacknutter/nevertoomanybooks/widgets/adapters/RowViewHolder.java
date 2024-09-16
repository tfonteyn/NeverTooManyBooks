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

package com.hardbacknutter.nevertoomanybooks.widgets.adapters;

import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.widgets.ScreenSize;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuButton;

/**
 * Base for all ViewHolder classes.
 * <p>
 * Provides unified handling of clicks, long-clicks and an optional context menu button.
 * <p>
 * Uses pre-defined ID's:
 * <ul>
 *      <li>R.id.ROW_ONCLICK_TARGET</li>
 * </ul>
 */
@SuppressWarnings("WeakerAccess")
public class RowViewHolder
        extends RecyclerView.ViewHolder {

    /**
     * The view to install on-click listeners on. Can be the same as the itemView.
     * This is also the view where we can/should add tags,
     * as it is this View that will be passed to the onClick handlers.
     */
    @NonNull
    private final View onClickTargetView;

    @Nullable
    private final MaterialButton btnRowMenu;

    /**
     * Constructor.
     *
     * @param itemView the view specific for this holder
     */
    protected RowViewHolder(@NonNull final View itemView) {
        super(itemView);

        btnRowMenu = itemView.findViewById(R.id.btn_row_menu);

        onClickTargetView = Objects.requireNonNullElse(
                itemView.findViewById(R.id.ROW_ONCLICK_TARGET), itemView);
    }

    /**
     * Set whether the ClickTargetView/ItemView can receive the focus.
     *
     * @param focusable If true, this view can receive the focus.
     */
    public void setClickTargetViewFocusable(final boolean focusable) {
        onClickTargetView.setFocusable(focusable);
    }

    /**
     * Sets the icon drawable resource to show for the row-menu button.
     *
     * @param iconResourceId Drawable resource ID to use for the button's icon.
     */
    public void setRowMenuButtonIconResource(@DrawableRes final int iconResourceId) {
        //noinspection DataFlowIssue
        btnRowMenu.setIconResource(iconResourceId);
    }

    /**
     * Set the {@link OnRowClickListener} for a click on the background/row.
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
     * Setup the onClick listener for showing the context menu on the background/row.
     * <p>
     * Provides long-click on a row, and optionally a dedicated button for the same.
     *
     * @param contextMenuMode user preferred context menu mode
     * @param listener        to receive clicks
     */
    public void setOnRowLongClickListener(@Nullable final ExtMenuButton contextMenuMode,
                                          @Nullable final OnRowClickListener listener) {
        if (listener != null && contextMenuMode != null) {
            // long-click on the background
            onClickTargetView.setOnLongClickListener(v -> {
                listener.onClick(v, getBindingAdapterPosition());
                return true;
            });

            // Add a dedicated button as per user-preference.
            if (btnRowMenu != null) {
                btnRowMenu.setOnClickListener(
                        v -> listener.onClick(v, getBindingAdapterPosition()));

                final int visibility = getButtonVisibility(contextMenuMode);
                btnRowMenu.setVisibility(visibility);
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
     * Determine visibility based on the given mode and the screen size.
     *
     * @param contextMenuMode user-desired mode
     *
     * @return visibility
     */
    protected int getButtonVisibility(@NonNull final ExtMenuButton contextMenuMode) {
        final int visibility;
        switch (contextMenuMode) {
            case Always: {
                visibility = View.VISIBLE;
                break;
            }
            case IfRoom: {
                final ScreenSize.Value size =
                        ScreenSize.compute(itemView.getContext()).getWidth();
                if (size == ScreenSize.Value.Medium
                    || size == ScreenSize.Value.Expanded) {
                    visibility = View.VISIBLE;
                } else {
                    visibility = View.GONE;
                }
                break;
            }
            case None: {
                visibility = View.GONE;
                break;
            }
            default:
                visibility = View.GONE;
                break;
        }
        return visibility;
    }
}
