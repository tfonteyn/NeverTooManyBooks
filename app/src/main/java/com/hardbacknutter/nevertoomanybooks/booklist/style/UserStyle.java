/*
 * @Copyright 2018-2021 HardBackNutter
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

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.booklist.style.filters.Filters;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.Groups;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PBitmask;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PInteger;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

/**
 * Except for the clone constructor (which is 'protected') all constructors are private.
 * Use the factory methods instead for clarity.
 */
public class UserStyle
        extends BooklistStyle {

    /** Style display name. */
    public static final String PK_STYLE_NAME = "style.booklist.name";

    /**
     * Constructor for <strong>Global defaults</strong>.
     *
     * @param context Current context
     */
    private UserStyle(@NonNull final Context context) {
        // empty uuid indicates global
        super(context, "", true);

        // negative == builtin; MIN_VALUE because why not....
        mId = Integer.MIN_VALUE;

        initPrefs(context, true);
    }

    /**
     * Constructor for <strong>importing</strong> styles.
     *
     * @param context Current context
     * @param uuid    UUID of the style
     *
     * @see com.hardbacknutter.nevertoomanybooks.backup.json.coders.ListStyleCoder
     */
    private UserStyle(@NonNull final Context context,
                      @NonNull final String uuid) {
        super(context, uuid, true);
        mId = 0;

        initPrefs(context, true);
    }

    /**
     * Constructor for styles <strong>loaded from database</strong>.
     *
     * @param context    Current context
     * @param dataHolder with data
     */
    private UserStyle(@NonNull final Context context,
                      @NonNull final DataHolder dataHolder) {
        super(context, dataHolder.getString(DBDefinitions.KEY_STYLE_UUID), true);

        mId = dataHolder.getLong(DBDefinitions.KEY_PK_ID);
        mIsPreferred = dataHolder.getBoolean(DBDefinitions.KEY_STYLE_IS_PREFERRED);
        mMenuPosition = dataHolder.getInt(DBDefinitions.KEY_STYLE_MENU_POSITION);

        initPrefs(context, true);
    }

    /**
     * Copy constructor. Used for cloning.
     * <p>
     * The id and uuid are passed in to allow testing,
     * see {@link #clone(Context)}.
     *
     * @param context      Current context
     * @param style        to clone
     * @param id           for the new style
     * @param uuid         for the new style
     * @param isPersistent Should always be {@code true},
     *                     but for testing {@code false} can be passed in.
     */
    protected UserStyle(@NonNull final Context context,
                        @NonNull final BooklistStyle style,
                        final long id,
                        @NonNull final String uuid,
                        final boolean isPersistent) {
        super(context, uuid, isPersistent);
        mId = id;

        // Store the new name.
        mPersistenceLayer.setString(PK_STYLE_NAME, style.getLabel(context));

        // copy the basic settings (i.e. non-preferences)
        mIsPreferred = style.isPreferred();
        mMenuPosition = style.getMenuPosition();

        // clone the preferences
        mShowAuthorByGivenName = new PBoolean(isPersistent, mPersistenceLayer,
                                              style.mShowAuthorByGivenName);
        mSortAuthorByGivenName = new PBoolean(isPersistent, mPersistenceLayer,
                                              style.mSortAuthorByGivenName);

        mExpansionLevel = new PInteger(isPersistent, mPersistenceLayer, style.mExpansionLevel);
        mShowHeaderInfo = new PBitmask(isPersistent, mPersistenceLayer, style.mShowHeaderInfo);
        mGroupRowPreferredHeight = new PBoolean(isPersistent, mPersistenceLayer,
                                                style.mGroupRowPreferredHeight);

        mTextScale = new TextScale(isPersistent, mPersistenceLayer, style.mTextScale);

        mListScreenBookFields = new ListScreenBookFields(isPersistent, mPersistenceLayer,
                                                         style.mListScreenBookFields);

        mDetailScreenBookFields = new DetailScreenBookFields(isPersistent, mPersistenceLayer,
                                                             style.mDetailScreenBookFields);

        mFilters = new Filters(isPersistent, mPersistenceLayer, style.mFilters);

        mGroups = new Groups(isPersistent, this, style.mGroups);
    }

    public static UserStyle createGlobal(@NonNull final Context context) {
        return new UserStyle(context);
    }

    static UserStyle createFromDatabase(@NonNull final Context context,
                                        @NonNull final DataHolder dataHolder) {
        return new UserStyle(context, dataHolder);
    }

    public static UserStyle createFromImport(@NonNull final Context context,
                                             @NonNull final String uuid) {
        return new UserStyle(context, uuid);
    }

    public void setName(@NonNull final String name) {
        mPersistenceLayer.setString(PK_STYLE_NAME, name);
    }

    @NonNull
    public String getName() {
        //noinspection ConstantConditions
        return mPersistenceLayer.getNonGlobalString(PK_STYLE_NAME);
    }

    @Override
    @NonNull
    public String getLabel(@NonNull final Context context) {
        return getName();
    }
}
