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

/**
 * Value class holding Task Result data.
 *
 * @param <Result> type of the payload
 */
public class TaskResult<Result> {

    private static final String MISSING_TASK_RESULTS = "message.result";

    /**
     * The result object from the task.
     * It can be {@code null} regardless of the task implementation.
     */
    @Nullable
    private final Result result;

    public TaskResult(@Nullable final Result result) {
        this.result = result;
    }

    /**
     * Data MIGHT be present.
     *
     * @return Result or {@code null}
     */
    @Nullable
    public Result getResult() {
        return result;
    }

    /**
     * Data WILL/MUST be present.
     *
     * @return Result
     */
    @NonNull
    public Result requireResult() {
        return Objects.requireNonNull(result, MISSING_TASK_RESULTS);
    }

    @Override
    @NonNull
    public String toString() {
        return "TaskResult{"
               + "result=" + result
               + '}';
    }
}
