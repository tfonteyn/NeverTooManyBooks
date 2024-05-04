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

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.PopupMenu;

import androidx.annotation.IdRes;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A lot of the methods in this class are in fact not used.
 * We added them with the idea of "implements Menu".
 * To be revisited some day...
 */
public class ExtMenu
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<ExtMenu> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public ExtMenu createFromParcel(@NonNull final Parcel in) {
            return new ExtMenu(in);
        }

        @Override
        @NonNull
        public ExtMenu[] newArray(final int size) {
            return new ExtMenu[size];
        }
    };

    @NonNull
    private final List<ExtMenuItem> items;

    /**
     * Constructor.
     */
    ExtMenu() {
        items = new ArrayList<>();
    }

    private ExtMenu(@NonNull final Parcel in) {
        //noinspection DataFlowIssue
        items = in.createTypedArrayList(ExtMenuItem.CREATOR);
    }

    /**
     * Convert a {@link Menu} to a list of {@link ExtMenuItem}s.
     *
     * @param menu                to convert
     * @param groupDividerEnabled flag
     *
     * @return new list
     */
    @NonNull
    public static ArrayList<ExtMenuItem> convert(@NonNull final Menu menu,
                                                 final boolean groupDividerEnabled) {
        final ArrayList<ExtMenuItem> list = new ArrayList<>();
        int previousGroupId = menu.size() > 0 ? menu.getItem(0).getGroupId() : 0;

        // We don't have to bother with the 'orderInCategory' as the Menu
        // will have ordered all items at the time of adding them.
        // Hence, menu.getItem(i) will deliver them in the correct order as needed.
        for (int i = 0; i < menu.size(); i++) {
            final MenuItem menuItem = menu.getItem(i);
            final int groupId = menuItem.getGroupId();
            if (menuItem.isVisible()) {
                if (groupDividerEnabled && groupId != previousGroupId) {
                    previousGroupId = groupId;
                    list.add(ExtMenuItem.createDivider(menuItem.getOrder()));
                }
                list.add(ExtMenuItem.convert(menuItem, groupDividerEnabled));
            }
        }
        return list;
    }

    /**
     * Inflate a menu resource into this menu.
     *
     * @param context             Current context
     * @param inflater            to use
     * @param menuResId           Menu resource to inflate
     * @param groupDividerEnabled flag
     */
    public void inflate(@NonNull final Context context,
                        @NonNull final MenuInflater inflater,
                        @MenuRes final int menuResId,
                        final boolean groupDividerEnabled) {
        final Menu tmpMenu = new PopupMenu(context, null).getMenu();
        inflater.inflate(menuResId, tmpMenu);
        items.addAll(convert(tmpMenu, groupDividerEnabled));
    }

    /**
     * Inflate a menu resource into this menu.
     *
     * @param context             Current context
     * @param menuResId           Menu resource to inflate
     * @param groupDividerEnabled flag
     */
    public void inflate(@NonNull final Context context,
                        @MenuRes final int menuResId,
                        final boolean groupDividerEnabled) {
        inflate(context, new MenuInflater(context), menuResId, groupDividerEnabled);
    }

    @NonNull
    public ExtMenu clear() {
        items.clear();
        return this;
    }

    @NonNull
    public ExtMenu addAll(@NonNull final List<ExtMenuItem> list) {
        items.addAll(list);
        return this;
    }

    @NonNull
    public ExtMenu add(@NonNull final ExtMenuItem menuItem) {
        items.add(menuItem);
        return this;
    }

    @NonNull
    public ExtMenu add(@IdRes final int groupId,
                       @IdRes final int itemId,
                       final int order,
                       final String title) {
        add(new ExtMenuItem()
                    .setGroup(groupId)
                    .setId(itemId)
                    .setOrderInCategory(order)
                    .setTitle(title));
        return this;
    }

    @Nullable
    public ExtMenuItem findItem(@IdRes final int id) {
        return items.stream()
                    .filter(menuItem -> menuItem.getItemId() == id)
                    .findAny()
                    .orElse(null);
    }

    public int size() {
        return items.size();
    }

    @Nullable
    public ExtMenuItem getItem(final int i) {
        return items.get(i);
    }

    @NonNull
    public List<ExtMenuItem> getItems() {
        return items;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeTypedList(items);
    }

    @Override
    public int describeContents() {
        return 0;
    }

}
