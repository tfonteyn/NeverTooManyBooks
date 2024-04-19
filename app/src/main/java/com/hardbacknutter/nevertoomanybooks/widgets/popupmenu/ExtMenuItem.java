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

    public ExtMenuItem() {
    }

    public ExtMenuItem(@NonNull final MenuItem menuItem,
                       final boolean groupDividerEnabled) {
        groupId = menuItem.getGroupId();
        id = menuItem.getItemId();
        icon = menuItem.getIcon();
        orderInCategory = menuItem.getOrder();
        final CharSequence tmpTitle = menuItem.getTitle();
        title = tmpTitle != null ? tmpTitle.toString() : "";
        visible = menuItem.isVisible();
        enabled = menuItem.isEnabled();

        if (menuItem.hasSubMenu()) {
            subMenu = new ExtMenu();
            //noinspection DataFlowIssue
            subMenu.addAll(ExtMenu.convert(menuItem.getSubMenu(), groupDividerEnabled));
        }
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

    /**
     * Interface definition for a callback to be invoked when a menu item is clicked.
     */
    @FunctionalInterface
    public interface OnMenuItemClickListener {
        /**
         * Called when a menu item has been invoked.  This is the first code
         * that is executed; if it returns true, no other callbacks will be
         * executed.
         *
         * @param item The menu item that was invoked.
         *
         * @return Return true to consume this click and prevent others from
         *         executing.
         */
        boolean onMenuItemClick(@NonNull ExtMenuItem item);
    }
}
