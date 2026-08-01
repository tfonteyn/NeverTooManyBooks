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

package com.hardbacknutter.nevertoomanybooks.settings.searchsites;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.searchengines.Site;

/**
 * Used for both input and output.
 * <p>
 * Dev. note: this is a tiny class which we don't really need
 * but is used regardless for consistency and hiding bundle calls.
 * <p>
 * Both {@link #toBundle(ArrayList)} and {@link #fromBundle(Bundle)}
 * are static and bypass local object creation.
 */
final class SingleSiteListInputOutput {

    private static final String TAG = "SingleSiteListIOput";

    /**
     * Single-list/tab mode parameter.
     * <p>
     * Type: {@code java.util.ArrayList<? extends android.os.Parcelable>)}
     */
    private static final String BKEY_LIST = TAG + ":list";

    private SingleSiteListInputOutput() {
    }

    @Nullable
    static List<Site> fromBundle(@Nullable final Bundle args) {
        if (args == null) {
            return null;
        }

        //noinspection deprecation
        return args.getParcelableArrayList(BKEY_LIST);
    }

    @NonNull
    static Bundle toBundle(@NonNull final ArrayList<Site> siteList) {
        final Bundle args = new Bundle(1);
        args.putParcelableArrayList(BKEY_LIST, siteList);

        return args;
    }
}
