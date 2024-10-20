/*
 * @Copyright 2018-2024 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.bookreadstatus;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

/**
 * Common interface for the show- and edit-book screens ViewModel's.
 */
public interface BookReadStatusViewModel {

    /**
     * Get the Read/Unread status of the book we're currently displaying/editing.
     *
     * @return flag
     */
    boolean isRead();

    /**
     * Set the Read/Unread status of the book we're currently displaying/editing.
     * <p>
     * If set to {@code true}, the read-end date will be set to {@code now}.
     *
     * @param read flag
     *
     * @see #onReadStatusChanged()
     */
    void setReadNow(boolean read);

    /**
     * Get the ReadingProgress status of the book we're currently displaying/editing.
     *
     * @return progress
     */
    @NonNull
    ReadingProgress getReadingProgress();

    /**
     * Set the ReadingProgress status of the book we're currently displaying/editing.
     * <p>
     * If set to {@code true}, the read-end date will be set to {@code now}.
     *
     * @param readingProgress to set
     *
     * @see #onReadStatusChanged()
     */
    void setReadingProgress(@NonNull ReadingProgress readingProgress);

    /**
     * Trigger a call to {@link #onReadStatusChanged()}.
     */
    void readStatusChanged();

    /**
     * Triggered after a call to
     * {@link #setReadNow(boolean)},
     * {@link #setReadingProgress(ReadingProgress)} or
     * {@link #readStatusChanged()}.
     *
     * @return void; indicates the UI should update the read-status related fields.
     *
     * @see #setReadNow(boolean)
     * @see #setReadingProgress(ReadingProgress)
     */
    @NonNull
    MutableLiveData<Void> onReadStatusChanged();
}
