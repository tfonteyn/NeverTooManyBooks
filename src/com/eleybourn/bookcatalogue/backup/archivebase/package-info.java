/**
 * Archive Format
 * ==============
 *
 * Assumed to have multiple 'entries', processed sequentially. Could be zip, tar, or any other
 * amenable stream
 *
 * First entry: "INFO.xml"
 * -----------------------
 *
 * Contains a simple name-value set including:
 *
 * {@link com.eleybourn.bookcatalogue.backup.archivebase.BackupInfo#INFO_ARCHIVER_VERSION}
 * Archiver internal Version number (initially 1)
 *
 * {@link com.eleybourn.bookcatalogue.backup.archivebase.BackupInfo#INFO_CREATION_DATE}
 * Creation Date of archive (in SQL format)
 *
 * {@link com.eleybourn.bookcatalogue.backup.archivebase.BackupInfo#INFO_NUMBOOKS}
 * # of books in archive (will/may be fewer pictures)
 *
 * {@link com.eleybourn.bookcatalogue.backup.archivebase.BackupInfo#INFO_APPVERSIONCODE}
 * BC Version that created it
 *
 * {@link com.eleybourn.bookcatalogue.backup.archivebase.BackupInfo#INFO_COMPATARCHIVER}
 * CompatArchiver
 * Last known compatible archiver version (initially 1, ideally always 1) that can read this archive
 * We may in the dim distance future decide to make a new TAR format that older versions will be unable to
 * understand. This is only a relevant field for DOWNGRADING.
 *
 * For a full list, see {@link com.eleybourn.bookcatalogue.backup.archivebase.BackupInfo}
 *
 * Optional Entries: "INFO_*.xml"
 * ------------------------------
 *
 * Later versions of the archiver *may* introduce specific INFO files for their versions.
 * The archiver should skip over INFO files it does not understand or expect.
 *
 * First Data Entry: "books.csv"
 * -----------------------------
 *
 * A CSV export appropriate for the archiver that created the archive. ie. the most recent
 * archiver version for the app version that is installed.
 *
 * Optional Data Entries: "books<ArchVersion>.csv"
 * -----------------------------------------------
 *
 * For backwards compatibility, there may be alternate CSV files for older versions.
 * The ArchVersion field indicates the last version it was completely compatible with.
 *
 * Scanning for "books*.csv"
 * -------------------------
 *
 * The basic rule is to scan the file until the last BOOKS*.csv is found and use the version that
 * has an ArchVersion >= the current archiver version, or BOOKS.CSV if none match. eg.
 *
 * Suppose the archive contains:
 *
 * BOOKS.csv
 * BOOKS_3.csv
 * BOOKS_5.csv
 *
 * if the current Archiver is version 7 then use BOOKS.csv
 * if the current Archiver is version 5 then use BOOKS_5.csv
 * if the current Archiver is version 4 then use BOOKS_5.csv
 * if the current Archiver is version 1 then use BOOKS_3.csv
 *
 * Optional Data Entries: "snapshot.db"
 * ------------------------------------
 * A copy of the database.
 * Note: v5.2.2 does not write this file. Not sure if any older versions did.
 *
 * Optional Data Entries: "preferences"
 * ------------------------------------
 * xml file with {@link com.eleybourn.bookcatalogue.BookCatalogueApp#APP_SHARED_PREFERENCES}
 *
 * Note: future versions may drop this entry when writing and instead write
 * into an extendable xml file, for example "config.xml", section "<preferences>"
 * Reading legacy file *should* be preserved.
 *
 * Optional Data Entries: "style.blob.[0-9]*"
 * ------------------------------------------
 * Serialised User-defined BooklistStyle
 *
 * Note: future versions may drop this entry when writing and instead write xml file(s)
 * Reading legacy file *should* be preserved.
 *
 * Optional Data Entries: "*.xml"
 * ------------------------------
 * Definition added 2018-11-10, for future expansion.
 *
 *
 * Remaining Data Entries: *.*
 * ---------------------------
 * Any other name is assumed to be a cover image.
 */
package com.eleybourn.bookcatalogue.backup.archivebase;