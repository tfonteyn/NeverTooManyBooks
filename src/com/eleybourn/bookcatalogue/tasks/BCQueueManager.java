/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.tasks;

import android.content.Context;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.taskqueue.LegacyTask;
import com.eleybourn.bookcatalogue.taskqueue.QueueManager;

/**
 * BookCatalogue implementation of QueueManager.
 *
 * This just implements the application-specific (and localized) versions of basic
 * QueueManager objects.
 *
 * @author Philip Warner
 */
public class BCQueueManager extends QueueManager {

    public static final String QUEUE_MAIN = "main";
    public static final String QUEUE_SMALL_JOBS = "small_jobs";

    // 0 is reserved for Legacy.
    public static final int CAT_GOODREADS_AUTH = 2;
    public static final int CAT_GOODREADS_IMPORT_ALL = 3;
    public static final int CAT_GOODREADS_EXPORT_ALL = 4;
    public static final int CAT_GOODREADS_EXPORT_ONE = 5;

    /**
     * Create the queue we need, if they do not already exist.
     *
     * main: long-running tasks, or tasks that can just wait
     * small_jobs: trivial background tasks that will only take a few seconds.
     */
    public BCQueueManager(final @NonNull Context applicationContext) {
        super(applicationContext);
        initializeQueue(QUEUE_MAIN);
        initializeQueue(QUEUE_SMALL_JOBS);
    }

    /**
     * Get a new Task object capable of representing a non-deserializable Task object.
     *
     * @return a localized current LegacyTask object.
     */
    @NonNull
    @Override
    public LegacyTask newLegacyTask() {
        return new LegacyTask();
    }

    @Override
    @NonNull
    protected Context getApplicationContext() {
        return BookCatalogueApp.getAppContext();
    }
}
