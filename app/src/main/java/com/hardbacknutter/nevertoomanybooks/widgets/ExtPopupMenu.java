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
package com.hardbacknutter.nevertoomanybooks.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.PopupWindow;

import androidx.annotation.Dimension;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.PopupMenuBinding;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.MenuItemListAdapter;

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

    private final MenuItemListAdapter.MenuCallback menuCallback =
            new MenuItemListAdapter.MenuCallback() {
                @Override
                public void update(@NonNull final String title) {
                    vb.title.setText(title);
                    vb.title.setVisibility(View.VISIBLE);
                    final int[] wh = calculatePopupWindowWidthAndHeight();
                    popupWindow.update(wh[0], wh[1]);
                }

                @Override
                public void dismiss() {
                    popupWindow.dismiss();
                }
            };

    @NonNull
    private Menu menu;
    private boolean groupDividerEnabled;
    private MenuItemListAdapter adapter;

    /**
     * Constructor.
     *
     * @param context Current context
     *
     * @see #getMenu()
     * @see #inflate(int)
     */
    @SuppressLint("InflateParams")
    public ExtPopupMenu(@NonNull final Context context) {
        // legal trick to get an instance of Menu.
        // We leave the anchor 'null' as we're not actually going to display this object.
        menu = new PopupMenu(context, null).getMenu();

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
    }

    /**
     * Inflate a menu resource into this PopupMenu. This is equivalent to
     * calling {@code popupMenu.getMenuInflater().inflate(menuRes, popupMenu.getMenu())}.
     *
     * @param menuResId Menu resource to inflate
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public ExtPopupMenu inflate(@MenuRes final int menuResId) {
        new MenuInflater(popupWindow.getContentView().getContext())
                .inflate(menuResId, menu);
        return this;
    }

    /**
     * The {@link Menu} builtin API for group dividers is only available in API 28,
     * and even there it's not possible to read the value back.
     * <p>
     * Call this method to enable the dividers.
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public ExtPopupMenu setGroupDividerEnabled() {
        groupDividerEnabled = true;
        return this;
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
     * Returns the {@link Menu} associated with this popup. Populate the
     * returned Menu with items before calling one of the {@code show} methods.
     *
     * @return the {@link Menu} associated with this popup
     *
     * @see #showAsDropDown(View, MenuItem.OnMenuItemClickListener)
     * @see #show(View, int, MenuItem.OnMenuItemClickListener)
     */
    @NonNull
    public Menu getMenu() {
        return menu;
    }

    /**
     * Replace the existing menu with the given one.
     * This method can be called at any time.
     *
     * @param menu to use
     */
    @SuppressLint("NotifyDataSetChanged")
    public void setMenu(@NonNull final Menu menu) {
        this.menu = menu;
        if (adapter != null) {
            adapter.setMenu(this.menu);
            adapter.notifyDataSetChanged();
        }
    }

    /**
     * Show the menu popup anchored to the given view.
     *
     * @param anchor   the view on which to anchor the popup window
     * @param listener callback with the selected menu item
     */
    public void showAsDropDown(@NonNull final View anchor,
                               @NonNull final MenuItem.OnMenuItemClickListener listener) {

        initAdapter(anchor.getContext(), listener);

        final int[] wh = calculatePopupWindowWidthAndHeight();
        popupWindow.setWidth(wh[0]);
        popupWindow.setHeight(wh[1]);
        // preferred location: halfway on top of the anchor, and indented by mXOffset
        popupWindow.showAsDropDown(anchor, xOffset, -anchor.getHeight() / 2);
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

    /**
     * Display the menu.
     *
     * @param view     a view from which the window token can be used
     * @param gravity  the gravity which controls the placement of the popup window
     *                 One of {@link Gravity#START}, {@link Gravity#END}
     *                 or {@link Gravity#CENTER}.
     * @param listener callback with the selected menu item
     *
     * @throws IllegalArgumentException when an invalid gravity value is passed in
     */
    public void show(@NonNull final View view,
                     final int gravity,
                     @NonNull final MenuItem.OnMenuItemClickListener listener) {

        initAdapter(view.getContext(), listener);

        if (gravity == Gravity.START || gravity == Gravity.END) {
            popupWindow.showAtLocation(view, gravity, xOffset, 0);
        } else if (gravity == Gravity.CENTER) {
            popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0);
        } else {
            throw new IllegalArgumentException(String.valueOf(gravity));
        }
    }

    private void initAdapter(@NonNull final Context context,
                             @NonNull final MenuItem.OnMenuItemClickListener listener) {
        adapter = new MenuItemListAdapter(context, menu, groupDividerEnabled,
                                          menuCallback, listener);
        vb.itemList.setAdapter(adapter);
    }
}
