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

/**
 * This package contains all date related classes and information.
 * <p>
 * <strong>VERY IMPORTANT:</strong> SQLite date functions:
 * <a href="https://sqlite.org/lang_datefunc.html">https://sqlite.org/lang_datefunc.html</a>
 * <p>
 * <pre>
 *      Function        Equivalent strftime()
 *      date(...)       strftime('%Y-%m-%d', ...)
 *      time(...)       strftime('%H:%M:%S', ...)
 *      datetime(...)   strftime('%Y-%m-%d %H:%M:%S', ...)
 * </pre>
 * <p>
 * ==> the default 'datetime' function <strong>does NOT include the 'T' character</strong>
 * as used in ISO standards.
 * <p>
 * ==> the standard 'current_timestamp' <strong>does NOT include the 'T' character</strong>
 * * as used in ISO standards.
 * <p>
 * Which means that when using {@link java.time.format.DateTimeFormatter}
 * versus SQLite 'datetime/current_timestamp' conversions between 'T' and ' ' will need to be done.
 * Reading/writing itself is not an issue (parsers in sqlite and in our app take care of that),
 * but <strong>string COMPARE as in 'where' clauses</strong> will cause faulty results.
 * <p>
 * Affected columns are those of type
 * {@link  com.hardbacknutter.nevertoomanybooks.database.definitions.SqLiteDataType#DateTime}.
 * Status on 2020-09-26:
 * {@link com.hardbacknutter.nevertoomanybooks.database.DBKey#DATE_ADDED__UTC}
 * {@link com.hardbacknutter.nevertoomanybooks.database.DBKey#DATE_LAST_UPDATED__UTC}
 * and
 * {@link com.hardbacknutter.nevertoomanybooks.database.CacheDbHelper}#IMAGE_LAST_UPDATED__UTC
 * <p>
 * Columns of type
 * {@link  com.hardbacknutter.nevertoomanybooks.database.definitions.SqLiteDataType#Date}
 * Status on 2020-09-26:
 * {@link com.hardbacknutter.nevertoomanybooks.database.DBKey#READ_START__DATE}
 * {@link com.hardbacknutter.nevertoomanybooks.database.DBKey#READ_END__DATE}
 * {@link com.hardbacknutter.nevertoomanybooks.database.DBKey#DATE_ACQUIRED}
 * {@link com.hardbacknutter.nevertoomanybooks.database.DBKey#BOOK_PUBLICATION__DATE}
 * {@link com.hardbacknutter.nevertoomanybooks.database.DBKey#FIRST_PUBLICATION__DATE}
 */
package com.hardbacknutter.nevertoomanybooks.utils.dates;
