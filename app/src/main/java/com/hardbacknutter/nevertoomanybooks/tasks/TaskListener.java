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
package com.hardbacknutter.nevertoomanybooks.tasks;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;

/**
 * <strong>Warning:</strong> To prevent unintended garbage collection, you must store
 * a strong reference to the listener. Do NOT simply pass a lambda, or anonymous class.
 * <p>
 * The AsyncTask implementations as used, store only a WeakReference to the listener which is good.
 * We don't want them to store a strong reference, as that could/would lead to memory leaks.
 * <ul>This does mean that the creator of the AsyncTask must:
 *      <li>implemented the interface on the caller itself, and pass the caller; i.e. 'this'</li>
 *      <li>or create an instance variable in the caller of the listener type, and pass that</li>
 * </ul>
 *
 * @param <Result> type of the task result
 */
public interface TaskListener<Result> {

    /**
     * Called when a task finishes.
     */
    void onFinished(@NonNull FinishedMessage<Result> message);

    /**
     * Called when a task was cancelled.
     */
    void onCancelled(@NonNull FinishedMessage<Result> message);

    /**
     * Called when a task failed. The result is the Exception.
     */
    void onFailure(@NonNull FinishedMessage<Exception> message);

    /**
     * Progress messages.
     */
    default void onProgress(@NonNull final ProgressMessage message) {
        // ignore by default
    }
}
