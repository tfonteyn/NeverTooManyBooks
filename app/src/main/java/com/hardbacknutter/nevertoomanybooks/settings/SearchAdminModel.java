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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.searches.Site;

/**
 * Shared between ALL tabs (fragments) and the hosting Activity.
 */
public class SearchAdminModel
        extends ViewModel {

    private static final String ERROR_NOT_IN_SINGLE_LIST_MODE = "NOT in single-list mode";
    /** Log tag. */
    private static final String TAG = "SearchAdminModel";
    /** Single-list/tab mode parameter. */
    public static final String BKEY_LIST = TAG + ":list";

    private final Map<Site.Type, ArrayList<Site>> mSiteListMap = new EnumMap<>(Site.Type.class);

    /**
     * Pseudo constructor.
     * <p>
     * If a single tab is asked for, we read the list first from the arguments if present.
     * When all tabs are asked for, we get the system/user preferred lists.
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@Nullable final Bundle args) {
        if (mSiteListMap.isEmpty()) {
            // single-list mode ?
            final ArrayList<Site> singleList;
            if (args != null) {
                // will be null if not present
                singleList = args.getParcelableArrayList(BKEY_LIST);
            } else {
                singleList = null;
            }

            if (singleList != null) {
                final Site.Type type = singleList.get(0).getType();
                mSiteListMap.put(type, singleList);

            } else {
                for (Site.Type type : Site.Type.values()) {
                    mSiteListMap.put(type, type.getSites());
                }
            }
        }
    }

    boolean isSingleListMode() {
        return mSiteListMap.size() == 1;
    }

    /**
     * Get the type of the list for single-list mode.
     * Should only be called when {@link #isSingleListMode()} returns {@code true}
     *
     * @return the type
     */
    @NonNull
    Site.Type getType() {
        if (!isSingleListMode()) {
            throw new IllegalStateException(ERROR_NOT_IN_SINGLE_LIST_MODE);
        }
        return mSiteListMap.values().iterator().next().get(0).getType();
    }

    /**
     * Get the list for single-list mode.
     * Should only be called when {@link #isSingleListMode()} returns {@code true}
     *
     * @return the list
     */
    @NonNull
    ArrayList<Site> getList() {
        if (!isSingleListMode()) {
            throw new IllegalStateException(ERROR_NOT_IN_SINGLE_LIST_MODE);
        }
        return mSiteListMap.values().iterator().next();
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
    ArrayList<Site> getList(@NonNull final Site.Type type) {
        final ArrayList<Site> list = mSiteListMap.get(type);
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
        for (ArrayList<Site> list : mSiteListMap.values()) {
            if (list.stream().noneMatch(Site::isEnabled)) {
                return false;
            }
        }

        return true;
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

        for (Map.Entry<Site.Type, ArrayList<Site>> entry : mSiteListMap.entrySet()) {
            entry.getKey().setList(context, entry.getValue());
        }
    }
}
