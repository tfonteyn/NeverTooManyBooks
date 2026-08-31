/*
 * @Copyright 2018-2026 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.covers.ImageStorageException;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoImageException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoUpdateException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.IdentifierValueDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorMergeHelper;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.BookLite;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.EntityMergeHelper;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PSEUDONYM_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

public class AuthorDaoImpl
        extends BaseDaoImpl
        implements AuthorDao {

    /** Log tag. */
    private static final String TAG = "AuthorDaoImpl";

    private static final String ERROR_INSERT_FROM = "Insert from\n";
    private static final String ERROR_UPDATE_FROM = "Update from\n";
    private static final String ERROR_STORING_IMAGES =
            "Failed storing the pictures for author from\n";

    private static final String[] Z_ARRAY_STRING = new String[0];
    private final IdentifierValueDao authorIdentifierDao;

    private final DateParser<PartialDate> partialDateParser = new PartialDateParser();

    /**
     * Constructor.
     *
     * @param db Database Access
     */
    public AuthorDaoImpl(@NonNull final SynchronizedDb db) {
        super(db, TAG);
        authorIdentifierDao = ServiceLocator.getInstance().getAuthorIdentifierDao();
    }

    /**
     * Single column, for sorting on the formatted name of the Author.
     * <p>
     * Dev note: Note how the 'otherwise' will always concatenate the names without white space.
     *
     * @param givenNameFirst {@code true}
     *                       If no given name -> "FamilyName"
     *                       otherwise -> "GivenNamesFamilyName"
     *                       {@code false}
     *                       If no given name -> "FamilyName"
     *                       otherwise -> "FamilyNameGivenNames"
     *
     * @return column expression
     */
    @NonNull
    public static String getSortingDomainExpression(final boolean givenNameFirst) {
        if (givenNameFirst) {
            return Sql.SORT_AUTHOR_GIVEN_FIRST;
        } else {
            return Sql.SORT_AUTHOR_FAMILY_FIRST;
        }
    }

    /**
     * Single column, with the formatted name of the Author.
     *
     * @param givenNameFirst {@code true}
     *                       If no given name -> "FamilyName"
     *                       otherwise -> "GivenNames FamilyName"
     *                       {@code false}
     *                       If no given name -> "FamilyName"
     *                       otherwise -> "FamilyName, GivenNames"
     *
     * @return column expression
     */
    @NonNull
    public static String getDisplayDomainExpression(final boolean givenNameFirst) {
        if (givenNameFirst) {
            return Sql.DISPLAY_AUTHOR_GIVEN_FIRST;
        } else {
            return Sql.DISPLAY_AUTHOR_FAMILY_FIRST;
        }
    }

    @NonNull
    @Override
    public Optional<Author> findById(@IntRange(from = 1) final long id) {
        try (Cursor cursor = db.rawQuery(Sql.SELECT_BY_ID, new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return Optional.of(new Author(id, new CursorRow(cursor)));
            } else {
                return Optional.empty();
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <strong>IMPORTANT:</strong> the query can return more than one row if the
     * given-name of the author is empty. e.g. "Asimov" and "Asimov"+"Isaac"
     * We only return the <strong>first entity found</strong>.
     *
     * @param context Current context
     * @param author  to find the id of
     * @param locale  Current Locale
     *
     * @return the Author
     */
    @Override
    @NonNull
    public Optional<Author> findByName(@NonNull final Context context,
                                       @NonNull final Author author,
                                       @NonNull final Locale locale) {

        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_NAME, new String[]{
                author.getFamilyName(), author.getGivenNames()})) {
            if (cursor.moveToFirst()) {
                final CursorRow rowData = new CursorRow(cursor);
                return Optional.of(new Author(rowData.getLong(DBKey.PK_ID), rowData));
            } else {
                return Optional.empty();
            }
        }
    }

    @Override
    @NonNull
    public List<String> getNames(@NonNull final String key) {
        switch (key) {
            case DBKey.AUTHOR.FAMILY_NAME:
                return getColumnAsStringArrayList(Sql.SELECT_ALL_FAMILY_NAMES);

            case DBKey.AUTHOR.GIVEN_NAMES:
                return getColumnAsStringArrayList(Sql.SELECT_ALL_GIVEN_NAMES);

            case DBKey.AUTHOR.FORMATTED_FULL_NAME:
                return getColumnAsStringArrayList(Sql.SELECT_ALL_NAMES_FORMATTED_FAMILY_FIRST);

            case DBKey.AUTHOR.FORMATTED_FULL_NAME_GIVEN_FIRST:
                return getColumnAsStringArrayList(Sql.SELECT_ALL_NAMES_FORMATTED_GIVEN_FIRST);

            default:
                throw new IllegalArgumentException(key);
        }
    }

    @Override
    @NonNull
    public List<Author> getByBookId(@IntRange(from = 1) final long bookId) {
        final List<Author> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_BOOK_ID,
                                         new String[]{String.valueOf(bookId)})) {
            final CursorRow rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Author(rowData.getLong(DBKey.PK_ID), rowData));
            }
        }
        return list;
    }

    @Override
    @NonNull
    public List<Long> getBookIds(final long authorId) {
        final List<Long> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.FIND_BOOK_IDS_BY_AUTHOR_ID,
                                         new String[]{String.valueOf(authorId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    @NonNull
    @Override
    public List<String> getImageUuidList() {
        return getColumnAsStringArrayList(Sql.SELECT_ALL_IMAGE_UUID);
    }

    @Override
    @WorkerThread
    @NonNull
    public List<AuthorWork> getAuthorWorks(@NonNull final Author author,
                                           final long bookshelfId,
                                           final boolean withTocEntries,
                                           final boolean withBooks,
                                           @WorksOrderBy @Nullable final String orderBy) {
        // sanity check
        if (!withTocEntries && !withBooks) {
            throw new IllegalArgumentException("Must specify what to fetch");
        }

        final String orderByColumns;
        if (orderBy == null || DBKey.TITLE_OB.equals(orderBy)) {
            orderByColumns = DBKey.TITLE_OB + _COLLATION;
        } else if (DBKey.FIRST_PUBLICATION_DATE.equals(orderBy)) {
            orderByColumns = DBKey.FIRST_PUBLICATION_DATE + ',' + DBKey.TITLE_OB + _COLLATION;
        } else {
            throw new IllegalArgumentException("Invalid orderBy");
        }

        final boolean byShelf = bookshelfId != Bookshelf.ALL_BOOKS;

        // rawQuery wants String[] as bind parameters
        final String authorIdStr = String.valueOf(author.getId());
        final String bookshelfIdStr = String.valueOf(bookshelfId);

        String sql = "";
        final List<String> paramList = new ArrayList<>();

        // MUST be toc first, books second; otherwise the GROUP BY is done on the whole
        // UNION instead of on the toc only; and SqLite rejects () around the sub selects.
        if (withTocEntries) {
            sql += Sql.FIND_TOC_ENTRIES_BY_AUTHOR_ID
                   + (byShelf ? " JOIN " + TBL_BOOK_BOOKSHELF.as()
                                + " ON (" + TBL_BOOK_TOC_ENTRIES.dot(DBKey.FK_BOOK)
                                + '=' + TBL_BOOK_BOOKSHELF.dot(DBKey.FK_BOOK) + ')'
                              : "")
                   + _WHERE_ + TBL_TOC_ENTRIES.dot(DBKey.FK_AUTHOR) + "=?"
                   + (byShelf ? _AND_ + TBL_BOOK_BOOKSHELF.dot(DBKey.FK_BOOKSHELF) + "=?" : "")
                   + " GROUP BY " + TBL_TOC_ENTRIES.dot(DBKey.PK_ID);
            paramList.add(authorIdStr);
            if (byShelf) {
                paramList.add(bookshelfIdStr);
            }
        }

        if (withBooks && withTocEntries) {
            sql += " UNION ";
        }

        if (withBooks) {
            sql += Sql.FIND_BOOK_TITLES_BY_AUTHOR_ID
                   + (byShelf ? TBL_BOOKS.join(TBL_BOOK_BOOKSHELF) : "")
                   + _WHERE_ + TBL_BOOK_AUTHOR.dot(DBKey.FK_AUTHOR) + "=?"
                   + (byShelf ? _AND_ + TBL_BOOK_BOOKSHELF.dot(DBKey.FK_BOOKSHELF) + "=?" : "");
            paramList.add(authorIdStr);
            if (byShelf) {
                paramList.add(bookshelfIdStr);
            }
        }

        sql += _ORDER_BY_ + orderByColumns;

        final List<AuthorWork> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(sql, paramList.toArray(Z_ARRAY_STRING))) {
            final CursorRow rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                final AuthorWork.Type type = AuthorWork.Type.getType(
                        rowData.getString(DBKey.AUTHOR_WORK_TYPE).charAt(0));

                switch (type) {
                    case TocEntry:
                        list.add(new TocEntry(rowData.getLong(DBKey.PK_ID), author, rowData,
                                              partialDateParser));
                        break;

                    case BookLite:
                        list.add(new BookLite(rowData.getLong(DBKey.PK_ID), author, rowData,
                                              partialDateParser));
                        break;

                    case Book:
                    default:
                        throw new IllegalArgumentException(String.valueOf(type));
                }
            }
        }
        return list;
    }

    @Override
    public int count() {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.COUNT_ALL)) {
            return (int) stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    public int countBooks(@NonNull final Author author) {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.COUNT_BOOKS)) {
            stmt.bindLong(1, author.getId());
            return (int) stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    public boolean setComplete(@NonNull final Author author,
                               final boolean complete) {
        final int rowsAffected;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.SET_COMPLETE)) {
            stmt.bindBoolean(1, complete);

            stmt.bindLong(2, author.getId());
            rowsAffected = stmt.executeUpdateDelete(null);
        }

        if (rowsAffected > 0) {
            author.setComplete(complete);
            return true;
        }
        return false;
    }

    /**
     * Remove duplicates.
     * Consolidates author/- and author/role.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public boolean pruneList(@NonNull final Context context,
                             @NonNull final Collection<Author> list,
                             @NonNull final Function<Author, Locale> localeSupplier,
                             @NonNull final BiConsumer<Author, Locale> idFixer) {
        // Reminder: only abort if empty. We rely on 'fixId' being called for ALL list values.
        if (list.isEmpty()) {
            return false;
        }

        boolean modified = false;

        // Remove 'unknown' authors if there are other 'known' authors.
        if (list.size() > 1) {
            final String unknown = context.getString(R.string.unknown_author);
            // remove ALL unknown author entries.
            // Note we check on exact equality as the incoming name
            // would have come from the same resource string
            modified = list.removeIf(author -> author.getFamilyName().equals(unknown));
            if (list.isEmpty()) {
                // All authors were unknown and hence removed,
                // re-add a single unknown author.
                final Author unknownAuthor = Author.createUnknownAuthor(context);
                // shortcut: no need to drop through and engage the AuthorMergeHelper,
                // just fix the id manually here
                fixId(context, unknownAuthor, localeSupplier.apply(unknownAuthor));
                list.add(unknownAuthor);
                return true;
            }
        }

        final EntityMergeHelper<Author> mergeHelper = new AuthorMergeHelper();
        return mergeHelper.merge(context, list, localeSupplier, idFixer)
               || modified;
    }

    @Override
    public void fixId(@NonNull final Context context,
                      @NonNull final Author author,
                      @NonNull final Locale locale) {
        final long found = findByName(context, author, locale)
                .map(Author::getId).orElse(0L);
        author.setId(found);

        final Author realAuthor = author.getRealAuthor();
        if (realAuthor != null) {
            fixId(context, realAuthor, locale);
        }
    }

    @Override
    public void refresh(@NonNull final Context context,
                        @NonNull final Author author,
                        @NonNull final Locale locale) {

        // If needed, check if we already have it in the database.
        if (author.getId() == 0) {
            fixId(context, author, locale);
        }

        // If we do already have it, update the object
        if (author.getId() > 0) {
            final Optional<Author> dbAuthor = findById(author.getId());
            // Sanity check
            if (dbAuthor.isPresent()) {
                // copy any updated fields
                author.copyFrom(dbAuthor.get(), false);
            } else {
                // we shouldn't get here... but if we do, set it to 'new'
                author.setId(0);
            }
        }
    }

    @Override
    public void insertOrUpdate(@NonNull final Context context,
                               @IntRange(from = 1) final long bookId,
                               final boolean doUpdates,
                               @NonNull final Collection<Author> list,
                               @NonNull final Function<Author, Locale> localeSupplier)
            throws DaoWriteException {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        pruneList(context, list, localeSupplier);

        // Just delete all current links; we'll re-insert them for easier positioning
        try (SynchronizedStatement stmt1 = db.compileStatement(Sql.DELETE_BOOK_LINKS_BY_BOOK_ID)) {
            stmt1.bindLong(1, bookId);
            stmt1.executeUpdateDelete(null);
        }

        // is there anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        int position = 0;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT_BOOK_LINK)) {
            for (final Author author : list) {
                final Locale locale = localeSupplier.apply(author);
                fixId(context, author, locale);

                // create if needed - do NOT do updates unless explicitly allowed
                if (author.getId() == 0) {
                    insert(context, author, locale);
                } else if (doUpdates) {
                    // https://stackoverflow.com/questions/6677517/update-if-different-changed
                    // ONLY update if there are actual changes.
                    // Otherwise, the trigger "after_update_on" + TBL_AUTHORS
                    // would set DATE_LAST_UPDATED__UTC for ALL books by that author
                    // while not needed.
                    final Optional<Author> oFound = findById(author.getId());
                    if (oFound.isPresent()) {
                        final Author found = oFound.get();
                        // always merge, but no need to check if modified or not
                        author.merge(found, true);
                        // Check for the name AND user fields being different.
                        if (!found.isIdentical(author)) {
                            update(context, author, locale);
                        }
                    }
                }

                position++;

                stmt.bindLong(1, bookId);
                stmt.bindLong(2, author.getId());
                stmt.bindLong(3, position);
                stmt.bindLong(4, author.getRole());
                if (stmt.executeInsert(null) == -1) {
                    throw new DaoInsertException("insert Book-Author");
                }
            }
        }
    }

    @Override
    @IntRange(from = 1)
    public long insert(@NonNull final Context context,
                       @NonNull final Author author,
                       @NonNull final Locale locale)
            throws DaoWriteException {

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            // Store first, this will update the author fields if successful
            persistPicture(author);

            final long iId;
            try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT)) {
                stmt.bindString(1, author.getFamilyName());
                stmt.bindString(2, textNormaliser.strict(author.getFamilyName(), locale));
                stmt.bindString(3, author.getGivenNames());
                stmt.bindString(4, textNormaliser.strict(author.getGivenNames(), locale));
                stmt.bindString(5, author.getBirthDate().orElse(null));
                stmt.bindString(6, author.getDeathDate().orElse(null));
                stmt.bindString(7, author.getImageUuid().orElse(null));
                stmt.bindBoolean(8, author.isComplete());
                iId = stmt.executeInsert(null);
            }

            if (iId != -1) {
                author.setId(iId);

                authorIdentifierDao.insertOrUpdate(Identifier.EntityType.Author,
                                                   author.getId(), author.getIdentifiers());
                insertOrUpdateRealAuthor(context, author, locale);

                if (txLock != null) {
                    db.setTransactionSuccessful();
                }
                return iId;
            }
        } catch (@NonNull final DaoWriteException e) {
            author.setId(0);
            throw e;
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
        // The insert failed with -1
        author.setId(0);
        throw new DaoInsertException(ERROR_INSERT_FROM + author);
    }

    @Override
    public void update(@NonNull final Context context,
                       @NonNull final Author author,
                       @NonNull final Locale locale)
            throws DaoWriteException {

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            // Store first, this will update the author fields if successful
            persistPicture(author);

            final int rowsAffected;
            try (SynchronizedStatement stmt = db.compileStatement(Sql.UPDATE)) {
                stmt.bindString(1, author.getFamilyName());
                stmt.bindString(2, textNormaliser.strict(author.getFamilyName(), locale));
                stmt.bindString(3, author.getGivenNames());
                stmt.bindString(4, textNormaliser.strict(author.getGivenNames(), locale));
                stmt.bindString(5, author.getBirthDate().orElse(null));
                stmt.bindString(6, author.getDeathDate().orElse(null));
                stmt.bindString(7, author.getImageUuid().orElse(null));
                stmt.bindBoolean(8, author.isComplete());

                stmt.bindLong(9, author.getId());
                rowsAffected = stmt.executeUpdateDelete(null);
            }

            if (rowsAffected > 0) {
                authorIdentifierDao.insertOrUpdate(Identifier.EntityType.Author,
                                                   author.getId(), author.getIdentifiers());
                insertOrUpdateRealAuthor(context, author, locale);

                if (txLock != null) {
                    db.setTransactionSuccessful();
                }
                return;
            }

            throw new DaoUpdateException(ERROR_UPDATE_FROM + author);
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    /**
     * Handle the real-author storage.
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param context Current context
     * @param author  the 'original' author
     * @param locale  Locale to use if the item has none set
     *
     * @throws DaoWriteException    on failure
     * @throws TransactionException (debug)
     */
    private void insertOrUpdateRealAuthor(@NonNull final Context context,
                                          @NonNull final Author author,
                                          @NonNull final Locale locale)
            throws DaoWriteException {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        // always delete any previous link
        deletePseudonymLink(author.getId());

        final Author realAuthor = author.getRealAuthor();
        if (realAuthor == null) {
            // all done
            return;
        }

        // We're not copying the birth/death dates, image, ...
        // between pen-name-author and the real-author.
        // There are/were some authors who use(d) pen-names with a made-up profile/picture.
        fixId(context, realAuthor, locale);
        if (realAuthor.getId() == 0) {
            insert(context, realAuthor, locale);
        } else {
            update(context, realAuthor, locale);
        }
        insertPseudonymLink(author.getId(), realAuthor.getId());
    }

    // ENHANCE: allow delete of author if all books have another author
    public boolean isDeletable(@NonNull final Author author) {

        final String sql = SELECT_DISTINCT_ + 1
                           + _FROM_ + TBL_BOOK_AUTHOR.getName()
                           + _WHERE_ + DBKey.FK_BOOK
                           + _IN_ + '(' + Sql.FIND_BOOK_IDS_BY_AUTHOR_ID + ')'
                           + _GROUP_BY_ + DBKey.FK_BOOK
                           + " HAVING COUNT(" + DBKey.FK_AUTHOR + ")=1";

        final long rows;
        try (SynchronizedStatement stmt = db.compileStatement(sql)) {
            stmt.bindLong(1, author.getId());
            rows = stmt.simpleQueryForLongOrZero();
        }

        // no rows? then DELETE OK
        return rows == 0;
    }

    /**
     * Persist the temporary FileSpec to a permanent UUID based filename.
     * Hardcoded to using one image {@code cIdx=0}.
     *
     * @param author to store
     *
     * @throws DaoImageException on any error.
     *                           Note this will wrap any IOException or ImageStorageException
     */
    private void persistPicture(@NonNull final Author author)
            throws DaoImageException {
        final Optional<String> fileSpec = author.getTmpPictureFileSpec();
        if (fileSpec.isPresent()) {
            try {
                final File file = new File(fileSpec.get());
                // Check existence! We can run into situations where we had a
                // pen-name author and the real-author both having the same temp image file.
                // The pen-name author would have stored/renamed the file,
                // and the real-author would end up with the temp file set, but physical file
                // already gone.
                // This could be a bug... it's getting pretty complicated dealing with
                // multiple resolvers and multiple sites.
                // Call it a workaround/bug/solution/paranoia... it works.
                if (file.exists() && file.length() > 0) {
                    final String uuid = UUID.randomUUID().toString();
                    ServiceLocator.getInstance().getCoverStorage()
                                  .persist(file, uuid, 0);
                    author.setImageUuid(uuid);
                }
                author.setTmpPictureFileSpec(null);
            } catch (@NonNull final IOException | ImageStorageException e) {
                throw new DaoImageException(ERROR_STORING_IMAGES + author, e);
            }
        }
    }

    @Override
    public boolean delete(@NonNull final Context context,
                          @NonNull final Author author) {
        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            final int rowsAffected;
            try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_BY_ID)) {
                stmt.bindLong(1, author.getId());
                rowsAffected = stmt.executeUpdateDelete(null);
            }
            if (rowsAffected > 0) {
                fixPositions(context);
                deletePicture(author);

                author.setId(0);
                if (txLock != null) {
                    db.setTransactionSuccessful();
                }
                return true;
            }
            return false;
        } catch (@NonNull final DaoWriteException e) {
            return false;
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    /**
     * Hardcoded to using one image {@code cIdx=0}.
     *
     * @param author to handle
     */
    private void deletePicture(@NonNull final Author author) {
        author.getImageUuid().ifPresent(pictureUuid -> {
            ASyncExecutor.STORAGE_WRITES.execute(
                    () -> ServiceLocator.getInstance()
                                        .getCoverStorage()
                                        .delete(pictureUuid, 0));
            author.setImageUuid(null);
        });
    }

    private void insertPseudonymLink(final long authorId,
                                     final long realAuthorId)
            throws DaoInsertException {

        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT_PSEUDONYM_LINKS)) {
            stmt.bindLong(1, authorId);
            stmt.bindLong(2, realAuthorId);
            if (stmt.executeInsert(null) == -1) {
                throw new DaoInsertException("Failed to insert PseudonymLink author=" + authorId
                                             + ", real=" + realAuthorId);
            }
        }
    }

    private void deletePseudonymLink(final long pseudonymId) {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_PSEUDONYM_LINKS)) {
            stmt.bindLong(1, pseudonymId);
            stmt.executeUpdateDelete(null);
        }
    }

    @Override
    @IntRange(from = 0)
    public int moveBooks(@NonNull final Context context,
                         @NonNull final Author source,
                         @NonNull final Author target)
            throws DaoWriteException {

        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        int booksMoved;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            // Updating the author on all TOCs is easy: just do a mass update
            try (SynchronizedStatement stmt = db.compileStatement(Sql.BULK_UPDATE_AUTHOR)) {
                stmt.bindLong(1, target.getId());
                stmt.bindLong(2, source.getId());
                stmt.executeUpdateDelete(null);
            }

            // Relink books with the target Author,
            // respecting the position of the Author in the list for each book.
            final List<Long> bookIds = getBookIds(source.getId());
            booksMoved = bookIds.size();

            for (final long bookId : bookIds) {
                final Book book = Book.from(bookId);

                final List<Author> fromBook = book.getAuthors();
                final List<Author> destList = new ArrayList<>();

                for (final Author item : fromBook) {
                    if (source.getId() == item.getId()) {
                        // We MUST preserve the author role as originally set.
                        target.setRole(item.getRole());
                        destList.add(target);
                        // We could 'break' here as there should be no duplicates,
                        // but paranoia...
                    } else {
                        // just keep/copy
                        destList.add(item);
                    }
                }

                // delete old links and store all new links
                // We KNOW there are no updates needed.
                insertOrUpdate(context, bookId, false, destList, author ->
                        book.getLocale(userLocale).orElse(userLocale));
            }

            // delete the obsolete source.
            delete(context, source);

            if (txLock != null) {
                db.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }

        return booksMoved;
    }

    @Override
    @WorkerThread
    @IntRange(from = 0)
    public int purge() {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.PURGE)) {
            return stmt.executeUpdateDelete(null);
        }
    }

    @Override
    @WorkerThread
    public int rebuildOrderByColumns(@NonNull final Locale locale) {
        int i = 0;
        // We should use the locale from the 1st book in the series...
        // but that is a huge overhead so we use the users preferred Locale.
        try (Cursor cursor = db.rawQuery(Sql.OB_REBUILD_NAMES, null);
             SynchronizedStatement stmt = db.compileStatement(Sql.OB_REBUILD)) {

            while (cursor.moveToNext()) {
                final long id = cursor.getLong(0);
                final String familyName = cursor.getString(1);
                final String familyNameOb = cursor.getString(2);
                final String givenNames = cursor.getString(3);
                final String givenNamesOb = cursor.getString(4);

                // reordering is not applicable, we just want to re-normalise.
                final String newFamilyOb = textNormaliser.strict(familyName, locale);
                final String newGivenOb = textNormaliser.strict(givenNames, locale);

                // only update the database if actually needed.
                if (!Objects.equals(familyNameOb, newFamilyOb)
                    || !Objects.equals(givenNamesOb, newGivenOb)) {
                    stmt.bindString(1, newFamilyOb);
                    stmt.bindString(2, newGivenOb);
                    stmt.bindLong(3, id);
                    stmt.executeUpdateDelete(null);
                    i++;
                }
            }
        }
        return i;
    }

    @Override
    public int fixPositions(@NonNull final Context context)
            throws DaoWriteException {
        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);

        final List<Long> bookIds = getColumnAsLongArrayList(Sql.REPOSITION);
        if (!bookIds.isEmpty()) {
            Synchronizer.SyncLock txLock = null;
            try {
                if (!db.inTransaction()) {
                    txLock = db.beginTransaction(true);
                }

                for (final long bookId : bookIds) {
                    final Book book = Book.from(bookId);
                    final Locale bookLocale = book.getLocale(userLocale).orElse(userLocale);
                    // We KNOW there are no updates needed.
                    insertOrUpdate(context, bookId, false,
                                   book.getAuthors(),
                                   author -> bookLocale);
                }
                if (txLock != null) {
                    db.setTransactionSuccessful();
                }
            } finally {
                if (txLock != null) {
                    db.endTransaction(txLock);
                }
            }
        }
        return bookIds.size();
    }

    private static final class Sql {

        /** Insert an {@link Author}. */
        static final String INSERT =
                INSERT_INTO_ + TBL_AUTHORS.getName()
                + '(' + DBKey.AUTHOR.FAMILY_NAME + ',' + DBKey.AUTHOR.FAMILY_NAME_OB
                + ',' + DBKey.AUTHOR.GIVEN_NAMES + ',' + DBKey.AUTHOR.GIVEN_NAMES_OB
                + ',' + DBKey.AUTHOR.BIRTH_DATE
                + ',' + DBKey.AUTHOR.DEATH_DATE
                + ',' + DBKey.AUTHOR.PICTURE_UUID
                + ',' + DBKey.AUTHOR.COMPLETE
                + ") VALUES (?,?,?,?,?,?,?,?)";

        /** Update an {@link Author}. */
        static final String UPDATE =
                UPDATE_ + TBL_AUTHORS.getName()
                + _SET_ + DBKey.AUTHOR.FAMILY_NAME + "=?," + DBKey.AUTHOR.FAMILY_NAME_OB + "=?"
                + ',' + DBKey.AUTHOR.GIVEN_NAMES + "=?," + DBKey.AUTHOR.GIVEN_NAMES_OB + "=?"
                + ',' + DBKey.AUTHOR.BIRTH_DATE + "=?"
                + ',' + DBKey.AUTHOR.DEATH_DATE + "=?"
                + ',' + DBKey.AUTHOR.PICTURE_UUID + "=?"
                + ',' + DBKey.AUTHOR.COMPLETE + "=?"
                + _WHERE_ + DBKey.PK_ID + "=?";

        static final String SET_COMPLETE =
                UPDATE_ + TBL_AUTHORS.getName()
                + _SET_ + DBKey.AUTHOR.COMPLETE + "=?"
                + _WHERE_ + DBKey.PK_ID + "=?";

        /** Delete an {@link Author}. */
        static final String DELETE_BY_ID =
                DELETE_FROM_ + TBL_AUTHORS.getName()
                + _WHERE_ + DBKey.PK_ID + "=?";

        /** Purge all {@link Author}s which are no longer in use. */
        static final String PURGE =
                DELETE_FROM_ + TBL_AUTHORS.getName()

                + _WHERE_ + DBKey.PK_ID + _NOT_IN_
                + '(' + SELECT_DISTINCT_ + DBKey.FK_AUTHOR
                + _FROM_ + TBL_BOOK_AUTHOR.getName() + ')'

                + _AND_ + DBKey.PK_ID + _NOT_IN_
                + '(' + SELECT_DISTINCT_ + DBKey.FK_AUTHOR
                + _FROM_ + TBL_TOC_ENTRIES.getName() + ')'

                + _AND_ + DBKey.PK_ID + _NOT_IN_
                + '(' + SELECT_DISTINCT_ + DBKey.FK_AUTHOR_PSEUDONYM
                + _FROM_ + TBL_PSEUDONYM_AUTHOR.getName() + ')'
                + _AND_ + DBKey.PK_ID + _NOT_IN_
                + '(' + SELECT_DISTINCT_ + DBKey.FK_AUTHOR_REAL_AUTHOR
                + _FROM_ + TBL_PSEUDONYM_AUTHOR.getName() + ')';

        /** Insert the link between a {@link Book} and an {@link Author}. */
        static final String INSERT_BOOK_LINK =
                INSERT_INTO_ + TBL_BOOK_AUTHOR.getName()
                + '(' + DBKey.FK_BOOK
                + ',' + DBKey.FK_AUTHOR
                + ',' + DBKey.AUTHOR.BOOK_AUTHOR_POSITION
                + ',' + DBKey.AUTHOR.BOOK_AUTHOR_ROLE
                + ") VALUES(?,?,?,?)";

        /**
         * Delete the link between a {@link Book} and an {@link Author}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String DELETE_BOOK_LINKS_BY_BOOK_ID =
                DELETE_FROM_ + TBL_BOOK_AUTHOR.getName() + _WHERE_ + DBKey.FK_BOOK + "=?";

        /** Insert the link between a pseudonym name and an {@link Author}. */
        static final String INSERT_PSEUDONYM_LINKS =
                INSERT_INTO_ + TBL_PSEUDONYM_AUTHOR.getName()
                + '(' + DBKey.FK_AUTHOR_PSEUDONYM
                + ',' + DBKey.FK_AUTHOR_REAL_AUTHOR
                + ") VALUES (?,?)";

        /**
         * Delete the link between a pseudonym name and an {@link Author}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String DELETE_PSEUDONYM_LINKS =
                DELETE_FROM_ + TBL_PSEUDONYM_AUTHOR.getName()
                + _WHERE_ + DBKey.FK_AUTHOR_PSEUDONYM + "=?";

        /** Get a count of the {@link Author}s. */
        static final String COUNT_ALL =
                SELECT_COUNT_FROM_ + TBL_AUTHORS.getName();

        /** Count the number of {@link Book}'s by an {@link Author}. */
        static final String COUNT_BOOKS =
                SELECT_COUNT_FROM_ + TBL_BOOK_AUTHOR.getName()
                + _WHERE_ + DBKey.FK_AUTHOR + "=?";

        /** A list of all {@link Author}s, unordered. */
        static final String SELECT_ALL =
                SELECT_ + TBL_AUTHORS.dot("*")
                + ',' + TBL_PSEUDONYM_AUTHOR.dotAs(DBKey.FK_AUTHOR_REAL_AUTHOR)
                + _FROM_ + TBL_AUTHORS.as() + TBL_AUTHORS.leftOuterJoin(TBL_PSEUDONYM_AUTHOR);

        /** Get an {@link Author} by its id. */
        static final String SELECT_BY_ID = SELECT_ALL + _WHERE_ + DBKey.PK_ID + "=?";

        /**
         * Find an {@link Author} by family and given name.
         * The lookup is by EQUALITY and CASE-SENSITIVE.
         * <p>
         * Searching on reordered is not applicable as the fields are separated.
         */
        static final String FIND_BY_NAME =
                SELECT_ALL
                + _WHERE_ + DBKey.AUTHOR.FAMILY_NAME + "=?" + _COLLATION
                + _AND_ + DBKey.AUTHOR.GIVEN_NAMES + "=?" + _COLLATION;

        /**
         * All {@link Author}s for a {@link Book}.
         * Ordered by position.
         */
        static final String FIND_BY_BOOK_ID =
                SELECT_DISTINCT_ + TBL_AUTHORS.dotAs(DBKey.PK_ID,
                                                     DBKey.AUTHOR.FAMILY_NAME,
                                                     DBKey.AUTHOR.GIVEN_NAMES,
                                                     DBKey.AUTHOR.BIRTH_DATE,
                                                     DBKey.AUTHOR.DEATH_DATE,
                                                     DBKey.AUTHOR.PICTURE_UUID,
                                                     DBKey.AUTHOR.COMPLETE)
                + ',' + TBL_BOOK_AUTHOR.dotAs(DBKey.AUTHOR.BOOK_AUTHOR_POSITION,
                                              DBKey.AUTHOR.BOOK_AUTHOR_ROLE)

                + ',' + TBL_PSEUDONYM_AUTHOR.dotAs(DBKey.FK_AUTHOR_REAL_AUTHOR)

                + _FROM_ + TBL_BOOK_AUTHOR.startJoin(TBL_AUTHORS)
                + TBL_AUTHORS.leftOuterJoin(TBL_PSEUDONYM_AUTHOR)
                + _WHERE_ + TBL_BOOK_AUTHOR.dot(DBKey.FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOK_AUTHOR.dot(DBKey.AUTHOR.BOOK_AUTHOR_POSITION);

        /** All {@link Book}s (id only!) for a given {@link Author}. */
        static final String FIND_BOOK_IDS_BY_AUTHOR_ID =
                SELECT_ + TBL_BOOK_AUTHOR.dotAs(DBKey.FK_BOOK)
                + _FROM_ + TBL_BOOK_AUTHOR.as()
                + _WHERE_ + TBL_BOOK_AUTHOR.dot(DBKey.FK_AUTHOR) + "=?";


        /**
         * 2025-12: we're using LEFT JOIN now... so eliminate nulls.
         * This is paranoia... we're already/supposed to filter for null->""
         * when we get the fields from the {@link DataHolder}
         */
        @SuppressWarnings("CheckStyle")
        private static String COALESCE(@NonNull final String column) {
            return "COALESCE(" + column + ",'')";
        }

        /** Column definition for sorting by given-names first. */
        static final String SORT_AUTHOR_GIVEN_FIRST =
                CASE_WHEN_ + COALESCE(TBL_AUTHORS.dot(DBKey.AUTHOR.GIVEN_NAMES_OB)) + "=''"
                + _THEN_ + COALESCE(TBL_AUTHORS.dot(DBKey.AUTHOR.FAMILY_NAME_OB))
                + _ELSE_ + TBL_AUTHORS.dot(DBKey.AUTHOR.GIVEN_NAMES_OB)
                + "||" + TBL_AUTHORS.dot(DBKey.AUTHOR.FAMILY_NAME_OB)
                + _END;

        /** Column definition for sorting by family-name first. */
        static final String SORT_AUTHOR_FAMILY_FIRST =
                CASE_WHEN_ + COALESCE(TBL_AUTHORS.dot(DBKey.AUTHOR.GIVEN_NAMES_OB)) + "=''"
                + _THEN_ + COALESCE(TBL_AUTHORS.dot(DBKey.AUTHOR.FAMILY_NAME_OB))
                + _ELSE_ + TBL_AUTHORS.dot(DBKey.AUTHOR.FAMILY_NAME_OB)
                + "||" + TBL_AUTHORS.dot(DBKey.AUTHOR.GIVEN_NAMES_OB)
                + _END;

        /** Column definition for displaying by given-names first. */
        static final String DISPLAY_AUTHOR_GIVEN_FIRST =
                CASE_WHEN_ + COALESCE(TBL_AUTHORS.dot(DBKey.AUTHOR.GIVEN_NAMES)) + "=''"
                + _THEN_ + COALESCE(TBL_AUTHORS.dot(DBKey.AUTHOR.FAMILY_NAME))
                + _ELSE_ + TBL_AUTHORS.dot(DBKey.AUTHOR.GIVEN_NAMES)
                + "||' '||" + TBL_AUTHORS.dot(DBKey.AUTHOR.FAMILY_NAME)
                + _END;

        /** Column definition for displaying by family-name first. */
        static final String DISPLAY_AUTHOR_FAMILY_FIRST =
                CASE_WHEN_ + COALESCE(TBL_AUTHORS.dot(DBKey.AUTHOR.GIVEN_NAMES)) + "=''"
                + _THEN_ + COALESCE(TBL_AUTHORS.dot(DBKey.AUTHOR.FAMILY_NAME))
                + _ELSE_ + TBL_AUTHORS.dot(DBKey.AUTHOR.FAMILY_NAME)
                + "||', '||" + TBL_AUTHORS.dot(DBKey.AUTHOR.GIVEN_NAMES)
                + _END;

        /** Get a list of {@link Author} "given family" names for use in a dropdown selection. */
        static final String SELECT_ALL_NAMES_FORMATTED_GIVEN_FIRST =
                SELECT_ + DISPLAY_AUTHOR_GIVEN_FIRST
                + _FROM_ + TBL_AUTHORS.as()
                + _ORDER_BY_ + DBKey.AUTHOR.FAMILY_NAME_OB + _COLLATION
                + ',' + DBKey.AUTHOR.GIVEN_NAMES_OB + _COLLATION;

        /** Get a list of {@link Author} "family, given" names for use in a dropdown selection. */
        static final String SELECT_ALL_NAMES_FORMATTED_FAMILY_FIRST =
                SELECT_ + DISPLAY_AUTHOR_FAMILY_FIRST
                + _FROM_ + TBL_AUTHORS.as()
                + _ORDER_BY_ + DBKey.AUTHOR.FAMILY_NAME_OB + _COLLATION
                + ',' + DBKey.AUTHOR.GIVEN_NAMES_OB + _COLLATION;

        /** Get a list of {@link Author} family names for use in a dropdown selection. */
        static final String SELECT_ALL_FAMILY_NAMES =
                SELECT_DISTINCT_ + DBKey.AUTHOR.FAMILY_NAME
                + _FROM_ + TBL_AUTHORS.getName()
                + _ORDER_BY_ + DBKey.AUTHOR.FAMILY_NAME_OB + _COLLATION;

        /** Get a list of {@link Author} given names for use in a dropdown selection. */
        static final String SELECT_ALL_GIVEN_NAMES =
                SELECT_DISTINCT_ + DBKey.AUTHOR.GIVEN_NAMES
                + _FROM_ + TBL_AUTHORS.getName()
                + _WHERE_ + DBKey.AUTHOR.GIVEN_NAMES_OB + "<> ''"
                + _ORDER_BY_ + DBKey.AUTHOR.GIVEN_NAMES_OB + _COLLATION;

        /**
         * All Book titles and their first pub. date, for an Author,
         * returned as an {@link AuthorWork}.
         * <p>
         * ORDER BY clause NOT added here, as this statement is used in a union as well.
         * <p>
         * The pub. date is cut down to the year (4 year digits) only.
         * We need TITLE_OB as it will be used to ORDER BY
         */
        static final String FIND_BOOK_TITLES_BY_AUTHOR_ID =
                SELECT_
                + '\'' + AuthorWork.Type.BookLite.asChar() + '\'' + _AS_ + DBKey.AUTHOR_WORK_TYPE
                + ',' + TBL_BOOKS.dotAs(DBKey.PK_ID, DBKey.TITLE, DBKey.TITLE_OB)
                + ",SUBSTR(" + TBL_BOOKS.dot(DBKey.FIRST_PUBLICATION_DATE) + ",0,5)"
                + _AS_ + DBKey.FIRST_PUBLICATION_DATE
                + ',' + TBL_BOOKS.dotAs(DBKey.LANGUAGE)
                + ",1" + _AS_ + DBKey.BOOK_COUNT
                + _FROM_ + TBL_BOOKS.startJoin(TBL_BOOK_AUTHOR);

        /**
         * All {@link TocEntry}'s for an Author,
         * returned as an {@link AuthorWork}.
         * <p>
         * ORDER BY clause NOT added here, as this statement is used in a union as well.
         * <p>
         * The pub. date is cut down to the year (4 year digits) only.
         * We need TITLE_OB as it will be used to ORDER BY
         */
        static final String FIND_TOC_ENTRIES_BY_AUTHOR_ID =
                SELECT_
                + '\'' + AuthorWork.Type.TocEntry.asChar() + "'" + _AS_ + DBKey.AUTHOR_WORK_TYPE
                + ',' + TBL_TOC_ENTRIES.dotAs(DBKey.PK_ID, DBKey.TITLE, DBKey.TITLE_OB)
                // Year only
                + ",SUBSTR(" + TBL_TOC_ENTRIES.dot(DBKey.FIRST_PUBLICATION_DATE) + ",0,5)"
                + _AS_ + DBKey.FIRST_PUBLICATION_DATE
                // The Toc table does not have a language field, just return an empty string
                + ",''" + _AS_ + DBKey.LANGUAGE
                // count the number of books this TOC entry is present in.
                + ", COUNT(" + TBL_TOC_ENTRIES.dot(DBKey.PK_ID) + ")" + _AS_ + DBKey.BOOK_COUNT
                // join with the books, so we can group by toc id, and get the number of books.
                + _FROM_ + TBL_TOC_ENTRIES.startJoin(TBL_BOOK_TOC_ENTRIES);

        static final String REPOSITION =
                SELECT_ + DBKey.FK_BOOK
                + _FROM_
                + '(' + SELECT_ + DBKey.FK_BOOK
                + ",MIN(" + DBKey.AUTHOR.BOOK_AUTHOR_POSITION + ')' + _AS_ + "mp"
                + _FROM_ + TBL_BOOK_AUTHOR.getName()
                + _GROUP_BY_ + DBKey.FK_BOOK
                + ')'
                + _WHERE_ + "mp>1";

        /**
         * Bulk update/replace one Author id with another;
         * effectively moving toc-entries from one Author to the other.
         */
        static final String BULK_UPDATE_AUTHOR =
                UPDATE_ + TBL_TOC_ENTRIES.getName()
                + _SET_ + DBKey.FK_AUTHOR + "=?"
                + _WHERE_ + DBKey.FK_AUTHOR + "=?";

        static final String SELECT_ALL_IMAGE_UUID =
                SELECT_ + DBKey.AUTHOR.PICTURE_UUID + _FROM_ + TBL_AUTHORS.getName()
                + _WHERE_ + DBKey.AUTHOR.PICTURE_UUID + " IS NOT NULL";

        /** All Authors for a rebuild of the {@link DBKey.AUTHOR} name columns. */
        private static final String OB_REBUILD_NAMES =
                SELECT_ + DBKey.PK_ID
                + ',' + DBKey.AUTHOR.FAMILY_NAME
                + ',' + DBKey.AUTHOR.FAMILY_NAME_OB
                + ',' + DBKey.AUTHOR.GIVEN_NAMES
                + ',' + DBKey.AUTHOR.GIVEN_NAMES_OB
                + _FROM_ + TBL_AUTHORS.getName();
        private static final String OB_REBUILD =
                UPDATE_ + TBL_AUTHORS.getName() + _SET_
                + DBKey.AUTHOR.FAMILY_NAME_OB + "=?"
                + ',' + DBKey.AUTHOR.GIVEN_NAMES_OB + "=?"
                + _WHERE_ + DBKey.PK_ID + "=?";
    }
}
