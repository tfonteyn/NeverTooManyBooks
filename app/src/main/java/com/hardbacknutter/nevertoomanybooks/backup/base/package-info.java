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
 * This package contains the format agnostic classes.
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveExportTask}
 * The core work is done by:
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriter}
 * Writes to a File, and when done, copies that File to a {@link android.net.Uri}
 * The actual writing is done by:
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.Exporter}
 * Writes to a {@link java.io.Writer}.
 * Reports back with:
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.ExportResults}.
 * <p>
 * <p>
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveImportTask}
 * The core work is done by:
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader}
 * Reads from a {@link android.net.Uri}.
 * The actual reading is done by:
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.Importer}
 * Reads from the input stream using:
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.ReaderEntity}
 * Reports back with:
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.ImportResults}.
 */
package com.hardbacknutter.nevertoomanybooks.backup.base;
