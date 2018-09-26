package com.eleybourn.bookcatalogue;

/**
 * Global location where you can switch individual DEBUG options of/off
 *
 * Note: when set to true, the global BuildConfig.DEBUG should still suppress them
 *
 * So use something like this:
 *
 *         if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
 *              System.out.println("some debug info);
 *         }
 *
 *         RELEASE: set all these to false !
 *
 *         The compiler should remove all code between dead if() blocks
 */
public final class DEBUG_SWITCHES {

    /** enable timers for performance measurements */
    public static final boolean TIMERS = false;

    public static final boolean DB_ADAPTER = false;

    public static final boolean DB_SYNC = false;
    public static final boolean SQL = false;
    public static final boolean DB_SYNC_QUERY_FOR_LONG = false;

    public static final boolean TRACKED_CURSOR = false;

    public static final boolean LIBRARYTHING = false;

    public static final boolean BOOKLIST_BUILDER = true;

    public static final boolean BOOKSONBOOKSHELF = true;

    public static final boolean IMAGEUTILS = false;

    public static final boolean SQPFragment = false;

    public static final boolean STORAGEUTILS = false;

    public static final boolean TASKMANAGER = false;

}
