/*
 * @Copyright 2018-2025 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.util.Pair;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.Filter;
import com.hardbacknutter.nevertoomanybooks.booklist.header.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.MTask;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.util.logger.LoggerFactory;

public class BoBTask
        extends MTask<BoBTask.Outcome> {

    /**
     * Counter for generating ID's. Only increments.
     * Used to create unique names for the temporary tables.
     */
    @NonNull
    private static final AtomicInteger ID_COUNTER = new AtomicInteger();
    /** Log tag. */
    private static final String TAG = "BoBTask";

    /** Currently selected bookshelf. */
    private Bookshelf bookshelf;
    /** Preferred booklist state in next rebuild. */
    private RebuildBooklist rebuildMode;
    /** Search Filters. */
    private Collection<Filter> criteriaFilters;
    /** The row id we want the new list to display more-or-less in the center. */
    private long desiredCentralBookId;

    /**
     * Constructor.
     */
    public BoBTask() {
        super(R.id.TASK_ID_BOOKLIST_BUILDER, TAG);
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            LoggerFactory.getLogger().d(TAG, "NEW TASK_ID_BOOKLIST_BUILDER");
        }
    }

    /**
     * Start the task.
     *
     * @param bookshelf            the shelf for which we're building the list
     * @param mode                 see {@link RebuildBooklist}
     * @param criteriaFilters      filters
     * @param desiredCentralBookId the book id we want the new list to display
     *                             more-or-less in the center of the screen
     */
    public void start(@NonNull final Bookshelf bookshelf,
                      @NonNull final RebuildBooklist mode,
                      @NonNull final Collection<Filter> criteriaFilters,
                      final long desiredCentralBookId) {
        this.bookshelf = bookshelf;
        this.rebuildMode = mode;
        this.criteriaFilters = criteriaFilters;
        this.desiredCentralBookId = desiredCentralBookId;

        execute();
    }

    @Override
    @WorkerThread
    @NonNull
    protected Outcome doWork() {
        final Style style = bookshelf.getStyle();

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            LoggerFactory.getLogger().d(TAG, "doWork",
                                        "bookshelf=`" + bookshelf.getName() + '`',
                                        "style=" + style.getUuid(),
                                        "instances: "
                                        + Booklist.DEBUG_INSTANCE_COUNTER.incrementAndGet(),
                                        new Throwable());
        }

        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();
        final SynchronizedDb db = ServiceLocator.getInstance().getDb();

        Booklist booklist = null;
        try {
            booklist = buildBooklist(context, db, bookshelf, style,
                                     rebuildMode,
                                     criteriaFilters);

            // pre-count and cache these while we're in the background.
            // They are used for the header, and will not change even if the list cursor changes.
            if (style.isShowHeaderField(BooklistHeader.SHOW_BOOK_COUNT)) {
                booklist.countBooks();
                booklist.countDistinctBooks();
            }

            // Get the row(s) which will be used to determine new cursor position
            return new Outcome(booklist, booklist.getVisibleBookNodes(desiredCentralBookId));

        } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final Exception e) {
            if (booklist != null) {
                booklist.close();
            }
            throw e;
        }
    }

    /**
     * Build the temporary list of books.
     *
     * @param context         Current context
     * @param db              db
     * @param bookshelf       the shelf for which we're building the list
     * @param style           to use
     * @param rebuildMode     see {@link RebuildBooklist}
     * @param criteriaFilters filters
     *
     * @return the Booklist ready to use
     *
     * @throws IllegalArgumentException if the rebuild-mode is unknown
     */
    @VisibleForTesting
    @NonNull
    Booklist buildBooklist(@NonNull final Context context,
                           @NonNull final SynchronizedDb db,
                           @NonNull final Bookshelf bookshelf,
                           @NonNull final Style style,
                           @NonNull final RebuildBooklist rebuildMode,
                           @NonNull final Collection<Filter> criteriaFilters) {

        final int instanceId = ID_COUNTER.incrementAndGet();

        final BooklistBuilder booklistBuilder = new BooklistBuilder(instanceId, style,
                                                                    bookshelf);

        final Synchronizer.SyncLock txLock = db.beginTransaction(true);
        try {
            // Construct the list table and all needed structures.
            final Pair<TableDefinition, TableDefinition> tables = booklistBuilder
                    .build(context, db, rebuildMode, criteriaFilters);

            final TableDefinition listTable = tables.first;
            final TableDefinition navTable = tables.second;

            final BooklistNodeDao rowStateDao = createNodeDao(db, listTable, bookshelf, style,
                                                              rebuildMode);

            db.setTransactionSuccessful();

            return new Booklist(instanceId, db, listTable, navTable, rowStateDao);

        } finally {
            db.endTransaction(txLock);
        }
    }

    @NonNull
    private BooklistNodeDao createNodeDao(@NonNull final SynchronizedDb db,
                                          @NonNull final TableDefinition listTable,
                                          @NonNull final Bookshelf bookshelf,
                                          @NonNull final Style style,
                                          @NonNull final RebuildBooklist rebuildMode) {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final BooklistNodeDao rowStateDao = new BooklistNodeDao(db, listTable, style, bookshelf);

        switch (rebuildMode) {
            case FromSaved:
                // all rows will be collapsed/hidden; restore the saved state
                rowStateDao.restoreSavedState();
                break;

            case Preferred:
                // all rows will be collapsed/hidden; now adjust as required.
                rowStateDao.setAllNodes(style.getExpansionLevel(), false);
                break;

            case Expanded:
            case Collapsed:
                // handled during table creation
                break;

            default:
                throw new IllegalArgumentException(String.valueOf(rebuildMode));
        }
        return rowStateDao;
    }

    public static class Outcome {

        @NonNull
        private final Booklist booklist;
        @NonNull
        private final List<BooklistNode> targetNodes;

        Outcome(@NonNull final Booklist booklist,
                @NonNull final List<BooklistNode> targetNodes) {
            this.booklist = booklist;
            this.targetNodes = targetNodes;
        }

        /**
         * The resulting list.
         *
         * @return list
         */
        @NonNull
        public Booklist getList() {
            return booklist;
        }

        /**
         * One or more nodes representing the book which we should try and scroll-to/display.
         *
         * @return nodes
         */
        @NonNull
        public List<BooklistNode> getTargetNodes() {
            return targetNodes;
        }

        @Override
        @NonNull
        public String toString() {
            return "Outcome{"
                   + "booklist=" + booklist
                   + ", targetNodes=" + targetNodes
                   + '}';
        }
    }
}
