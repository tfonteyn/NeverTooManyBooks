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

/**
 * March 2021: restructured tasks once again, dropping all android.os.ASyncTask usage:
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.tasks.LTask}
 * They use a {@link com.hardbacknutter.nevertoomanybooks.core.tasks.TaskListener}.
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.tasks.MTask}
 * They use {@link androidx.lifecycle.MutableLiveData} and
 * {@link com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent}.
 * <p>
 * In some other places, we use {@link java.util.concurrent.Executor} and
 * {@link android.os.Handler} directly and the above is overkill.
 * - fire-and-forget type of tasks
 * - tasks that manage the result themselves
 */
package com.hardbacknutter.nevertoomanybooks.tasks;
