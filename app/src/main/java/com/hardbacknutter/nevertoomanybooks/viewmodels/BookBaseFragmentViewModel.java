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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import java.util.HashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.fields.Fields;

/**
 * Used by the set of fragments that allow viewing and editing a Book.
 * <ul>
 *     <li>Hold the field lists</li>
 *     <li>Keep track of the currently in-action CoverHandler</li>
 * </ul>
 */
public abstract class BookBaseFragmentViewModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "BookBaseFragmentVM";

    /** The fields collection handled in this model. They key is the fragment tag. */
    private final Map<String, Fields> mFieldsMap = new HashMap<>();
    /** Database Access. */
    protected DAO mDb;
    /** Prevent needless re-init of fields after a fragment was recreated (screen rotation etc.) */
    private boolean mFieldsAreInitialised;
    /** Track on which cover view the context menu was used. */
    @IntRange(from = -1)
    private int mCurrentCoverHandlerIndex = -1;

    @Override
    @CallSuper
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
        }
    }

    /**
     * Pseudo constructor.
     */
    @CallSuper
    public void init() {
        if (mDb == null) {
            mDb = new DAO(TAG);
        }
    }

    public DAO getDb() {
        return mDb;
    }

    @NonNull
    public Fields getFields(@Nullable final String key) {
        Fields fields;
        synchronized (mFieldsMap) {
            fields = mFieldsMap.get(key);
            if (fields == null) {
                fields = new Fields();
                mFieldsMap.put(key, fields);
            }
        }
        return fields;
    }

    public boolean shouldInitFields() {
        return !mFieldsAreInitialised;
    }

    public void setFieldsAreInitialised() {
        mFieldsAreInitialised = true;
    }

    /**
     * Retrieve the previously set cover handler index.
     * This is destructive: the value will be reset to -1 immediately.
     *
     * @return the index; will be {@code -1} if none was set.
     */
    @IntRange(from = -1)
    public int getAndClearCurrentCoverHandlerIndex() {
        final int current = mCurrentCoverHandlerIndex;
        mCurrentCoverHandlerIndex = -1;
        return current;
    }

    /**
     * Set the current cover handler index.
     * Call this before starting the camera and similar actions.
     *
     * @param index to preserve
     */
    public void setCurrentCoverHandlerIndex(@IntRange(from = 0) final int index) {
        mCurrentCoverHandlerIndex = index;
    }
}
