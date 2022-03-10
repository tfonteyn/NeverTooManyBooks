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

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;

/**
 * Show a context menu on a view - will show icons if present.
 */
public class ExtPopupMenu {

    @NonNull
    private final VBLite mVb;

    private final int mXOffset;
    private final int mPaddingBottom;

    @NonNull
    private final PopupWindow mPopupWindow;

    @NonNull
    private Menu mMenu;
    private boolean mGroupDividerEnabled;
    private MenuItemListAdapter mAdapter;

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
        mMenu = new PopupMenu(context, null).getMenu();

        final Resources res = context.getResources();
        mPaddingBottom = res.getDimensionPixelSize(R.dimen.dialogPreferredPaddingBottom);
        mXOffset = res.getDimensionPixelSize(R.dimen.popup_menu_x_offset);

        mVb = new VBLite(LayoutInflater.from(context).inflate(R.layout.popup_menu, null, false));

        mPopupWindow = new PopupWindow(context);
        mPopupWindow.setFocusable(true);
        mPopupWindow.setContentView(mVb.rootView);

        mPopupWindow.setBackgroundDrawable(
                AttrUtils.getDrawable(context, R.attr.popupMenuBackground));
        mPopupWindow.setElevation(res.getDimensionPixelSize(R.dimen.popup_menu_elevation));
    }

    /**
     * Inflate a menu resource into this PopupMenu. This is equivalent to
     * calling {@code popupMenu.getMenuInflater().inflate(menuRes, popupMenu.getMenu())}.
     *
     * @param menuRes Menu resource to inflate
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public ExtPopupMenu inflate(@MenuRes final int menuRes) {
        getMenuInflater().inflate(menuRes, mMenu);
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
        mGroupDividerEnabled = true;
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
            mVb.title.setVisibility(View.VISIBLE);
            mVb.title.setText(title);
        } else {
            mVb.title.setVisibility(View.GONE);
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
            mVb.message.setVisibility(View.VISIBLE);
            mVb.message.setText(message);
        } else {
            mVb.message.setVisibility(View.GONE);
        }
        return this;
    }

    /**
     * @return a {@link MenuInflater} that can be used to inflate menu items
     *         from XML into the menu returned by {@link #getMenu()}
     *
     * @see #getMenu()
     */
    public MenuInflater getMenuInflater() {
        return new MenuInflater(mPopupWindow.getContentView().getContext());
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
        return mMenu;
    }

    /**
     * Replace the existing menu with the given one.
     * This method can be called at any time.
     *
     * @param menu to use
     */
    @SuppressLint("NotifyDataSetChanged")
    public void setMenu(@NonNull final Menu menu) {
        mMenu = menu;
        if (mAdapter != null) {
            mAdapter.setMenu(mMenu);
            mAdapter.notifyDataSetChanged();
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
        mPopupWindow.setWidth(wh[0]);
        mPopupWindow.setHeight(wh[1]);
        // preferred location: halfway on top of the anchor, and indented by mXOffset
        mPopupWindow.showAsDropDown(anchor, mXOffset, -anchor.getHeight() / 2);
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
        final View contentView = mPopupWindow.getContentView();
        contentView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        return new int[]{contentView.getMeasuredWidth(),
                         contentView.getMeasuredHeight() + mPaddingBottom};
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
            mPopupWindow.showAtLocation(view, gravity, mXOffset, 0);
        } else if (gravity == Gravity.CENTER) {
            mPopupWindow.showAtLocation(view, Gravity.CENTER, 0, 0);
        } else {
            throw new IllegalArgumentException(String.valueOf(gravity));
        }
    }

    private void initAdapter(@NonNull final Context context,
                             @NonNull final OnMenuItemClickListener listener) {
        mAdapter = new MenuItemListAdapter(context, mMenu, listener);
        mVb.itemList.setAdapter(mAdapter);
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
            itemList = rootView.findViewById(R.id.item_list);
            message = rootView.findViewById(R.id.message);
            title = rootView.findViewById(R.id.title);
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

            if (viewType == MenuItemListAdapter.MENU_ITEM) {
                textView = itemView.findViewById(R.id.menu_item);
            } else {
                textView = null;
            }
        }
    }

    private class MenuItemListAdapter
            extends RecyclerView.Adapter<Holder> {

        /** ViewType. */
        static final int MENU_DIVIDER = 0;
        /** ViewType. */
        static final int MENU_ITEM = 1;

        @NonNull
        private final Drawable mSubMenuPointer;
        @NonNull
        private final List<MenuItem> mList = new ArrayList<>();
        /** Cached inflater. */
        @NonNull
        private final LayoutInflater mInflater;

        /** Listener for the result. */
        private final OnMenuItemClickListener mListener;

        /**
         * Constructor.
         *
         * @param context Current context
         * @param menu    Menu (list of items) to display
         */
        @SuppressLint("UseCompatLoadingForDrawables")
        MenuItemListAdapter(@NonNull final Context context,
                            @NonNull final Menu menu,
                            @NonNull final OnMenuItemClickListener listener) {

            mInflater = LayoutInflater.from(context);
            mListener = listener;

            //noinspection ConstantConditions
            mSubMenuPointer = context.getDrawable(R.drawable.ic_baseline_arrow_right_24);

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
            mList.clear();
            int previousGroupId = menu.size() > 0 ? menu.getItem(0).getGroupId() : 0;

            for (int i = 0; i < menu.size(); i++) {
                final MenuItem item = menu.getItem(i);
                final int groupId = item.getGroupId();
                if (item.isVisible()) {
                    if (mGroupDividerEnabled && groupId != previousGroupId) {
                        previousGroupId = groupId;
                        // this is silly... but the only way we can create a MenuItem directly
                        final MenuItem divider = new PopupMenu(mInflater.getContext(), null)
                                .getMenu()
                                .add(Menu.NONE, R.id.MENU_DIVIDER, item.getOrder(), "")
                                .setEnabled(false);
                        mList.add(divider);
                    }
                    mList.add(item);
                }
            }
        }

        @Override
        public int getItemViewType(final int position) {
            if (mList.get(position).getItemId() == R.id.MENU_DIVIDER) {
                return MENU_DIVIDER;
            } else {
                return MENU_ITEM;
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final View root;
            if (viewType == MENU_ITEM) {
                root = mInflater.inflate(R.layout.row_simple_list_item, parent, false);
            } else {
                root = mInflater.inflate(R.layout.row_simple_list_divider, parent, false);
            }
            final Holder holder = new Holder(viewType, root);
            if (holder.textView != null) {
                holder.textView.setOnClickListener(v -> onItemClicked(holder));
            }
            return holder;
        }

        @SuppressLint("NotifyDataSetChanged")
        void onItemClicked(@NonNull final Holder holder) {
            final MenuItem item = mList.get(holder.getBindingAdapterPosition());
            if (item.isEnabled()) {
                if (item.hasSubMenu()) {
                    mVb.title.setText(item.getTitle());
                    mVb.title.setVisibility(View.VISIBLE);
                    setMenu(item.getSubMenu());
                    notifyDataSetChanged();

                    final int[] wh = calculatePopupWindowWidthAndHeight();
                    mPopupWindow.update(wh[0], wh[1]);

                } else {
                    mPopupWindow.dismiss();
                    mListener.onMenuItemClick(item);
                }
            }
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            if (holder.textView != null) {
                final MenuItem item = mList.get(position);
                holder.textView.setEnabled(item.isEnabled());

                holder.textView.setText(item.getTitle());

                // add a little arrow to indicate sub-menus.
                if (item.hasSubMenu()) {
                    holder.textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            item.getIcon(), null, mSubMenuPointer, null);
                } else {
                    holder.textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            item.getIcon(), null, null, null);
                }
            }
        }

        @Override
        public int getItemCount() {
            return mList.size();
        }
    }
}
