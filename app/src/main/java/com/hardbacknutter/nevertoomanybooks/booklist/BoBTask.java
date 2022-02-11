/*
 * @Copyright 2018-2021 HardBackNutter
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
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.SearchCriteria;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListScreenBookFields;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.AuthorDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineRegistry;
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

    /** The fixed list of domains we always need for building the book list. */
    private final Collection<DomainExpression> mFixedDomainList = new ArrayList<>();

    /** Currently selected bookshelf. */
    private Bookshelf mBookshelf;

    /** Preferred booklist state in next rebuild. */
    private RebuildBooklist mRebuildMode;

    /** Holder for all search criteria. See {@link SearchCriteria} for more info. */
    private SearchCriteria mSearchCriteria;

    /** The row id we want the new list to display more-or-less in the center. */
    private long mDesiredCentralBookId;

    /**
     * Constructor.
     */
    public BoBTask() {
        super(R.id.TASK_ID_BOOKLIST_BUILDER, TAG);
        initFixedDomainList();
    }

    private void initFixedDomainList() {
        mFixedDomainList.add(
                // Title for displaying; do NOT sort on it
                // Example: "The Dream Master"
                new DomainExpression(
                        DBDefinitions.DOM_TITLE,
                        DBDefinitions.TBL_BOOKS.dot(DBKey.KEY_TITLE)));
        mFixedDomainList.add(
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
                        DBDefinitions.TBL_BOOKS.dot(DBKey.KEY_TITLE_OB),
                        DomainExpression.SORT_ASC));

        mFixedDomainList.add(
                // the book language is needed for reordering titles
                new DomainExpression(
                        DBDefinitions.DOM_BOOK_LANGUAGE,
                        DBDefinitions.TBL_BOOKS.dot(DBKey.KEY_LANGUAGE)));

        mFixedDomainList.add(
                // Always get the read flag
                new DomainExpression(
                        DBDefinitions.DOM_BOOK_READ,
                        DBDefinitions.TBL_BOOKS.dot(DBKey.BOOL_READ)));

        mFixedDomainList.add(
                // Always get the Author ID
                // (the need for the name will depend on the style).
                new DomainExpression(
                        DBDefinitions.DOM_FK_AUTHOR,
                        DBDefinitions.TBL_BOOK_AUTHOR.dot(DBKey.FK_AUTHOR)));

        mFixedDomainList.add(
                // We want the UUID for the book so we can get thumbnails
                new DomainExpression(
                        DBDefinitions.DOM_BOOK_UUID,
                        DBDefinitions.TBL_BOOKS.dot(DBKey.KEY_BOOK_UUID)));

        mFixedDomainList.add(
                // Always get the ISBN
                new DomainExpression(
                        DBDefinitions.DOM_BOOK_ISBN,
                        DBDefinitions.TBL_BOOKS.dot(DBKey.KEY_ISBN)));

        // external site ID's
        for (final Domain domain : SearchEngineRegistry.getInstance().getExternalIdDomains()) {
            mFixedDomainList.add(
                    new DomainExpression(domain, DBDefinitions.TBL_BOOKS.dot(domain.getName())));
        }
    }

    public void build(@NonNull final Bookshelf bookshelf,
                      @NonNull final RebuildBooklist mode,
                      @NonNull final SearchCriteria searchCriteria,
                      final long desiredCentralBookId) {
        mBookshelf = bookshelf;
        mRebuildMode = mode;
        mSearchCriteria = searchCriteria;
        mDesiredCentralBookId = desiredCentralBookId;

        execute();
    }

    @Nullable
    @Override
    @WorkerThread
    protected Outcome doWork(@NonNull final Context context) {

        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        final ListStyle style = mBookshelf.getStyle(context);

        Booklist booklist = null;
        try {
            // get a new builder and add the required domains
            final BooklistBuilder builder = new BooklistBuilder(style, mBookshelf, mRebuildMode);

            // Add the fixed list of domains we always need.
            for (final DomainExpression domainDetails : mFixedDomainList) {
                builder.addDomain(domainDetails);
            }

            // Add Calibre bridging data ?
            if (CalibreHandler.isSyncEnabled(global)) {
                addCalibreDomains(builder);
            }

            // Add the conditional domains; global level.

            if (DBKey.isUsed(global, DBKey.BITMASK_EDITION)) {
                // The edition bitmask
                builder.addDomain(new DomainExpression(
                        DBDefinitions.DOM_BOOK_EDITION_BITMASK,
                        DBDefinitions.TBL_BOOKS.dot(DBKey.BITMASK_EDITION)));
            }

            if (DBKey.isUsed(global, DBKey.BOOL_SIGNED)) {
                builder.addDomain(new DomainExpression(
                        DBDefinitions.DOM_BOOK_SIGNED,
                        DBDefinitions.TBL_BOOKS.dot(DBKey.BOOL_SIGNED)));
            }

            if (DBKey.isUsed(global, DBKey.KEY_BOOK_CONDITION)) {
                builder.addDomain(new DomainExpression(
                        DBDefinitions.DOM_BOOK_CONDITION,
                        DBDefinitions.TBL_BOOKS.dot(DBKey.KEY_BOOK_CONDITION)));
            }

            if (DBKey.isUsed(global, DBKey.KEY_LOANEE)) {
                // Used to display/hide the 'lend' icon for each book.
                builder.addLeftOuterJoin(DBDefinitions.TBL_BOOK_LOANEE);
                builder.addDomain(new DomainExpression(
                        DBDefinitions.DOM_LOANEE,
                        DBDefinitions.TBL_BOOK_LOANEE.dot(DBKey.KEY_LOANEE)));
            }

            // Add the conditional domains; style level.
            final ListScreenBookFields bookFields = style.getListScreenBookFields();

            if (bookFields.isShowField(global, ListScreenBookFields.PK_BOOKSHELVES)) {
                // This collects a CSV list of the bookshelves the book is on.
                builder.addDomain(new DomainExpression(
                        DBDefinitions.DOM_BOOKSHELF_NAME_CSV,
                        BooklistBuilder.EXP_BOOKSHELF_NAME_CSV));
            }

            // we fetch ONLY the primary author to show on the Book level
            if (bookFields.isShowField(global, ListScreenBookFields.PK_AUTHOR)) {
                builder.addDomain(AuthorDaoImpl.createDisplayDomainExpression(
                        style.isShowAuthorByGivenName()));
            }

            // for now, don't get the author type.
//              if (bookFields.isShowField(context, ListScreenBookFields.PK_AUTHOR_TYPE)) {
//                  builder.addDomain(new DomainExpression(
//                          DBDefinitions.DOM_BOOK_AUTHOR_TYPE_BITMASK,
//                          DBDefinitions.TBL_BOOK_AUTHOR
//                          .dot(DBDefinitions.KEY_BOOK_AUTHOR_TYPE_BITMASK)));
//              }

            if (bookFields.isShowField(global, ListScreenBookFields.PK_PUBLISHER)) {
                // Collect a CSV list of the publishers of the book
                builder.addDomain(new DomainExpression(
                        DBDefinitions.DOM_PUBLISHER_NAME_CSV,
                        BooklistBuilder.EXP_PUBLISHER_NAME_CSV));
            }

            if (bookFields.isShowField(global, ListScreenBookFields.PK_PUB_DATE)) {
                builder.addDomain(new DomainExpression(
                        DBDefinitions.DOM_BOOK_DATE_PUBLISHED,
                        DBDefinitions.TBL_BOOKS.dot(DBKey.DATE_BOOK_PUBLICATION)));
            }

            if (bookFields.isShowField(global, ListScreenBookFields.PK_FORMAT)) {
                builder.addDomain(new DomainExpression(
                        DBDefinitions.DOM_BOOK_FORMAT,
                        DBDefinitions.TBL_BOOKS.dot(DBKey.KEY_FORMAT)));
            }

            if (bookFields.isShowField(global, ListScreenBookFields.PK_LOCATION)) {
                builder.addDomain(new DomainExpression(
                        DBDefinitions.DOM_BOOK_LOCATION,
                        DBDefinitions.TBL_BOOKS.dot(DBKey.KEY_LOCATION)));
            }

            if (bookFields.isShowField(global, ListScreenBookFields.PK_RATING)) {
                builder.addDomain(new DomainExpression(
                        DBDefinitions.DOM_BOOK_RATING,
                        DBDefinitions.TBL_BOOKS.dot(DBKey.KEY_RATING)));
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
                Log.d(TAG, "mSearchCriteria=" + mSearchCriteria);
            }

            // if we have a list of ID's, ignore other criteria
            if (mSearchCriteria.hasIdList()) {
                builder.addFilterOnBookIdList(mSearchCriteria.getBookIdList());

            } else {
                // Criteria supported by FTS
                builder.addFilterOnKeywords(mSearchCriteria.getFtsAuthor(),
                                            mSearchCriteria.getFtsTitle(),
                                            mSearchCriteria.getFtsSeries(),
                                            mSearchCriteria.getFtsPublisher(),
                                            mSearchCriteria.getFtsKeywords());

                builder.addFilterOnLoanee(mSearchCriteria.getLoanee());
            }

            // if we have any criteria set at all, the build should expand the book list.
            if (!mSearchCriteria.isEmpty()) {
                builder.setRebuildMode(RebuildBooklist.Expanded);
            }

            // Build the underlying data
            booklist = builder.build(context);

            // pre-count and cache (in the builder) these while we're in the background.
            // They are used for the header, and will not change even if the list cursor changes.
            if (style.isShowHeader(ListStyle.HEADER_SHOW_BOOK_COUNT)) {
                booklist.countBooks();
                booklist.countDistinctBooks();
            }

            // Get the row(s) which will be used to determine new cursor position
            return new Outcome(booklist, booklist.getVisibleBookNodes(mDesiredCentralBookId));

        } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final Exception e) {
            if (booklist != null) {
                booklist.close();
            }
            throw e;
        }
    }

    private void addCalibreDomains(@NonNull final BooklistBuilder builder) {
        builder.addLeftOuterJoin(DBDefinitions.TBL_CALIBRE_BOOKS);

        builder.addDomain(new DomainExpression(
                DBDefinitions.DOM_CALIBRE_BOOK_ID,
                DBDefinitions.TBL_CALIBRE_BOOKS.dot(DBKey.KEY_CALIBRE_BOOK_ID)));
        builder.addDomain(new DomainExpression(
                DBDefinitions.DOM_CALIBRE_BOOK_UUID,
                DBDefinitions.TBL_CALIBRE_BOOKS.dot(DBKey.KEY_CALIBRE_BOOK_UUID)));
        builder.addDomain(new DomainExpression(
                DBDefinitions.DOM_CALIBRE_BOOK_MAIN_FORMAT,
                DBDefinitions.TBL_CALIBRE_BOOKS.dot(DBKey.KEY_CALIBRE_BOOK_MAIN_FORMAT)));

        builder.addDomain(new DomainExpression(
                DBDefinitions.DOM_FK_CALIBRE_LIBRARY,
                DBDefinitions.TBL_CALIBRE_BOOKS.dot(DBKey.FK_CALIBRE_LIBRARY)));
    }

    public static class Outcome {

        @NonNull
        private final Booklist mList;
        @NonNull
        private final List<BooklistNode> mTargetNodes;

        Outcome(@NonNull final Booklist list,
                @NonNull final List<BooklistNode> targetNodes) {
            this.mList = list;
            this.mTargetNodes = targetNodes;
        }

        @NonNull
        public Booklist getList() {
            return mList;
        }

        @NonNull
        public List<BooklistNode> getTargetNodes() {
            return mTargetNodes;
        }
    }
}
