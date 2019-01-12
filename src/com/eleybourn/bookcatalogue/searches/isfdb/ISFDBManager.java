package com.eleybourn.bookcatalogue.searches.isfdb;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.EditBookTOCFragment;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class ISFDBManager {

    private static final String TAG = "ISFDB.";

    private static final String PREFS_HOST_URL = TAG + "hostUrl";
    /**
     * task queue for the searching/parsing of content (ant titles).
     * specifically used for direct search calls from {@link EditBookTOCFragment}
     * <p>
     * Not used for 'small' search
     */
    @Nullable
    private static SimpleTaskQueue taskQueue;

    private ISFDBManager() {
    }

    @NonNull
    public static String getBaseURL() {
        //noinspection ConstantConditions
        return Prefs.getPrefs().getString(PREFS_HOST_URL, "http://www.isfdb.org");
    }

    public static void setBaseURL(@NonNull final String url) {
        Prefs.getPrefs().edit().putString(PREFS_HOST_URL, url).apply();
    }

    /**
     * ENHANCE: For now, always returns the image from the first edition found.
     *
     * @param isbn for book cover to find.
     *
     * @return found & saved File, or null when none
     */
    @Nullable
    public static File getCoverImage(@NonNull final String isbn) {
        Bundle bookData = new Bundle();
        try {
            // no specific API, just go search the book
            search(isbn, "", "", bookData, true);

            ArrayList<String> imageList =
                    bookData.getStringArrayList(UniqueId.BKEY_THUMBNAIL_FILE_SPEC_ARRAY);
            if (imageList != null && !imageList.isEmpty()) {
                File found = new File(imageList.get(0));
                File coverFile = new File(found.getAbsolutePath() + '_' + isbn);
                StorageUtils.renameFile(found, coverFile);
                return coverFile;
            }
        } catch (IOException e) {
            Logger.error(e, "Error getting thumbnail from ISFDB");
        }

        return null;
    }

    public static void search(@NonNull final String isbn,
                              @NonNull final String author,
                              @NonNull final String title,
                              @NonNull final Bundle /* out */ bookData,
                              final boolean fetchThumbnail)
            throws IOException {
        if (IsbnUtils.isValid(isbn)) {
            List<String> editions = new Editions(isbn).fetch();
            if (editions.size() > 0) {
                ISFDBBook isfdbBook = new ISFDBBook(editions);
                isfdbBook.fetch(bookData, fetchThumbnail);
            }
        } else {
            //replace spaces in author/title with %20
            //TODO: implement ISFDB search by author/title
            String urlText = getBaseURL() + "/cgi-bin/adv_search_results.cgi?" +
                    "title_title%3A" + title.replace(" ", "%20") +
                    "%2B" +
                    "author_canonical%3A" + author.replace(" ", "%20");
            throw new UnsupportedOperationException(urlText);
        }
        //TODO: only let IOExceptions out (except RTE's)
    }

    /**
     * FIXME: Need to redo this by using the SearchISFDBTask really but do this *after* migrating
     * away from ManagedTask.
     * <p>
     * specifically used by {@link EditBookTOCFragment}.
     * First onProgress, get all editions for the ISBN
     */
    public static void searchEditions(@NonNull final String isbn,
                                      @NonNull final ISFDBResultsListener listener) {
        // Setup the background fetcher
        if (taskQueue == null) {
            taskQueue = new SimpleTaskQueue("ISFDB-toc-search-tasks");
        }
        taskQueue.enqueue(new ISFDBEditionsTask(isbn, listener));
    }

    /**
     * specifically used by {@link EditBookTOCFragment}.
     * First onProgress, get all editions for the ISBN via
     * {@link #searchEditions(String, ISFDBResultsListener)}
     * That will then call this one
     */
    public static void search(@NonNull final List<String> editionUrls,
                              @NonNull final ISFDBResultsListener listener) {
        // Setup the background fetcher
        if (taskQueue == null) {
            taskQueue = new SimpleTaskQueue("ISFDB-toc-search-tasks");
        }
        taskQueue.enqueue(new ISFDBBookTask(editionUrls, false, listener));
    }
}
