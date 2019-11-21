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
import android.os.Bundle;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;

/**
 * Shared between ALL tabs (fragments) and the hosting Activity.
 */
public class SearchAdminModel
        extends ViewModel {

    public static final int TAB_BOOKS = 1;
    static final int TAB_COVERS = 1 << 1;
    static final int TAB_ALT_ED = 1 << 2;

    static final int SHOW_ALL_TABS = TAB_BOOKS | TAB_COVERS | TAB_ALT_ED;

    private static final String TAG = "SearchAdminModel";
    public static final String BKEY_TABS_TO_SHOW = TAG + ":tabs";
    static final String BKEY_PERSIST = TAG + ":persist";

    /**
     * Optional: set to one of the {@link SearchOrderFragment} tabs,
     * if we should *only* show that tab, and NOT save the new setting (i.e. the "use" scenario).
     */
    @Tabs
    private int mTabsToShow;
    @Nullable
    private ArrayList<Site> mBooks;
    @Nullable
    private ArrayList<Site> mCovers;
    @Nullable
    private ArrayList<Site> mAltEd;

    private boolean mPersist;

    /**
     * Pseudo constructor.
     * <p>
     * If a single tab is asked for, we read the list first from the arguments if present.
     * When all tabs are asked for, we get the system/user preferred lists.
     * <p>
     * {@link #BKEY_PERSIST} must be explicitly set to {@code true} if needed.
     *
     * @param context Current context
     * @param args    {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @NonNull final Bundle args) {
        mTabsToShow = args.getInt(BKEY_TABS_TO_SHOW, SHOW_ALL_TABS);
        mPersist = args.getBoolean(BKEY_PERSIST, false);

        // first see if we got passed in custom lists.
        if (mBooks == null) {
            mBooks = args.getParcelableArrayList(SearchSites.BKEY_DATA_SITES);
        }
        if (mCovers == null) {
            mCovers = args.getParcelableArrayList(SearchSites.BKEY_COVERS_SITES);
        }
        if (mAltEd == null) {
            mAltEd = args.getParcelableArrayList(SearchSites.BKEY_ALT_ED_SITES);
        }
        // now depending on which tabs to show, make sure the lists are not null.
        // List(s) for tabs which are not shown remain null.
        switch (mTabsToShow) {
            case TAB_BOOKS:
                if (mBooks == null) {
                    mBooks = SearchSites.getSites(context, SearchSites.ListType.Data);
                }
                break;

            case TAB_COVERS:
                if (mCovers == null) {
                    mCovers = SearchSites.getSites(context, SearchSites.ListType.Covers);
                }
                break;

            case TAB_ALT_ED:
                if (mAltEd == null) {
                    mAltEd = SearchSites.getSites(context, SearchSites.ListType.AltEditions);
                }
                break;

            case SHOW_ALL_TABS:
                if (mBooks == null) {
                    mBooks = SearchSites.getSites(context, SearchSites.ListType.Data);
                }
                if (mCovers == null) {
                    mCovers = SearchSites.getSites(context, SearchSites.ListType.Covers);
                }
                if (mAltEd == null) {
                    mAltEd = SearchSites.getSites(context, SearchSites.ListType.AltEditions);
                }
                break;

            default:
                throw new UnexpectedValueException(mTabsToShow);
        }
    }

    /**
     * Getter for single tab mode.
     *
     * @return list matching the single tab.
     */
    @NonNull
    public ArrayList<Site> getList() {
        switch (mTabsToShow) {
            case TAB_BOOKS:
                //noinspection ConstantConditions
                return mBooks;

            case TAB_COVERS:
                //noinspection ConstantConditions
                return mCovers;

            case TAB_ALT_ED:
                //noinspection ConstantConditions
                return mAltEd;

            case SHOW_ALL_TABS:
            default:
                throw new UnexpectedValueException(mTabsToShow);
        }
    }

    /**
     * Persist the lists.
     *
     * @param context Current context
     *
     * @return {@code true} if each list handled has at least one site enabled.
     */
    public boolean persist(@NonNull final Context context) {
        int shouldHave = 0;
        int has = 0;
        if (mPersist) {
            if (mBooks != null) {
                SearchSites.setList(context, SearchSites.ListType.Data, mBooks);
                shouldHave++;
                has += SearchSites.getEnabledSites(mBooks) > 0 ? 1 : 0;
            }
            if (mCovers != null) {
                SearchSites.setList(context, SearchSites.ListType.Covers, mCovers);
                shouldHave++;
                has += SearchSites.getEnabledSites(mCovers) > 0 ? 1 : 0;
            }
            if (mAltEd != null) {
                SearchSites.setList(context, SearchSites.ListType.AltEditions, mAltEd);
                shouldHave++;
                has += SearchSites.getEnabledSites(mAltEd) > 0 ? 1 : 0;
            }
        }
        return (has > 0) && (shouldHave == has);
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, value = {SHOW_ALL_TABS, TAB_BOOKS, TAB_COVERS, TAB_ALT_ED})
    @interface Tabs {

    }
}
