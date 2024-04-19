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
package com.hardbacknutter.nevertoomanybooks.widgets.popupmenu;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.widget.PopupWindow;

import androidx.annotation.Dimension;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.PopupMenuBinding;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;

/**
 * Show a context menu on a view - will show icons if present.
 */
public class ExtPopupMenu {

    @NonNull
    private final PopupMenuBinding vb;

    @Dimension
    private final int xOffset;
    @Dimension
    private final int paddingBottom;

    @NonNull
    private final PopupWindow popupWindow;
    @NonNull
    private final MenuItemListAdapter adapter;

    private OnMenuItemClickListener listener;

    private final MenuItemListAdapter.MenuCallback menuCallback =
            new MenuItemListAdapter.MenuCallback() {
                @Override
                public void onNewMenuTitle(@NonNull final CharSequence title) {
                    vb.title.setText(title);
                    vb.title.setVisibility(View.VISIBLE);
                    final int[] wh = calculatePopupWindowWidthAndHeight();
                    popupWindow.update(wh[0], wh[1]);
                }

                @Override
                public void onMenuItemClick(@IdRes final int menuItemId) {
                    popupWindow.dismiss();
                    listener.onMenuItemClick(menuItemId);
                }
            };

    /**
     * Constructor.
     *
     * @param context             Current context
     */
    @SuppressLint("InflateParams")
    public ExtPopupMenu(@NonNull final Context context) {
        final Resources res = context.getResources();
        paddingBottom = res.getDimensionPixelSize(R.dimen.dialogPreferredPaddingBottom);
        xOffset = res.getDimensionPixelSize(R.dimen.popup_menu_x_offset);

        vb = PopupMenuBinding.inflate(LayoutInflater.from(context), null, false);

        popupWindow = new PopupWindow(context);
        popupWindow.setFocusable(true);
        popupWindow.setContentView(vb.getRoot());

        popupWindow.setBackgroundDrawable(AttrUtils.getDrawable(
                context, com.google.android.material.R.attr.popupMenuBackground));
        popupWindow.setElevation(res.getDimensionPixelSize(R.dimen.popup_menu_elevation));

        adapter = new MenuItemListAdapter(context, menuCallback);
        vb.itemList.setAdapter(adapter);
    }

    /**
     * Set the title at the top of the menu.
     *
     * @param title to set, {@code null} or {@code ""} to remove
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public ExtPopupMenu setTitle(@Nullable final CharSequence title) {
        if (title != null && title.length() > 0) {
            vb.title.setVisibility(View.VISIBLE);
            vb.title.setText(title);
        } else {
            vb.title.setVisibility(View.GONE);
        }
        return this;
    }

    /**
     * Set the message at the top of the menu.
     *
     * @param message to set, {@code null} or {@code ""} to remove
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public ExtPopupMenu setMessage(@Nullable final CharSequence message) {
        if (message != null && message.length() > 0) {
            vb.message.setVisibility(View.VISIBLE);
            vb.message.setText(message);
        } else {
            vb.message.setVisibility(View.GONE);
        }
        return this;
    }

    /**
     * A more or less accurate way of setting the width/height...
     * <p>
     * So why are we doing the measuring and setting width/height manually?
     * (androids internals... to remind myself)
     * <p>
     * The PopupWindow is set to LayoutParams.WRAP_CONTENT / LayoutParams.WRAP_CONTENT
     * and this fails to work reliably.
     * Setting it to MATCH works as expected due to the Android *explicitly* checking for it.
     * <p>
     * The real width/height is dynamic due to the RecyclerView.
     * but we need an absolute value for PopupWindow#findDropDownPosition which calls
     * PopupWindow#tryFitVertical + PopupWindow#tryFitHorizontal.
     * <p>
     * The latter try to determine the absolute position of the window versus the anchor.
     * i.e. as requested under the anchor, or if not enough space, above the anchor.
     * <p>
     * PopupWindow lines 1414 is where things start to go wrong.
     * 'p' is initialized to the original width/height -> WRAP_CONTENT
     * instead of the ACTUAL width/height....
     * line 2427 states:
     * <pre>
     * // WRAP_CONTENT case. findDropDownPosition will have resolved
     * // this to absolute values, but we don't want to update
     * // mWidth/mHeight to these absolute values.
     * </pre>
     * Reality: no it does not... it just uses mWidth/mHeight *AS-IS*. i.e. wrap -> "-2"
     * and so it does its calculations using the absolute value of -2... oops...
     *
     * @return int[0] width, int[1] height
     */
    private int[] calculatePopupWindowWidthAndHeight() {
        final View contentView = popupWindow.getContentView();
        contentView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        return new int[]{contentView.getMeasuredWidth(),
                contentView.getMeasuredHeight() + paddingBottom};
    }

    @NonNull
    public ExtPopupMenu setListener(@NonNull final OnMenuItemClickListener listener) {
        this.listener = listener;

        return this;
    }

    @NonNull
    public ExtPopupMenu setMenu(@NonNull final Menu menu,
                                final boolean groupDividerEnabled) {
        adapter.setMenu(ExtMenu.convert(menu, groupDividerEnabled));
        return this;
    }

    /**
     * Display the menu.
     *
     * @param view     the anchor for {@link Location#Anchored},
     *                 or a view from which the window token can be used
     * @param location the gravity which controls the placement of the popup window
     *
     * @throws IllegalArgumentException when an invalid gravity value is passed in
     */
    public void show(@NonNull final View view,
                     @NonNull final Location location) {
        switch (location) {
            case Start:
                popupWindow.showAtLocation(view, Gravity.START, xOffset, 0);
                break;
            case End:
                popupWindow.showAtLocation(view, Gravity.END, xOffset, 0);
                break;
            case Center:
                popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0);
                break;
            case Anchored: {
                final int[] wh = calculatePopupWindowWidthAndHeight();
                popupWindow.setWidth(wh[0]);
                popupWindow.setHeight(wh[1]);
                // preferred location: halfway on top of the anchor, and indented by mXOffset
                popupWindow.showAsDropDown(view, xOffset, -view.getHeight() / 2);
                break;
            }
            default:
                throw new IllegalArgumentException(String.valueOf(location));
        }
    }


    public enum Location {
        /** Show at the Start of a specific offset. */
        Start,
        /** Show at the End of a specific offset. */
        End,
        /** Show at the Center of a specific offset. */
        Center,
        /** Show Anchored to the given view. */
        Anchored
    }

    /**
     * Interface definition for a callback to be invoked when a menu item is clicked.
     */
    public interface OnMenuItemClickListener {
        /**
         * Called when a menu item has been invoked.  This is the first code that
         * is executed; if it returns true, no other callbacks will be executed.
         *
         * @param menuItemId The menu item that was invoked.
         *
         * @return Return true to consume this click and prevent others from executing.
         */
        public boolean onMenuItemClick(@IdRes int menuItemId);
    }
}
