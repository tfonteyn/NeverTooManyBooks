/*
 * @Copyright 2020 HardBackNutter
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
 * This package contains the encoding-agnostic classes.
 * <p>
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.backup.ExportHelper}
 * is setup by the user UI, and determines the
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveEncoding}
 * of the given archive.
 * <p>
 * The ExportHelper is passed to:
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriterTask}
 * The task gets an ArchiveWriter from the ExportHelper, and delegates the job to it:
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriter}
 * <p>
 * The ArchiveWriter gets the desired list of entries it needs to write from the helper.
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.RecordType}
 * Writing starts with the header in a fixed format suitable for the archive type
 * Next it loops over the entries, and for each entry
 * invokes an RecordWriter that can write that entry in the desired format.
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.RecordWriter}
 * The output from the RecordWriter is then streamed into the actual archive.
 * <p>
 * When done, the ArchiveWriter copies the archive file to to a {@link android.net.Uri}
 * and reports back to the task... back to the user UI with:
 * {@link com.hardbacknutter.nevertoomanybooks.backup.ExportResults}.
 * <p>
 * <p>
 * *************************************************************************************
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.backup.ImportHelper}
 * is setup by the user UI, inspects the Uri and determines the
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveEncoding}
 * of the given archive.
 * <p>
 * The helper is passed to:
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReaderTask}
 * The task gets a reader from the helper, and delegates the job to it:
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader}
 * <p>
 * The reader parses the archive for one or more
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReaderRecord}
 * Each of these are passed for actual reading to an importer suitable for that record type.
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.RecordReader}
 * The importer reports back with:
 * {@link com.hardbacknutter.nevertoomanybooks.backup.ImportResults}.
 * The reader accumulates all results from the used importer(s),
 * and finally passes the result back to the task... back to the user UI.
 * <p>
 * In short:
 * Uri -> Helper -> ArchiveEncoding -> ArchiveReader -> LOOP(ArchiveReaderRecord -> RecordReader) -> Results
 */
package com.hardbacknutter.nevertoomanybooks.backup.base;
