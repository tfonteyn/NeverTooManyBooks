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

package com.hardbacknutter.nevertoomanybooks.backup;

import com.hardbacknutter.nevertoomanybooks.Base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ImportResultsTest
        extends Base {

    @Test
    void t01() {
        final ImportResults results = new ImportResults();
        int line = 0;

        for (int i = 0; i < 8; i++) {
            results.handleRowException(context, ++line, new Exception("ex-" + i), "comment-" + i);
        }

        assertEquals(8, results.booksFailed);
        assertEquals(8, results.failedLinesNr.size());
        assertEquals(8, results.failedLinesMessage.size());

        for (int i = 0; i < 8; i++) {
            results.handleRowException(context, ++line, new Exception("ex2-" + i), "comment2-" + i);
        }

        assertEquals(16, results.booksFailed);
        assertEquals(ImportResults.MAX_FAIL_LINES_REPORTED, results.failedLinesNr.size());
        assertEquals(ImportResults.MAX_FAIL_LINES_REPORTED, results.failedLinesMessage.size());
    }

    @Test
    void t02() {
        final ImportResults results = new ImportResults();
        int line = 0;

        for (int i = 0; i < 8; i++) {
            results.handleRowException(context, ++line, new Exception("ex-" + i), "comment-" + i);
        }

        assertEquals(8, results.booksFailed);
        assertEquals(8, results.failedLinesNr.size());
        assertEquals(8, results.failedLinesMessage.size());

        final ImportResults results2 = new ImportResults();
        for (int i = 0; i < 8; i++) {
            results2.handleRowException(context, ++line, new Exception("ex2-" + i),
                                        "comment2-" + i);
        }

        assertEquals(8, results2.booksFailed);
        assertEquals(8, results2.failedLinesNr.size());
        assertEquals(8, results2.failedLinesMessage.size());

        results.add(results2);

        assertEquals(16, results.booksFailed);
        assertEquals(ImportResults.MAX_FAIL_LINES_REPORTED, results.failedLinesNr.size());
        assertEquals(ImportResults.MAX_FAIL_LINES_REPORTED, results.failedLinesMessage.size());
    }
}
