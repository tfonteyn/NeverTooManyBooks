/*
 * @Copyright 2018-2026 HardBackNutter
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

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"WeakerAccess", "TypeMayBeWeakened", "JavaDoc"})
public class LifoThreadPoolExecutor
        extends ThreadPoolExecutor {

    static final String ERROR_EXECUTOR_IS_SHUT_DOWN = "Executor is shut down";
    static final String ERROR_TASK_QUEUE_IS_FULL = "Task queue is full";

    public LifoThreadPoolExecutor(final int corePoolSize,
                                  final int maximumPoolSize,
                                  final long keepAliveTime,
                                  @NonNull final TimeUnit unit,
                                  @NonNull final BlockingDeque<Runnable> workQueue,
                                  @NonNull final ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    @Override
    public void execute(@NonNull final Runnable command) {
        if (isShutdown()) {
            throw new RejectedExecutionException(ERROR_EXECUTOR_IS_SHUT_DOWN);
        }

        try {
            final BlockingDeque<Runnable> deque = (BlockingDeque<Runnable>) getQueue();

            // Push to the front of the deque for LIFO behaviour
            if (!deque.offerFirst(command)) {
                throw new RejectedExecutionException(ERROR_TASK_QUEUE_IS_FULL);
            }

            // Only call super.execute to trigger thread creation
            // IF core threads are not at capacity.
            // OR we already have a task waiting (other than this one)
            try {
                if (getPoolSize() < getCorePoolSize()
                    || getPoolSize() < getMaximumPoolSize() && deque.size() > 1) {
                    // no-op task; thread startup only
                    // worker will pick up the next real task from the queue
                    super.execute(() -> {
                    });
                }
            } catch (@NonNull final RejectedExecutionException ignored) {
                // shutdown between check and execute
            }

        } catch (@NonNull final ClassCastException e) {
            // Should never get here
            super.execute(command);
        }
    }
}
