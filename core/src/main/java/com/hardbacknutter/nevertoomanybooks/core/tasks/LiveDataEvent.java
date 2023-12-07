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
package com.hardbacknutter.nevertoomanybooks.core.tasks;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

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
 *
 * @param <T> type of payload
 */
public final class LiveDataEvent<T> {

    @Nullable
    private final T data;
    private boolean processed;

    /**
     * Private constructor. Use factory methods instead.
     *
     * @param data the payload
     */
    private LiveDataEvent(@Nullable final T data) {
        this.data = data;
    }

    /**
     * Create a LiveDataEvent with an optional payload.
     * <p>
     * Used for operation {@link MTask#onCancelled()}.
     *
     * @param data the payload
     * @param <T>  type of payload
     *
     * @return LiveDataEvent
     */
    @NonNull
    static <T> LiveDataEvent<T> ofNullable(@Nullable final T data) {
        return new LiveDataEvent<>(data);
    }

    /**
     * Create a LiveDataEvent with a valid non-null payload.
     * <p>
     * Used for operations {@link MTask#onFinished()} and {@link MTask#onFailure()}.
     *
     * @param data the payload
     * @param <T>  type of payload
     *
     * @return LiveDataEvent
     *
     * @throws NullPointerException if the payload is {@code null}
     */
    @NonNull
    public static <T> LiveDataEvent<T> of(@NonNull final T data) {
        Objects.requireNonNull(data);
        return new LiveDataEvent<>(data);
    }

    /**
     * Process the payload.
     * <p>
     * For a {@link MTask#onFinished()} and {@link MTask#onFailure()},
     * the payload will never be {@code null}.
     * <p>
     * For a {@link MTask#onCancelled()}, the payload <strong>may</strong> be {@code null}.
     * <p>
     * The payload is <strong>only</strong> passed to the given Consumer the first time
     * this method is called. Subsequent calls are simply ignored.
     *
     * @param consumer to process the payload
     */
    public void process(@NonNull final Consumer<T> consumer) {
        if (!processed) {
            this.processed = true;
            consumer.accept(data);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "LiveDataEvent{"
               + "processed=" + processed
               + ", data=" + data
               + '}';
    }
}
