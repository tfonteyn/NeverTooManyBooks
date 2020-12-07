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

import java.util.HashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.booklist.groups.Groups;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PBitmask;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PInteger;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PPref;
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
    private static final String TAG = "UserStyle";
    /**
     * A parcelled ListStyle. This should only be used during the EDITING of a style.
     * <p>
     * <br>type: {@link UserStyle}
     */
    public static final String BKEY_STYLE = TAG + ":style";
    /**
     * Styles related data was modified (or not).
     * This includes a ListStyle being modified or deleted,
     * or the order of the preferred styles modified,
     * or the selected ListStyle changed,
     * or ...
     * ENHANCE: make this fine grained and reduce unneeded rebuilds
     * <p>
     * <br>type: {@code boolean}
     * setResult
     */
    public static final String BKEY_STYLE_MODIFIED = TAG + ":modified";

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
        mStyleSharedPreferences.setString(PK_STYLE_NAME, name);
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

    private UserStyle(@NonNull final Parcel in) {
        super(in);
    }

    /**
     * Copy constructor. Used for cloning.
     * <p>
     * The id and uuid are passed in to allow testing,
     * see {@link #clone(Context)}.
     *
     * @param context Current context
     * @param style   to clone
     * @param id      for the new style
     * @param uuid    for the new style
     */
    public UserStyle(@NonNull final Context context,
                     @NonNull final BooklistStyle style,
                     final long id,
                     @NonNull final String uuid) {
        super(context, uuid);
        mId = id;

        // Store the new name.
        mStyleSharedPreferences.setString(PK_STYLE_NAME, style.getLabel(context));

        // continue with basic settings
        mIsPreferred = style.isPreferred();
        mMenuPosition = style.getMenuPosition();

        // We have a valid uuid and have read the basic (database stored) settings.
        // Now clone the preferences
        mShowAuthorByGivenName = new PBoolean(this, style.mShowAuthorByGivenName);
        mSortAuthorByGivenName = new PBoolean(this, style.mSortAuthorByGivenName);

        mExpansionLevel = new PInteger(this, style.mExpansionLevel);
        mShowHeaderInfo = new PBitmask(this, style.mShowHeaderInfo);
        mGroupsUseListPreferredHeight = new PBoolean(this, style.mGroupsUseListPreferredHeight);

        mTextScale = new TextScale(this, style.mTextScale);

        mListScreenBookFields = new ListScreenBookFields(this, style.mListScreenBookFields);

        mDetailScreenBookFields = new DetailScreenBookFields(this, style.mDetailScreenBookFields);

        mGroups = new Groups(context, this, style.mGroups);
        mFilters = new Filters(this, style.mFilters);
    }

    public void setName(@NonNull final String name) {
        mStyleSharedPreferences.setString(PK_STYLE_NAME, name);
    }

    @Override
    public boolean isUserDefined() {
        return true;
    }

    @Override
    @NonNull
    public String getLabel(@NonNull final Context context) {
        //noinspection ConstantConditions
        return mStyleSharedPreferences.getString(PK_STYLE_NAME);
    }

    /**
     * Get all of the preferences of this Style and its groups/filters.
     *
     * @return unordered map with all preferences for this style
     */
    @NonNull
    public Map<String, PPref> getPreferences() {
        final Map<String, PPref> tmpMap = new HashMap<>();

        tmpMap.put(mExpansionLevel.getKey(), mExpansionLevel);
        tmpMap.put(mGroupsUseListPreferredHeight.getKey(), mGroupsUseListPreferredHeight);
        tmpMap.put(mShowHeaderInfo.getKey(), mShowHeaderInfo);

        tmpMap.put(mShowAuthorByGivenName.getKey(), mShowAuthorByGivenName);
        tmpMap.put(mSortAuthorByGivenName.getKey(), mSortAuthorByGivenName);

        mTextScale.addToMap(tmpMap);

        mListScreenBookFields.addToMap(tmpMap);
        mDetailScreenBookFields.addToMap(tmpMap);

        tmpMap.put(mGroups.getKey(), mGroups);
        mGroups.addToMap(tmpMap);
        mFilters.addToMap(tmpMap);

        return tmpMap;
    }
}
