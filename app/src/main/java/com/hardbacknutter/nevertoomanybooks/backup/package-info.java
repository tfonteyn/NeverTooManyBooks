/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
 * Top level:
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriter}
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader}
 * When needed, the archiver uses:
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.Exporter}
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.Importer}
 *
 * Currently (2020-03-15) the CSV import/export functionality bypasses the archiver,
 * and accesses the CsvExport/CsvImporter directly.
 *
 * Archive Format
 * ==============
 * <p>
 * Assumed to have multiple 'entries', processed sequentially. Could be zip, tar, or any other
 * amenable stream and even a text-file (see the XMLArchiver)
 *
 * Entries don't have to be in a particular order, but reading is faster if the order is:
 * <ol>
 *     <li>INFO.xml</li>
 *     <li>styles.xml</li>
 *     <li>preferences.xml</li>
 *     <li>other files</li>
 * </ol>
 * <p>
 * Other then INFO.xml, all entries are optional.
 * <p>
 * ---------------------------------------------------------------------
 * "INFO.xml"
 * <p>
 * Contains a simple name-value set including:
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveInfo} #INFO_ARCHIVER_VERSION
 * Archiver internal Version number.
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveInfo} #INFO_CREATION_DATE
 * Creation Date of archive (in SQL format).
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveInfo} #INFO_NUMBER_OF_BOOKS
 * # of books in archive (will/may be fewer pictures).
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveInfo} #INFO_APP_VERSION_CODE
 * Application Version that created it.
 * <p>
 * For a full list, see {@link com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveInfo}
 * <p>
 * ---------------------------------------------------------------------
 * "styles.xml"
 * <p>
 * User-defined BooklistStyles
 * <p>
 * ---------------------------------------------------------------------
 * "preferences.xml"
 * <p>
 * User settings
 * <p>
 * ---------------------------------------------------------------------
 * "books.csv"
 * <p>
 * A CSV export appropriate for the archiver that created the archive. i.e. the most recent
 * archiver version for the app version that is installed.
 * <p>
 * ---------------------------------------------------------------------
 * "*.jpg" / "*.png"
 * <p>
 * Cover image. The full filename pattern is determined by the archiver.
 */
package com.hardbacknutter.nevertoomanybooks.backup;
