package com.eleybourn.bookcatalogue.searches.isfdb;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.EditBookTOCFragment;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ISFDBManager {

    private static final String PREFS_HOST_URL = "ISFDBManager.hostUrl";
    /**
     * task queue for the searching/parsing of content (ant titles)
     * specifically used for direct search calls from {@link EditBookTOCFragment}
     *
     * Not used for 'normal' search
     */
    @Nullable
    private static SimpleTaskQueue taskQueue = null;

    @NonNull
    public static String getBaseURL() {
        //noinspection ConstantConditions
        return BookCatalogueApp.Prefs.getString(PREFS_HOST_URL, "http://www.isfdb.org");
    }

    public static void setBaseURL(final @NonNull String url) {
        BookCatalogueApp.Prefs.putString(PREFS_HOST_URL, url);
    }

    @Nullable
    static public File getCoverImage(final @NonNull String isbn) {
        Bundle bookData = new Bundle();
        try {
            search(isbn, "", "", bookData, true);
            String fileSpec = bookData.getString(UniqueId.BKEY_THUMBNAIL_FILES_SPEC);
            if (fileSpec != null) {
                File incomingFile = new File(fileSpec);
                File newName = new File(incomingFile.getAbsolutePath() + "_" + isbn);
                StorageUtils.renameFile(incomingFile, newName);
                return newName;
            } else {
                return null;
            }
        } catch (Exception e) {
            Logger.error(e, "Error getting thumbnail from ISFDB");
            return null;
        }
    }

    public static void search(final @NonNull String isbn,
                              @NonNull String author,
                              @NonNull String title,
                              final @NonNull Bundle /* out */ book,
                              final boolean fetchThumbnail) throws IOException {
        //replace spaces with %20
        author = author.replace(" ", "%20");
        title = title.replace(" ", "%20");

        if (IsbnUtils.isValid(isbn)) {
            List<String> editions = new Editions(isbn).fetch();
            if (editions.size() > 0) {
                ISFDBBook isfdbBook = new ISFDBBook(editions.get(0));
                isfdbBook.fetch(book, fetchThumbnail);
            }
        } else {
            //TODO: implement ISFDB search by author/title
            String path = getBaseURL() + "/cgi-bin/adv_search_results.cgi?title_title%3A" + title + "%2Bauthor_canonical%3A" + author;
            throw new UnsupportedOperationException(path);
        }
        //TODO: only let IOExceptions out (except RTE's)
    }


    /**
     * FIXME this has been shoehorned in here. Need to redo this by using the SearchISFDBTask really
     *
     * specifically used by {@link EditBookTOCFragment}
     * First step, get all editions for the ISBN
     */
    public static void searchEditions(final @NonNull String isbn, final @NonNull HandlesISFDB callback) {
        // Setup the background fetcher
        if (taskQueue == null) {
            taskQueue = new SimpleTaskQueue("isfdb");
        }
        taskQueue.enqueue(new ISFDBEditionsTask(isbn, callback));
    }

    /**
     * FIXME this has been shoehorned in here. Need to redo this by using the SearchISFDBTask really
     *
     * specifically used by {@link EditBookTOCFragment}
     * First step, get all editions for the ISBN via {@link #searchEditions(String, HandlesISFDB)}
     * That will then call this one
     */
    public static void search(final @NonNull String bookUrl, final @NonNull HandlesISFDB callback) {
        // Setup the background fetcher
        if (taskQueue == null) {
            taskQueue = new SimpleTaskQueue("isfdb");
        }
        taskQueue.enqueue(new ISFDBBookTask(bookUrl, false, callback));
    }
}
