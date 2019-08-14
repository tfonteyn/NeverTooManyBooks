/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
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
     * Constructor.
     * <p>
     * The caller can create a menu calling {@link #createMenu(Context)},
     * populate it and pass it here.
     *
     * @param inflater   LayoutInflater to use
     * @param title      for the dialog/menu
     * @param menu       the menu options to show
     * @param userObject a reference free to set/use by the caller
     * @param listener   callback handler with the MenuItem the user chooses + the position
     */
    public MenuPicker(@NonNull final LayoutInflater inflater,
                      @Nullable final String title,
                      @NonNull final Menu menu,
                      @NonNull final T userObject,
                      @NonNull final ContextItemSelected<T> listener) {
        super(inflater, title, null, false);

        mAdapter = new MenuItemListAdapter(inflater, menu, menuItem -> {
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
                                      @NonNull T userObject);
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
         * @param inflater LayoutInflater to use
         * @param menu     Menu (list of items) to display
         * @param listener Callback handler
         */
        MenuItemListAdapter(@NonNull final LayoutInflater inflater,
                            @NonNull final Menu menu,
                            @NonNull final PickListener<MenuItem> listener) {

            mInflater = inflater;
            mListener = listener;
            setMenu(menu);

            //noinspection ConstantConditions
            mSubMenuPointer = inflater.getContext().getDrawable(R.drawable.ic_submenu);
        }

        void setMenu(@NonNull final Menu menu) {
            mList.clear();
            for (int i = 0; i < menu.size(); i++) {
                mList.add(menu.getItem(i));
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
                holder.textView.setCompoundDrawablesWithIntrinsicBounds(
                        item.getIcon(), null, mSubMenuPointer, null);
            } else {
                holder.textView.setCompoundDrawablesWithIntrinsicBounds(
                        item.getIcon(), null, null, null);
            }

            // onClick on the whole view.
            holder.itemView.setOnClickListener(v -> mListener.onPicked(item));
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
