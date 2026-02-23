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

package com.hardbacknutter.nevertoomanybooks;

import androidx.annotation.NonNull;
import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 equivalent to InstantTaskExecutorRule.
 * Forces Architecture Components to run everything synchronously.
 * <p>
 * Replaces:
 * <pre>
 *     {@code
 *         @org.junit.Rule
 *         public org.junit.rules.TestRule rule = new InstantTaskExecutorRule();
 *     }
 * </pre>
 */
public class InstantTaskExecutorExtension
        implements BeforeEachCallback, AfterEachCallback {
    @Override
    public void beforeEach(@NonNull final ExtensionContext context) {
        ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            @Override
            public void executeOnDiskIO(@NonNull final Runnable runnable) {
                runnable.run();
            }

            @Override
            public void postToMainThread(@NonNull final Runnable runnable) {
                runnable.run();
            }

            @Override
            public boolean isMainThread() {
                return true;
            }
        });
    }

    @Override
    public void afterEach(@NonNull final ExtensionContext context) {
        ArchTaskExecutor.getInstance().setDelegate(null);
    }
}
