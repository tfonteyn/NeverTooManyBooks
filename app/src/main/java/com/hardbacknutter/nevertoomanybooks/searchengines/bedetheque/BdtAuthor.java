/*
 * @Copyright 2018-2025 HardBackNutter
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.database.CacheDbHelper;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

public class BdtAuthor {

    private static final Pattern BDT_ID_PATTERN =
            Pattern.compile("https://www.bedetheque.com/auteur-(\\d+)-BD.*");
    @NonNull
    private final String name;
    @Nullable
    private final String url;
    private long id;
    @Nullable
    private String resolvedName;

    @Nullable
    private String bdtId;

    /**
     * Full constructor.
     *
     * @param id      the Bookshelf id
     * @param rowData with data
     */
    public BdtAuthor(final long id,
                     @NonNull final DataHolder rowData) {
        this.id = id;
        this.name = rowData.getString(CacheDbHelper.BDT_AUTHOR_LIST_NAME);
        this.resolvedName = rowData.getString(CacheDbHelper.BDT_AUTHOR_REAL_NAME, null);
        this.url = rowData.getString(CacheDbHelper.BDT_AUTHOR_URL, null);
    }

    BdtAuthor(@NonNull final String name,
              @NonNull final String url) {
        this.name = name;
        this.url = url;
    }

    /**
     * Get the local row-id in the cache database for this author.
     * Do <strong>NOT</strong> confuse with the bedetheque author-id.
     *
     * @return id
     */
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

    /**
     * Get the author url.
     * <p>
     * Example:{@code https://www.bedetheque.com/auteur-1888-BD-Jacobs-Edgar-Pierre.html}
     *
     * @return the full url to the website author page.
     */
    @Nullable
    public String getUrl() {
        return url;
    }

    /**
     * Get the Author SID.
     *
     * @return SID, should NEVER be {@code null} or {@code ""}
     *         but we're going to be paranoid and assume it can be
     */
    @Nullable
    String getBdtId() {
        if (bdtId != null && !bdtId.isEmpty()) {
            return bdtId;
        }
        if (url != null && !url.isEmpty()) {
            final Matcher matcher = BDT_ID_PATTERN.matcher(url);
            if (matcher.find()) {
                bdtId = matcher.group(1);
                return bdtId;
            }
        }
        // we should never get here as there should always be a url
        return null;
    }

    /**
     * Get the resolved name. This is the "family, given" formatted name of the author.
     *
     * @return resolved name; or {@code null} if none or equal to the actual name
     */
    @Nullable
    public String getRealName() {
        if (resolvedName == null || resolvedName.isEmpty() || resolvedName.equals(name)) {
            return null;
        }
        return resolvedName;
    }

    /**
     * Set the resolved status/data.
     *
     * @param realName to use, formatted as "family, given", or {@code null} to delete.
     */
    void setRealName(@Nullable final String realName) {
        this.resolvedName = realName;
    }

    @Override
    @NonNull
    public String toString() {
        return "BdtAuthor{"
               + "id=" + id
               + ", bdtId=" + bdtId
               + ", name=`" + name + '`'
               + ", url=`" + url + '`'
               + ", resolvedName=`" + resolvedName + '`'
               + '}';
    }
}
