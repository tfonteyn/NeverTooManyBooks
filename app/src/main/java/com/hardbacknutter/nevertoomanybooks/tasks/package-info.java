/*
 * @Copyright 2020 HardBackNutter
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
 * July 2020: restructured tasks:
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.tasks.LTask}
 * extends {@link android.os.AsyncTask}.
 * They use an {@link com.hardbacknutter.nevertoomanybooks.tasks.TaskListener}.
 * <p>
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.tasks.VMTask}
 * extends {@link androidx.lifecycle.ViewModel}.
 * They use {@link androidx.lifecycle.MutableLiveData} and
 * {@link com.hardbacknutter.nevertoomanybooks.viewmodels.LiveDataEvent}.
 * <p>
 * <p>
 * In some other places, we use {@link android.os.AsyncTask} when there is no feedback required.
 * and the above is overkill.
 * - fire-and-forget type of tasks
 * - tasks that manage the result themselves
 */
package com.hardbacknutter.nevertoomanybooks.tasks;
