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
package com.eleybourn.bookcatalogue.utils;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.AdapterView;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;

import net.philipwarner.taskqueue.ContextDialogItem;
import net.philipwarner.taskqueue.LegacyEvent;
import net.philipwarner.taskqueue.LegacyTask;
import net.philipwarner.taskqueue.QueueManager;

import java.util.ArrayList;

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

    private static final long CAT_LEGACY = 1;
    public static final long CAT_GOODREADS_AUTH = 2;
    public static final long CAT_GOODREADS_IMPORT_ALL = 3;
    public static final long CAT_GOODREADS_EXPORT_ALL = 4;
    public static final long CAT_GOODREADS_EXPORT_ONE = 5;

    public BCQueueManager() {
        super(BookCatalogueApp.getAppContext());
        initializeQueue(QUEUE_MAIN);
        initializeQueue(QUEUE_SMALL_JOBS);
    }

    /**
     * Create the queue we need, if they do not already exist.
     *
     * main: long-running tasks, or tasks that can just wait
     * small_jobs: trivial background tasks that will only take a few seconds.
     */
    public BCQueueManager(@NonNull final Context context) {
        super(context);
        initializeQueue(QUEUE_MAIN);
        initializeQueue(QUEUE_SMALL_JOBS);
    }

//	/**
//	 * Create the queue we need, if they do not already exist.
//	 * 
//	 * main: long-running tasks, or tasks that can just wait
//	 * small_jobs: trivial background tasks that will only take a few seconds.
//	 */
//	@Override
//    public void onCreate() {
//		super.onCreate();
//
//		initializeQueue(QUEUE_MAIN);
//		initializeQueue(QUEUE_SMALL_JOBS);
//	}

    /**
     * Return a localized LegacyEvent for the passed blob.
     * This method is used when deserialization fails, most likely as a result of changes
     * to the underlying serialized class.
     */
    @Override
    @NonNull
    public LegacyEvent newLegacyEvent(byte[] original) {
        return new BCLegacyEvent(original);
    }

    /**
     * Return a localized current LegacyTask object.
     */
    @Override
    @NonNull
    public LegacyTask newLegacyTask(byte[] original) {
        return new BCLegacyTask(original, BookCatalogueApp.getResourceString(R.string.legacy_task));
    }

    @Override
    @NonNull
    public Context getApplicationContext() {
        return BookCatalogueApp.getAppContext();
    }

    /**
     * The only reason that this class has to be implemented in the client application is
     * so that the call to addContextMenuItems(...) can return a LOCALIZED context menu.
     *
     * @author Philip Warner
     */
    public class BCLegacyEvent extends LegacyEvent {
        private static final long serialVersionUID = 1992740024689009867L;

        BCLegacyEvent(byte[] original) {
            super(original, "Legacy Event");
        }

        @Override
        public void addContextMenuItems(final Context ctx, final AdapterView<?> parent,
                                        final View v, final int position, final long id,
                                        final ArrayList<ContextDialogItem> items,
                                        final Object appInfo) {

            items.add(new ContextDialogItem(ctx.getString(R.string.delete_event), new Runnable() {
                @Override
                public void run() {
                    QueueManager.getQueueManager().deleteEvent(BCLegacyEvent.this.getId());
                }
            }));

        }
    }

    /**
     * The only reason that this class has to be implemented in the client application is
     * so that the call to addContextMenuItems(...) can return a LOCALIZED context menu.
     *
     * @author Philip Warner
     */
    public class BCLegacyTask extends LegacyTask {
        private static final long serialVersionUID = 164669981603757736L;

        BCLegacyTask(byte[] original, String description) {
            super(original, description);
        }

        @Override
        public void addContextMenuItems(final Context ctx, final AdapterView<?> parent,
                                        final View v, final int position, final long id,
                                        final  ArrayList<ContextDialogItem> items,
                                        final Object appInfo) {

            items.add(new ContextDialogItem(ctx.getString(R.string.delete_task), new Runnable() {
                @Override
                public void run() {
                    QueueManager.getQueueManager().deleteTask(BCLegacyTask.this.getId());
                }
            }));

        }

        @Override
        @NonNull
        public String getDescription() {
            return BookCatalogueApp.getResourceString(R.string.unrecognized_task);
        }

        @Override
        public long getCategory() {
            return CAT_LEGACY;
        }
    }
}
