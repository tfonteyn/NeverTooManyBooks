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
package com.hardbacknutter.nevertoomanybooks.searches;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.tasks.Canceller;

public abstract class SearchEngineBase
        implements SearchEngine {

    @SearchSites.EngineId
    protected final int mId;

    @NonNull
    protected final Context mAppContext;

    @Nullable
    private Canceller mCaller;

    /**
     * Constructor.
     *
     * @param appContext Application context
     */
    public SearchEngineBase(@NonNull final Context appContext) {
        mAppContext = appContext;

        final SearchEngine.Configuration se = getClass().getAnnotation(
                SearchEngine.Configuration.class);
        Objects.requireNonNull(se);
        mId = se.id();
    }

    @Override
    public int getId() {
        return mId;
    }

    @NonNull
    @Override
    public Context getAppContext() {
        return mAppContext;
    }

    @Override
    public void setCaller(@Nullable final Canceller caller) {
        mCaller = caller;
    }

    @Override
    public boolean isCancelled() {
        return mCaller == null || mCaller.isCancelled();
    }

}
