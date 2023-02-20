/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.core.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.core.BuildConfig;

/**
 * A data class representing a domain + the sql column expression + a sorting flag.
 * <p>
 * Immutable.
 */
public class DomainExpression {

    /** Domain. */
    @NonNull
    private final Domain domain;
    /** Expression to use to fetch the domain value. */
    @Nullable
    private final String expression;
    @NonNull
    private final Sort sort;

    /**
     * Constructor.
     * <p>
     * Auto-generates the expression using {@link TableDefinition#dot(Domain)}
     * No sorting.
     *
     * @param domain underlying domain
     * @param table  table owning the domain
     */
    public DomainExpression(@NonNull final Domain domain,
                            @NonNull final TableDefinition table) {
        this.domain = domain;
        this.expression = table.dot(domain);
        this.sort = Sort.Unsorted;
    }

    /**
     * Constructor.
     * <p>
     * Auto-generates the expression using {@link TableDefinition#dot(Domain)}
     * Sorting as required.
     *
     * @param domain underlying domain
     * @param table  table owning the domain
     * @param sort   flag
     */
    public DomainExpression(@NonNull final Domain domain,
                            @NonNull final TableDefinition table,
                            @NonNull final Sort sort) {
        this.domain = domain;
        this.expression = table.dot(domain);
        this.sort = sort;
    }

    /**
     * Constructor.
     * <p>
     * No expression; the domain will <strong>only</strong> be added to the ORDER BY clause .
     *
     * @param domain underlying domain
     * @param sort   flag; must be either {@link Sort#Asc} or {@link Sort#Desc}.
     *
     * @throws IllegalArgumentException on passing in the wrong sort flag
     */
    public DomainExpression(@NonNull final Domain domain,
                            @NonNull final Sort sort) {
        if (BuildConfig.DEBUG /* always */) {
            if (sort == Sort.Unsorted) {
                throw new IllegalArgumentException("Sort must be Asc/Desc");
            }
        }
        this.domain = domain;
        this.expression = null;
        this.sort = sort;
    }

    /**
     * Constructor.
     * <p>
     * Customized expressions.
     * Sorting as required.
     *
     * @param domain     underlying domain
     * @param expression to use for fetching the data
     * @param sort       flag
     */
    public DomainExpression(@NonNull final Domain domain,
                            @Nullable final String expression,
                            @NonNull final Sort sort) {
        this.domain = domain;
        this.expression = expression;
        this.sort = sort;
    }

    @NonNull
    public Domain getDomain() {
        return domain;
    }

    @Nullable
    public String getExpression() {
        return expression;
    }

    @NonNull
    public Sort getSort() {
        return sort;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DomainExpression that = (DomainExpression) o;
        return domain.equals(that.domain)
               && Objects.equals(expression, that.expression)
               && sort == that.sort;
    }

    @Override
    public int hashCode() {
        return Objects.hash(domain, expression, sort);
    }

    @Override
    @NonNull
    public String toString() {
        return "DomainExpression{"
               + "domain=" + domain
               + ", expression=`" + expression + '`'
               + ", sort=" + sort
               + '}';
    }
}
