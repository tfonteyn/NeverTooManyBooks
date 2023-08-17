/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.covers;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEditionsTask;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;

public class CoverBrowserViewModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "CoverBrowserViewModel";
    public static final String BKEY_REQUEST_KEY = TAG + ":rk";

    /** int 0..1 */
    static final String BKEY_FILE_INDEX = TAG + ":cIdx";
    /** Progressbar for the gallery. */
    private final MutableLiveData<Boolean> showGalleryProgress = new MutableLiveData<>();
    /** GalleryImage. */
    private final MutableLiveData<ImageFileInfo> galleryImage = new MutableLiveData<>();
    /** SelectedImage. */
    private final MutableLiveData<ImageFileInfo> selectedImage = new MutableLiveData<>();
    /** Unique identifier generator for all tasks. */
    private final AtomicInteger taskIdCounter = new AtomicInteger();
    /** Executor for displaying gallery images. */
    private final Executor galleryDisplayExecutor = ASyncExecutor.create("gallery/d");
    /** Executor for fetching gallery images. */
    private final Executor galleryNetworkExecutor = ASyncExecutor.create("gallery/n");
    /** Executor for displaying preview images. */
    private final Executor previewDisplayExecutor = ASyncExecutor.MAIN;
    /** Executor for fetching preview images. */
    private final Executor previewNetworkExecutor = ASyncExecutor.MAIN;
    /**
     * Holder for all active tasks, so we can cancel them if needed.
     * key: isbn.
     */
    private final Map<String, FetchImageTask> galleryTasks = new HashMap<>();
    /** Editions. */
    private final SearchEditionsTask searchEditionsTask = new SearchEditionsTask();
    /** List of ISBN numbers for alternative editions. The base list for the gallery adapter. */
    @NonNull
    private final List<String> editions = new ArrayList<>();
    /** FragmentResultListener request key to use for our response. */
    private String requestKey;
    /** SelectedImage. */
    @Nullable
    private FetchImageTask selectedImageTask;
    /** FetchImageTask listener. */
    private final TaskListener<ImageFileInfo> taskListener = new TaskListener<>() {
        @Override
        public void onFinished(final int taskId,
                               @Nullable final ImageFileInfo result) {
            if (taskId == R.id.TASK_ID_PREVIEW_IMAGE) {
                selectedImageTask = null;
                selectedImage.setValue(result);
            } else {
                removeTask(taskId);
                galleryImage.setValue(result);
            }
        }

        @Override
        public void onCancelled(final int taskId,
                                @Nullable final ImageFileInfo result) {
            if (taskId == R.id.TASK_ID_PREVIEW_IMAGE) {
                selectedImageTask = null;
                selectedImage.setValue(null);
            } else {
                removeTask(taskId);
                galleryImage.setValue(null);
            }
        }

        @Override
        public void onFailure(final int taskId,
                              @Nullable final Throwable e) {
            if (taskId == R.id.TASK_ID_PREVIEW_IMAGE) {
                selectedImageTask = null;
                selectedImage.setValue(null);
            } else {
                removeTask(taskId);
                galleryImage.setValue(null);
            }
        }
    };
    /** Indicates cancel has been requested. */
    private boolean cancelled;

    /**
     * The selected (i.e. displayed in the preview) file.
     * This is the absolute/resolved path for the file
     */
    @Nullable
    private String selectedFileAbsolutePath;
    /** Handles downloading, checking and cleanup of files. */
    private FileManager fileManager;
    /** ISBN of book to fetch other editions of. */
    private String baseIsbn;
    /** Index of the image we're handling. */
    private int cIdx;

    @Override
    protected void onCleared() {
        cancelAllTasks();

        if (fileManager != null) {
            fileManager.purge();
        }
    }

    /**
     * Pseudo constructor.
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Bundle args) {
        if (requestKey == null) {
            requestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                                BKEY_REQUEST_KEY);
            baseIsbn = SanityCheck.requireValue(args.getString(DBKey.BOOK_ISBN),
                                                DBKey.BOOK_ISBN);
            cIdx = args.getInt(BKEY_FILE_INDEX);

            // optional
            List<Site> sites = args.getParcelableArrayList(Site.Type.Covers.getBundleKey());
            if (sites == null) {
                sites = Site.Type.Covers.getSites();
            }
            // Filter for active engines only
            final List<EngineId> engineIds = sites.stream()
                                                  .filter(Site::isActive)
                                                  .map(Site::getEngineId)
                                                  .collect(Collectors.toList());
            fileManager = new FileManager(engineIds);
        }
    }

    @NonNull
    public String getRequestKey() {
        return requestKey;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Cancel all active tasks; called before we're dismissed in any way.
     */
    void cancelAllTasks() {
        // prevent new tasks being started.
        cancelled = true;

        if (selectedImageTask != null) {
            selectedImageTask.cancel();
        }

        synchronized (galleryTasks) {
            for (final FetchImageTask task : galleryTasks.values()) {
                task.cancel();
            }
            // not strictly needed, but future-proof
            galleryTasks.clear();
        }
    }

    /**
     * Remove the given task.
     *
     * @param taskId to remove
     */
    private void removeTask(final int taskId) {
        synchronized (galleryTasks) {

            galleryTasks.entrySet()
                        .stream()
                        .filter(entry -> entry.getValue().getTaskId() == taskId)
                        .findFirst()
                        .ifPresent(entry -> galleryTasks.remove(entry.getKey()));

            if (galleryTasks.isEmpty()) {
                showGalleryProgress.setValue(false);
            }
        }
    }

    /**
     * Get the executor used for displaying the selected image.
     *
     * @return executor
     */
    @NonNull
    Executor getPreviewDisplayExecutor() {
        return previewDisplayExecutor;
    }

    /**
     * Get the executor used for displaying gallery images.
     *
     * @return executor
     */
    @NonNull
    Executor getGalleryDisplayExecutor() {
        return galleryDisplayExecutor;
    }

    @NonNull
    public List<String> getEditions() {
        // used directly
        return editions;
    }

    /**
     * Set the given list as the editions list.
     *
     * @param list with ISBN numbers
     */
    public void setEditions(@Nullable final Collection<String> list) {
        editions.clear();
        if (list != null && !list.isEmpty()) {
            editions.addAll(list);
        }
    }

    @Nullable
    String getSelectedFileAbsPath() {
        return selectedFileAbsolutePath;
    }

    void setSelectedFile(@Nullable final File file) {
        if (file != null) {
            selectedFileAbsolutePath = file.getAbsolutePath();
        } else {
            selectedFileAbsolutePath = null;
        }
    }

    /**
     * wrapper for {@link FileManager#getFileInfo}.
     *
     * @param isbn to search
     *
     * @return a {@link ImageFileInfo} object with or without a valid fileSpec,
     *         or {@code null} if there is no cached file at all
     */
    @Nullable
    ImageFileInfo getFileInfo(@NonNull final String isbn) {
        return fileManager.getFileInfo(isbn);
    }

    /**
     * Start a task to fetch a Gallery image.
     *
     * @param isbn to search for, <strong>must</strong> be valid.
     */
    void fetchGalleryImage(@NonNull final String isbn) {
        synchronized (galleryTasks) {
            if (!galleryTasks.containsKey(isbn)) {
                final FetchImageTask task =
                        new FetchImageTask(taskIdCounter.getAndIncrement(), isbn, cIdx,
                                           fileManager, taskListener,
                                           Size.SMALL_FIRST);
                task.setExecutor(galleryNetworkExecutor);

                galleryTasks.put(isbn, task);
                task.start();

                final Boolean isShowing = showGalleryProgress.getValue();
                if (isShowing == null || !isShowing) {
                    showGalleryProgress.setValue(true);
                }
            }
        }
    }

    /**
     * Observable.
     *
     * @return boolean whether to show or hide the progress bar
     */
    @NonNull
    LiveData<Boolean> onShowGalleryProgress() {
        return showGalleryProgress;
    }

    /**
     * Observable.
     *
     * @return a gallery image file info; can be {@code null}.
     */
    @NonNull
    LiveData<ImageFileInfo> onGalleryImage() {
        return galleryImage;
    }

    /**
     * Start a task to get the preview image; i.e. the full size image.
     *
     * @param imageFileInfo of the selected image
     */
    void fetchSelectedImage(@NonNull final ImageFileInfo imageFileInfo) {
        if (selectedImageTask != null) {
            selectedImageTask.cancel();
        }
        selectedImageTask = new FetchImageTask(R.id.TASK_ID_PREVIEW_IMAGE,
                                               imageFileInfo.getIsbn(), cIdx,
                                               fileManager, taskListener,
                                               Size.LARGE_FIRST);

        selectedImageTask.setExecutor(previewNetworkExecutor);
        selectedImageTask.start();
    }

    /**
     * Observable.
     *
     * @return the preview image file info; can be {@code null}.
     */
    @NonNull
    LiveData<ImageFileInfo> onSelectedImage() {
        return selectedImage;
    }

    @NonNull
    LiveData<LiveDataEvent<Collection<String>>> onSearchEditionsTaskFinished() {
        return searchEditionsTask.onFinished();
    }

    /**
     * Observable to receive failure.
     *
     * @return the result is the Exception
     */
    @NonNull
    LiveData<LiveDataEvent<Throwable>> onSearchEditionsTaskFailure() {
        return searchEditionsTask.onFailure();
    }

    @NonNull
    LiveData<LiveDataEvent<Collection<String>>> onSearchEditionsTaskCancelled() {
        return searchEditionsTask.onCancelled();
    }

    void searchEditions() {
        searchEditionsTask.search(baseIsbn);
    }

    boolean isSearchEditionsTaskRunning() {
        return searchEditionsTask.isActive();
    }
}
