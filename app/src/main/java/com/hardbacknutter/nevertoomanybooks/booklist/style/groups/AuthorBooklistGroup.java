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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleSharedPreferences;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PBitmask;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.AuthorDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.definitions.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_AUTHOR_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;

/**
 * Specialized BooklistGroup representing an {@link Author} group.
 * Includes extra attributes based on preferences.
 * <p>
 * {@link #getDisplayDomainExpression()} returns a customized display domain
 * {@link #getGroupDomainExpressions} adds the group/sorted domain based on the OB column.
 */
public class AuthorBooklistGroup
        extends AbstractLinkedTableBooklistGroup {

    /** The value is a {@link PBitmask}. */
    public static final String PK_PRIMARY_TYPE =
            "style.booklist.group.authors.primary.type";

    /** Style - PreferenceScreen/PreferenceCategory Key. */
    private static final String PSK_STYLE_AUTHOR = "psk_style_author";

    public static final String PK_SHOW_BOOKS_UNDER_EACH =
            "style.booklist.group.authors.show.all";
    /** For sorting. */
    private static final Domain DOM_SORTING;

    static {
        DOM_SORTING = new Domain.Builder(DBKey.KEY_BL_AUTHOR_SORT, ColumnInfo.TYPE_TEXT)
                .build();
    }

    /** DomainExpression for sorting the data - depends on the style used. */
    @NonNull
    private final DomainExpression sortingDomainExpression;
    /** The primary author type the user prefers. */
    private final PBitmask primaryAuthorType;

    /**
     * Constructor.
     *
     * @param isPersistent flag
     * @param style        Style reference.
     */
    AuthorBooklistGroup(final boolean isPersistent,
                        @NonNull final ListStyle style) {
        super(AUTHOR, isPersistent, style, PK_SHOW_BOOKS_UNDER_EACH);
        sortingDomainExpression = createSortingDomainExpression();

        primaryAuthorType = new PBitmask(mPersisted, mPersistenceLayer, PK_PRIMARY_TYPE,
                                         Author.TYPE_UNKNOWN, Author.TYPE_BITMASK_ALL);
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
        sortingDomainExpression = createSortingDomainExpression();

        primaryAuthorType = new PBitmask(mPersisted, mPersistenceLayer, group.primaryAuthorType);
    }


    /**
     * Get the global default for this preference.
     *
     * @return {@code true} if we want to show a book under each of its Authors.
     */
    public static boolean showBooksUnderEachDefault() {
        return ServiceLocator.getGlobalPreferences().getBoolean(PK_SHOW_BOOKS_UNDER_EACH, false);
    }

    /**
     * Get the global default for this preference.
     *
     * @return the type of author we consider the primary author
     */
    public static int getPrimaryTypeGlobalDefault() {
        return StyleSharedPreferences.getBitmaskPref(PK_PRIMARY_TYPE, Author.TYPE_UNKNOWN);
    }

    @Override
    @NonNull
    public GroupKey createGroupKey() {
        // We use the foreign ID to create the key domain.
        // We override the display domain in #createDisplayDomainExpression.
        // We do not sort on the key domain but add the OB column in #createSortingDomainExpression
        return new GroupKey(R.string.lbl_author, "a",
                            new DomainExpression(DOM_FK_AUTHOR,
                                                 TBL_AUTHORS.dot(DBKey.PK_ID)))

                .addGroupDomain(
                        // Group by id (we want the id available and there is
                        // a chance two Authors will have the same name)
                        new DomainExpression(DOM_FK_AUTHOR,
                                             TBL_BOOK_AUTHOR.dot(DBKey.FK_AUTHOR)))
                .addGroupDomain(
                        // Group by complete-flag
                        new DomainExpression(DOM_AUTHOR_IS_COMPLETE,
                                             TBL_AUTHORS.dot(DBKey.AUTHOR_IS_COMPLETE)));
    }

    @Override
    @NonNull
    protected DomainExpression createDisplayDomainExpression() {
        // Not sorted; sort as defined in #createSortingDomainExpression
        return AuthorDaoImpl.createDisplayDomainExpression(mStyle.isShowAuthorByGivenName());
    }

    @NonNull
    private DomainExpression createSortingDomainExpression() {
        // Sorting depends on user preference
        return new DomainExpression(DOM_SORTING,
                                    AuthorDaoImpl.getSortAuthor(mStyle.isSortAuthorByGivenName()),
                                    DomainExpression.SORT_ASC);
    }

    @Override
    @NonNull
    public ArrayList<DomainExpression> getGroupDomainExpressions() {
        // We inject the mSortedDomain as first in the list.
        final ArrayList<DomainExpression> list = new ArrayList<>();
        list.add(0, sortingDomainExpression);
        list.addAll(super.getGroupDomainExpressions());
        return list;
    }

    @Override
    @CallSuper
    @NonNull
    public Collection<PPref<?>> getRawPreferences() {
        final Collection<PPref<?>> list = super.getRawPreferences();
        list.add(primaryAuthorType);
        return list;
    }

    @Override
    public void setPreferencesVisible(@NonNull final PreferenceScreen screen,
                                      final boolean visible) {

        final PreferenceCategory category = screen.findPreference(PSK_STYLE_AUTHOR);
        if (category != null) {
            final String[] keys = {PK_SHOW_BOOKS_UNDER_EACH, PK_PRIMARY_TYPE};
            setPreferenceVisibility(category, keys, visible);
        }
    }

    /**
     * Get this preference.
     *
     * @return the type of author we consider the primary author
     */
    @Author.Type
    public int getPrimaryType() {
        return primaryAuthorType.getValue();
    }

    public void setPrimaryType(@Nullable final Integer value) {
        primaryAuthorType.set(value);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (!super.equals(o)) {
            return false;
        }
        final AuthorBooklistGroup that = (AuthorBooklistGroup) o;
        return Objects.equals(sortingDomainExpression, that.sortingDomainExpression)
               && Objects.equals(primaryAuthorType, that.primaryAuthorType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), sortingDomainExpression, primaryAuthorType);
    }

    @Override
    @NonNull
    public String toString() {
        return "AuthorBooklistGroup{"
               + super.toString()
               + ", mSortedDomain=" + sortingDomainExpression
               + ", mPrimaryType=" + primaryAuthorType
               + '}';
    }
}
