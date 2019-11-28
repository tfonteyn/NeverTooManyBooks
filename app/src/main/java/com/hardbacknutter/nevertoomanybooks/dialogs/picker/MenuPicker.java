/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.dialogs.picker;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Show context menu on a view.
 *
 * @param <T> type of the actual Object that is represented by a row in the selection list.
 */
public class MenuPicker<T>
        extends ValuePicker {

    private MenuItemListAdapter mAdapter;

    /**
     * Convenience Constructor.
     * <p>
     * The caller can create a menu calling {@link #createMenu(Context)},
     * populate it and pass it here.
     *
     * @param context    Current context
     * @param title      (optional) for the dialog/menu
     * @param menu       the menu options to show
     * @param userObject (optional) a reference free to set/use by the caller
     * @param listener   callback handler with the MenuItem the user chooses + the position
     */
    public MenuPicker(@NonNull final Context context,
                      @Nullable final CharSequence title,
                      @NonNull final Menu menu,
                      @Nullable final T userObject,
                      @NonNull final ContextItemSelected<T> listener) {
        this(context, title, null, menu, userObject, listener);
    }

    /**
     * Full Constructor with actual strings.
     * <p>
     * The caller can create a menu calling {@link #createMenu(Context)},
     * populate it and pass it here.
     * @param context          Current context
     * @param title            (optional) for the dialog/menu
     * @param message          (optional) message to display above the menu
     * @param menu             the menu options to show
     * @param userObject       (optional) a reference free to set/use by the caller
     * @param listener         callback handler with the MenuItem the user chooses + the position
     */
    private MenuPicker(@NonNull final Context context,
                       @Nullable final CharSequence title,
                       @Nullable final CharSequence message,
                       @NonNull final Menu menu,
                       @Nullable final T userObject,
                       @NonNull final ContextItemSelected<T> listener) {
        super(context, title, message, false);

        mAdapter = new MenuItemListAdapter(context, menu, menuItem -> {
            if (menuItem.hasSubMenu()) {
                setTitle(menuItem.getTitle());
                mAdapter.setMenu(menuItem.getSubMenu());
            } else {
                dismiss();
                listener.onContextItemSelected(menuItem, userObject);
            }
        });

        setAdapter(mAdapter, 0);
    }

    public static Menu createMenu(@NonNull final Context context) {
        // legal trick to get an instance of Menu.
        return new PopupMenu(context, null).getMenu();
    }

    public interface ContextItemSelected<T> {

        /**
         * @param menuItem   that was selected
         * @param userObject that the caller passed in when creating the context menu
         *
         * @return {@code true} if handled.
         */
        @SuppressWarnings("UnusedReturnValue")
        boolean onContextItemSelected(@NonNull MenuItem menuItem,
                                      @Nullable T userObject);
    }

    private static class MenuItemListAdapter
            extends RecyclerView.Adapter<Holder> {

        @NonNull
        private final Drawable mSubMenuPointer;
        @NonNull
        private final List<MenuItem> mList = new ArrayList<>();
        @NonNull
        private final LayoutInflater mInflater;

        @NonNull
        private final PickListener<MenuItem> mListener;

        /**
         * @param context  Current context
         * @param menu     Menu (list of items) to display
         * @param listener Callback handler
         */
        MenuItemListAdapter(@NonNull final Context context,
                            @NonNull final Menu menu,
                            @NonNull final PickListener<MenuItem> listener) {

            mInflater = LayoutInflater.from(context);
            mListener = listener;
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
                MenuItem item = menu.getItem(i);
                if (item.isVisible()) {
                    mList.add(item);
                }
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            View root = mInflater.inflate(R.layout.row_simple_dialog_list_item, parent, false);
            return new Holder(root);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            MenuItem item = mList.get(position);
            holder.textView.setText(item.getTitle());

            // add a little arrow to indicate sub-menus.
            if (item.hasSubMenu()) {
                holder.textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        item.getIcon(), null, mSubMenuPointer, null);
            } else {
                holder.textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        item.getIcon(), null, null, null);
            }

            holder.textView.setEnabled(item.isEnabled());
            if (item.isEnabled()) {
                holder.textView.setOnClickListener(v -> mListener.onPicked(item));
            }
        }

        @Override
        public int getItemCount() {
            return mList.size();
        }
    }

    static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        final TextView textView;

        Holder(@NonNull final View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.menu_item);
        }
    }
}
