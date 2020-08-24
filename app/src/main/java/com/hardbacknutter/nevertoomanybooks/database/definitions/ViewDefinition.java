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
package com.hardbacknutter.nevertoomanybooks.database.definitions;

import androidx.annotation.NonNull;

/**
 * This does not represent a traditional SQL View, but it's very similar.
 * <p>
 * We simulate a virtual table by setting up a SELECT expression and a NAME(==alias).
 * which we then can use in select or joins.
 * <p>
 * Note that this is not a complete implementation like {@link TableDefinition}
 * but strictly what is needed 'for now.
 */
public class ViewDefinition {

    private final String mName;
    private final String mAlias;
    private final String expression;

    public ViewDefinition(final String name,
                          final String expression) {
        this.mName = name;
        this.mAlias = name;
        this.expression = expression;
    }

    /**
     * Return an aliased table name.
     * <p>
     * format: [(select-expression)] AS [table-alias]
     * <p>
     * e.g. '(select ...) AS b'.
     *
     * @return SQL Fragment
     */
    public String ref() {
        return "(" + expression + ") AS " + mAlias;
    }

    /**
     * Get the alias name.
     *
     * @return the table alias, or if blank, return the table name.
     */
    @NonNull
    public String getAlias() {
        if (mAlias == null || mAlias.isEmpty()) {
            return mName;
        } else {
            return mAlias;
        }
    }

    @NonNull
    public String dot(@NonNull final String domain) {
        return getAlias() + '.' + domain;
    }

    @NonNull
    public String dotAs(@NonNull final String domain) {
        return getAlias() + '.' + domain + " AS " + domain;
    }
}
