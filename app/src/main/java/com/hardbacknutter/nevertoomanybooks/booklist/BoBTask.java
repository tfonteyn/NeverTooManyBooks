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
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.AuthorDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Sort;
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
    /** The fixed list of domains we always need if sync with Calibre is enabled. */
    private final Collection<DomainExpression> calibreDomainList = new ArrayList<>();

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
            Log.d(TAG, "NEW TASK_ID_BOOKLIST_BUILDER");
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

        final BooklistBuilder builder = new BooklistBuilder(style, bookshelf, rebuildMode);

        if (fixedDomainList.isEmpty()) {
            initFixedDomainExpressions();
        }
        fixedDomainList.forEach(builder::addDomain);

        if (CalibreHandler.isSyncEnabled()) {
            if (calibreDomainList.isEmpty()) {
                initCalibreDomainExpressions();
            }
            builder.addLeftOuterJoin(DBDefinitions.TBL_CALIBRE_BOOKS);
            calibreDomainList.forEach(builder::addDomain);
        }

        addDomains(builder, style);

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

    private void initFixedDomainExpressions() {
        // Title for displaying; do NOT sort on it
        // Example: "The Dream Master"
        fixedDomainList.add(new DomainExpression(DBDefinitions.DOM_TITLE,
                                                 DBDefinitions.TBL_BOOKS));

        // Title for sorting
        // Example: "dreammasterthe" OR "thedreammaster"
        // i.e. depending on user preference, the first format
        // consists of the original title stripped of whitespace and any special characters,
        // and with the article/prefix moved to the end.
        // The second format leaves the article/prefix in its original location.
        // The choice between the two formats is a user preference which, when changed,
        // updates ALL rows in the database with the newly formatted title.
        fixedDomainList.add(new DomainExpression(DBDefinitions.DOM_TITLE_OB,
                                                 DBDefinitions.TBL_BOOKS,
                                                 Sort.Asc));

        // the book language is needed for reordering titles in BooklistGroup rows.
        fixedDomainList.add(new DomainExpression(DBDefinitions.DOM_BOOK_LANGUAGE,
                                                 DBDefinitions.TBL_BOOKS));

        // Always get the read flag
        fixedDomainList.add(new DomainExpression(DBDefinitions.DOM_BOOK_READ,
                                                 DBDefinitions.TBL_BOOKS));

        // Always get the Author ID (the need for the name will depend on the style).
        fixedDomainList.add(new DomainExpression(DBDefinitions.DOM_FK_AUTHOR,
                                                 DBDefinitions.TBL_BOOK_AUTHOR));

        // Always get the ISBN
        fixedDomainList.add(new DomainExpression(DBDefinitions.DOM_BOOK_ISBN,
                                                 DBDefinitions.TBL_BOOKS));

        // external site ID's; needed for the context menu "View on..."
        SearchEngineConfig.getExternalIdDomains()
                          .stream()
                          .map(domain -> new DomainExpression(
                                  domain, DBDefinitions.TBL_BOOKS.dot(domain),
                                  Sort.Unsorted))
                          .forEach(fixedDomainList::add);
    }

    private void initCalibreDomainExpressions() {
        calibreDomainList.add(new DomainExpression(DBDefinitions.DOM_CALIBRE_BOOK_ID,
                                                   DBDefinitions.TBL_CALIBRE_BOOKS));
        calibreDomainList.add(new DomainExpression(DBDefinitions.DOM_CALIBRE_BOOK_UUID,
                                                   DBDefinitions.TBL_CALIBRE_BOOKS));
        calibreDomainList.add(new DomainExpression(DBDefinitions.DOM_CALIBRE_BOOK_MAIN_FORMAT,
                                                   DBDefinitions.TBL_CALIBRE_BOOKS));
        calibreDomainList.add(new DomainExpression(DBDefinitions.DOM_FK_CALIBRE_LIBRARY,
                                                   DBDefinitions.TBL_CALIBRE_BOOKS));
    }

    /**
     * Add fields which are (depending on Style) shown on the Book level.
     *
     * @param builder to use
     * @param style   to use
     */
    private void addDomains(@NonNull final BooklistBuilder builder,
                            @NonNull final Style style) {

        if (style.isShowField(Style.Screen.List, FieldVisibility.COVER[0])) {
            // We need the UUID for the book to get covers
            builder.addDomain(new DomainExpression(DBDefinitions.DOM_BOOK_UUID,
                                                   DBDefinitions.TBL_BOOKS));
        }

        if (style.isShowField(Style.Screen.List, DBKey.EDITION__BITMASK)) {
            builder.addDomain(new DomainExpression(DBDefinitions.DOM_BOOK_EDITION,
                                                   DBDefinitions.TBL_BOOKS));
        }

        if (style.isShowField(Style.Screen.List, DBKey.SIGNED__BOOL)) {
            builder.addDomain(new DomainExpression(DBDefinitions.DOM_BOOK_SIGNED,
                                                   DBDefinitions.TBL_BOOKS));
        }

        if (style.isShowField(Style.Screen.List, DBKey.BOOK_CONDITION)) {
            builder.addDomain(new DomainExpression(DBDefinitions.DOM_BOOK_CONDITION,
                                                   DBDefinitions.TBL_BOOKS));
        }

        if (style.isShowField(Style.Screen.List, DBKey.LOANEE_NAME)) {
            // Used to display/hide the 'lend' icon for each book.
            builder.addLeftOuterJoin(DBDefinitions.TBL_BOOK_LOANEE);
            builder.addDomain(new DomainExpression(DBDefinitions.DOM_LOANEE,
                                                   DBDefinitions.TBL_BOOK_LOANEE));
        }

        if (style.isShowField(Style.Screen.List, DBKey.FK_BOOKSHELF)) {
            // This collects a CSV list of the bookshelves the book is on.
            builder.addDomain(new DomainExpression(DBDefinitions.DOM_BOOKSHELF_NAME_CSV,
                                                   BooklistBuilder.EXP_BOOKSHELF_NAME_CSV,
                                                   Sort.Unsorted));
        }

        // we fetch ONLY the primary author to show on the Book level
        if (style.isShowField(Style.Screen.List, DBKey.FK_AUTHOR)) {
            builder.addDomain(new DomainExpression(
                    DBDefinitions.DOM_AUTHOR_FORMATTED_FAMILY_FIRST,
                    AuthorDaoImpl.getDisplayDomainExpression(style.isShowAuthorByGivenName()),
                    Sort.Unsorted));
        }

        // for now, don't get the author type.
        // if (style.isShowField(Style.Screen.List, DBKey.AUTHOR_TYPE__BITMASK)) {
        //    builder.addDomain(new DomainExpression(DBDefinitions.DOM_BOOK_AUTHOR_TYPE_BITMASK,
        //                                           DBDefinitions.TBL_BOOK_AUTHOR));
        // }

        if (style.isShowField(Style.Screen.List, DBKey.FK_SERIES)) {
            // We only collect the primary series!
            builder.addDomain(new DomainExpression(DBDefinitions.DOM_SERIES_TITLE,
                                                   DBDefinitions.TBL_SERIES));
            builder.addDomain(new DomainExpression(DBDefinitions.DOM_BOOK_NUM_IN_SERIES,
                                                   DBDefinitions.TBL_BOOK_SERIES));
        }

        if (style.isShowField(Style.Screen.List, DBKey.FK_PUBLISHER)) {
            // Collect a CSV list of the publishers of the book
            builder.addDomain(new DomainExpression(DBDefinitions.DOM_PUBLISHER_NAME_CSV,
                                                   BooklistBuilder.EXP_PUBLISHER_NAME_CSV,
                                                   Sort.Unsorted));
        }

        if (style.isShowField(Style.Screen.List, DBKey.BOOK_PUBLICATION__DATE)) {
            builder.addDomain(new DomainExpression(DBDefinitions.DOM_BOOK_DATE_PUBLISHED,
                                                   DBDefinitions.TBL_BOOKS));
        }

        if (style.isShowField(Style.Screen.List, DBKey.FORMAT)) {
            builder.addDomain(new DomainExpression(DBDefinitions.DOM_BOOK_FORMAT,
                                                   DBDefinitions.TBL_BOOKS));
        }

        if (style.isShowField(Style.Screen.List, DBKey.LOCATION)) {
            builder.addDomain(new DomainExpression(DBDefinitions.DOM_BOOK_LOCATION,
                                                   DBDefinitions.TBL_BOOKS));
        }

        if (style.isShowField(Style.Screen.List, DBKey.RATING)) {
            builder.addDomain(new DomainExpression(DBDefinitions.DOM_BOOK_RATING,
                                                   DBDefinitions.TBL_BOOKS));
        }
    }

    private void addCriteria(@NonNull final BooklistBuilder builder) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            Log.d(TAG, "searchCriteria=" + searchCriteria);
        }

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
