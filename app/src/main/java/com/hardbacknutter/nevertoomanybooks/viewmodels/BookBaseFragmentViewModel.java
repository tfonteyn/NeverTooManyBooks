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

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.hardbacknutter.nevertoomanybooks.BookBaseFragment;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.fields.Fields;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;

/**
 * Used by the set of fragments that allow viewing and editing a Book.
 */
public abstract class BookBaseFragmentViewModel
        extends ViewModel
        implements BookBaseFragment.FieldsViewModel {

    /** Log tag. */
    private static final String TAG = "BookBaseFragmentVM";

    private final MutableLiveData<String> mUserMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mNeedsGoodreads = new MutableLiveData<>();

    /** The fields collection handled in this model. */
    private final Fields mFields = new Fields();
    /** Database Access. */
    protected DAO mDb;
    /** Track on which cover view the context menu was used. */
    private int mCurrentCoverHandlerIndex = -1;

    /** Lazy init, always use {@link #getGoodreadsTaskListener(Context)}. */
    private TaskListener<GrStatus> mGoodreadsTaskListener;

    @Override
    @CallSuper
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
        }
    }

    @CallSuper
    public void init(@Nullable final Bundle args) {
        if (mDb == null) {
            mDb = new DAO(TAG);
        }
    }

    @NonNull
    public DAO getDb() {
        return mDb;
    }

    @NonNull
    public Fields getFields() {
        return mFields;
    }

    public int getCurrentCoverHandlerIndex() {
        return mCurrentCoverHandlerIndex;
    }

    public void setCurrentCoverHandlerIndex(final int currentCoverHandlerIndex) {
        mCurrentCoverHandlerIndex = currentCoverHandlerIndex;
    }

    /**
     * Called when a task wants to display a user message.
     *
     * @return Observable: string to display
     */
    @NonNull
    public MutableLiveData<String> onUserMessage() {
        return mUserMessage;
    }

    /**
     * Called when a task needs Goodreads access, and current has no access.
     *
     * @return Observable: {@code true} when access is needed
     */
    @NonNull
    public MutableLiveData<Boolean> onNeedsGoodreads() {
        return mNeedsGoodreads;
    }

    @NonNull
    public TaskListener<GrStatus> getGoodreadsTaskListener(@NonNull final Context context) {
        if (mGoodreadsTaskListener == null) {
            mGoodreadsTaskListener = new TaskListener<GrStatus>() {

                @Override
                public void onFinished(@NonNull final FinishMessage<GrStatus> message) {
                    String msg = GoodreadsHandler.handleResult(context, message);
                    if (msg != null) {
                        // success, failure, cancelled
                        mUserMessage.setValue(msg);
                    } else {
                        // needs Registration
                        mNeedsGoodreads.setValue(true);
                    }
                }

                @Override
                public void onProgress(@NonNull final ProgressMessage message) {
                    if (message.text != null) {
                        mUserMessage.setValue(message.text);
                    }
                }
            };
        }
        return mGoodreadsTaskListener;
    }

}
