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

package com.hardbacknutter.nevertoomanybooks.searchengines.douban;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEdition;

public class AltEditionDouban
        implements AltEdition {

    /** {@link Identifier#SID_DOUBAN}. */
    private final long sid;
    @Nullable
    private final String bookUrl;
    @Nullable
    private final String coverUrl;

    /**
     * Constructor.
     *
     * @param sid      {@link Identifier#SID_DOUBAN}
     * @param bookUrl  full url to the book on the Douban site
     * @param coverUrl full url to the cover on the Douban site
     */
    AltEditionDouban(final long sid,
                     @Nullable final String bookUrl,
                     @Nullable final String coverUrl) {
        this.sid = sid;
        this.bookUrl = bookUrl;
        this.coverUrl = coverUrl;
    }

    @Override
    public boolean mayHaveCover() {
        return coverUrl != null && !coverUrl.isEmpty();
    }

    /**
     * {@link Identifier#SID_DOUBAN}.
     *
     * @return the website id
     */
    public long getSid() {
        return sid;
    }

    @Nullable
    public String getBookUrl() {
        return bookUrl;
    }

    @Nullable
    public String getCoverUrl() {
        return coverUrl;
    }

    @Override
    @NonNull
    public String toString() {
        return "AltEditionDouban{"
               + "sid=" + sid
               + ", bookUrl=`" + bookUrl + '`'
               + ", coverUrl=`" + coverUrl + '`'
               + '}';
    }
}
