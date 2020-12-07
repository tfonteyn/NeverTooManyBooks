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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

public class UserStyle
        extends BooklistStyle {

    /** {@link Parcelable}. */
    public static final Creator<UserStyle> CREATOR = new Creator<UserStyle>() {
        @Override
        public UserStyle createFromParcel(@NonNull final Parcel source) {
            return new UserStyle(source);
        }

        @Override
        public UserStyle[] newArray(final int size) {
            return new UserStyle[size];
        }
    };
    /** Style display name. */
    public static final String PK_STYLE_NAME = "style.booklist.name";

    /**
     * Constructor for <strong>Global defaults</strong>.
     *
     * @param context Current context
     */
    public UserStyle(@NonNull final Context context) {
        // empty uuid indicates global
        super(context, "");

        // negative == builtin; MIN_VALUE because why not....
        mId = Integer.MIN_VALUE;

        initPrefs(context);
    }

    public UserStyle(@NonNull final Context context,
                     @NonNull final String uuid,
                     @NonNull final String name) {
        super(context, uuid);
        mId = 0;

        initPrefs(context);
        mStyleSettings.setString(PK_STYLE_NAME, name);
    }

    /**
     * Constructor for styles <strong>loaded from database</strong>.
     *
     * @param context    Current context
     * @param dataHolder with data
     */
    UserStyle(@NonNull final Context context,
              @NonNull final DataHolder dataHolder) {
        super(context, dataHolder.getString(DBDefinitions.KEY_UUID));

        mId = dataHolder.getLong(DBDefinitions.KEY_PK_ID);
        mIsPreferred = dataHolder.getBoolean(DBDefinitions.KEY_STYLE_IS_PREFERRED);
        mMenuPosition = dataHolder.getInt(DBDefinitions.KEY_STYLE_MENU_POSITION);

        initPrefs(context);
    }

    /**
     * Clone constructor: create a new object but with the settings from the Parcel.
     * <p>
     * The id and uuid are passed in to allow testing,
     * see {@link #clone(Context)}.
     *
     * @param context Current context
     * @param id      for the new style
     * @param uuid    for the new style
     * @param name    for the new style
     * @param in      Parcel to construct the object from
     */
    UserStyle(@NonNull final Context context,
              final long id,
              @NonNull final String uuid,
              @NonNull final String name,
              @NonNull final Parcel in) {
        super(context, uuid);
        mId = id;

        // skip mUuid
        in.readString();
        // skip mId
        in.readLong();

        // Store the new name.
        mStyleSettings.setString(PK_STYLE_NAME, name);

        // continue with basic settings
        mIsPreferred = in.readByte() != 0;
        mMenuPosition = in.readInt();

        // We have a valid uuid and have read the basic (database stored) settings.
        // Now init the preferences.
        initPrefs(context);
        unparcelPrefs(in);

        // skip dummy mNameResId
        in.readInt();
    }

    private UserStyle(@NonNull final Parcel in) {
        super(in);
        // skip dummy mNameResId
        in.readInt();
    }

    public void setName(@NonNull final String name) {
        mStyleSettings.setString(PK_STYLE_NAME, name);
    }

    @Override
    public boolean isUserDefined() {
        return true;
    }

    @Override
    public boolean isBuiltin() {
        return false;
    }

    @Override
    @NonNull
    public String getLabel(@NonNull final Context context) {
        //noinspection ConstantConditions
        return mStyleSettings.getString(PK_STYLE_NAME);
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        super.writeToParcel(dest, flags);
        // write dummy mNameResId
        dest.writeInt(0);
    }
}
