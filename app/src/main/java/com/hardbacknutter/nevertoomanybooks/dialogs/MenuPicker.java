/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Show context menu on a view.
 * <p>
 * Note this is <strong>NOT</strong> a DialogFrame and will not survive screen rotations.
 * It's easy enough to transform it into one, see {@link MenuPickerDialogFragment},
 * but when using a menu inflater, it's impossible to show icons.
 * Menu building can of course be handled fully in code but the trade-off can be huge.
 */
public class MenuPicker {

    @NonNull
    private final AlertDialog mDialog;

    /** Cached position of the item in the list this menu was invoked on. */
    private final int mPosition;
    /** Listener for the result. */
    @NonNull
    private final MenuPickListener mListener;

    /**
     * Constructor.
     * <p>
     * The caller should create a menu by calling {@link #createMenu(Context)},
     * populate it and pass it here.
     *
     * @param context  Current context
     * @param title    (optional) for the dialog/menu
     * @param message  (optional) for the dialog/menu
     * @param menu     the menu options to show
     * @param position of the item in a list where the context menu was initiated
     * @param listener callback handler with the MenuItem the user chooses + the position
     */
    public MenuPicker(@NonNull final Context context,
                      @Nullable final String title,
                      @Nullable final String message,
                      @NonNull final Menu menu,
                      final int position,
                      @NonNull final MenuPickListener listener) {

        mPosition = position;
        mListener = listener;

        final View view = LayoutInflater.from(context).inflate(R.layout.dialog_popupmenu, null);

        // optional title
        final TextView titleView = view.findViewById(R.id.alertTitle);
        if (title != null && !title.isEmpty()) {
            titleView.setVisibility(View.VISIBLE);
            titleView.setText(title);
        } else {
            titleView.setVisibility(View.GONE);
        }

        // optional message
        final TextView messageView = view.findViewById(R.id.alertMessage);
        if (message != null && !message.isEmpty()) {
            messageView.setVisibility(View.VISIBLE);
            messageView.setText(message);
        } else {
            messageView.setVisibility(View.GONE);
        }

        final MenuItemListAdapter adapter = new MenuItemListAdapter(context, menu);
        final RecyclerView listView = view.findViewById(R.id.item_list);
        listView.setAdapter(adapter);

        mDialog = new MaterialAlertDialogBuilder(context)
                .setView(view)
                .create();
    }

    public static Menu createMenu(@NonNull final Context context) {
        // legal trick to get an instance of Menu.
        // We leave the anchor 'null' as we're not actually going to display this object.
        //noinspection ConstantConditions
        return new PopupMenu(context, null).getMenu();
    }

    public void show() {
        mDialog.show();
    }

    public interface MenuPickListener {

        /**
         * Callback handler.
         *
         * @param menuItemId that was selected
         * @param position   of the item in a list where the context menu was initiated
         *
         * @return {@code true} if handled.
         */
        @SuppressWarnings("UnusedReturnValue")
        boolean onContextItemSelected(@IdRes int menuItemId,
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
        static final int DIVIDER = 0;
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
            mSubMenuPointer = context.getDrawable(R.drawable.ic_submenu);
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
            if (mList.get(position).getItemId() != R.id.MENU_DIVIDER) {
                return MENU_ITEM;
            } else {
                return DIVIDER;
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
                    mDialog.setTitle(item.getTitle());
                    setMenu(item.getSubMenu());
                } else {
                    mDialog.dismiss();
                    mListener.onContextItemSelected(item.getItemId(), mPosition);
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
