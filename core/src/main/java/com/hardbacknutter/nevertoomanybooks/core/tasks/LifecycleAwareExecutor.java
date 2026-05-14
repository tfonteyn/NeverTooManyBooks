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
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import java.util.concurrent.ThreadPoolExecutor;

public final class LifecycleAwareExecutor
        implements DefaultLifecycleObserver {

    private final ThreadPoolExecutor executor;

    private LifecycleAwareExecutor(@NonNull final ThreadPoolExecutor executor) {
        this.executor = executor;
    }

    @NonNull
    public static LifecycleAwareExecutor attach(@NonNull final LifecycleOwner lifecycleOwner,
                                                @NonNull final ThreadPoolExecutor executor) {
        final LifecycleAwareExecutor wrapper = new LifecycleAwareExecutor(executor);
        lifecycleOwner.getLifecycle().addObserver(wrapper);
        return wrapper;
    }

    @Override
    public void onDestroy(@NonNull final LifecycleOwner lifecycleOwner) {
        executor.shutdownNow();
        lifecycleOwner.getLifecycle().removeObserver(this);
    }

    public void execute(@NonNull final Runnable task) {
        executor.execute(task);
    }
}


