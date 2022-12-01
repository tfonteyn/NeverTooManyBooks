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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;

/**
 * Show a context menu on a view - will show icons if present.
 */
public class ExtPopupMenu {

    @NonNull
    private final VBLite vb;

    private final int xOffset;
    private final int paddingBottom;

    @NonNull
    private final PopupWindow popupWindow;
    private final MenuCallback menuCallback = new MenuCallback() {
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

        vb = new VBLite(LayoutInflater.from(context).inflate(R.layout.popup_menu, null, false));

        popupWindow = new PopupWindow(context);
        popupWindow.setFocusable(true);
        popupWindow.setContentView(vb.rootView);

        popupWindow.setBackgroundDrawable(
                AttrUtils.getDrawable(context, R.attr.popupMenuBackground));
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
        getMenuInflater().inflate(menuResId, menu);
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
     * @return a {@link MenuInflater} that can be used to inflate menu items
     *         from XML into the menu returned by {@link #getMenu()}
     *
     * @see #getMenu()
     */
    @NonNull
    public MenuInflater getMenuInflater() {
        return new MenuInflater(popupWindow.getContentView().getContext());
    }

    /**
     * Returns the {@link Menu} associated with this popup. Populate the
     * returned Menu with items before calling one of the {@code show} methods.
     *
     * @return the {@link Menu} associated with this popup
     *
     * @see #showAsDropDown(View, OnMenuItemClickListener)
     * @see #show(View, int, OnMenuItemClickListener)
     * @see #getMenuInflater()
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
                               @NonNull final OnMenuItemClickListener listener) {

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
                     @NonNull final OnMenuItemClickListener listener) {

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
                             @NonNull final OnMenuItemClickListener listener) {
        adapter = new MenuItemListAdapter(context, menu, groupDividerEnabled,
                                          menuCallback, listener);
        vb.itemList.setAdapter(adapter);
    }

    /**
     * Interface responsible for receiving menu item click events if the items
     * themselves do not have individual item click listeners.
     */
    @FunctionalInterface
    public interface OnMenuItemClickListener {

        /**
         * This method will be invoked when a menu item is clicked if the item
         * itself did not already handle the event.
         *
         * @param item the menu item that was clicked
         *
         * @return {@code true} if the event was handled, {@code false}
         *         otherwise
         */
        @SuppressWarnings("UnusedReturnValue")
        boolean onMenuItemClick(@NonNull MenuItem item);
    }

    private interface MenuCallback {

        void update(@NonNull String title);

        void dismiss();
    }

    private static class VBLite {

        @NonNull
        final RecyclerView itemList;
        @NonNull
        final TextView message;
        @NonNull
        final TextView title;
        @NonNull
        private final View rootView;

        VBLite(@NonNull final View rootView) {
            this.rootView = rootView;
            itemList = Objects.requireNonNull(rootView.findViewById(R.id.item_list));
            message = Objects.requireNonNull(rootView.findViewById(R.id.message));
            title = Objects.requireNonNull(rootView.findViewById(R.id.title));
        }
    }

    /**
     * Row ViewHolder for {@link MenuItemListAdapter}.
     */
    private static class Holder
            extends RecyclerView.ViewHolder {

        @Nullable
        final TextView textView;

        Holder(final int viewType,
               @NonNull final View itemView) {
            super(itemView);

            if (viewType == R.layout.row_simple_list_item) {
                textView = itemView.findViewById(R.id.menu_item);
            } else {
                textView = null;
            }
        }
    }

    private static class MenuItemListAdapter
            extends RecyclerView.Adapter<Holder> {

        @NonNull
        private final Drawable subMenuPointer;
        @NonNull
        private final List<MenuItem> list = new ArrayList<>();
        /** Cached inflater. */
        @NonNull
        private final LayoutInflater inflater;

        private final boolean groupDividerEnabled;
        @NonNull
        private final MenuCallback menuCallback;
        /** Listener for the result. */
        @NonNull
        private final OnMenuItemClickListener menuItemClickListener;

        /**
         * Constructor.
         *
         * @param context Current context
         * @param menu    Menu (list of items) to display
         */
        @SuppressLint("UseCompatLoadingForDrawables")
        MenuItemListAdapter(@NonNull final Context context,
                            @NonNull final Menu menu,
                            final boolean groupDividerEnabled,
                            @NonNull final MenuCallback menuCallback,
                            @NonNull final OnMenuItemClickListener listener) {

            inflater = LayoutInflater.from(context);
            this.groupDividerEnabled = groupDividerEnabled;
            this.menuCallback = menuCallback;
            menuItemClickListener = listener;

            //noinspection ConstantConditions
            subMenuPointer = context.getDrawable(R.drawable.ic_baseline_arrow_right_24);

            setMenu(menu);
        }

        /**
         * Add all menu items to the adapter list.
         * Invisible items are <strong>not added</strong>,
         * disabled items are added and will be shown disabled.
         *
         * @param menu to add.
         */
        private void setMenu(@NonNull final Menu menu) {
            list.clear();
            int previousGroupId = menu.size() > 0 ? menu.getItem(0).getGroupId() : 0;

            for (int i = 0; i < menu.size(); i++) {
                final MenuItem item = menu.getItem(i);
                final int groupId = item.getGroupId();
                if (item.isVisible()) {
                    if (groupDividerEnabled && groupId != previousGroupId) {
                        previousGroupId = groupId;
                        // this is silly... but the only way we can create a MenuItem directly
                        final MenuItem divider = new PopupMenu(inflater.getContext(), null)
                                .getMenu()
                                .add(Menu.NONE, R.id.MENU_DIVIDER, item.getOrder(), "")
                                .setEnabled(false);
                        list.add(divider);
                    }
                    list.add(item);
                }
            }
        }

        @Override
        public int getItemViewType(final int position) {
            if (list.get(position).getItemId() == R.id.MENU_DIVIDER) {
                return R.layout.row_simple_list_divider;
            } else {
                return R.layout.row_simple_list_item;
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final View root = inflater.inflate(viewType, parent, false);

            final Holder holder = new Holder(viewType, root);
            if (holder.textView != null) {
                holder.textView.setOnClickListener(v -> onItemClicked(holder));
            }
            return holder;
        }

        @SuppressLint("NotifyDataSetChanged")
        void onItemClicked(@NonNull final Holder holder) {
            final MenuItem item = list.get(holder.getBindingAdapterPosition());
            if (item.isEnabled()) {
                if (item.hasSubMenu()) {
                    //noinspection ConstantConditions
                    setMenu(item.getSubMenu());
                    notifyDataSetChanged();
                    //noinspection ConstantConditions
                    menuCallback.update(item.getTitle().toString());

                } else {
                    menuCallback.dismiss();
                    menuItemClickListener.onMenuItemClick(item);
                }
            }
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            if (holder.textView != null) {
                final MenuItem item = list.get(position);
                holder.textView.setEnabled(item.isEnabled());

                holder.textView.setText(item.getTitle());

                // add a little arrow to indicate sub-menus.
                if (item.hasSubMenu()) {
                    holder.textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            item.getIcon(), null, subMenuPointer, null);
                } else {
                    holder.textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            item.getIcon(), null, null, null);
                }
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }
    }
}
