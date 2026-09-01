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

// 2026-08-22: First steps in splitting the BookDao....
public class BookRepository {

    private final BookDao bookDao;
    private final BookDaoHelper bookDaoHelper;
    private final Locale userLocale;

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

    @IntRange(from = 1)
    public long insert(@NonNull final Context context,
                       @NonNull final Book book,
                       @NonNull final Set<BookDao.BookFlag> flags)
            throws DaoWriteException {
        return bookDao.insert(context, userLocale, bookDaoHelper, book, flags);
    }

    public void update(@NonNull final Context context,
                       @NonNull final Book book,
                       @NonNull final Set<BookDao.BookFlag> flags)
            throws DaoWriteException {
        bookDao.update(context, userLocale, bookDaoHelper, book, flags);
    }
}
