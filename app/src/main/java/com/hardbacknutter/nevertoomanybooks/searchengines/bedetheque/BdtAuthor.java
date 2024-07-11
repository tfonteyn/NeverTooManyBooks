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

package com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.database.CacheDbHelper;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

public class BdtAuthor {

    @NonNull
    private final String name;
    @Nullable
    private final String url;
    private long id;
    private boolean resolved;
    @Nullable
    private String resolvedName;

    public BdtAuthor(final long id,
                     @NonNull final DataHolder rowData) {
        this.id = id;
        this.name = rowData.getString(CacheDbHelper.BDT_AUTHOR_NAME);
        this.resolved = rowData.getBoolean(CacheDbHelper.BDT_AUTHOR_IS_RESOLVED);
        this.resolvedName = rowData.getString(CacheDbHelper.BDT_AUTHOR_RESOLVED_NAME, null);
        this.url = rowData.getString(CacheDbHelper.BDT_AUTHOR_URL, null);
    }

    BdtAuthor(@NonNull final String name,
              @NonNull final String url) {
        this.name = name;
        this.url = url;
    }

    public long getId() {
        return id;
    }

    public void setId(final long id) {
        this.id = id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @Nullable
    public String getUrl() {
        return url;
    }

    public boolean isResolved() {
        return resolved;
    }

    /**
     * Get the resolved name.
     *
     * @return resolved name; or {@code null} if none or equal to the actual name
     */
    @Nullable
    public String getResolvedName() {
        if (!resolved
            || resolvedName == null || resolvedName.isEmpty()
            || resolvedName.equals(name)) {
            return null;
        }
        return resolvedName;
    }

    void setResolvedName(@Nullable final String resolvedName) {
        this.resolved = resolvedName != null;
        this.resolvedName = resolvedName;
    }

    @Override
    @NonNull
    public String toString() {
        return "BdtAuthor{"
               + "id=" + id
               + ", name=`" + name + '`'
               + ", url=`" + url + '`'
               + ", resolved=" + resolved
               + ", resolvedName=`" + resolvedName + '`'
               + '}';
    }
}
