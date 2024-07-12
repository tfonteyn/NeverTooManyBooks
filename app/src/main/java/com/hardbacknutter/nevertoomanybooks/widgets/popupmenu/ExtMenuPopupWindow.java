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
import com.hardbacknutter.nevertoomanybooks.settings.MenuMode;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;

/**
 * Show a context menu on a view - will show icons if present.
 */
public class ExtMenuPopupWindow {

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

    private ExtMenuResultListener listener;
    private int positionOrId;
    @SuppressWarnings("FieldCanBeLocal")
    private final MenuItemListAdapter.MenuCallback menuCallback =
            new MenuItemListAdapter.MenuCallback() {
                @Override
                public boolean onSubMenuClick(@NonNull final ExtMenuItem item) {
                    vb.title.setText(item.getTitle());
                    vb.title.setVisibility(View.VISIBLE);
//                    final int[] wh = calculatePopupWindowWidthAndHeight();
//                    popupWindow.update(wh[0], wh[1]);
                    return true;
                }

                @Override
                public void onMenuItemClick(@IdRes final int menuItemId) {
                    popupWindow.dismiss();
                    listener.onMenuItemClick(positionOrId, menuItemId);
                }
            };


    /**
     * Constructor.
     *
     * @param context Current context
     */
    @SuppressLint("InflateParams")
    public ExtMenuPopupWindow(@NonNull final Context context) {
        final Resources res = context.getResources();
        paddingBottom = res.getDimensionPixelSize(R.dimen.dialogPreferredPaddingBottom);
        xOffset = res.getDimensionPixelSize(R.dimen.popup_menu_x_offset);

        vb = PopupMenuBinding.inflate(LayoutInflater.from(context), null, false);
        vb.dragHandle.setVisibility(View.GONE);

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
    public ExtMenuPopupWindow setTitle(@Nullable final CharSequence title) {
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
    public ExtMenuPopupWindow setMessage(@Nullable final CharSequence message) {
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
     * Line numbers based on API 34 source.
     * {@link PopupWindow} lines 1418 is where things start to go wrong.
     * 'p' is initialized to the original width/height -> WRAP_CONTENT
     * instead of the ACTUAL width/height....
     * line 2440 states:
     * <pre>
     * // If width and mWidth were both < 0 then we have a MATCH_PARENT or
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
     * Set the listener which will received the selected menu-item-id.
     *
     * @param listener to set
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public ExtMenuPopupWindow setListener(@NonNull final ExtMenuResultListener listener) {
        this.listener = listener;

        return this;
    }

    /**
     * Set the position/id for whom this menu is meant. The value will
     * be passed back in the {@link ExtMenuResultListener}.
     *
     * @param position The adapter-position for the View/item which
     *                 owns the menu. But can also be a generic id.
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public ExtMenuPopupWindow setPosition(final int position) {
        this.positionOrId = position;
        return this;
    }

    /**
     * Set the menu which will be displayed when {@link #show(View, MenuMode)} is called.
     *
     * @param menu                to set
     * @param groupDividerEnabled flag
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public ExtMenuPopupWindow setMenu(@NonNull final Menu menu,
                                      final boolean groupDividerEnabled) {
        adapter.setMenu(ExtMenu.convert(menu, groupDividerEnabled));
        return this;
    }

    /**
     * Display the menu.
     *
     * @param view     the anchor for {@link MenuMode#Anchored},
     *                 or a view from which the window token can be used for the other modes
     * @param menuMode the placement of the popup window
     *
     * @throws IllegalArgumentException when an invalid menuMode is passed in
     */
    public void show(@NonNull final View view,
                     @NonNull final MenuMode menuMode) {
        switch (menuMode) {
            case Start:
                // 2024-07-12: 'Start' is not in use; this may be used in the future
                popupWindow.showAtLocation(view, Gravity.START, xOffset, 0);
                break;
            case End:
                // 2024-07-12: 'End' is not in use; this may be used in the future
                popupWindow.showAtLocation(view, Gravity.END, xOffset, 0);
                break;
            case Center:
                popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0);
                break;
            case Anchored: {
                final int[] wh = calculatePopupWindowWidthAndHeight();
                popupWindow.setWidth(wh[0]);
                popupWindow.setHeight(wh[1]);
                popupWindow.showAsDropDown(view, 0, 0);
                break;
            }
            default:
                throw new IllegalArgumentException(String.valueOf(menuMode));
        }
    }
}
