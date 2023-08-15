/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.tasks;

import androidx.annotation.NonNull;

import java.util.Optional;

/**
 * TODO: our use of observables is not always best-practice. Maybe look at RxJava
 * <p>
 * Prevent acting twice on a delivered {@link androidx.lifecycle.LiveData} event.
 * <p>
 * See <a href="https://medium.com/androiddevelopers/ac2622673150">this Medium post</a>
 * <p>
 * <a href="https://medium.com/androiddevelopers/viewmodel-one-off-event-antipatterns-16a1da869b95">
 * *     ViewModel: One-off event anti-patterns</a>
 * <p>
 * <a href = "https://developer.android.com/topic/architecture/ui-layer/events#consuming-trigger-updates">
 * consuming-trigger-updates</a>
 * Problem: the UI layer must perform a handshake with the VM for each and every UI update.
 */
public class LiveDataEvent<T> {

    @NonNull
    private final T data;
    private boolean hasBeenHandled;

    public LiveDataEvent(@NonNull final T data) {
        this.data = data;
    }

    /**
     * Get the payload.
     * <p>
     * This method will return a {@code Optional.of(data)} the first time it's called.
     * Any subsequent calls will return an {@code Optional.empty()}.
     *
     * @return data as an Optional
     */
    @NonNull
    public Optional<T> getData() {
        if (hasBeenHandled) {
            return Optional.empty();
        } else {
            hasBeenHandled = true;
            return Optional.of(data);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "LiveDataEvent{"
               + "hasBeenHandled=" + hasBeenHandled
               + ", data=" + data
               + '}';
    }
}
