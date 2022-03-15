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
package com.hardbacknutter.nevertoomanybooks.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UncheckedSAXException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UncheckedStorageException;

public class FutureHttpGet<T> {

    private final int mTimeoutInMs;
    @NonNull
    private final Function<String, Optional<TerminatorConnection>> mConnectionProducer;

    @Nullable
    private Future<T> mFuture;

    public FutureHttpGet(@NonNull final
                         Function<String, Optional<TerminatorConnection>> connectionProducer,
                         final int timeoutInMs) {
        mConnectionProducer = connectionProducer;
        mTimeoutInMs = timeoutInMs;
    }

    @NonNull
    public T get(@NonNull final String url,
                 @NonNull final Function<TerminatorConnection, T> callable)
            throws StorageException,
                   CancellationException,
                   SocketTimeoutException,
                   IOException {
        try {
            mFuture = ASyncExecutor.SERVICE.submit(() -> {
                try (TerminatorConnection con = mConnectionProducer
                        .apply(url)
                        .orElseThrow(() -> new IOException("Connection failed url=" + url))) {
                    return callable.apply(con);
                }
            });
            return mFuture.get(mTimeoutInMs, TimeUnit.MILLISECONDS);

        } catch (@NonNull final ExecutionException e) {
            final Throwable cause = e.getCause();

            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof UncheckedStorageException) {
                //noinspection ConstantConditions
                throw (StorageException) cause.getCause();
            }
            if (cause instanceof UncheckedIOException) {
                //noinspection ConstantConditions
                throw (IOException) cause.getCause();
            }
            if (cause instanceof UncheckedSAXException) {
                final SAXException saxException = ((UncheckedSAXException) cause).getCause();
                // unwrap SAXException using getException() !
                final Exception saxCause = Objects.requireNonNull(saxException).getException();
                if (saxCause instanceof IOException) {
                    throw (IOException) saxCause;
                }
                if (saxCause instanceof StorageException) {
                    throw (StorageException) saxCause;
                }
                throw new IOException(saxCause);
            }

            throw new IOException(cause);

        } catch (@NonNull final RejectedExecutionException | InterruptedException e) {
            throw new IOException(e);

        } catch (@NonNull final TimeoutException e) {
            // re-throw as if it's coming from the network call.
            throw new SocketTimeoutException(e.getMessage());

        } finally {
            mFuture = null;
        }
    }

    public void cancel() {
        synchronized (this) {
            if (mFuture != null) {
                mFuture.cancel(true);
            }
        }
    }
}
