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

    public static final boolean DEBUG_DB_ADAPTER = false;

    public static final boolean DEBUG_DB_SYNC = false;

    public static final boolean DEBUG_TRACKED_CURSER = false;

    public static final boolean DEBUG_LIBRARYTHING = false;

    public static final boolean DEBUG_BOOKLIST_BUILDER = true;

    public static final boolean DEBUG_BOOKSONBOOKSHELF = true;

    public static final boolean DEBUG_IMAGEUTILS = false;

    public static final boolean DEBUG_SQPFragment = false;

    public static final boolean DEBUG_STORAGEUTILS = false;

    public static final boolean DEBUG_TASK_MANAGER = false;

}
