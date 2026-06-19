/*
 * @Copyright 2018-2026 HardBackNutter
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

import androidx.annotation.IntRange;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.core.utils.ProductCode;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEdition;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEditionsTask;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.util.livedataevent.LiveDataEvent;
import com.hardbacknutter.util.logger.LoggerFactory;

@SuppressWarnings("WeakerAccess")
public class CoverBrowserViewModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "CoverBrowserViewModel";

    /** 0..n image index. */
    static final String BKEY_FILE_INDEX = TAG + ":cIdx";
    /** Progressbar for the gallery. */
    private final MutableLiveData<Boolean> showGalleryProgress = new MutableLiveData<>();
    /** GalleryImage. */
    private final MutableLiveData<ImageFileInfo> galleryImage = new MutableLiveData<>();
    /** SelectedImage. */
    private final MutableLiveData<ImageFileInfo> selectedImage = new MutableLiveData<>();
    /** Unique identifier generator for all tasks. */
    private final AtomicInteger taskIdCounter = new AtomicInteger();

    /**
     * Executor for displaying gallery images.
     * We want a separate executor from the 'preview' one.
     */
    private final ExecutorService galleryDisplayExecutor =
            ASyncExecutor.create("gallery/d", android.os.Process.THREAD_PRIORITY_LOWEST);
    /** Executor for displaying preview images. */
    private final ExecutorService previewDisplayExecutor = ASyncExecutor.IMAGES;

    /**
     * Holder for all active tasks, so we can cancel them if needed.
     */
    private final Map<AltEdition, FetchImageTask> galleryTasks = new HashMap<>();
    /** Editions. */
    private final SearchEditionsTask searchEditionsTask = new SearchEditionsTask();
    /** List of alternative editions. The base list for the gallery adapter. */
    @NonNull
    private final List<AltEdition> editions = new ArrayList<>();

    /** Indicates cancel has been requested (user dismissed the dialog). */
    private final AtomicBoolean cancelled = new AtomicBoolean();

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
    /**
     * The selected (i.e. displayed in the preview) file.
     * This is the absolute/resolved path for the file
     */
    @Nullable
    private String selectedFileAbsolutePath;
    /** Handles downloading, checking and clean-up of files. */
    private FileManager fileManager;
    /** Code of book to fetch other editions of. */
    private ProductCode productCode;
    /** Index of the image we're handling. */
    @IntRange(from = 0, to = 3)
    private int cIdx;

    @Override
    protected void onCleared() {
        cancelAllTasks();

        galleryDisplayExecutor.shutdownNow();

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
        if (productCode == null) {
            productCode = ISBN.parse(SanityCheck.requireValue(args.getString(DBKey.ISBN),
                                                              DBKey.ISBN));
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

    /**
     * Cancel all active tasks; called before we're dismissed in any way.
     */
    void cancelAllTasks() {
        // prevent new tasks being started.
        cancelled.set(true);

        if (selectedImageTask != null) {
            selectedImageTask.cancel();
        }

        synchronized (galleryTasks) {
            galleryTasks.values().forEach(TaskBase::cancel);
            // not strictly needed, but future-proof
            galleryTasks.clear();
        }
    }

    /**
     * Remove the given task.
     *
     * @param taskId to remove
     */
    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
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
    ExecutorService getPreviewDisplayExecutor() {
        return previewDisplayExecutor;
    }

    /**
     * Get the executor used for displaying gallery images.
     *
     * @return executor
     */
    @NonNull
    ExecutorService getGalleryDisplayExecutor() {
        return galleryDisplayExecutor;
    }

    // TODO: if there is only a single edition, we should skip the displaying and just use it
    @NonNull
    public List<AltEdition> getEditions() {
        // used directly, the caller can remove items
        return editions;
    }

    /**
     * Set the given list as the editions list.
     *
     * @param list editions
     *
     * @return {@code true} if we have at least one edition which <strong>might</strong>
     *         have images.
     */
    public boolean setEditions(@Nullable final Collection<AltEdition> list) {
        editions.clear();

        if (BuildConfig.DEBUG /* always */) {
            LoggerFactory.getLogger().d(
                    TAG, "list=" + (list != null ? list.size() + ", " + list : "null"));
        }

        if (list == null || list.isEmpty()) {
            return false;
        }

        // Some AltEdition implementations know for certain whether they have / do not have images.
        // Others *may* have images. Discard the ones we are sure NOT to have images.
        final List<AltEdition> filtered = list.stream()
                                              .filter(AltEdition::mayHaveCover)
                                              .collect(Collectors.toList());
        if (filtered.isEmpty()) {
            return false;
        }

        editions.addAll(list);
        return true;
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
     * @param edition to search
     *
     * @return a {@link ImageFileInfo} object with or without a valid fileSpec,
     *         or {@code null} if there is no cached file at all
     */
    @Nullable
    ImageFileInfo getFileInfo(@NonNull final AltEdition edition) {
        return fileManager.getFileInfo(edition);
    }

    /**
     * Start a task to fetch a Gallery image.
     *
     * @param edition to search
     */
    void fetchGalleryImage(@NonNull final AltEdition edition) {
        if (cancelled.get()) {
            // abort
            return;
        }

        synchronized (galleryTasks) {
            if (!galleryTasks.containsKey(edition)) {
                final FetchImageTask task =
                        new FetchImageTask(taskIdCounter.incrementAndGet(), edition, cIdx,
                                           fileManager, taskListener,
                                           ImageWebSize.SMALL_FIRST);
                task.setExecutor(ASyncExecutor.NETWORK);

                galleryTasks.put(edition, task);
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
        if (cancelled.get()) {
            // abort
            return;
        }

        if (selectedImageTask != null) {
            selectedImageTask.cancel();
        }
        selectedImageTask = new FetchImageTask(R.id.TASK_ID_PREVIEW_IMAGE,
                                               imageFileInfo.getEdition(), cIdx,
                                               fileManager, taskListener,
                                               ImageWebSize.LARGE_FIRST);

        selectedImageTask.setExecutor(ASyncExecutor.NETWORK);
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
    LiveData<LiveDataEvent<Collection<AltEdition>>> onSearchEditionsTaskFinished() {
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
    LiveData<LiveDataEvent<Collection<AltEdition>>> onSearchEditionsTaskCancelled() {
        return searchEditionsTask.onCancelled();
    }

    void searchEditions() {
        searchEditionsTask.search(productCode);
    }

    boolean isSearchEditionsTaskRunning() {
        return searchEditionsTask.isActive();
    }
}
