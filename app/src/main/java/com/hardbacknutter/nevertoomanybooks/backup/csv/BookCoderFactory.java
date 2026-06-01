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

package com.hardbacknutter.nevertoomanybooks.backup.csv;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.csv.bc.DefaultBookCoder;
import com.hardbacknutter.nevertoomanybooks.backup.csv.calibre.CalibreBookCoder;
import com.hardbacknutter.nevertoomanybooks.backup.csv.goodreads.GoodreadsBookCoder;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.parsers.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;

final class BookCoderFactory {

    private BookCoderFactory() {

    }

    @NonNull
    static BookCoder create(@NonNull final Context context,
                            @NonNull final CsvFormat csvFormat,
                            @NonNull final DataReader.Updates updateOption,
                            @NonNull final List<String> csvColumnNames,
                            @NonNull final Style defaultStyle,
                            @NonNull final List<Locale> userLocales)
            throws DataReaderException {

        switch (csvFormat) {
            case Goodreads:
                return new GoodreadsBookCoder(context, defaultStyle, userLocales, csvColumnNames);

            case Calibre:
                return new CalibreBookCoder(context, defaultStyle, userLocales, csvColumnNames,
                                            updateOption);

            case BC:
            case Unknown:
            default:
                return createDefault(context, defaultStyle, userLocales, csvColumnNames,
                                     updateOption);
        }
    }

    @NonNull
    private static DefaultBookCoder createDefault(@NonNull final Context context,
                                                  @NonNull final Style defaultStyle,
                                                  @NonNull final List<Locale> userLocales,
                                                  @NonNull final List<String> csvColumnNames,
                                                  @NonNull final DataReader.Updates updateOption)
            throws DataReaderException {
        // Special treatment for the floating point parsers,
        // due to CSV files potentially coming from "foreign" websites.

        // Make sure US is added so we can parse "dot" decimal-separator
        // even when the user Locale is a comma decimal-separator.
        final List<Locale> tmpLocales = new ArrayList<>(userLocales);
        if (!userLocales.contains(Locale.US)) {
            tmpLocales.add(Locale.US);
        }
        // Make sure FRANCE is added so we can parse "comma" decimal-separator
        // even when the user Locale is a dot decimal-separator.
        if (!userLocales.contains(Locale.FRANCE)) {
            tmpLocales.add(Locale.FRANCE);
        }
        final MoneyParser moneyParser = new MoneyParser(userLocales.get(0), tmpLocales);

        final Locale systemLocale = ServiceLocator.getInstance().getSystemLocaleList().get(0);
        final FullDateParser dateParser = new FullDateParser(new ISODateParser(systemLocale),
                                                             userLocales);

        return new DefaultBookCoder(context, defaultStyle, tmpLocales, csvColumnNames, updateOption,
                                    dateParser, moneyParser);
    }
}
