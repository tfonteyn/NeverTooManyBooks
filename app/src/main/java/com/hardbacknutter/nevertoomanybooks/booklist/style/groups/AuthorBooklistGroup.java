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
package com.hardbacknutter.nevertoomanybooks.booklist.style.groups;

import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PBitmask;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PPref;
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

    /** Show a book under each {@link Author} it is linked to. */
    private PBoolean mUnderEach;
    /** The primary author type the user prefers. */
    private PBitmask mPrimaryType;

    /**
     * Constructor.
     *
     * @param isPersistent flag
     * @param style        Style reference.
     */
    AuthorBooklistGroup(final boolean isPersistent,
                        @NonNull final ListStyle style) {
        super(AUTHOR, isPersistent, style);

        mDisplayDomain = createDisplayDomain();
        mSortedDomain = createSortDomain();

        initPrefs();
    }

    /**
     * Copy constructor.
     *
     * @param isPersistent flag
     * @param style        Style reference.
     * @param group        to copy from
     */
    AuthorBooklistGroup(final boolean isPersistent,
                        @NonNull final ListStyle style,
                        @NonNull final AuthorBooklistGroup group) {
        super(isPersistent, style, group);

        mDisplayDomain = createDisplayDomain();
        mSortedDomain = createSortDomain();

        mUnderEach = new PBoolean(mPersisted, mPersistence, group.mUnderEach);
        mPrimaryType = new PBitmask(mPersisted, mPersistence, group.mPrimaryType);
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
                .getDisplayAuthor(TBL_AUTHORS.getAlias(), mStyle.isShowAuthorByGivenName()));
    }

    @NonNull
    private VirtualDomain createSortDomain() {
        // Sorting depends on user preference
        return new VirtualDomain(DOM_BL_AUTHOR_SORT, DAOSql.SqlColumns
                .getSortAuthor(mStyle.isSortAuthorByGivenName()), VirtualDomain.SORT_ASC);
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

    private void initPrefs() {
        mUnderEach = new PBoolean(mPersisted, mPersistence, PK_SHOW_BOOKS_UNDER_EACH);

        mPrimaryType = new PBitmask(mPersisted, mPersistence, PK_PRIMARY_TYPE,
                                    Author.TYPE_UNKNOWN, Author.TYPE_BITMASK_ALL);
    }

    @NonNull
    @Override
    @CallSuper
    public Map<String, PPref> getRawPreferences() {
        final Map<String, PPref> map = super.getRawPreferences();
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
     * @return {@code true} if we want to show a book under each of its Authors.
     */
    public boolean showBooksUnderEach() {
        return mUnderEach.isTrue();
    }

    /**
     * Get this preference.
     *
     * @return the type of author we consider the primary author
     */
    @Author.Type
    public int getPrimaryType() {
        return mPrimaryType.getValue();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final AuthorBooklistGroup that = (AuthorBooklistGroup) o;
        return mDisplayDomain.equals(that.mDisplayDomain)
               && mSortedDomain.equals(that.mSortedDomain)
               && Objects.equals(mUnderEach, that.mUnderEach)
               && Objects.equals(mPrimaryType, that.mPrimaryType);
    }

    @Override
    public int hashCode() {
        return Objects
                .hash(super.hashCode(), mDisplayDomain, mSortedDomain, mUnderEach, mPrimaryType);
    }

    @Override
    @NonNull
    public String toString() {
        return "AuthorBooklistGroup{"
               + super.toString()
               + ", mDisplayDomain=" + mDisplayDomain
               + ", mSortedDomain=" + mSortedDomain
               + ", mUnderEach=" + mUnderEach
               + ", mPrimaryType=" + mPrimaryType
               + '}';
    }
}
