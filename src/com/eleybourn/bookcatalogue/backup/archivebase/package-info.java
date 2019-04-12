/**
 * Archive Format
 * ==============
 * <p>
 * The below was for archiver version 1. Things have changed... TODO: update this description
 * <p>
 * <p>
 * Assumed to have multiple 'entries', processed sequentially. Could be zip, tar, or any other
 * amenable stream
 * <p>
 * First entry: "INFO.xml"
 * -----------------------
 * <p>
 * Contains a simple name-value set including:
 * <p>
 * {@link com.eleybourn.bookcatalogue.backup.archivebase.BackupInfo#INFO_ARCHIVER_VERSION}
 * Archiver internal Version number.
 * <p>
 * {@link com.eleybourn.bookcatalogue.backup.archivebase.BackupInfo#INFO_CREATION_DATE}
 * Creation Date of archive (in SQL format).
 * <p>
 * {@link com.eleybourn.bookcatalogue.backup.archivebase.BackupInfo#INFO_NUMBER_OF_BOOKS}
 * # of books in archive (will/may be fewer pictures).
 * <p>
 * {@link com.eleybourn.bookcatalogue.backup.archivebase.BackupInfo#INFO_APP_VERSION_CODE}
 * Application Version that created it.
 * <p>
 * For a full list, see {@link com.eleybourn.bookcatalogue.backup.archivebase.BackupInfo}
 * <p>
 * Optional Entries: "INFO_*.xml"
 * ------------------------------
 * <p>
 * Later versions of the archiver *may* introduce specific INFO files for their versions.
 * The archiver should skip over INFO files it does not understand or expect.
 * <p>
 * First Data Entry: "books.csv"
 * -----------------------------
 * <p>
 * A CSV export appropriate for the archiver that created the archive. i.e. the most recent
 * archiver version for the app version that is installed.
 * <p>
 * Optional Data Entries: "books<ArchVersion>.csv"
 * -----------------------------------------------
 * <p>
 * For backwards compatibility, there may be alternate CSV files for older versions.
 * The ArchVersion field indicates the last version it was completely compatible with.
 * <p>
 * Scanning for "books*.csv"
 * -------------------------
 * <p>
 * The basic rule is to scan the file until the last BOOKS*.csv is found and use the version that
 * has an ArchVersion >= the current archiver version, or BOOKS.CSV if none match. eg.
 * <p>
 * Suppose the archive contains:
 * <p>
 * BOOKS.csv
 * BOOKS_3.csv
 * BOOKS_5.csv
 * <p>
 * if the current Archiver is version 7 then use BOOKS.csv
 * if the current Archiver is version 5 then use BOOKS_5.csv
 * if the current Archiver is version 4 then use BOOKS_5.csv
 * if the current Archiver is version 1 then use BOOKS_3.csv
 * <p>
 * Optional Data Entries: "snapshot.db"
 * ------------------------------------
 * A copy of the database.
 * Note: v5.2.2 does not write this file. Not sure if any older versions did.
 * <p>
 * Optional Data Entries: "preferences"
 * ------------------------------------
 * <p>
 * Note: future versions may drop this entry when writing and instead write
 * into an extendable xml file, for example "config.xml", section "<preferences>"
 * Reading legacy file *should* be preserved.
 * <p>
 * Optional Data Entries: "style.blob.[0-9]*"
 * ------------------------------------------
 * Serialised User-defined BooklistStyle
 * <p>
 * Note: future versions may drop this entry when writing and instead write xml file(s)
 * Reading legacy file *should* be preserved.
 * <p>
 * Optional Data Entries: "*.xml"
 * ------------------------------
 * Definition added 2018-11-10, for future expansion.
 * <p>
 * <p>
 * Remaining Data Entries: *.*
 * ---------------------------
 * Any other name is assumed to be a cover image.
 */
package com.eleybourn.bookcatalogue.backup.archivebase;