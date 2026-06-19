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

package com.hardbacknutter.nevertoomanybooks.search.queue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.core.utils.ProductCode;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchResult;
import com.hardbacknutter.util.logger.LoggerFactory;

public class QueuedItem {

    private static final String TAG = "QueuedItem";

    @NonNull
    private final ProductCode productCode;
    /** Set when the search is started. */
    private int searchId;
    /** Set when the result is available. */
    @Nullable
    private BookSearchResult result;

    /**
     * Constructor.
     *
     * @param productCode the {@link ProductCode} this item will represent
     */
    public QueuedItem(@NonNull final ProductCode productCode) {
        this.productCode = productCode;
    }

    /**
     * Get the wrapped {@link ProductCode} for this item.
     *
     * @return code
     */
    @NonNull
    public ProductCode getProductCode() {
        return productCode;
    }

    /**
     * Is there an active search for this item.
     *
     * @return flag
     */
    public boolean isSearching() {
        return searchId > 0 && result == null;
    }

    int getSearchId() {
        return searchId;
    }

    void setSearchId(final int searchId) {
        this.searchId = searchId;
    }

    /**
     * Get the result of the search.
     *
     * @return result; can be {@code null}
     */
    @Nullable
    public BookSearchResult getResult() {
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
        final QueuedItem item = (QueuedItem) o;
        return searchId == item.searchId
               && Objects.equals(productCode, item.productCode)
               && Objects.equals(result, item.result);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productCode, searchId, result);
    }

    @Override
    @NonNull
    public String toString() {
        return "QueuedItem{"
               + "code=" + productCode
               + ", searchId=" + searchId
               + ", result=" + result
               + '}';
    }
}
