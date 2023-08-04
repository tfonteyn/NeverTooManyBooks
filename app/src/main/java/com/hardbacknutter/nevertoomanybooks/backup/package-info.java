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

/**
 * {@link com.hardbacknutter.nevertoomanybooks.backup.ExportHelper}
 * is setup by the user UI, and determines the
 * {@link com.hardbacknutter.nevertoomanybooks.io.ArchiveEncoding}
 * of the given archive.
 * <p>
 * The ExportHelper is passed to:
 * {@link com.hardbacknutter.nevertoomanybooks.io.DataWriterViewModel}.DataWriterTask
 * The task gets an writer from the ExportHelper, and delegates the job to it:
 * {@link com.hardbacknutter.nevertoomanybooks.io.DataWriter}
 * <p>
 * The writer gets the desired list of entries it needs to write from the helper.
 * {@link com.hardbacknutter.nevertoomanybooks.io.RecordType}
 * Writing starts with the header in a fixed format suitable for the archive type
 * Next it loops over the entries, and for each entry
 * invokes an RecordWriter that can write that entry in the desired format.
 * {@link com.hardbacknutter.nevertoomanybooks.io.RecordWriter}
 * The output from the RecordWriter is then streamed into the actual archive.
 * <p>
 * When done, the writer copies the archive file to to a {@link android.net.Uri}
 * and reports back to the task... back to the user UI with:
 * {@link com.hardbacknutter.nevertoomanybooks.backup.ExportResults}.
 * <p>
 * *************************************************************************************
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.backup.ImportHelper}
 * is setup by the user UI, inspects the Uri and determines the
 * {@link com.hardbacknutter.nevertoomanybooks.io.ArchiveEncoding}
 * of the given archive.
 * <p>
 * The helper is passed to:
 * {@link com.hardbacknutter.nevertoomanybooks.io.DataReaderViewModel}.DataReaderTask
 * The task gets a reader from the helper, and delegates the job to it:
 * {@link com.hardbacknutter.nevertoomanybooks.io.DataReader}
 * <p>
 * The reader parses the archive for one or more
 * {@link com.hardbacknutter.nevertoomanybooks.io.ArchiveReaderRecord}
 * Each of these are passed for actual reading to an importer suitable for that record type.
 * {@link com.hardbacknutter.nevertoomanybooks.io.RecordReader}
 * The importer reports back with:
 * {@link com.hardbacknutter.nevertoomanybooks.backup.ImportResults}.
 * The reader accumulates all results from the used importer(s),
 * and finally passes the result back to the task... back to the user UI.
 * <p>
 * In short:
 * Uri -> Helper -> ArchiveEncoding -> Reader
 * -> LOOP(ArchiveReaderRecord -> RecordReader) -> Results
 * <p>
 * The import/export classes are really <strong>too</strong> flexible.
 * <p>
 * Archives written:
 * <ul>
 * <li>csv: books only</li>
 * <li>db: a copy of the internal database</li>
 * <li>json: all data except covers</li>
 * <li>xml: books only</li>
 * <li>zip: all data in json format + covers == full backup</li>
 * </ul>
 * zip can be forced (by changing the version number in the code + recompiling)
 * to contain csv encoded books
 * <p>
 * Archives read:
 * <ul>
 * <li>csv: books only</li>
 * <li>db: nothing actually implemented for now</li>
 * <li>json: all data (except covers)</li>
 * <li>zip: all data in json,xml,csv formats + covers</li>
 * </ul>
 * <p>
 * An entry in an archive will have a Type (books, styles,...) and an Encoding (csv, xml, ...)
 * <p>
 * Not all combinations are implemented (which would be rather pointless) but there are more
 * implementations than really needed.
 */
package com.hardbacknutter.nevertoomanybooks.backup;
