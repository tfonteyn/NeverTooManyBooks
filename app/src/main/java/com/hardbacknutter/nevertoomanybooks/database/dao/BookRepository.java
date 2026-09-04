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

package com.hardbacknutter.nevertoomanybooks.database.dao;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.TableInfo;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.BookDaoHelper;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;

// 2026-08-22: First steps in splitting the BookDao....
public class BookRepository {

    private final BookDao bookDao;
    private final BookDaoHelper bookDaoHelper;
    private final Locale userLocale;

    /**
     * Constructor.
     * <p>
     * ENHANCE: pass in {@link DataReader.Updates} option to propagate to Authors
     *  and eventually to other linked objects.
     * <p>
     * Dev. note: This class is used/created in ViewModels, do NOT store the Context!
     *
     * @param context Current context (noy stored)
     */
    public BookRepository(@NonNull final Context context) {
        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        bookDao = serviceLocator.getBookDao();

        final SynchronizedDb db = serviceLocator.getDb();
        final TableInfo tableInfo = db.getTableInfo(DBDefinitions.TBL_BOOKS);

        final List<Locale> userLocales = LocaleListUtils.asList(
                context.getResources().getConfiguration().getLocales());
        bookDaoHelper = new BookDaoHelper(tableInfo, userLocales);
        userLocale = userLocales.get(0);
    }

    /**
     * Create a new {@link Book}.
     *
     * @param context Current context
     * @param book    object to insert. Will be updated with the id.
     * @param flags   See {@link BookDao.ImportFlag} for flag definitions
     *
     * @return the row id of the newly inserted row
     *
     * @throws DaoWriteException on failure
     */
    @IntRange(from = 1)
    public long insert(@NonNull final Context context,
                       @NonNull final Book book,
                       @NonNull final Set<BookDao.ImportFlag> flags)
            throws DaoWriteException {
        return bookDao.insert(context, userLocale, bookDaoHelper, book, flags);
    }

    /**
     * Update the given {@link Book}.
     * <p>
     * This will update <strong>ONLY</strong> the fields present in the given Book.
     * Non-present fields will not be touched. i.e. this is a delta operation.
     * <p>
     * TRIGGERS:
     * - If the Code of a {@link Book} is changed, reset external ID's and sync dates.
     *
     * @param context Current context
     * @param book    A collection with the columns to be set.
     *                May contain extra data which will be ignored.
     * @param flags   See {@link BookDao.ImportFlag} for flag definitions
     *
     * @throws DaoWriteException on failure
     */
    public void update(@NonNull final Context context,
                       @NonNull final Book book,
                       @NonNull final Set<BookDao.ImportFlag> flags)
            throws DaoWriteException {
        bookDao.update(context, userLocale, bookDaoHelper, book, flags);
    }
}
