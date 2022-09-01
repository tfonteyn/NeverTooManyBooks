/*
 * @Copyright 2018-2022 HardBackNutter
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
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.SearchCriteria;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.FtsMatchFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.NumberListFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PEntityListFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.AuthorDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainExpression;
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

    /** The fixed list of domains we always need for building the book list. */
    private final Collection<DomainExpression> fixedDomainList = new ArrayList<>();
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
        initFixedDomainList();
    }

    private void initFixedDomainList() {
        fixedDomainList.add(
                // Title for displaying; do NOT sort on it
                // Example: "The Dream Master"
                new DomainExpression(
                        DBDefinitions.DOM_TITLE,
                        DBDefinitions.TBL_BOOKS.dot(DBKey.TITLE)));
        fixedDomainList.add(
                // Title for sorting
                // Example: "dreammasterthe" OR "thedreammaster"
                // i.e. depending on user preference, the first format
                // consists of the original title stripped of whitespace and any special characters,
                // and with the article/prefix moved to the end.
                // The second format leaves the article/prefix in its original location.
                // The choice between the two formats is a user preference which, when changed,
                // updates ALL rows in the database with the newly formatted title.
                new DomainExpression(
                        DBDefinitions.DOM_TITLE_OB,
                        DBDefinitions.TBL_BOOKS.dot(DBKey.TITLE_OB),
                        DomainExpression.Sort.Asc));

        fixedDomainList.add(
                // the book language is needed for reordering titles
                new DomainExpression(
                        DBDefinitions.DOM_BOOK_LANGUAGE,
                        DBDefinitions.TBL_BOOKS.dot(DBKey.LANGUAGE)));

        fixedDomainList.add(
                // Always get the read flag
                new DomainExpression(
                        DBDefinitions.DOM_BOOK_READ,
                        DBDefinitions.TBL_BOOKS.dot(DBKey.READ__BOOL)));

        fixedDomainList.add(
                // Always get the Author ID
                // (the need for the name will depend on the style).
                new DomainExpression(
                        DBDefinitions.DOM_FK_AUTHOR,
                        DBDefinitions.TBL_BOOK_AUTHOR.dot(DBKey.FK_AUTHOR)));

        fixedDomainList.add(
                // We want the UUID for the book so we can get thumbnails
                new DomainExpression(
                        DBDefinitions.DOM_BOOK_UUID,
                        DBDefinitions.TBL_BOOKS.dot(DBKey.BOOK_UUID)));

        fixedDomainList.add(
                // Always get the ISBN
                new DomainExpression(
                        DBDefinitions.DOM_BOOK_ISBN,
                        DBDefinitions.TBL_BOOKS.dot(DBKey.BOOK_ISBN)));

        // external site ID's
        for (final Domain domain : SearchEngineConfig.getExternalIdDomains()) {
            fixedDomainList.add(
                    new DomainExpression(domain, DBDefinitions.TBL_BOOKS.dot(domain.getName())));
        }
    }

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

    @Nullable
    @Override
    @WorkerThread
    protected Outcome doWork(@NonNull final Context context) {

        final Style style = bookshelf.getStyle(context);

        Booklist booklist = null;
        try {
            // get a new builder and add the required domains
            final BooklistBuilder builder = new BooklistBuilder(style, bookshelf, rebuildMode);

            // Add the fixed list of domains we always need.
            for (final DomainExpression domainDetails : fixedDomainList) {
                builder.addDomain(domainDetails);
            }

            if (CalibreHandler.isSyncEnabled()) {
                addCalibreDomains(builder);
            }

            addConditionalDomains(builder, style);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
                Log.d(TAG, "searchCriteria=" + searchCriteria);
            }

            if (!searchCriteria.isEmpty()) {
                // if we have a list of ID's, we'll ignore other criteria
                if (searchCriteria.getBookIdList().isEmpty()) {
                    // Criteria supported by FTS
                    searchCriteria.getFtsMatchQuery().ifPresent(
                            query -> builder.addFilter(new FtsMatchFilter(query)));

                    // Add a filter to retrieve only books lend to the given person (exact name).
                    searchCriteria.getLoanee().ifPresent(loanee -> builder.addFilter(
                            c -> String.format(LOAN_FILTER, SqlEncode.string(loanee))));

                } else {
                    // Add a where clause for: "AND books._id IN (list)".
                    builder.addFilter(new NumberListFilter<>(
                            DBDefinitions.TBL_BOOKS, DBDefinitions.DOM_PK_ID,
                            searchCriteria.getBookIdList()));
                }

                // when criteria are used, the build should expand the book list.
                builder.setRebuildMode(RebuildBooklist.Expanded);
            }


            // Prepare the Bookshelf filters
            final List<PFilter<?>> filters = bookshelf.getFilters();

            // Add a filter on the current Bookshelf?
            // Only consider doing this if this is NOT the "All books" Bookshelf
            if (!bookshelf.isAllBooks()) {
                // and only if the current style does NOT contain the Bookshelf group.
                if (!bookshelf.getStyle(context).hasGroup(BooklistGroup.BOOKSHELF)) {
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


            // Build the underlying data
            booklist = builder.build(context);

            // pre-count and cache (in the builder) these while we're in the background.
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

    private void addConditionalDomains(@NonNull final BooklistBuilder builder,
                                       @NonNull final Style style) {
        if (style.isShowField(Style.Screen.List, DBKey.EDITION__BITMASK)) {
            // The edition bitmask
            builder.addDomain(new DomainExpression(
                    DBDefinitions.DOM_BOOK_EDITION,
                    DBDefinitions.TBL_BOOKS.dot(DBKey.EDITION__BITMASK)));
        }

        if (style.isShowField(Style.Screen.List, DBKey.SIGNED__BOOL)) {
            builder.addDomain(new DomainExpression(
                    DBDefinitions.DOM_BOOK_SIGNED,
                    DBDefinitions.TBL_BOOKS.dot(DBKey.SIGNED__BOOL)));
        }

        if (style.isShowField(Style.Screen.List, DBKey.BOOK_CONDITION)) {
            builder.addDomain(new DomainExpression(
                    DBDefinitions.DOM_BOOK_CONDITION,
                    DBDefinitions.TBL_BOOKS.dot(DBKey.BOOK_CONDITION)));
        }

        if (style.isShowField(Style.Screen.List, DBKey.LOANEE_NAME)) {
            // Used to display/hide the 'lend' icon for each book.
            builder.addLeftOuterJoin(DBDefinitions.TBL_BOOK_LOANEE);
            builder.addDomain(new DomainExpression(
                    DBDefinitions.DOM_LOANEE,
                    DBDefinitions.TBL_BOOK_LOANEE.dot(DBKey.LOANEE_NAME)));
        }

        if (style.isShowField(Style.Screen.List, DBKey.FK_BOOKSHELF)) {
            // This collects a CSV list of the bookshelves the book is on.
            builder.addDomain(new DomainExpression(
                    DBDefinitions.DOM_BOOKSHELF_NAME_CSV,
                    BooklistBuilder.EXP_BOOKSHELF_NAME_CSV));
        }

        // we fetch ONLY the primary author to show on the Book level
        if (style.isShowField(Style.Screen.List, DBKey.FK_AUTHOR)) {
            builder.addDomain(AuthorDaoImpl.createDisplayDomainExpression(
                    style.isShowAuthorByGivenName()));
        }

        // for now, don't get the author type.
        // if (style.isBooklistShowsField(DBKey.BOOK_AUTHOR_TYPE_BITMASK)) {
        //     builder.addDomain(new DomainExpression(
        //             DBDefinitions.DOM_BOOK_AUTHOR_TYPE_BITMASK,
        //             DBDefinitions.TBL_BOOK_AUTHOR
        //                     .dot(DBDefinitions.DOM_BOOK_AUTHOR_TYPE_BITMASK)));
        // }

        if (style.isShowField(Style.Screen.List, DBKey.FK_PUBLISHER)) {
            // Collect a CSV list of the publishers of the book
            builder.addDomain(new DomainExpression(
                    DBDefinitions.DOM_PUBLISHER_NAME_CSV,
                    BooklistBuilder.EXP_PUBLISHER_NAME_CSV));
        }

        if (style.isShowField(Style.Screen.List, DBKey.BOOK_PUBLICATION__DATE)) {
            builder.addDomain(new DomainExpression(
                    DBDefinitions.DOM_BOOK_DATE_PUBLISHED,
                    DBDefinitions.TBL_BOOKS.dot(DBKey.BOOK_PUBLICATION__DATE)));
        }

        if (style.isShowField(Style.Screen.List, DBKey.FORMAT)) {
            builder.addDomain(new DomainExpression(
                    DBDefinitions.DOM_BOOK_FORMAT,
                    DBDefinitions.TBL_BOOKS.dot(DBKey.FORMAT)));
        }

        if (style.isShowField(Style.Screen.List, DBKey.LOCATION)) {
            builder.addDomain(new DomainExpression(
                    DBDefinitions.DOM_BOOK_LOCATION,
                    DBDefinitions.TBL_BOOKS.dot(DBKey.LOCATION)));
        }

        if (style.isShowField(Style.Screen.List, DBKey.RATING)) {
            builder.addDomain(new DomainExpression(
                    DBDefinitions.DOM_BOOK_RATING,
                    DBDefinitions.TBL_BOOKS.dot(DBKey.RATING)));
        }
    }

    private void addCalibreDomains(@NonNull final BooklistBuilder builder) {
        builder.addLeftOuterJoin(DBDefinitions.TBL_CALIBRE_BOOKS);

        builder.addDomain(new DomainExpression(
                DBDefinitions.DOM_CALIBRE_BOOK_ID,
                DBDefinitions.TBL_CALIBRE_BOOKS.dot(DBKey.CALIBRE_BOOK_ID)));
        builder.addDomain(new DomainExpression(
                DBDefinitions.DOM_CALIBRE_BOOK_UUID,
                DBDefinitions.TBL_CALIBRE_BOOKS.dot(DBKey.CALIBRE_BOOK_UUID)));
        builder.addDomain(new DomainExpression(
                DBDefinitions.DOM_CALIBRE_BOOK_MAIN_FORMAT,
                DBDefinitions.TBL_CALIBRE_BOOKS.dot(DBKey.CALIBRE_BOOK_MAIN_FORMAT)));

        builder.addDomain(new DomainExpression(
                DBDefinitions.DOM_FK_CALIBRE_LIBRARY,
                DBDefinitions.TBL_CALIBRE_BOOKS.dot(DBKey.FK_CALIBRE_LIBRARY)));
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

        @NonNull
        public Booklist getList() {
            return booklist;
        }

        @NonNull
        public List<BooklistNode> getTargetNodes() {
            return targetNodes;
        }
    }
}
