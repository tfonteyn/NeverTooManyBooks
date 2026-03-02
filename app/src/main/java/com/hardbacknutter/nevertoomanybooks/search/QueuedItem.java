/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.core.utils.Code;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchResult;
import com.hardbacknutter.util.logger.LoggerFactory;

class QueuedItem<CODE extends Code> {

    private static final String TAG = "QueuedItem";

    @NonNull
    private final CODE code;
    /** Set when the search is started. */
    private int searchId;
    /** Set when the result is available. */
    @Nullable
    private BookSearchResult result;

    QueuedItem(@NonNull final CODE code) {
        this.code = code;
    }

    @NonNull
    CODE getCode() {
        return code;
    }

    /**
     * Is there an active search for this item.
     *
     * @return flag
     */
    boolean isSearching() {
        return searchId > 0 && result == null;
    }

    int getSearchId() {
        return searchId;
    }

    void setSearchId(final int searchId) {
        this.searchId = searchId;
    }

    @Nullable
    BookSearchResult getResult() {
        return result;
    }

    void setResult(@NonNull final BookSearchResult result) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "result=" + result);
        }
        this.result = result;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        @SuppressWarnings("unchecked")
        final QueuedItem<CODE> item = (QueuedItem<CODE>) o;
        return searchId == item.searchId
               && Objects.equals(code, item.code)
               && Objects.equals(result, item.result);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, searchId, result);
    }

    @Override
    @NonNull
    public String toString() {
        return "QueuedItem{"
               + "code=" + code
               + ", searchId=" + searchId
               + ", result=" + result
               + '}';
    }
}
