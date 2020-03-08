/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
 * Sending progress messages:
 *
 * <ol>
 *     <li>A class method which runs as part of a background task calls {@link com.hardbacknutter.nevertoomanybooks.tasks.TaskBase#getProgressListener()} to get a {@link com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener}</li>
 *     <li>Calls one of the {@link com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener#onProgress} methods to send a counter + message String</li>
 *     <li>CRUCIAL: The listener (running in the background) uses {@link android.os.AsyncTask}#publishProgress to get the message to the foreground thread</li>
 *     <li>{@link com.hardbacknutter.nevertoomanybooks.tasks.TaskBase#onProgressUpdate(com.hardbacknutter.nevertoomanybooks.tasks.TaskListener.ProgressMessage...)} now forwards it to the {@link com.hardbacknutter.nevertoomanybooks.tasks.TaskListener}</li>
 *     <li>{@link com.hardbacknutter.nevertoomanybooks.tasks.TaskListener#onProgress(com.hardbacknutter.nevertoomanybooks.tasks.TaskListener.ProgressMessage)} takes the message and sets it on the ViewModel LiveData variable.</li>
 *     <li>LiveData sends it to the observer, which in turn displays it.</li>
 * </ol>
 * <p>
 * The progress state must be held in the {@link com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener}.
 * <p>
 * The {@link com.hardbacknutter.nevertoomanybooks.tasks.TaskListener} could accumulate the progress state,
 * or could leave that up to the final destination object.
 * <p>
 * In a nutshell: ProgressListener -> ASyncTask -> TaskListener -> LiveData -> User
 */
package com.hardbacknutter.nevertoomanybooks.tasks;
