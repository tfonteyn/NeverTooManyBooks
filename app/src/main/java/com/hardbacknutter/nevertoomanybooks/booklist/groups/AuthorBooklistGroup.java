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
package com.hardbacknutter.nevertoomanybooks.booklist.groups;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PBitmask;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.database.DAOSql;
import com.hardbacknutter.nevertoomanybooks.database.definitions.VirtualDomain;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_AUTHOR_FORMATTED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_AUTHOR_SORT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;

/**
 * Specialized BooklistGroup representing an {@link Author} group.
 * Includes extra attributes based on preferences.
 * <p>
 * {@link #getDisplayDomain()} returns a customized display domain
 * {@link #getGroupDomains} adds the group/sorted domain based on the OB column.
 */
public class AuthorBooklistGroup
        extends BooklistGroup {

    /** {@link Parcelable}. */
    public static final Creator<AuthorBooklistGroup> CREATOR =
            new Creator<AuthorBooklistGroup>() {
                @Override
                public AuthorBooklistGroup createFromParcel(@NonNull final Parcel source) {
                    return new AuthorBooklistGroup(source);
                }

                @Override
                public AuthorBooklistGroup[] newArray(final int size) {
                    return new AuthorBooklistGroup[size];
                }
            };

    /** Style - PreferenceScreen/PreferenceCategory Key. */
    private static final String PSK_STYLE_AUTHOR = "psk_style_author";

    private static final String PK_PRIMARY_TYPE =
            "style.booklist.group.authors.primary.type";
    private static final String PK_SHOW_BOOKS_UNDER_EACH =
            "style.booklist.group.authors.show.all";

    /** Customized domain with display data. */
    @NonNull
    private final VirtualDomain mDisplayDomain;
    /** Customized domain with sorted data. */
    @NonNull
    private final VirtualDomain mSortedDomain;
    /** We cannot parcel the style here, so keep a local copy of this preference. */
    private final boolean mShowAuthorWithGivenNameFirst;
    /** We cannot parcel the style here, so keep a local copy of this preference. */
    private final boolean mSortAuthorByGivenNameFirst;

    /** Show a book under each {@link Author} it is linked to. */
    private PBoolean mUnderEach;
    /** The primary author type the user prefers. */
    private PBitmask mPrimaryType;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param style   the style
     */
    AuthorBooklistGroup(@NonNull final Context context,
                        @NonNull final ListStyle style) {
        super(AUTHOR, style);

        mShowAuthorWithGivenNameFirst = style.isShowAuthorByGivenName(context);
        mSortAuthorByGivenNameFirst = style.isSortAuthorByGivenName(context);
        mDisplayDomain = createDisplayDomain();
        mSortedDomain = createSortDomain();

        initPrefs();
    }

    /**
     * Copy constructor.
     *
     * @param context Current context
     * @param group   to copy from
     */
    AuthorBooklistGroup(@NonNull final Context context,
                        @NonNull final ListStyle style,
                        @NonNull final AuthorBooklistGroup group) {
        super(style, group);
        mShowAuthorWithGivenNameFirst = style.isShowAuthorByGivenName(context);
        mSortAuthorByGivenNameFirst = style.isSortAuthorByGivenName(context);

        mDisplayDomain = createDisplayDomain();
        mSortedDomain = createSortDomain();

        mUnderEach = new PBoolean(mStyle, group.mUnderEach);
        mPrimaryType = new PBitmask(mStyle, group.mPrimaryType);
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private AuthorBooklistGroup(@NonNull final Parcel in) {
        super(in);
        mShowAuthorWithGivenNameFirst = in.readByte() != 0;
        mSortAuthorByGivenNameFirst = in.readByte() != 0;

        mDisplayDomain = createDisplayDomain();
        mSortedDomain = createSortDomain();

        initPrefs();
        mUnderEach.set(in);
    }


    /**
     * Get the global default for this preference.
     *
     * @param context Current context
     *
     * @return {@code true} if we want to show a book under each of its Authors.
     */
    public static boolean showBooksUnderEachDefault(@NonNull final Context context) {
        return PreferenceManager
                .getDefaultSharedPreferences(context)
                .getBoolean(PK_SHOW_BOOKS_UNDER_EACH, false);
    }

    /**
     * Get the global default for this preference.
     *
     * @param context Current context
     *
     * @return the type of author we consider the primary author
     */
    public static int getPrimaryTypeGlobalDefault(@NonNull final Context context) {
        return PreferenceManager
                .getDefaultSharedPreferences(context)
                .getInt(PK_PRIMARY_TYPE, Author.TYPE_UNKNOWN);
    }

    @NonNull
    private VirtualDomain createDisplayDomain() {
        // Not sorted; sort as defined in #createSortDomain
        return new VirtualDomain(DOM_AUTHOR_FORMATTED, DAOSql.SqlColumns
                .getDisplayAuthor(TBL_AUTHORS.getAlias(), mShowAuthorWithGivenNameFirst));
    }

    @NonNull
    private VirtualDomain createSortDomain() {
        // Sorting depends on user preference
        return new VirtualDomain(DOM_BL_AUTHOR_SORT,
                                 DAOSql.SqlColumns.getSortAuthor(mSortAuthorByGivenNameFirst),
                                 VirtualDomain.SORT_ASC);
    }

    @NonNull
    @Override
    public VirtualDomain getDisplayDomain() {
        return mDisplayDomain;
    }

    @NonNull
    @Override
    public ArrayList<VirtualDomain> getGroupDomains() {
        // We need to inject the mSortedDomain as first in the list.
        final ArrayList<VirtualDomain> list = new ArrayList<>();
        list.add(0, mSortedDomain);
        list.addAll(super.getGroupDomains());
        return list;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        super.writeToParcel(dest, flags);
        dest.writeByte((byte) (mShowAuthorWithGivenNameFirst ? 1 : 0));
        dest.writeByte((byte) (mSortAuthorByGivenNameFirst ? 1 : 0));

        mUnderEach.writeToParcel(dest);
    }

    private void initPrefs() {
        mUnderEach = new PBoolean(mStyle, PK_SHOW_BOOKS_UNDER_EACH);

        mPrimaryType = new PBitmask(mStyle, PK_PRIMARY_TYPE,
                                    Author.TYPE_UNKNOWN, Author.TYPE_BITMASK_ALL);
    }

    @NonNull
    @Override
    @CallSuper
    public Map<String, PPref> getPreferences() {
        final Map<String, PPref> map = super.getPreferences();
        map.put(mUnderEach.getKey(), mUnderEach);
        map.put(mPrimaryType.getKey(), mPrimaryType);
        return map;
    }

    @Override
    public void setPreferencesVisible(@NonNull final PreferenceScreen screen,
                                      final boolean visible) {

        final PreferenceCategory category = screen.findPreference(PSK_STYLE_AUTHOR);
        if (category != null) {
            setPreferenceVisibility(category,
                                    new String[]{PK_SHOW_BOOKS_UNDER_EACH, PK_PRIMARY_TYPE},
                                    visible);
        }
    }

    /**
     * Get this preference.
     *
     * @param context Current context
     *
     * @return {@code true} if we want to show a book under each of its Authors.
     */
    public boolean showBooksUnderEach(@NonNull final Context context) {
        return mUnderEach.isTrue(context);
    }

    /**
     * Get this preference.
     *
     * @param context Current context
     *
     * @return the type of author we consider the primary author
     */
    @Author.Type
    public int getPrimaryType(@NonNull final Context context) {
        return mPrimaryType.getValue(context);
    }

    @Override
    @NonNull
    public String toString() {
        return "AuthorBooklistGroup{"
               + super.toString()
               + ", mDisplayDomain=" + mDisplayDomain
               + ", mSortedDomain=" + mSortedDomain
               + ", mShowAuthorWithGivenNameFirst=" + mShowAuthorWithGivenNameFirst
               + ", mSortAuthorByGivenNameFirst=" + mSortAuthorByGivenNameFirst
               + ", mUnderEach=" + mUnderEach
               + ", mPrimaryType=" + mPrimaryType
               + '}';
    }
}
