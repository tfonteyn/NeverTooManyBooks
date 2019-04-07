/*
 * @copyright 2011 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.editordialog.EditorDialogFragment;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.searches.SearchSites.ImageSizes;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;
import com.eleybourn.bookcatalogue.tasks.AlternativeExecutor;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * Displays and manages a cover image browser in a dialog, allowing the user to select
 * an image from a list to use as the (new) book cover image.
 * <p>
 * To send the clicked image (fileSpec) back, the caller must use
 * {@link #setTargetFragment(Fragment, int)}.
 * <p>
 * This class then uses {@link #getTargetFragment()#onActivityResult(int, int, Intent)}.
 * <p>
 * The above is an alternative solution to {@link EditorDialogFragment} and its use of the
 * fragment manager/tag. TODO: test & pick one solution of the two.
 *
 * <p>
 * ENHANCE: For each edition, try to get TWO images from a different site each.
 */
public class CoverBrowser
        extends DialogFragment {

    /** Fragment manager tag. */
    public static final String TAG = CoverBrowser.class.getSimpleName();

    /** ArrayList<String> with edition isbn's */
    private static final String BKEY_EDITION_LIST = TAG + ":editions";

    /** Holder for all active tasks, so we can cancel them if needed. */
    private final Set<AsyncTask> mAllTasks = new HashSet<>();

    /** ISBN of book to lookup. */
    private String mIsbn;

    private ImageUtils.DisplaySizes mDisplaySizes;

    /** List of all editions for the given ISBN. */
    private ArrayList<String> mAlternativeEditions;
    /** Handles downloading, checking and cleanup of files. */
    private FileManager mFileManager;
    /** Indicates a 'shutdown()' has been requested. */
    private boolean mShutdown;

    /** cached activity. */
    private BaseActivity mActivity;

    /** The gallery displays a list of images, one for each edition. */
    private RecyclerView mGalleryView;

    /** The switcher will be used to display larger versions. */
    private ImageSwitcher mImageSwitcherView;

    /** Prior to showing a preview, the switcher can show text updates. */
    private TextView mStatusTextView;

    /**
     * WARNING: LibraryThing is in fact the only site searched for alternative editions!
     * See {@link GetEditionsTask}.
     * <p>
     * Images themselves are search from the 'searchSites' as usual.
     *
     * @param isbn        ISBN of book
     * @param searchSites bitmask with sites to search,
     *                    see {@link SearchSites.Site#SEARCH_ALL} and individual flags
     *
     * @return the instance
     */
    @NonNull
    public static CoverBrowser newInstance(@NonNull final String isbn,
                                           final int searchSites) {
        // dev sanity check
        if ((searchSites & SearchSites.Site.SEARCH_ALL) == 0) {
            throw new IllegalArgumentException("Must specify at least one source to use");
        }
        if (LibraryThingManager.noKey()) {
            throw new IllegalStateException("LibraryThing Key must be tested before calling this");
        }

        CoverBrowser frag = new CoverBrowser();
        Bundle args = new Bundle();
        args.putInt(UniqueId.BKEY_SEARCH_SITES, searchSites);
        args.putString(DBDefinitions.KEY_ISBN, isbn);
        frag.setArguments(args);
        return frag;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        return inflater.inflate(R.layout.dialog_cover_browser, container);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        mActivity = (BaseActivity) requireActivity();

        Bundle args = requireArguments();
        mIsbn = args.getString(DBDefinitions.KEY_ISBN);
        // Create an object to manage the downloaded files
        mFileManager = new FileManager(args.getInt(UniqueId.BKEY_SEARCH_SITES), savedInstanceState);
        if (savedInstanceState != null) {
            mAlternativeEditions = savedInstanceState.getStringArrayList(BKEY_EDITION_LIST);
        }

        mDisplaySizes = ImageUtils.getDisplaySizes(mActivity);

        mStatusTextView = view.findViewById(R.id.statusMessage);

        // setup the gallery; make it horizontal scrolling.
        mGalleryView = view.findViewById(R.id.gallery);
        LinearLayoutManager galleryLayoutManager = new LinearLayoutManager(getContext());
        galleryLayoutManager.setOrientation(RecyclerView.HORIZONTAL);
        mGalleryView.setLayoutManager(galleryLayoutManager);

        // setup the switcher.
        mImageSwitcherView = view.findViewById(R.id.switcher);
        mImageSwitcherView.setFactory(() -> {
            ImageView imageView = new ImageView(getContext());
            imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            imageView.setAdjustViewBounds(true);
            imageView.setLayoutParams(
                    new ImageSwitcher.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                                                   ViewGroup.LayoutParams.WRAP_CONTENT));

            // placeholder image
            imageView.setImageResource(R.drawable.ic_image);
            return imageView;
        });
        // When the switcher image is clicked, send the fileSpec back to the caller and terminate.
        mImageSwitcherView.setOnClickListener(v -> {
            // When the image was loaded, the filename was stored in the tag.
            String fileSpec = (String) mImageSwitcherView.getTag();
            if (BuildConfig.DEBUG) {
                Logger.info(CoverBrowser.this, "mImageSwitcherView.onClick",
                            "fileSpec=" + fileSpec);
            }
            if (fileSpec != null) {
                Intent data = new Intent().putExtra(UniqueId.BKEY_FILE_SPEC, fileSpec);
                // Was a target fragment set ?
                Fragment targetFragment = getTargetFragment();
                if (targetFragment != null) {
                    targetFragment.onActivityResult(UniqueId.REQ_ALT_EDITION,
                                                    Activity.RESULT_OK, data);
                } else {
                    // if no fragment, assume the activity wants us.
                    mActivity.onActivityResult(UniqueId.REQ_ALT_EDITION,
                                               Activity.RESULT_OK, data);
                }
            }
            // close the CoverBrowser
            dismiss();
        });
    }

    @Override
    public void onStart() {
        super.onStart();

    }

    @Override
    public void onResume() {
        super.onResume();

        // force the enclosing dialog to be big enough. When using onCreateView,
        // this must be done here.
        Dialog dialog = getDialog();
        if (dialog != null) {
            int width = ViewGroup.LayoutParams.MATCH_PARENT;
            int height = ViewGroup.LayoutParams.WRAP_CONTENT;
            //noinspection ConstantConditions
            dialog.getWindow().setLayout(width, height);
        }

        if (mAlternativeEditions == null) {
            mStatusTextView.setText(R.string.progress_msg_finding_editions);
            // Start a search for alternative editions of the book (isbn).
            GetEditionsTask task = new GetEditionsTask(this, mIsbn);
            addTask(task);
            task.execute();
        } else {
            //noinspection ConstantConditions
            getView().post(() -> showGallery(null, mAlternativeEditions));
        }
    }

    /**
     * Cancel any running tasks, but keep the downloaded files until {@link #onDestroy()}.
     *
     * @param dialog of this fragment.
     */
    @Override
    public void onDismiss(@NonNull final DialogInterface dialog) {
        mShutdown = true;

        cancelAllTasks();

        super.onDismiss(dialog);
    }

    /**
     * Purge the downloaded files the last possible moment.
     */
    @Override
    public void onDestroy() {
        if (mFileManager != null) {
            mFileManager.purge();
            mFileManager = null;
        }

        super.onDestroy();
    }

    /**
     * Called after we got results from the edition search.
     * Show the user a selection of other covers and allow selection of a replacement.
     */
    private void showGallery(@Nullable final GetEditionsTask task,
                             @Nullable final ArrayList<String> editions) {
        removeTask(task);

        if (editions == null || editions.isEmpty()) {
            dismiss();
            UserMessage.showUserMessage(mGalleryView, R.string.warning_no_editions);
            return;
        }

        mAlternativeEditions = editions;

        // Show help message
        mStatusTextView.setText(R.string.info_tap_on_thumb);

        // Use our custom adapter to load images
        GalleryAdapter adapter = new GalleryAdapter();
        mGalleryView.setAdapter(adapter);
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putStringArrayList(BKEY_EDITION_LIST, mAlternativeEditions);
        mFileManager.onSaveInstanceState(outState);
    }

    /**
     * Cancel all active tasks.
     */
    private void cancelAllTasks() {
        // cancel any active tasks.
        synchronized (mAllTasks) {
            for (AsyncTask task : mAllTasks) {
                task.cancel(true);
            }
        }
    }

    /**
     * Keep track of all tasks so we can remove/cancel at will.
     *
     * @param task to add
     * @param <T>  type of task
     */
    private <T extends AsyncTask> void addTask(@NonNull final T task) {
        synchronized (mAllTasks) {
            mAllTasks.add(task);
        }
    }

    /**
     * Remove a specific task from the set (NOT the queue).
     * This method should be called *after* a task has notified us it finished.
     *
     * @param task to remove
     * @param <T>  type of task
     */
    private <T extends AsyncTask> void removeTask(@Nullable final T task) {
        if (task != null) {
            synchronized (mAllTasks) {
                mAllTasks.remove(task);
            }
        }
    }

    /**
     * Handles downloading, checking and cleanup of files.
     */
    private static class FileManager {

        private static final String TAG = FileManager.class.getSimpleName();

        /** Map with downloaded files. */
        private static final String BKEY_FILES_MAP = TAG + ":files";

        /** key = isbn + "_ + size. */
        private final Map<String, String> mFiles = Collections.synchronizedMap(new HashMap<>());

        /** Flags applicable to *current* search. */
        private final int mSearchSites;

        /**
         * Constructor.
         *
         * @param initialSearchSites bitmask with sites to search,
         *                           see {@link SearchSites.Site#SEARCH_ALL} and individual flags
         */
        FileManager(final int initialSearchSites,
                    @Nullable final Bundle savedInstanceState) {

            //TODO: plumbing in place to use changed mSearchSites, now ENHANCE: allow switching custom search sites.
            if (savedInstanceState == null) {
                mSearchSites = initialSearchSites;

            } else {
                mSearchSites = savedInstanceState.getInt(UniqueId.BKEY_SEARCH_SITES,
                                                         initialSearchSites);
                Bundle files = savedInstanceState.getBundle(BKEY_FILES_MAP);
                if (files != null) {
                    for (String key : files.keySet()) {
                        //noinspection ConstantConditions
                        mFiles.put(key, files.getString(key));
                    }
                }
            }
        }

        /**
         * Check if a file is an image with an acceptable size.
         *
         * @param file to check
         *
         * @return <tt>true</tt> if file is acceptable.
         */
        private boolean isGood(@NonNull final File file) {
            boolean ok = false;

            if (file.exists() && file.length() != 0) {
                try {
                    // Just read the image files to get file size
                    BitmapFactory.Options opt = new BitmapFactory.Options();
                    opt.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(file.getAbsolutePath(), opt);
                    // If too small, it's no good
                    ok = opt.outHeight >= 10 && opt.outWidth >= 10;
                } catch (RuntimeException e) {
                    // Failed to decode; probably not an image
                    ok = false;
                    Logger.error(e, "Unable to decode thumbnail");
                }
            }

            // cleanup bad files.
            if (!ok) {
                StorageUtils.deleteFile(file);
            }
            return ok;
        }

        /**
         * Download a file if not present and keep a record of it.
         * <p>
         * ENHANCE: use {@link SearchSites.SearchSiteManager#isAvailable()}.
         * <p>
         * Reminder: the for() loop will bailout (return) as soon as a cover file is found.
         * i.e. for each edition of the isbn, we try to get an image from the search-sites.
         * The first search site which has an image is accepted for *that* edition.
         * Repeat for all editions.
         * <p>
         * ENHANCE: allow the user to prioritize the order on the fly.
         *
         * @param isbn       ISBN of file
         * @param imageSizes a list of images sizes in order of preference
         *
         * @return the fileSpec, or null when not found
         */
        @Nullable
        @WorkerThread
        String download(@NonNull final String isbn,
                        @NonNull final ImageSizes... imageSizes) {

            for (ImageSizes size : imageSizes) {
                String key = isbn + '_' + size;
                String fileSpec = mFiles.get(key);

                if (BuildConfig.DEBUG) {
                    Logger.info(this, "download",
                                "isbn=" + isbn,
                                "size=" + size,
                                "fileSpec=" + fileSpec);
                }

                // Is the file present && good ?
                if ((fileSpec != null) && !fileSpec.isEmpty() && isGood(new File(fileSpec))) {
                    return fileSpec;

                } else {
                    mFiles.remove(key);
                }

                for (SearchSites.Site site : SearchSites.getSitesForCoverSearches()) {
                    // Are we allowed to search this site ?
                    if (site.isEnabled()
                            // and should we search this site ?
                            && ((mSearchSites & site.id) != 0)
                    ) {
                        SearchSites.SearchSiteManager sm = site.getSearchSiteManager();
                        // don't search the same site for all sizes if it only supports one size.
                        if (sm.supportsImageSize(size)) {
                            File file = sm.getCoverImage(isbn, size);
                            if (file != null && isGood(file)) {
                                fileSpec = file.getAbsolutePath();
                                mFiles.put(key, fileSpec);
                                if (BuildConfig.DEBUG) {
                                    Logger.info(this, "download",
                                                "FOUND",
                                                "isbn=" + isbn,
                                                "size=" + size,
                                                "fileSpec=" + fileSpec);
                                }
                                return fileSpec;

                            } else {
                                if (BuildConfig.DEBUG) {
                                    Logger.info(this, "download",
                                                "MISSING",
                                                "isbn=" + isbn,
                                                "size=" + size,
                                                "fileSpec=" + fileSpec);
                                }
                            }
                        }
                    }
                }

                // give up
                mFiles.remove(key);
            }
            return null;
        }

        /**
         * Get the requested file, if available, otherwise return null.
         *
         * @param isbn  to search
         * @param sizes required sizes in order to look for. First found is used.
         *
         * @return the file, or null if not found
         */
        @Nullable
        File getFile(@NonNull final String isbn,
                     @NonNull final ImageSizes... sizes) {
            for (ImageSizes size : sizes) {
                String fileSpec = mFiles.get(isbn + '_' + size);
                if (fileSpec != null && !fileSpec.isEmpty()) {
                    return new File(fileSpec);
                }
            }
            return null;
        }

        public void onSaveInstanceState(@NonNull final Bundle outState) {
            outState.putInt(UniqueId.BKEY_SEARCH_SITES, mSearchSites);
            Bundle bundle = new Bundle();
            for (Map.Entry<String, String> entry : mFiles.entrySet()) {
                bundle.putString(entry.getKey(), entry.getValue());
            }
            outState.putBundle(BKEY_FILES_MAP, bundle);
        }

        /**
         * Clean up all files.
         */
        void purge() {
            for (String fileSpec : mFiles.values()) {
                if (fileSpec != null) {
                    StorageUtils.deleteFile(new File(fileSpec));
                }
            }
            mFiles.clear();
        }
    }

    /**
     * Fetch all alternative edition isbn's from LibraryThing.
     */
    private static class GetEditionsTask
            extends AsyncTask<Void, Void, ArrayList<String>> {

        @NonNull
        private final String mIsbn;
        @NonNull
        private final CoverBrowser mCallback;

        /**
         * Constructor.
         *
         * @param coverBrowser to send results to
         * @param isbn         to search for
         */
        @UiThread
        GetEditionsTask(@NonNull final CoverBrowser coverBrowser,
                        @NonNull final String isbn) {
            mIsbn = isbn;
            mCallback = coverBrowser;
        }

        @Override
        @Nullable
        @WorkerThread
        protected ArrayList<String> doInBackground(final Void... params) {
            // Get some editions
            // ENHANCE: the list of editions should be expanded to include other sites
            // As well as the alternate user-contributed images from LibraryThing. The latter are
            // often the best source but at present could only be obtained by HTML scraping.
            try {
                return LibraryThingManager.searchEditions(mIsbn);
            } catch (RuntimeException e) {
                return null;
            }
        }

        @Override
        @UiThread
        protected void onPostExecute(final ArrayList<String> result) {
            mCallback.showGallery(this, result);
        }
    }

    /**
     * Fetch a thumbnail and stick it into the gallery.
     */
    private static class GetGalleryImageTask
            extends AsyncTask<Void, Void, String> {

        @NonNull
        private final GalleryAdapter mGalleryAdapter;
        @NonNull
        private final FileManager mFileManager;

        /** View to populate. */
        @NonNull
        private final GalleryViewHolder mGalleryViewHolder;

        /**
         * Constructor.
         *
         * @param galleryAdapter    to send results to
         * @param galleryViewHolder view to populate.
         * @param fileManager       for downloads
         */
        @UiThread
        GetGalleryImageTask(@NonNull final GalleryAdapter galleryAdapter,
                            @NonNull final GalleryViewHolder galleryViewHolder,
                            @NonNull final FileManager fileManager) {
            mGalleryAdapter = galleryAdapter;
            mGalleryViewHolder = galleryViewHolder;

            mFileManager = fileManager;
        }

        @Override
        @Nullable
        @WorkerThread
        protected String doInBackground(final Void... params) {
            try {
                return mFileManager.download(mGalleryViewHolder.isbn,
                                             ImageSizes.SMALL, ImageSizes.MEDIUM, ImageSizes.LARGE);

            } catch (@SuppressWarnings("OverlyBroadCatchBlock") Exception ignore) {
                // tad annoying... java.io.InterruptedIOException: thread interrupted
                // can be thrown, but for some reason javac does not think so.
            }
            return null;
        }

        @Override
        @UiThread
        protected void onPostExecute(@Nullable final String result) {
            if (!isCancelled() && result != null && !result.isEmpty()) {
                mGalleryAdapter.updateGallery(mGalleryViewHolder, this, result);
            }
        }
    }

    /**
     * Fetch a full-size image and stick it into the ImageSwitcher.
     */
    private static class GetSwitcherImageTask
            extends AsyncTask<Void, Void, String> {

        @NonNull
        private final GalleryAdapter mGalleryAdapter;
        @NonNull
        private final FileManager mFileManager;
        @NonNull
        private final String mIsbn;

        /**
         * Constructor.
         *  @param galleryAdapter to send results to
         * @param isbn           book to search
         * @param fileManager    for downloads
         */
        @UiThread
        GetSwitcherImageTask(@NonNull final GalleryAdapter galleryAdapter,
                             @NonNull final String isbn,
                             @NonNull final FileManager fileManager) {
            mGalleryAdapter = galleryAdapter;

            mFileManager = fileManager;
            mIsbn = isbn;
        }

        @Override
        @Nullable
        @WorkerThread
        protected String doInBackground(final Void... params) {
            try {
                return mFileManager.download(mIsbn, ImageSizes.LARGE, ImageSizes.MEDIUM,
                                             ImageSizes.SMALL);

            } catch (@SuppressWarnings("OverlyBroadCatchBlock") Exception ignore) {
                // tad annoying... java.io.InterruptedIOException: thread interrupted
                // can be thrown, but for some reason javac does not think so.
            }
            return null;
        }

        @Override
        @UiThread
        protected void onPostExecute(@Nullable final String result) {
            if (!isCancelled() && result != null && !result.isEmpty()) {
                mGalleryAdapter.updateSwitcher(this, result);
            }
        }
    }

    /** Stores and recycles views as they are scrolled off screen. */
    private static class GalleryViewHolder
            extends RecyclerView.ViewHolder {

        /** Strong reference to the image. */
        @NonNull
        final ImageView imageView;
        final int maxWidth;
        final int maxHeight;

        String isbn;

        GalleryViewHolder(@NonNull final ImageView imageView,
                          final int maxWidth,
                          final int maxHeight) {
            super(imageView);
            this.imageView = imageView;
            this.maxWidth = maxWidth;
            this.maxHeight = maxHeight;
        }
    }

    public class GalleryAdapter
            extends RecyclerView.Adapter<GalleryViewHolder> {

        @Override
        @NonNull
        public GalleryViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                    final int viewType) {

            ImageView imageView = new ImageView(parent.getContext());
            imageView.setBackgroundResource(R.drawable.border);
            int maxSize = mDisplaySizes.small;
            imageView.setLayoutParams(new ViewGroup.LayoutParams(maxSize, maxSize));
            return new GalleryViewHolder(imageView, maxSize, maxSize);
        }

        @Override
        public void onBindViewHolder(@NonNull final GalleryViewHolder holder,
                                     final int position) {

            // fetch an image based on the isbn
            holder.isbn = mAlternativeEditions.get(position);

            // Get the image file; try the sizes in order as specified here.
            File fileSpec = mFileManager.getFile(holder.isbn, SearchSites.ImageSizes.SMALL,
                                                 SearchSites.ImageSizes.MEDIUM,
                                                 SearchSites.ImageSizes.LARGE);

            if (BuildConfig.DEBUG) {
                Logger.info(this, "onBindViewHolder",
                            "position=" + position,
                            "isbn=" + holder.isbn,
                            "fileSpec=" + fileSpec);
            }
            // See if file is present.
            if (fileSpec != null) {
                ImageUtils.setImageView(holder.imageView, fileSpec,
                                        mDisplaySizes.small, mDisplaySizes.small, true);
            } else {
                // Not present; use a placeholder.
                holder.imageView.setImageResource(R.drawable.ic_image);
                // and queue a request for it.
                if (!mShutdown) {
                    try {
                        GetGalleryImageTask task =
                                new GetGalleryImageTask(this, holder, mFileManager);

                        addTask(task);
                        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

                    } catch (RejectedExecutionException e) {
                        // some books have a LOT of editions...
                        if (BuildConfig.DEBUG) {
                            Logger.debug(e);
                        }
                    }
                }
            }
            // image from gallery clicked -> load it into the larger preview (imageSwitcher).
            holder.imageView.setOnClickListener(v -> {
                // set & show the placeholder.
                mImageSwitcherView.setImageResource(R.drawable.ic_image);
                mImageSwitcherView.setVisibility(View.VISIBLE);
                mStatusTextView.setText(R.string.progress_msg_loading);
                if (BuildConfig.DEBUG) {
                    Logger.info(GalleryAdapter.this, "imageView.onClick",
                                "position=" + position,
                                "isbn=" + holder.isbn);
                }
                GetSwitcherImageTask task = new GetSwitcherImageTask(GalleryAdapter.this,
                                                                     holder.isbn, mFileManager);
                addTask(task);
                // use the alternative executor, so we get a result back without
                // waiting on the gallery tasks.
                task.executeOnExecutor(AlternativeExecutor.THREAD_POOL_EXECUTOR);
            });
        }

        /** @return total number of rows. */
        @Override
        public int getItemCount() {
            return mAlternativeEditions.size();
        }

        /**
         * handle result from the {@link GetGalleryImageTask}.
         *
         * @param task     the task that finished
         * @param fileSpec the file we got.
         */
        void updateGallery(@NonNull final GalleryViewHolder holder,
                           @NonNull final AsyncTask task,
                           @NonNull final String fileSpec) {
            removeTask(task);

            if (BuildConfig.DEBUG) {
                Logger.info(this, "update", "fileSpec=" + fileSpec);
            }

            // Load the temp file and apply to view
            File imageFile = new File(fileSpec);
            if (imageFile.exists()) {
                imageFile.deleteOnExit();
                ImageUtils.setImageView(holder.imageView, imageFile,
                                        holder.maxWidth, holder.maxHeight, true);
            } else {
                int position = holder.getAdapterPosition();
                mAlternativeEditions.remove(position);
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, mAlternativeEditions.size());
            }
        }

        /**
         * handle result from the {@link GetSwitcherImageTask}.
         *
         * @param task     the task that finished
         * @param fileSpec the file we got.
         */
        void updateSwitcher(@NonNull final AsyncTask task,
                            @NonNull final String fileSpec) {
            removeTask(task);

            if (BuildConfig.DEBUG) {
                Logger.info(this, "updateSwitcher", "fileSpec=" + fileSpec);
            }

            File file = new File(fileSpec);
            if (file.exists() && file.length() > 100) {

                // store the path. It will be send back to the caller.
                mImageSwitcherView.setTag(file.getAbsolutePath());

                Bitmap bm = ImageUtils.createScaledBitmap(BitmapFactory.decodeFile(file.getPath()),
                                                          mDisplaySizes.large, mDisplaySizes.large);

                // ImageSwitcher does not accept a bitmap; wants a Drawable instead.
                mImageSwitcherView.setImageDrawable(new BitmapDrawable(getResources(), bm));

                mImageSwitcherView.setVisibility(View.VISIBLE);
                mStatusTextView.setText(R.string.info_tap_on_thumb);
            } else {
                mImageSwitcherView.setVisibility(View.GONE);
                mStatusTextView.setText(R.string.warning_cover_not_found);
            }
        }
    }
}
