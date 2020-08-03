/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.searches.SiteList;

/**
 * Shared between ALL tabs (fragments) and the hosting Activity.
 */
public class SearchAdminModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "SearchAdminModel";
    /** Single-list/tab mode parameter. */
    public static final String BKEY_LIST = TAG + ":list";

    /**
     * In single-list mode: a local instance, containing a COPY of the single list we're editing.
     * In all-lists mode: a reference to the global list map.
     */
    private Map<SiteList.Type, SiteList> mListMap;

    /**
     * Pseudo constructor.
     * <p>
     * If a single tab is asked for, we read the list first from the arguments if present.
     * When all tabs are asked for, we get the system/user preferred lists.
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@Nullable final Bundle args) {
        if (mListMap == null) {
            // Check/load single-list mode
            final SiteList singleList;
            if (args != null) {
                // will be null if not present
                singleList = args.getParcelable(BKEY_LIST);
            } else {
                singleList = null;
            }

            if (singleList != null) {
                // create a local instance and add the list
                mListMap = new EnumMap<>(SiteList.Type.class);
                mListMap.put(singleList.getType(), singleList);

            } else {
                // Get a reference to the global lists
                mListMap = SiteList.getSiteListMap();
            }
        }
    }

    boolean isSingleListMode() {
        return mListMap.size() == 1;
    }

    /**
     * Get the list for single-list mode.
     * Should only be called when {@link #isSingleListMode()} returns {@code true}
     *
     * @return the list
     */
    @NonNull
    SiteList getList() {
        if (!isSingleListMode()) {
            throw new IllegalStateException("NOT in single-list mode");
        }
        return mListMap.values().iterator().next();
    }

    /**
     * Get the list for the given type.
     * <p>
     * Can be called in single-list, AND in all-lists mode.
     * For single-list mode, the type must match or a NullPointerException will be thrown.
     *
     * @param type type of list
     *
     * @return list
     *
     * @throws NullPointerException if in single-list mode and the given type does not match
     *                              (This would be a bug...)
     */
    @NonNull
    SiteList getList(@NonNull final SiteList.Type type) {
        final SiteList list = mListMap.get(type);
        Objects.requireNonNull(list, "in single-list mode, wrong type=" + type);
        return list;
    }

    /**
     * Validate if each list handled has at least one site enabled.
     * <p>
     * Can be called in single-list, AND in all-lists mode.
     *
     * @return {@code true} if each list handled has at least one site enabled.
     */
    public boolean validate() {
        int shouldHave = 0;
        int has = 0;
        for (SiteList list : mListMap.values()) {
            shouldHave++;
            has += list.getEnabledSites().isEmpty() ? 0 : 1;
        }

        return (has > 0) && (shouldHave == has);
    }


    /**
     * Persist the lists.
     * <p>
     * Should only be called when {@link #isSingleListMode()} returns {@code false}
     *
     * @param context Current context
     */
    public void persist(@NonNull final Context context) {
        if (isSingleListMode()) {
            throw new IllegalStateException("in single-list mode");
        }

        for (SiteList list : mListMap.values()) {
            list.savePrefs(context);
        }
    }
}
