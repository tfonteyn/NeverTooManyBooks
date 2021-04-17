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
package com.hardbacknutter.nevertoomanybooks.booklist.style.groups;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

/**
 * All plumbing present, but the 'under each' preference is not exposed to the user yet,
 * because there is no 'position' column for bookshelves.
 * <p>
 * <p>
 * Specialized BooklistGroup representing a {@link Bookshelf} group.
 * Includes extra attributes based on preferences.
 * <p>
 * {@link #getDisplayDomain()} returns a customized display domain
 * {@link #getGroupDomains} adds the group/sorted domain based on the OB column.
 */
public class BookshelfBooklistGroup
        extends BooklistGroup {

    /** Style - PreferenceScreen/PreferenceCategory Key. */
    private static final String PSK_STYLE_BOOKSHELF = "psk_style_bookshelf";

    private static final String PK_SHOW_BOOKS_UNDER_EACH =
            "style.booklist.group.bookshelf.show.all";

    /** Customized domain with display data. */
    @NonNull
    private final DomainExpression mDisplayDomain;

    /** Show a book under each {@link Bookshelf} it is linked to. */
    private PBoolean mUnderEach;

    /**
     * Constructor.
     *
     * @param isPersistent flag
     * @param style        Style reference.
     */
    BookshelfBooklistGroup(final boolean isPersistent,
                           @NonNull final ListStyle style) {
        super(BOOKSHELF, isPersistent, style);
        mDisplayDomain = createDisplayDomain();

        initPrefs();
    }

    /**
     * Copy constructor.
     *
     * @param isPersistent flag
     * @param style        Style reference.
     * @param group        to copy from
     */
    BookshelfBooklistGroup(final boolean isPersistent,
                           @NonNull final ListStyle style,
                           @NonNull final BookshelfBooklistGroup group) {
        super(isPersistent, style, group);
        mDisplayDomain = createDisplayDomain();

        mUnderEach = new PBoolean(mPersisted, mPersistenceLayer, group.mUnderEach);
    }

    /**
     * Get the global default for this preference.
     *
     * @return {@code true} if we want to show a book under each of its Bookshelves.
     */
    public static boolean showBooksUnderEachDefault() {
        return ServiceLocator.getGlobalPreferences().getBoolean(PK_SHOW_BOOKS_UNDER_EACH, false);
    }

    private void initPrefs() {
        mUnderEach = new PBoolean(mPersisted, mPersistenceLayer, PK_SHOW_BOOKS_UNDER_EACH);
    }

    @NonNull
    private DomainExpression createDisplayDomain() {
        // Not sorted; we sort on the OB domain as defined in the GroupKey.
        return new DomainExpression(DBDefinitions.DOM_BOOKSHELF_NAME,
                                    DBDefinitions.TBL_BOOKSHELF.dot(DBKey.KEY_BOOKSHELF_NAME));
    }

    @NonNull
    @Override
    public DomainExpression getDisplayDomain() {
        return mDisplayDomain;
    }

    @NonNull
    @Override
    @CallSuper
    public Map<String, PPref<?>> getRawPreferences() {
        final Map<String, PPref<?>> map = super.getRawPreferences();
        map.put(mUnderEach.getKey(), mUnderEach);
        return map;
    }

    @Override
    public void setPreferencesVisible(@NonNull final PreferenceScreen screen,
                                      final boolean visible) {

        final PreferenceCategory category = screen.findPreference(PSK_STYLE_BOOKSHELF);
        if (category != null) {
            setPreferenceVisibility(category, new String[]{PK_SHOW_BOOKS_UNDER_EACH}, visible);
        }
    }

    /**
     * Get this preference.
     *
     * @return {@code true} if we want to show a book under each of its Bookshelves.
     */
    public boolean showBooksUnderEach() {
        return mUnderEach.isTrue();
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
        final BookshelfBooklistGroup that = (BookshelfBooklistGroup) o;
        return mDisplayDomain.equals(that.mDisplayDomain)
               && Objects.equals(mUnderEach, that.mUnderEach);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mDisplayDomain, mUnderEach);
    }

    @Override
    @NonNull
    public String toString() {
        return "BookshelfBooklistGroup{"
               + super.toString()
               + ", mDisplayDomain=" + mDisplayDomain
               + ", mUnderEach=" + mUnderEach
               + '}';
    }
}
