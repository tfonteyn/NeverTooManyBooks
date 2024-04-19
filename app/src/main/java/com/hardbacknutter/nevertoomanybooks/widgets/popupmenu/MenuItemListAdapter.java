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
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * NOTE: sub-menus are handled automatically.
 */
public class MenuItemListAdapter
        extends RecyclerView.Adapter<MenuItemListAdapter.Holder> {

    @NonNull
    private final Drawable subMenuPointer;
    @NonNull
    private final List<ExtMenuItem> list = new ArrayList<>();
    /** Cached inflater. */
    @NonNull
    private final LayoutInflater inflater;

    private final boolean groupDividerEnabled;
    @NonNull
    private final MenuCallback menuCallback;

    /**
     * Constructor.
     *
     * @param context             Current context
     * @param groupDividerEnabled flag
     * @param menuCallback        callback for title change requests and dismiss/item selection
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    public MenuItemListAdapter(@NonNull final Context context,
                               final boolean groupDividerEnabled,
                               @NonNull final MenuCallback menuCallback) {

        inflater = LayoutInflater.from(context);
        this.groupDividerEnabled = groupDividerEnabled;
        this.menuCallback = menuCallback;

        //noinspection DataFlowIssue
        subMenuPointer = context.getDrawable(R.drawable.ic_baseline_arrow_right_24);
    }

    /**
     * Add all menu items to the adapter list.
     * Invisible items are <strong>not added</strong>,
     * disabled items are added and will be shown disabled.
     *
     * @param menu to add.
     */
    public void setMenu(@NonNull final Menu menu) {
        list.clear();
        int previousGroupId = menu.size() > 0 ? menu.getItem(0).getGroupId() : 0;

        for (int i = 0; i < menu.size(); i++) {
            final MenuItem item = menu.getItem(i);
            final int groupId = item.getGroupId();
            if (item.isVisible()) {
                if (groupDividerEnabled && groupId != previousGroupId) {
                    previousGroupId = groupId;
                    // this is silly... but the only way we can create a MenuItem directly
                    final ExtMenuItem divider = new ExtMenuItem()
                            .setId(R.id.MENU_DIVIDER)
                            .setOrderInCategory(item.getOrder())
                            .setTitle("")
                            .setEnabled(false);
                    list.add(divider);
                }
                list.add(new ExtMenuItem(item, groupDividerEnabled));
            }
        }
    }

    public void setMenu(@NonNull final List<ExtMenuItem> menuList) {
        list.clear();
        list.addAll(menuList);
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
    private void onItemClicked(@NonNull final Holder holder) {
        final ExtMenuItem item = list.get(holder.getBindingAdapterPosition());
        if (item.isEnabled()) {
            if (item.hasSubMenu()) {
                //noinspection DataFlowIssue
                setMenu(item.getSubMenu().getOrderedItems());
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
        if (holder.textView != null) {
            final ExtMenuItem item = list.get(position);
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

    public interface MenuCallback {

        /**
         * The menu was updated/replaced and the title should also be updated.
         *
         * @param title to set
         */
        void onNewMenuTitle(@NonNull CharSequence title);

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
                textView = null;
            }
        }
    }
}
