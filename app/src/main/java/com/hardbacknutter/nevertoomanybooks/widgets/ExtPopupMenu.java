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
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.PopupMenuBinding;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;

/**
 * Show a context menu on a view - will show icons if present.
 */
public class ExtPopupMenu {

    @NonNull
    private final PopupMenuBinding mVb;

    private final int mPaddingBottom;
    private final int mXOffset;

    @NonNull
    private final PopupWindow mPopupWindow;
    /** Listener for the result. */
    @NonNull
    private final ExtPopupMenuListener mListener;

    /** Cached position of the item in the list this menu was invoked on. */
    private int mPosition;

    /**
     * Constructor.
     * <p>
     * The caller should create a menu by calling {@link #createMenu(Context)},
     * populate it, and pass it here.
     *
     * @param menu     the menu options to show
     * @param listener callback handler
     */
    public ExtPopupMenu(@NonNull final Context context,
                        @NonNull final Menu menu,
                        @NonNull final ExtPopupMenuListener listener) {
        mListener = listener;

        final Resources res = context.getResources();
        mPaddingBottom = res.getDimensionPixelSize(R.dimen.dialogPreferredPaddingBottom);
        mXOffset = res.getDimensionPixelSize(R.dimen.popup_menu_x_offset);

        mVb = PopupMenuBinding.inflate(LayoutInflater.from(context));

        final MenuItemListAdapter adapter = new MenuItemListAdapter(context, menu);
        mVb.itemList.setAdapter(adapter);

        mPopupWindow = new PopupWindow(context);
        mPopupWindow.setFocusable(true);
        mPopupWindow.setContentView(mVb.getRoot());

        // Widget.MaterialComponents.PopupMenu
        mPopupWindow.setBackgroundDrawable(
                AttrUtils.getDrawable(context, R.attr.popupMenuBackground));
        mPopupWindow.setElevation(res.getDimensionPixelSize(R.dimen.popup_menu_elevation));
    }

    public static Menu createMenu(@NonNull final Context context) {
        // legal trick to get an instance of Menu.
        // We leave the anchor 'null' as we're not actually going to display this object.
        return new PopupMenu(context, null).getMenu();
    }

    public ExtPopupMenu setHeader(@Nullable final CharSequence title,
                                  @Nullable final CharSequence message) {
        // optional title
        if (title != null && title.length() > 0) {
            mVb.title.setVisibility(View.VISIBLE);
            mVb.title.setText(title);
        } else {
            mVb.title.setVisibility(View.GONE);
        }

        // optional message
        if (message != null && message.length() > 0) {
            mVb.message.setVisibility(View.VISIBLE);
            mVb.message.setText(message);
        } else {
            mVb.message.setVisibility(View.GONE);
        }

        return this;
    }

    /**
     * Show as a true popup, just below and a bit indented.
     *
     * @param anchor   the view on which to pin the popup window
     * @param position of the item in a list where the context menu was initiated.
     *                 Not used here, but passed back to the listener.
     */
    public void showAsDropDown(@NonNull final View anchor,
                               final int position) {
        mPosition = position;

        // So why are we doing the measuring and setting width/height manually?
        // (androids internals... to remind myself)
        //
        // The PopupWindow is set to LayoutParams.WRAP_CONTENT / LayoutParams.WRAP_CONTENT
        // and this fails to work reliably.
        // Setting it to MATCH works as expected due to the Android *explicitly* checking for it.
        //
        // The real width/height is dynamic due to the RecyclerView.
        // but we need an absolute value for PopupWindow#findDropDownPosition which calls
        // PopupWindow#tryFitVertical + PopupWindow#tryFitHorizontal.
        //
        // The latter try to determine the absolute position of the window versus the anchor.
        // i.e. as requested under the anchor, or if not enough space, above the anchor.
        //
        // PopupWindow lines 1414 is where things start to go wrong.
        // 'p' is initialized to the original width/height -> WRAP_CONTENT
        // instead of the ACTUAL width/height....
        // line 2427 states:
        //         // WRAP_CONTENT case. findDropDownPosition will have resolved this to
        //        // absolute values, but we don't want to update mWidth/mHeight to these
        //        // absolute values.
        // Reality: no it does not... it just uses mWidth/mHeight *AS-IS*. i.e. wrap -> "-2"
        // and so it does its calculations using the absolute value of -2... oops...

        // a more or less accurate way of setting the width/height...
        final int spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        final View contentView = mPopupWindow.getContentView();
        contentView.measure(spec, spec);

        mPopupWindow.setHeight(contentView.getMeasuredHeight() + mPaddingBottom);
        mPopupWindow.setWidth(contentView.getMeasuredWidth());
        // preferred location: halfway on top of the anchor, and indented by mXOffset
        mPopupWindow.showAsDropDown(anchor, mXOffset, -anchor.getHeight() / 2);
    }

    /**
     * Show centered on the screen.
     *
     * @param anchor   the view on which to pin the popup window
     *                 (Actually only used to get the window token)
     * @param position of the item in a list where the context menu was initiated.
     *                 Not used here, but passed back to the listener.
     */
    public void showCentered(@NonNull final View anchor,
                             final int position) {
        mPosition = position;
        mPopupWindow.showAtLocation(anchor, Gravity.CENTER, 0, 0);
    }

    public interface ExtPopupMenuListener {

        /**
         * Callback handler.
         *
         * @param menuItem that was selected
         * @param position of the item in a list where the context menu was initiated
         *
         * @return {@code true} if handled.
         */
        @SuppressWarnings("UnusedReturnValue")
        boolean onMenuItemSelected(@NonNull MenuItem menuItem,
                                   int position);
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

        /**
         * Constructor.
         *
         * @param context Current context
         * @param menu    Menu (list of items) to display
         */
        @SuppressLint("UseCompatLoadingForDrawables")
        MenuItemListAdapter(@NonNull final Context context,
                            @NonNull final Menu menu) {

            mInflater = LayoutInflater.from(context);
            setMenu(menu);

            //noinspection ConstantConditions
            mSubMenuPointer = context.getDrawable(R.drawable.ic_baseline_arrow_right_24);
        }

        /**
         * Add all menu items to the adapter list.
         * Invisible items are <strong>not added</strong>,
         * disabled items are added and will be shown disabled.
         *
         * @param menu to add.
         */
        void setMenu(@NonNull final Menu menu) {
            mList.clear();
            for (int i = 0; i < menu.size(); i++) {
                final MenuItem item = menu.getItem(i);
                if (item.isVisible()) {
                    mList.add(item);
                }
            }
            notifyDataSetChanged();
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

        void onItemClicked(@NonNull final Holder holder) {
            final MenuItem item = mList.get(holder.getBindingAdapterPosition());
            if (item.isEnabled()) {
                if (item.hasSubMenu()) {
                    mVb.title.setText(item.getTitle());
                    mVb.title.setVisibility(View.VISIBLE);
                    setMenu(item.getSubMenu());
                } else {
                    mPopupWindow.dismiss();
                    mListener.onMenuItemSelected(item, mPosition);
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
