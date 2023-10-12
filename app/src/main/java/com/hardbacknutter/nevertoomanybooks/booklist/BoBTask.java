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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.SearchCriteria;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.FtsMatchFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.NumberListFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PEntityListFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.AuthorDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.FtsDaoHelper;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreHandler;
import com.hardbacknutter.nevertoomanybooks.tasks.MTask;

/**
 * Logic flow is as follows.
 * <p>
 * BoBTask -> BooklistBuilder -> TableBuilder -> BooklistBuilder -> Booklist -> BoBTask.
 */
public class BoBTask
        extends MTask<BoBTask.Outcome> {

    /** Log tag. */
    private static final String TAG = "BoBTask";

    private static final String LOAN_FILTER =
            "EXISTS(SELECT NULL FROM " + DBDefinitions.TBL_BOOK_LOANEE.ref()
            + " WHERE " + DBDefinitions.TBL_BOOK_LOANEE.dot(DBKey.LOANEE_NAME) + "='%1$s'"
            + " AND " + DBDefinitions.TBL_BOOK_LOANEE.fkMatch(DBDefinitions.TBL_BOOKS) + ')';

    /** Currently selected bookshelf. */
    private Bookshelf bookshelf;
    /** Preferred booklist state in next rebuild. */
    private RebuildBooklist rebuildMode;
    /** Holder for all search criteria. See {@link SearchCriteria} for more info. */
    private SearchCriteria searchCriteria;
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

    @VisibleForTesting
    @NonNull
    public static List<DomainExpression> createDomainExpressions(@NonNull final String dbKey,
                                                                 @NonNull final Sort sort,
                                                                 @NonNull final Style style) {

        if (DBKey.COVER[0].equals(dbKey)
            || DBKey.COVER[1].equals(dbKey)) {
            // We need the (unsorted duh!) UUID for the book to get covers
            return List.of(new DomainExpression(
                    DBDefinitions.DOM_BOOK_UUID,
                    DBDefinitions.TBL_BOOKS,
                    Sort.Unsorted));
        } else {
            switch (dbKey) {
                case DBKey.BOOK_CONDITION: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOK_CONDITION,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.BOOK_ISBN: {
                    return List.of(
                            new DomainExpression(DBDefinitions.DOM_BOOK_ISBN,
                                                 DBDefinitions.TBL_BOOKS,
                                                 Sort.Unsorted)
                    );
                }
                case DBKey.BOOK_PUBLICATION__DATE: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOK_DATE_PUBLISHED,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.EDITION__BITMASK: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOK_EDITION,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.FK_AUTHOR: {
                    return List.of(
                            // primary author only
                            new DomainExpression(
                                    DBDefinitions.DOM_AUTHOR_FORMATTED_FAMILY_FIRST,
                                    AuthorDaoImpl.getDisplayDomainExpression(
                                            style.isShowAuthorByGivenName()),
                                    sort)
                    );
                }
                case DBKey.FK_BOOKSHELF: {
                    return List.of(
                            // Collect a CSV list of the bookshelves the book is on.
                            // It is ALWAYS unsorted, as the list is build by SQLite internals
                            // and the order returned is arbitrary.
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOKSHELF_NAME_CSV,
                                    DBDefinitions.EXP_BOOKSHELF_NAME_CSV,
                                    Sort.Unsorted)
                    );
                }
                case DBKey.FK_PUBLISHER: {
                    return List.of(
                            // primary publisher only
                            new DomainExpression(
                                    DBDefinitions.DOM_PUBLISHER_NAME,
                                    DBDefinitions.TBL_PUBLISHERS,
                                    sort)
                    );
                }
                case DBKey.FK_SERIES: {
                    return List.of(
                            // primary series only
                            new DomainExpression(
                                    DBDefinitions.DOM_SERIES_TITLE,
                                    DBDefinitions.TBL_SERIES,
                                    sort),
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOK_NUM_IN_SERIES,
                                    DBDefinitions.TBL_BOOK_SERIES,
                                    sort)
                    );
                }
                case DBKey.FORMAT: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOK_FORMAT,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.LANGUAGE: {
                    return List.of(
                            new DomainExpression(DBDefinitions.DOM_BOOK_LANGUAGE,
                                                 DBDefinitions.TBL_BOOKS,
                                                 Sort.Unsorted)
                    );
                }
                case DBKey.LOANEE_NAME: {
                    return List.of(
                            // Used to display/hide the 'lend' icon for each book.
                            new DomainExpression(
                                    DBDefinitions.DOM_LOANEE,
                                    DBDefinitions.TBL_BOOK_LOANEE,
                                    sort)
                    );
                }
                case DBKey.LOCATION: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOK_LOCATION,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.PAGE_COUNT: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOK_PAGES,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.RATING: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOK_RATING,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.READ__BOOL: {
                    return List.of(
                            new DomainExpression(DBDefinitions.DOM_BOOK_READ,
                                                 DBDefinitions.TBL_BOOKS,
                                                 Sort.Unsorted)
                    );
                }
                case DBKey.SIGNED__BOOL: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOK_SIGNED,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.TITLE: {
                    return List.of(
                            // Title for displaying; do NOT sort on it
                            // Example: "The Dream Master"
                            new DomainExpression(
                                    DBDefinitions.DOM_TITLE,
                                    DBDefinitions.TBL_BOOKS,
                                    Sort.Unsorted),
                            // Title for sorting
                            // Example: "dreammasterthe" OR "thedreammaster"
                            // i.e. depending on user preference, the first format
                            // consists of the original title stripped of whitespace and any
                            // special characters, and with the article/prefix moved to the end.
                            // The second format leaves the article/prefix in its original
                            // location.
                            // The choice between the two formats is a user preference which,
                            // when changed, updates ALL rows in the database with the
                            // newly formatted title.
                            new DomainExpression(
                                    DBDefinitions.DOM_TITLE_OB,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.TITLE_ORIGINAL_LANG: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_TITLE_ORIGINAL_LANG,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }

                default:
                    throw new IllegalArgumentException("DBKey missing: " + dbKey);
            }
        }
    }

    /**
     * Start the task.
     *
     * @param bookshelf            the shelf for which we're building the list
     * @param mode                 see {@link RebuildBooklist}
     * @param searchCriteria       filters
     * @param desiredCentralBookId the book id we want the new list to display
     *                             more-or-less in the center of the screen
     */
    public void build(@NonNull final Bookshelf bookshelf,
                      @NonNull final RebuildBooklist mode,
                      @NonNull final SearchCriteria searchCriteria,
                      final long desiredCentralBookId) {
        this.bookshelf = bookshelf;
        rebuildMode = mode;
        this.searchCriteria = searchCriteria;
        this.desiredCentralBookId = desiredCentralBookId;

        execute();
    }

    @NonNull
    @Override
    @WorkerThread
    protected Outcome doWork() {
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();
        final SynchronizedDb db = ServiceLocator.getInstance().getDb();

        final Style style = bookshelf.getStyle();

        final BooklistBuilder builder = new BooklistBuilder(db, style, bookshelf, rebuildMode);

        // Always get the UUID.
        builder.addDomain(new DomainExpression(DBDefinitions.DOM_BOOK_UUID,
                                               DBDefinitions.TBL_BOOKS,
                                               Sort.Unsorted));

        // Always add the the book language.
        // It is needed for reordering titles in BooklistGroup rows.
        builder.addDomain(new DomainExpression(DBDefinitions.DOM_BOOK_LANGUAGE,
                                               DBDefinitions.TBL_BOOKS,
                                               Sort.Unsorted));
        // Always get the read flag.
        builder.addDomain(new DomainExpression(DBDefinitions.DOM_BOOK_READ,
                                               DBDefinitions.TBL_BOOKS,
                                               Sort.Unsorted));
        // Always get the ISBN.
        builder.addDomain(new DomainExpression(DBDefinitions.DOM_BOOK_ISBN,
                                               DBDefinitions.TBL_BOOKS,
                                               Sort.Unsorted));

        // Always get the Author ID (the need for the name will depend on the style).
        builder.addDomain(new DomainExpression(DBDefinitions.DOM_FK_AUTHOR,
                                               DBDefinitions.TBL_BOOK_AUTHOR,
                                               Sort.Unsorted));

        // The domains for the book level, visibility and ordering according to style.
        // WARNING: the field {@link DBKey#LOANEE_NAME} requires a {@code LEFT JOIN}
        // with {@link DBDefinitions#TBL_BOOK_LOANEE}. See below
        style.getBookLevelFieldsOrderBy().entrySet()
             .stream()
             .filter(field -> style.isShowField(FieldVisibility.Screen.List, field.getKey()))
             .map(field -> createDomainExpressions(field.getKey(), field.getValue(),
                                                   style))
             .flatMap(List::stream)
             .forEach(builder::addDomain);

        if (style.isShowField(FieldVisibility.Screen.List, DBKey.LOANEE_NAME)) {
            builder.addLeftOuterJoin(DBDefinitions.TBL_BOOK_LOANEE);
        }

        // external site ID's; needed for the context menu "View on..."
        SearchEngineConfig.getExternalIdDomains()
                          .stream()
                          .map(domain -> new DomainExpression(
                                  domain, DBDefinitions.TBL_BOOKS.dot(domain),
                                  Sort.Unsorted))
                          .forEach(builder::addDomain);

        // If enabled, join with and add the Calibre fields.
        if (CalibreHandler.isSyncEnabled(context)) {
            builder.addLeftOuterJoin(DBDefinitions.TBL_CALIBRE_BOOKS);

            builder.addDomain(new DomainExpression(DBDefinitions.DOM_CALIBRE_BOOK_ID,
                                                   DBDefinitions.TBL_CALIBRE_BOOKS,
                                                   Sort.Unsorted));
            builder.addDomain(new DomainExpression(DBDefinitions.DOM_CALIBRE_BOOK_UUID,
                                                   DBDefinitions.TBL_CALIBRE_BOOKS,
                                                   Sort.Unsorted));
            builder.addDomain(new DomainExpression(DBDefinitions.DOM_CALIBRE_BOOK_MAIN_FORMAT,
                                                   DBDefinitions.TBL_CALIBRE_BOOKS,
                                                   Sort.Unsorted));
            builder.addDomain(new DomainExpression(DBDefinitions.DOM_FK_CALIBRE_LIBRARY,
                                                   DBDefinitions.TBL_CALIBRE_BOOKS,
                                                   Sort.Unsorted));
        }

        if (!searchCriteria.isEmpty()) {
            addCriteria(builder);
        }

        addFilters(context, builder, style);

        Booklist booklist = null;
        try {
            // Build the underlying data
            booklist = builder.build(context);

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


    private void addCriteria(@NonNull final BooklistBuilder builder) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            LoggerFactory.getLogger().d(TAG, "addCriteria", "searchCriteria=" + searchCriteria);
        }

        // if we have a list of ID's, we'll ignore other criteria
        if (searchCriteria.getBookIdList().isEmpty()) {
            // Criteria supported by FTS
            FtsDaoHelper.createMatchClause(searchCriteria.getFtsBookTitle(),
                                           searchCriteria.getFtsSeriesTitle(),
                                           searchCriteria.getFtsAuthor(),
                                           searchCriteria.getFtsPublisher(),
                                           searchCriteria.getFtsKeywords())
                        .map(FtsMatchFilter::new)
                        .ifPresent(builder::addFilter);

            // Add a filter to retrieve only books lend to the given person (exact name).
            // We want to use the exact string, so do not normalize the value,
            // but we do need to handle single quotes as we are concatenating.
            final String loanee = searchCriteria.getLoanee();
            if (loanee != null && !loanee.isBlank()) {
                builder.addFilter(c -> String.format(LOAN_FILTER, SqlEncode.singleQuotes(loanee)));
            }
        } else {
            // Add a where clause for: "AND books._id IN (list)".
            builder.addFilter(new NumberListFilter<>(
                    DBDefinitions.TBL_BOOKS, DBDefinitions.DOM_PK_ID,
                    searchCriteria.getBookIdList()));
        }

        // when criteria are used, the build should expand the book list.
        builder.setRebuildMode(RebuildBooklist.Expanded);
    }

    private void addFilters(@NonNull final Context context,
                            @NonNull final BooklistBuilder builder,
                            @NonNull final Style style) {
        // Prepare the Bookshelf filters
        final List<PFilter<?>> filters = bookshelf.getFilters();

        // Add a filter on the current Bookshelf?
        // Only consider doing this if this is NOT the "All books" Bookshelf
        if (!bookshelf.isAllBooks()) {
            // and only if the current style does NOT contain the Bookshelf group.
            if (!style.hasGroup(BooklistGroup.BOOKSHELF)) {
                // do we already have a Bookshelf based filter?
                final Optional<PFilter<?>> bookshelfFilter = filters
                        .stream()
                        .filter(pFilter -> DBKey.FK_BOOKSHELF.equals(pFilter.getDBKey()))
                        .findFirst();

                if (bookshelfFilter.isPresent()) {
                    // Add the current Bookshelf to the existing filter.
                    final PEntityListFilter<?> pFilter = (PEntityListFilter<?>)
                            bookshelfFilter.get();

                    final Set<Long> list = new HashSet<>(pFilter.getValue());
                    list.add(bookshelf.getId());
                    pFilter.setValue(list);

                } else {
                    // Filter on the current one only
                    builder.addFilter(new NumberListFilter<>(DBDefinitions.TBL_BOOKSHELF,
                                                             DBDefinitions.DOM_PK_ID,
                                                             bookshelf.getId()));
                }
            }
        }

        // ... and add them
        builder.addFilter(filters.stream()
                                 .filter(f -> f.isActive(context))
                                 .collect(Collectors.toList()));
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
    }
}
