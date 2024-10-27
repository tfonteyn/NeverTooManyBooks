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

package com.hardbacknutter.nevertoomanybooks.booklist.style.groups;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.core.database.DomainExpression;

final class GroupKey {

    @BooklistGroup.Id
    private final int id;
    /** User displayable label resource id. */
    @SuppressWarnings("FieldNotUsedInToString")
    @StringRes
    private final int labelResId;
    /** Unique keyPrefix used to represent a key in the hierarchy. */
    @NonNull
    private final String keyPrefix;

    /** The key domain, which is by default also the display-domain. */
    @NonNull
    private final DomainExpression keyDomain;

    /**
     * Aside of the main display domain, a group can have extra domains that should
     * be fetched/sorted.
     */
    @NonNull
    private final List<DomainExpression> groupDomains = new ArrayList<>();

    /**
     * A group can add domains to the lowest level (the book).
     */
    @NonNull
    private final List<DomainExpression> baseDomains = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param id                  of group to create
     * @param labelResId          User displayable label resource id
     * @param keyPrefix           the key prefix (as short as possible)
     *                            to use for the compound key
     * @param keyDomainExpression the domain to get the actual data from the Cursor
     */
    GroupKey(@BooklistGroup.Id final int id,
             @StringRes final int labelResId,
             @NonNull final String keyPrefix,
             @NonNull final DomainExpression keyDomainExpression) {
        this.id = id;
        this.labelResId = labelResId;
        this.keyPrefix = keyPrefix;
        keyDomain = keyDomainExpression;
    }

    @BooklistGroup.Id
    public int getId() {
        return id;
    }

    @NonNull
    String getLabel(@NonNull final Context context) {
        return context.getString(labelResId);
    }

    @NonNull
    GroupKey addGroupDomain(@NonNull final DomainExpression domainExpression) {
        // this is a static setup. We don't check on developer mistakenly adding duplicates!
        groupDomains.add(domainExpression);
        return this;
    }

    @NonNull
    GroupKey addBaseDomain(@NonNull final DomainExpression domainExpression) {
        // this is a static setup. We don't check on developer mistakenly adding duplicates!
        baseDomains.add(domainExpression);
        return this;
    }

    /**
     * Get the unique keyPrefix used to represent a key in the hierarchy.
     *
     * @return keyPrefix, never {@code null} but will be empty for a BOOK.
     */
    @VisibleForTesting
    @NonNull
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * Create the expression for the node key column: "/key=value".
     * A {@code null} value is reformatted as an empty string.
     * <p>
     * <strong>Dev. note:</strong> the "value" part is an SQL expression,
     * the "||" operator being a string 'concat' during the SQL execution.
     *
     * @return column expression
     */
    @NonNull
    String getNodeKeyExpression() {
        return "'/" + keyPrefix + "='||COALESCE(" + keyDomain.getExpression() + ",'')";
    }

    /**
     * Get the domain that contains the displayable data.
     *
     * @return domain to display
     */
    @NonNull
    DomainExpression getDisplayDomainExpression() {
        return keyDomain;
    }

    /**
     * Get the list of secondary domains.
     * <p>
     * Override in {@link BooklistGroup} implementations as needed.
     *
     * @return the list, can be empty.
     */
    @NonNull
    List<DomainExpression> getGroupDomainExpressions() {
        return groupDomains;
    }

    /**
     * Get the list of base (book) domains.
     * <p>
     * Override in {@link BooklistGroup} implementations as needed.
     *
     * @return the list, can be empty.
     */
    @NonNull
    List<DomainExpression> getBaseDomainExpressions() {
        return baseDomains;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GroupKey that = (GroupKey) o;
        return id == that.id
               && labelResId == that.labelResId
               && keyPrefix.equals(that.keyPrefix)
               && keyDomain.equals(that.keyDomain)
               && groupDomains.equals(that.groupDomains)
               && baseDomains.equals(that.baseDomains);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, labelResId, keyPrefix, keyDomain, groupDomains, baseDomains);
    }

    @NonNull
    @Override
    public String toString() {
        return "GroupKey{"
               + "id=" + id
               + ", keyPrefix=`" + keyPrefix + '`'
               + ", keyDomain=" + keyDomain
               + ", groupDomains=" + groupDomains
               + ", baseDomains=" + baseDomains
               + '}';
    }
}
