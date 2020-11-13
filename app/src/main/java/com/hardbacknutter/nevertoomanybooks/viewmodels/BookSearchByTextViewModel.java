/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.IsfdbSearchEngine;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

public class BookSearchByTextViewModel
        extends ViewModel
        implements ActivityResultViewModel {

    private static final String TAG = "BookSearchByTextViewModel";
    /** Accumulate all data that will be send in {@link Activity#setResult}. */
    @NonNull
    private final Intent mResultIntent = new Intent();
    /**
     * A list of author names we have already searched for in this session.
     */
    @NonNull
    private final Collection<String> mRecentAuthorNames = new ArrayList<>();
    /**
     * A list of Publisher names we have already searched for in this session.
     */
    @NonNull
    private final Collection<String> mRecentPublisherNames = new ArrayList<>();
    /** Database Access. */
    private DAO mDb;
    /** Flag: allow/provide searching by publisher. */
    private boolean mUsePublisher;

    /**
     * Inherits the result from {@link com.hardbacknutter.nevertoomanybooks.EditBookActivity}.
     */
    @Override
    @NonNull
    public Intent getResultIntent() {
        return mResultIntent;
    }

    @Override
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
        }

        super.onCleared();
    }

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     */
    public void init(@NonNull final Context context) {
        if (mDb == null) {
            mDb = new DAO(TAG);

            mUsePublisher = usePublisher(context);
        }
    }

    public boolean addAuthorName(@NonNull final String searchText) {
        if (mRecentAuthorNames.stream().noneMatch(s -> s.equalsIgnoreCase(searchText))) {
            mRecentAuthorNames.add(searchText);
            return true;
        }
        return false;
    }

    /**
     * Build a combined list of the passed in Authors + the database
     *
     * @param context Current context
     *
     * @return combined list
     */
    @NonNull
    public ArrayList<String> getAuthorNames(@NonNull final Context context) {

        final Locale userLocale = AppLocale.getInstance().getUserLocale(context);

        // Uses {@link DBDefinitions#KEY_AUTHOR_FORMATTED_GIVEN_FIRST} as not all
        // search sites can copy with the formatted version.
        final ArrayList<String> authors =
                mDb.getAuthorNames(DBDefinitions.KEY_AUTHOR_FORMATTED_GIVEN_FIRST);

        final Collection<String> uniqueNames = new HashSet<>(authors.size());
        for (final String s : authors) {
            uniqueNames.add(s.toLowerCase(userLocale));
        }

        // Add the names the user has already tried (to handle errors and mistakes)
        for (final String s : mRecentAuthorNames) {
            if (!uniqueNames.contains(s.toLowerCase(userLocale))) {
                authors.add(s);
            }
        }

        return authors;
    }

    /**
     * Whether a search should (also) use the publisher name to search for books.
     * <p>
     * Hardcoded to ISFDB only for now, as that's the only site supporting this flag.
     * This method will be refactored/moved/... at some point.
     *
     * @param context Current context
     *
     * @return flag
     */
    private boolean usePublisher(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(IsfdbSearchEngine.PREFS_USE_PUBLISHER, false);
    }

    /**
     * Whether a search should (also) use the publisher name to search for books.
     *
     * @return flag
     */
    public boolean usePublisher() {
        return mUsePublisher;
    }

    public boolean addPublisherName(@NonNull final String searchText) {
        if (mRecentPublisherNames.stream().noneMatch(s -> s.equalsIgnoreCase(searchText))) {
            mRecentPublisherNames.add(searchText);
            return true;
        }
        return false;
    }

    /**
     * Build a combined list of the passed in Publishers + the database
     *
     * @param context Current context
     *
     * @return combined list
     */
    @NonNull
    public ArrayList<String> getPublisherNames(@NonNull final Context context) {

        final Locale userLocale = AppLocale.getInstance().getUserLocale(context);

        final ArrayList<String> publishers = mDb.getPublisherNames();

        final Collection<String> uniqueNames = new HashSet<>(publishers.size());
        for (final String s : publishers) {
            uniqueNames.add(s.toLowerCase(userLocale));
        }

        // Add the names the user has already tried (to handle errors and mistakes)
        for (final String s : mRecentPublisherNames) {
            if (!uniqueNames.contains(s.toLowerCase(userLocale))) {
                publishers.add(s);
            }
        }

        return publishers;
    }
}
