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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * NOTE: sub-menus are handled automatically.
 */
class MenuItemListAdapter
        extends RecyclerView.Adapter<MenuItemListAdapter.Holder> {

    @NonNull
    private final List<ExtMenuItem> list = new ArrayList<>();
    /** Cached inflater. */
    @NonNull
    private final LayoutInflater inflater;

    @NonNull
    private final MenuCallback menuCallback;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param menuCallback callback for title change requests and dismiss/item selection
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    MenuItemListAdapter(@NonNull final Context context,
                        @NonNull final MenuCallback menuCallback) {

        inflater = LayoutInflater.from(context);
        this.menuCallback = menuCallback;
    }

    /**
     * Add all menu items to the adapter list.
     * Invisible items are <strong>not added</strong>,
     * disabled items are added and will be shown disabled.
     *
     * @param menu to add.
     */
    public void setMenu(@NonNull final List<ExtMenuItem> menu) {
        list.clear();
        list.addAll(menu);
    }


    @Override
    public int getItemViewType(final int position) {
        if (list.get(position).isDivider()) {
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
    private void onItemClicked(@NonNull final Holder holder) {
        final ExtMenuItem item = list.get(holder.getBindingAdapterPosition());
        if (item.isEnabled()) {
            if (item.hasSubMenu()) {
                //noinspection DataFlowIssue
                final List<ExtMenuItem> subMenuItems = item.getSubMenu().getItems();
                setMenu(subMenuItems);
                notifyDataSetChanged();
                //noinspection DataFlowIssue
                menuCallback.onNewMenuTitle(item.getTitle());

            } else {
                menuCallback.onMenuItemClick(item.getItemId());
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder,
                                 final int position) {
        holder.onBind(list.get(position));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public interface MenuCallback {

        /**
         * The menu was updated/replaced and the title should also be updated.
         *
         * @param title to set
         */
        void onNewMenuTitle(@NonNull CharSequence title);

        /**
         * The user clicked a menu item.
         *
         * @param menuItemId The menu item that was invoked.
         */
        void onMenuItemClick(@IdRes int menuItemId);
    }

    /**
     * Row ViewHolder for {@link MenuItemListAdapter}.
     */
    public static class Holder
            extends RecyclerView.ViewHolder {

        @Nullable
        final TextView textView;

        Holder(final int viewType,
               @NonNull final View itemView) {
            super(itemView);

            if (viewType == R.layout.row_simple_list_item) {
                textView = itemView.findViewById(R.id.menu_item);
            } else {
                // It's a divider
                textView = null;
            }
        }

        @SuppressLint("UseCompatLoadingForDrawables")
        void onBind(@NonNull final ExtMenuItem item) {
            if (textView != null) {
                textView.setEnabled(item.isEnabled());

                textView.setText(item.getTitle());

                final Context context = textView.getContext();
                if (item.hasSubMenu()) {
                    // add a little arrow to indicate sub-menus.
                    textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            item.getIcon(context),
                            null,
                            context.getDrawable(R.drawable.ic_baseline_arrow_right_24),
                            null);
                } else {
                    textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            item.getIcon(context), null, null, null);
                }
            }
        }
    }
}
