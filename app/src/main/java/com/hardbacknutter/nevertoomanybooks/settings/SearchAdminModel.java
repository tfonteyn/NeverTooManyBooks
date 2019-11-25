/*
 * @Copyright 2019 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.searches.SiteList;

/**
 * Shared between ALL tabs (fragments) and the hosting Activity.
 */
public class SearchAdminModel
        extends ViewModel {

    private static final String TAG = "SearchAdminModel";
    public static final String BKEY_LIST_TYPE = TAG + ":type";

    /** Contains cloned copies of the lists we're handling. */
    private Map<SiteList.ListType, SiteList> mListMap;

    /** {@code null} if we're doing all lists. */
    @Nullable
    private SiteList.ListType mListType;

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
            mListMap = new EnumMap<>(SiteList.ListType.class);
            if (args != null) {
                mListType = args.getParcelable(BKEY_LIST_TYPE);

                // first see if we got passed in any custom lists.
                for (SiteList.ListType type : SiteList.ListType.values()) {
                    if (!mListMap.containsKey(type)) {
                        SiteList siteList = args.getParcelable(type.getBundleKey());
                        if (siteList != null) {
                            mListMap.put(type, siteList);
                        }
                    }
                }
            }
        }
    }

    @Nullable
    SiteList.ListType getListType() {
        return mListType;
    }

    /**
     * Getter for single tab mode.
     *
     * @param appContext Current context
     * @param listType   type of list
     *
     * @return list matching the single tab.
     */
    @NonNull
    SiteList getList(@NonNull final Context appContext,
                     @NonNull final SiteList.ListType listType) {

        SiteList list = mListMap.get(listType);
        if (list == null) {
            list = SiteList.getList(appContext, listType);
            mListMap.put(listType, list);
        }
        return list;
    }

    /**
     * Persist the lists.
     *
     * @param appContext Current context
     *
     * @return {@code true} if each list handled has at least one site enabled.
     */
    public boolean persist(@NonNull final Context appContext) {
        int shouldHave = 0;
        int has = 0;
        for (SiteList list : mListMap.values()) {
            list.update(appContext);
            shouldHave++;
            has += list.getEnabledSites() > 0 ? 1 : 0;
        }

        return (has > 0) && (shouldHave == has);
    }

    void resetList(@NonNull final Context appContext,
                   @NonNull final SiteList.ListType listType) {

        SiteList newList = SiteList.resetList(appContext, listType);

        SiteList currentList = mListMap.get(listType);
        if (currentList == null) {
            mListMap.put(listType, newList);
        } else {
            currentList.clearAndAddAll(newList);
        }
    }
}
