package com.eleybourn.bookcatalogue.searches.isfdb;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BCPreferences;
import com.eleybourn.bookcatalogue.EditBookAnthologyFragment;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.util.List;
import java.util.Objects;

/**
 * Based on ISBN:
 *
 * Editions: with "%s" the isbn
 * http://www.isfdb.org/cgi-bin/se.cgi?arg=%s&type=ISBN
 * Book: with %s the bookId (a long really)
 * http://www.isfdb.org/cgi-bin/pl.cgi?%s
 */
public class ISFDBManager {

    private static final String PREFS_HOST_URL = "ISFDBManager.hostUrl";
    /**
     * task queue for the searching/parsing of content (ant titles)
     * specifically used for direct search calls from {@link EditBookAnthologyFragment}
     */
    private static SimpleTaskQueue taskQueue = null;

    @NonNull
    public static String getBaseURL() {
        //noinspection ConstantConditions
        return BCPreferences.getString(PREFS_HOST_URL, "http://www.isfdb.org");
    }

    public static void setBaseURL(@NonNull final String url) {
        BCPreferences.setString(PREFS_HOST_URL, url);
    }

    @Nullable
    static public File getCoverImage(@NonNull final String isbn) {
        Bundle bookData = new Bundle();
        try {
            search(isbn, "", "", bookData, true);
            if (bookData.containsKey(UniqueId.BKEY_THUMBNAIL_USCORE)
                    && bookData.getString(UniqueId.BKEY_THUMBNAIL_USCORE) != null) {
                File incomingFile = new File(Objects.requireNonNull(bookData.getString(UniqueId.BKEY_THUMBNAIL_USCORE)));
                File newName = new File(incomingFile.getAbsolutePath() + "_" + isbn);
                StorageUtils.renameFile(incomingFile, newName);
                return newName;
            } else {
                return null;
            }
        } catch (Exception e) {
            Logger.logError(e, "Error getting thumbnail from Google");
            return null;
        }
    }

    public static void search(@NonNull final String isbn,
                              @NonNull String author,
                              @NonNull String title,
                              @NonNull final Bundle /* out */ bookInfo,
                              final boolean fetchThumbnail) {
        //replace spaces with %20
        author = author.replace(" ", "%20");
        title = title.replace(" ", "%20");

        if (IsbnUtils.isValid(isbn)) {
            List<String> editions = new Editions(isbn).fetchEditions();
            if (editions.size() > 0) {
                new ISFDBBook(editions.get(0), bookInfo, fetchThumbnail);
            }
        } else {
            //TODO: implement ISFDB search by author/title
            String path = getBaseURL() + "/cgi-bin/adv_search_results.cgi?title_title%3A" + title + "%2Bauthor_canonical%3A" + author;
            throw new IllegalStateException("TODO: implement ISFDB search by author/title");
        }
    }


    /**
     * FIXME this has been shoehorned in here. Need to redo this by using the SearchISFDBThread really
     *
     * specifically used by {@link EditBookAnthologyFragment}
     * First step, get all editions for the ISBN
     */
    public static void searchEditions(@NonNull final String isbn, @NonNull final HandlesISFDB callback) {
        // Setup the background fetcher
        if (taskQueue == null) {
            taskQueue = new SimpleTaskQueue("isfdb-editions");
        }
        taskQueue.enqueue(new ISFDBEditionsTask(isbn, callback));
    }

    /**
     * FIXME this has been shoehorned in here. Need to redo this by using the SearchISFDBThread really
     *
     * specifically used by {@link EditBookAnthologyFragment}
     * First step, get all editions for the ISBN
     */
    public static void search(@NonNull final String bookUrl, @NonNull final HandlesISFDB callback) {
        // Setup the background fetcher
        if (taskQueue == null) {
            taskQueue = new SimpleTaskQueue("isfdb-book");
        }
        taskQueue.enqueue(new ISFDBBookTask(bookUrl, false, callback));
    }

    private static class ISFDBEditionsTask implements SimpleTaskQueue.SimpleTask {

        private List<String> editions;
        private String isbn;
        private HandlesISFDB callback;

        ISFDBEditionsTask(@NonNull final String isbn, HandlesISFDB callback) {
            if (!IsbnUtils.isValid(isbn)) {
                throw new IllegalArgumentException("Can not get editions without an ISBN");
            }
            this.isbn = isbn;
            this.callback = callback;
        }

        @Override
        public void run(@NonNull final SimpleTaskQueue.SimpleTaskContext taskContext) {
            Editions bookEditions = new Editions(isbn);
            editions = bookEditions.fetchEditions();
        }

        @Override
        public void onFinish(@Nullable final Exception e) {
            callback.onGotISFDBEditions(editions);
        }
    }

    private static class ISFDBBookTask implements SimpleTaskQueue.SimpleTask {
        private HandlesISFDB callback;
        private final String bookUrl;
        private Bundle mBookInfo = new Bundle();
        private boolean fetchThumbnail;

        ISFDBBookTask(@NonNull final String bookUrl,
                      final boolean fetchThumbnail,
                      @NonNull final HandlesISFDB callback) {
            this.callback = callback;
            this.bookUrl = bookUrl;
            this.fetchThumbnail = fetchThumbnail;
        }

        @Override
        public void run(@NonNull final SimpleTaskQueue.SimpleTaskContext taskContext) {
            new ISFDBBook(bookUrl, mBookInfo, fetchThumbnail);
        }

        @Override
        public void onFinish(@Nullable final Exception e) {
            callback.onGotISFDBBook(mBookInfo);
        }
    }
}
