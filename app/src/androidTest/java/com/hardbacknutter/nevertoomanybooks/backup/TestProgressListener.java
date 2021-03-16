/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;

public class TestProgressListener
        implements ProgressListener {

    @NonNull
    private final String mTag;
    private int mProgressCurrentPos;
    private int mProgressMaxPos;

    public TestProgressListener(@NonNull final String tag) {
        mTag = tag;
    }

    @Override
    public void publishProgress(final int delta,
                                @Nullable final String message) {
        mProgressCurrentPos += delta;
        // eat all message when in debug; it's to much of a slow down otherwise.
        if (BuildConfig.DEBUG) {
            Log.d(mTag + "|publishProgressStep",
                  "mProgressCurrentPos=" + mProgressCurrentPos
                  + "|delta=" + delta
                  + "|message=" + message);
        }

    }

    @Override
    public void publishProgress(@NonNull final ProgressMessage message) {
        // eat all message when in debug; it's to much of a slow down otherwise.
        if (BuildConfig.DEBUG) {
            Log.d(mTag + "|publishProgress", "message=" + message);
        }
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public void setIndeterminate(@Nullable final Boolean indeterminate) {
        Log.d(mTag + "|setProgressIsIndeterminate", "" + indeterminate);
    }

    @Override
    public int getMaxPos() {
        return mProgressMaxPos;
    }

    @Override
    public void setMaxPos(final int maxPosition) {
        Log.d(mTag + "|setProgressMaxPos", "" + maxPosition);
        mProgressMaxPos = maxPosition;
    }
}
