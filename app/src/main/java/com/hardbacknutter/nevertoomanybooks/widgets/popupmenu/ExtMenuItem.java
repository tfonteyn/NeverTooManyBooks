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
import android.os.Parcel;
import android.os.Parcelable;
import android.view.MenuItem;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.R;

public class ExtMenuItem
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<ExtMenuItem> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public ExtMenuItem createFromParcel(@NonNull final Parcel in) {
            return new ExtMenuItem(in);
        }

        @Override
        @NonNull
        public ExtMenuItem[] newArray(final int size) {
            return new ExtMenuItem[size];
        }
    };

    @IdRes
    private int groupId;
    @IdRes
    private int id;

    @Nullable
    private Drawable icon;

    private int orderInCategory;
    @Nullable
    private String title;
    private boolean visible = true;
    private boolean enabled = true;
    @Nullable
    private ExtMenu subMenu;

    /**
     * Constructor.
     */
    public ExtMenuItem() {
    }

    private ExtMenuItem(@NonNull final Parcel in) {
        //URGENT: the icon??
        groupId = in.readInt();
        id = in.readInt();
        orderInCategory = in.readInt();
        title = in.readString();
        visible = in.readByte() != 0;
        enabled = in.readByte() != 0;
        subMenu = in.readParcelable(getClass().getClassLoader());
    }

    /**
     * Convert a {@link MenuItem}.
     *
     * @param menuItem            to convert
     * @param groupDividerEnabled flag
     *
     * @return new list
     */
    @NonNull
    public static ExtMenuItem convert(@NonNull final MenuItem menuItem,
                                      final boolean groupDividerEnabled) {
        final CharSequence tmpTitle = menuItem.getTitle();
        final String title = tmpTitle != null ? tmpTitle.toString() : "";

        final ExtMenuItem item = new ExtMenuItem();
        item.setGroup(menuItem.getGroupId())
            .setId(menuItem.getItemId())
            .setOrderInCategory(menuItem.getOrder())
            .setTitle(title)
            .setVisible(menuItem.isVisible())
            .setEnabled(menuItem.isEnabled());

        item.setIcon(menuItem.getIcon());

        if (menuItem.hasSubMenu()) {
            final ExtMenu subMenu = new ExtMenu();
            //noinspection DataFlowIssue
            subMenu.addAll(ExtMenu.convert(menuItem.getSubMenu(), groupDividerEnabled));
        }

        return item;
    }

    @NonNull
    public static ExtMenuItem createDivider(final int order) {
        return new ExtMenuItem()
                .setId(R.id.MENU_DIVIDER)
                .setOrderInCategory(order)
                .setTitle("")
                .setEnabled(false);
    }

    /**
     * Check if this item is a divider.
     *
     * @return {@code true} if it is
     */
    public boolean isDivider() {
        return id == R.id.MENU_DIVIDER;
    }

    @IdRes
    public int getGroup() {
        return groupId;
    }

    @NonNull
    public ExtMenuItem setGroup(@IdRes final int groupId) {
        this.groupId = groupId;
        return this;
    }

    @IdRes
    public int getItemId() {
        return id;
    }

    @NonNull
    public ExtMenuItem setId(@IdRes final int id) {
        this.id = id;
        return this;
    }


    @SuppressLint("UseCompatLoadingForDrawables")
    @NonNull
    public ExtMenuItem setIcon(@NonNull final Context context,
                               @DrawableRes final int iconResId) {
        if (iconResId != 0) {
            this.icon = context.getResources().getDrawable(iconResId, context.getTheme());
        } else {
            this.icon = null;
        }
        return this;
    }

    @Nullable
    public Drawable getIcon() {
        return icon;
    }

    public void setIcon(@Nullable final Drawable icon) {
        this.icon = icon;
    }

    public int getOrder() {
        return orderInCategory;
    }

    @NonNull
    public ExtMenuItem setOrderInCategory(final int orderInCategory) {
        this.orderInCategory = orderInCategory;
        return this;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    @NonNull
    public ExtMenuItem setTitle(@Nullable final String title) {
        this.title = title;
        return this;
    }

    public boolean isVisible() {
        return visible;
    }

    @NonNull
    public ExtMenuItem setVisible(final boolean visible) {
        this.visible = visible;
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @NonNull
    public ExtMenuItem setEnabled(final boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public boolean hasSubMenu() {
        return subMenu != null;
    }

    @Nullable
    public ExtMenu getSubMenu() {
        return subMenu;
    }

    @NonNull
    public ExtMenuItem setSubMenu(@Nullable final ExtMenu subMenu) {
        this.subMenu = subMenu;
        return this;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        //URGENT: the icon??
        dest.writeInt(groupId);
        dest.writeInt(id);
        dest.writeInt(orderInCategory);
        dest.writeString(title);
        dest.writeByte((byte) (visible ? 1 : 0));
        dest.writeByte((byte) (enabled ? 1 : 0));
        dest.writeParcelable(subMenu, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
