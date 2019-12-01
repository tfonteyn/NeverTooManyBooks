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
package com.hardbacknutter.nevertoomanybooks.goodreads.tasks;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;

/** FIXME: Not exactly a clean bit of code. REALLY should not do this. */
public class GoodreadsTaskListener
        implements TaskListener<Integer> {

    private final MutableLiveData<String> mUserMessage;
    private final MutableLiveData<Boolean> mNeedsGoodreads;

    public GoodreadsTaskListener(@NonNull final MutableLiveData<String> userMessage,
                                 @NonNull final MutableLiveData<Boolean> needsGoodreads) {
        mUserMessage = userMessage;
        mNeedsGoodreads = needsGoodreads;
    }

    @Override
    public void onFinished(@NonNull final FinishMessage<Integer> message) {
        Context localContext = App.getLocalizedAppContext();
        switch (message.status) {
            case Success:
            case Failed: {
                String msg = GoodreadsTasks.handleResult(localContext, message);
                if (msg != null) {
                    mUserMessage.setValue(msg);
                } else {
                    // Need authorization
                    mNeedsGoodreads.setValue(true);
                }
                break;
            }
            case Cancelled: {
                mUserMessage.setValue(
                        localContext.getString(R.string.progress_end_cancelled));
                break;
            }
        }
    }

    @Override
    public void onProgress(@NonNull final ProgressMessage message) {
        if (message.text != null) {
            mUserMessage.setValue(message.text);
        }
    }
}
