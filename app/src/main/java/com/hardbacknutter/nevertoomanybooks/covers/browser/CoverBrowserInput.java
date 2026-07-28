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

package com.hardbacknutter.nevertoomanybooks.covers.browser;

import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;

/**
 * Input/Bundle convertor.
 */
final class CoverBrowserInput {

    private static final String TAG = "Input";

    /** 0..n image index. */
    private static final String BKEY_C_IDX = TAG + ":cIdx";

    @NonNull
    private final String requestKey;
    @NonNull
    private final String bookTitle;
    @NonNull
    private final String productCodeStr;
    @IntRange(from = 0, to = 3)
    private final int cIdx;
    @Nullable
    private final ArrayList<Site> sites;

    CoverBrowserInput(@NonNull final String requestKey,
                      @NonNull final String bookTitle,
                      @NonNull final String productCodeStr,
                      @IntRange(from = 0, to = 3) final int cIdx,
                      @Nullable final ArrayList<Site> sites) {
        this.requestKey = requestKey;
        this.bookTitle = bookTitle;
        this.productCodeStr = productCodeStr;
        this.cIdx = cIdx;
        this.sites = sites;
    }

    @NonNull
    static CoverBrowserInput fromBundle(@NonNull final Bundle args) {
        final String requestKey = Objects.requireNonNull(
                args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                DialogLauncher.BKEY_REQUEST_KEY);

        final String bookTitle = Objects.requireNonNull(
                args.getString(DBKey.TITLE), DBKey.TITLE);

        final String productCodeStr = SanityCheck.requireValue(
                args.getString(DBKey.ISBN), DBKey.ISBN);

        final int cIdx = args.getInt(BKEY_C_IDX);

        @SuppressWarnings("deprecation")
        final ArrayList<Site> sites = args.getParcelableArrayList(Site.Type.Covers.getBundleKey());

        return new CoverBrowserInput(requestKey, bookTitle, productCodeStr, cIdx, sites);
    }

    @NonNull
    Bundle toBundle() {
        final Bundle args = new Bundle(5);
        args.putString(DialogLauncher.BKEY_REQUEST_KEY, requestKey);
        args.putString(DBKey.TITLE, bookTitle);
        args.putString(DBKey.ISBN, productCodeStr);
        args.putInt(BKEY_C_IDX, cIdx);
        if (sites != null) {
            args.putParcelableArrayList(Site.Type.Covers.getBundleKey(), sites);
        }

        return args;
    }

    @NonNull
    String getRequestKey() {
        return requestKey;
    }

    @NonNull
    String getBookTitle() {
        return bookTitle;
    }

    @NonNull
    String getProductCodeStr() {
        return productCodeStr;
    }

    int getCoverIdx() {
        return cIdx;
    }

    @Nullable
    List<Site> getSites() {
        return sites;
    }
}
