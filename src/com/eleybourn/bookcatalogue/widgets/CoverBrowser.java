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

package com.eleybourn.bookcatalogue.widgets;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewSwitcher.ViewFactory;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.editordialog.EditorDialogFragment;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.searches.SearchSites.ImageSizes;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;
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
 * ENHANCE: (Somehow) remove non-existent images from ImageSelector.
 * Probably start with 1 image and GROW it.
 *
 * @author Philip Warner
 */
public class CoverBrowser
        extends DialogFragment {

    /** Fragment manager tag. */
    public static final String TAG = CoverBrowser.class.getSimpleName();

    /** Holder for all active tasks, so we can cancel them if needed. */
    private final Set<AsyncTask> mAllTasks = new HashSet<>();
    /** ISBN of book to lookup. */
    private String mIsbn;
    private int mPreviewSizeWidth;
    private int mPreviewSizeHeight;
    @ColorInt
    private int mImageBackgroundColor;

    /** List of all editions for the given ISBN. */
    private List<String> mAlternativeEditions;
    /** Handles downloading, checking and cleanup of files. */
    private FileManager mFileManager;
    /** Indicates a 'shutdown()' has been requested. */
    private boolean mShutdown;

    /** cached activity. */
    private BaseActivity mActivity;
    private PagerLayout mPagerView;
    private ImageSwitcher mImageSwitcherView;
    private TextView mSwitcherMessageView;

    /**
     * @param isbn        ISBN of book
     * @param searchFlags bitmask with sites to search,
     *                    see {@link SearchSites.Site#SEARCH_ALL} and individual flags
     *
     * @return the instance
     */
    @NonNull
    public static CoverBrowser newInstance(@NonNull final String isbn,
                                           final int searchFlags) {
        // dev sanity check
        if ((searchFlags & SearchSites.Site.SEARCH_ALL) == 0) {
            throw new IllegalArgumentException("Must specify at least one source to use");
        }
        if (LibraryThingManager.noKey()) {
            throw new IllegalStateException("LibraryThing Key must be tested before calling this");
        }

        CoverBrowser frag = new CoverBrowser();
        Bundle args = new Bundle();
        args.putInt(UniqueId.BKEY_SEARCH_SITES, searchFlags);
        args.putString(UniqueId.KEY_ISBN, isbn);
        frag.setArguments(args);
        return frag;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        mActivity = (BaseActivity) requireActivity();

        Bundle args = requireArguments();
        mIsbn = args.getString(UniqueId.KEY_ISBN);
        int searchFlags = args.getInt(UniqueId.BKEY_SEARCH_SITES);

        // Create an object to manage the downloaded files
        mFileManager = new FileManager(searchFlags);

        // Calculate some image sizes to display
        android.util.DisplayMetrics metric = ImageUtils.getDisplayMetrics(mActivity);
        int previewSize = Math.max(metric.widthPixels, metric.heightPixels) / 5;
        // for code clarity kept as two variable names.
        mPreviewSizeWidth = previewSize;
        mPreviewSizeHeight = previewSize;

        mImageBackgroundColor = getResources().getColor(R.color.CoverBrowser_background);

        @SuppressLint("InflateParams")
        View root = mActivity.getLayoutInflater().inflate(R.layout.dialog_cover_browser, null);

        // The switcher will be used to display larger versions; needed for onItemClick().
        mImageSwitcherView = root.findViewById(R.id.switcher);
        mSwitcherMessageView = root.findViewById(R.id.switcherStatus);

        mPagerView = root.findViewById(R.id.gallery);
        mPagerView.setMinimumWidth(metric.widthPixels);

        return new AlertDialog.Builder(mActivity)
                .setView(root)
                .setTitle(R.string.title_finding_editions)
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();

        fetchEditions();
    }

    /**
     * Start a search for alternative editions of the book (isbn).
     */
    private void fetchEditions() {
        GetEditionsTask task = new GetEditionsTask(this, mIsbn);
        addTask(task);
        task.execute();
    }

    /**
     * Called after we got results from the edition search.
     * Show the user a selection of other covers and allow selection of a replacement.
     */
    private void showGallery(@NonNull final GetEditionsTask task,
                             @Nullable final List<String> editions) {
        removeTask(task);

        mAlternativeEditions = editions;
        if (mAlternativeEditions == null || mAlternativeEditions.isEmpty()) {
            UserMessage.showUserMessage(mPagerView, R.string.warning_no_editions);
            dismiss();
            return;
        }

        final Dialog dialog = getDialog();
        //noinspection ConstantConditions
        dialog.setTitle(R.string.title_select_cover);

        // Setup the Gallery.
        final ViewPager gallery = mPagerView.getViewPager();

        // Use our custom adapter to load images
        final PagerAdapter adapter = new CoverImagePagerAdapter();
        gallery.setAdapter(adapter);

        //Necessary or the pager will only have one extra page to show
        // make this at least however many pages you can see
        gallery.setOffscreenPageLimit(adapter.getCount());

        //If hardware acceleration is enabled, you should also remove
        // clipping on the pager for its children.
        gallery.setClipChildren(false);

        // dev note: if we set width to MATCH_PARENT, the page (cover image) IN the
        // PageView is maximized.
        // we don't want that... we want PageView maximized, but the page itself as-is.
        gallery.setLayoutParams(
                new PagerLayout.LayoutParams(mPreviewSizeWidth, mPreviewSizeHeight));

        // Show help message
        mSwitcherMessageView.setText(R.string.info_click_on_thumb);
        mSwitcherMessageView.setVisibility(View.VISIBLE);

        // When the large image is clicked, send it back to the caller and terminate.
        mImageSwitcherView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                String newSpec = (String) mImageSwitcherView.getTag();
                if (newSpec != null) {
                    Intent data = new Intent()
                            .putExtra(UniqueId.BKEY_FILE_SPEC, newSpec);
                    // Was a target fragment was set ?
                    Fragment targetFragment = getTargetFragment();
                    if (targetFragment != null) {
                        targetFragment.onActivityResult(CoverHandler.REQ_ALT_EDITION,
                                                        Activity.RESULT_OK, data);
                    } else {
                        // if not, assume the activity wants us.
                        mActivity.onActivityResult(CoverHandler.REQ_ALT_EDITION,
                                                   Activity.RESULT_OK, data);
                    }
                }
                dialog.dismiss();
            }
        });

        // Required object. Just create an ImageView
        mImageSwitcherView.setFactory(new ViewFactory() {
            @NonNull
            @Override
            public View makeView() {
                ImageView view = new ImageView(mActivity);
                view.setBackgroundColor(mImageBackgroundColor);
                view.setScaleType(ImageView.ScaleType.FIT_CENTER);
                view.setLayoutParams(new ImageSwitcher.LayoutParams(
                        ImageSwitcher.LayoutParams.WRAP_CONTENT,
                        ImageSwitcher.LayoutParams.WRAP_CONTENT));
                view.setImageResource(R.drawable.ic_image);
                return view;
            }
        });
    }

    @Override
    public void onDismiss(@NonNull final DialogInterface dialog) {
        mShutdown = true;
        // cancel any active tasks.
        synchronized (mAllTasks) {
            for (AsyncTask task : mAllTasks) {
                task.cancel(true);
            }
        }

        if (mFileManager != null) {
            mFileManager.purge();
            mFileManager = null;
        }

        super.onDismiss(dialog);
    }

    /**
     * Synchronized access to all tasks.
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
     * Synchronized access to all tasks.
     *
     * @param task to remove
     * @param <T>  type of task
     */
    private <T extends AsyncTask> void removeTask(@NonNull final T task) {
        synchronized (mAllTasks) {
            mAllTasks.remove(task);
        }
    }

    /**
     * Handles downloading, checking and cleanup of files.
     */
    private static class FileManager {

        /** key = isbn + "_ + size. */
        private final Map<String, String> files = new HashMap<>();

        /** Flags applicable to *current* search. */
        private final int mSearchFlags;

        /**
         * Constructor.
         *
         * @param searchFlags bitmask with sites to search,
         *                    see {@link SearchSites.Site#SEARCH_ALL} and individual flags
         */
        FileManager(final int searchFlags) {
            mSearchFlags = searchFlags;
        }

        /**
         * Check if a file is an image with acceptable size.
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
                    ok = opt.outHeight >= 8 && opt.outWidth >= 8;
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
                String fileSpec;
                String key = isbn + '_' + size;
                synchronized (files) {
                    fileSpec = files.get(key);
                }

                // Is the file present && good ?
                if ((fileSpec != null) && !fileSpec.isEmpty() && isGood(new File(fileSpec))) {
                    return fileSpec;
                } else {
                    files.remove(key);
                }

                for (SearchSites.Site site : SearchSites.getSitesForCoverSearches()) {
                    // Are we allowed to search this site ?
                    if (site.isEnabled()
                            // should we search this site ?
                            && ((mSearchFlags & site.id) != 0)
                    ) {
                        SearchSites.SearchSiteManager sm = site.getSearchSiteManager();
                        if (sm.supportsImageSize(size)) {
                            File file = sm.getCoverImage(isbn, size);
                            if (file != null && isGood(file)) {
                                fileSpec = file.getAbsolutePath();
                                synchronized (files) {
                                    files.put(key, fileSpec);
                                    return fileSpec;
                                }
                            }
                        }
                    }
                }

                // give up
                files.remove(key);
            }
            return null;
        }

        /**
         * Get the requested file, if available, otherwise return null.
         *
         * @param isbn to search
         * @param size required size
         *
         * @return the file, or null if not found
         */
        @Nullable
        File getFile(@NonNull final String isbn,
                     @NonNull final ImageSizes size) {
            String key = isbn + '_' + size;
            String fileSpec;
            synchronized (files) {
                fileSpec = files.get(key);
            }

            if (fileSpec == null || fileSpec.isEmpty()) {
                return null;
            }

            return new File(fileSpec);
        }

        /**
         * Clean up all files.
         */
        void purge() {
            for (String fileSpec : files.values()) {
                if (fileSpec != null) {
                    StorageUtils.deleteFile(new File(fileSpec));
                }
            }
            files.clear();
        }
    }

    /**
     * Fetch all alternative edition isbn's from LibraryThing.
     */
    private static class GetEditionsTask
            extends AsyncTask<Void, Void, List<String>> {

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
        protected List<String> doInBackground(final Void... params) {
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
        protected void onPostExecute(final List<String> result) {
            mCallback.showGallery(this, result);
        }
    }

    /**
     * Fetch a thumbnail or a full-size image and apply it to an ImageView.
     * There are two constructors, one for each size.
     */
    private static class GetImageTask
            extends AsyncTask<Void, Void, Void> {

        private final CoverImagePagerAdapter mCoverImagePagerAdapter;
        private final FileManager mFileManager;
        @NonNull
        private final String mIsbn;
        private final boolean isThumbnail;

        private WeakReference<ImageView> mImageViewRef;
        private int mMaxWidth;
        private int mMaxHeight;
        @Nullable
        private String mFileSpec;

        /**
         * Constructor for getting a full size image.
         *
         * @param adapter     to send results to
         * @param fileManager to use
         * @param isbn        in the editions list for the ISBN to use
         */
        @UiThread
        GetImageTask(@NonNull final CoverImagePagerAdapter adapter,
                     @NonNull final FileManager fileManager,
                     @NonNull final String isbn) {
            mCoverImagePagerAdapter = adapter;
            mFileManager = fileManager;
            mIsbn = isbn;
            isThumbnail = false;
        }

        /**
         * Constructor for getting a thumbnail image.
         *
         * @param adapter     to send results to
         * @param fileManager to use
         * @param isbn        in the editions list for the ISBN to use
         */
        @UiThread
        GetImageTask(@NonNull final CoverImagePagerAdapter adapter,
                     @NonNull final FileManager fileManager,
                     @NonNull final String isbn,
                     final int maxWidth,
                     final int maxHeight,
                     @NonNull final ImageView view) {
            mCoverImagePagerAdapter = adapter;
            mFileManager = fileManager;
            mIsbn = isbn;
            isThumbnail = true;

            mImageViewRef = new WeakReference<>(view);
            mMaxWidth = maxWidth;
            mMaxHeight = maxHeight;
        }

        @Override
        @WorkerThread
        protected Void doInBackground(final Void... params) {
            if (isThumbnail) {
                mFileSpec = mFileManager.download(mIsbn, ImageSizes.SMALL, ImageSizes.MEDIUM,
                                                  ImageSizes.LARGE);
            } else {
                mFileSpec = mFileManager.download(mIsbn, ImageSizes.LARGE, ImageSizes.MEDIUM,
                                                  ImageSizes.SMALL);
            }
            return null;
        }

        @Override
        @UiThread
        protected void onPostExecute(final Void result) {
            if (isCancelled() || mFileSpec == null || mFileSpec.isEmpty()) {
                return;
            }

            if (isThumbnail) {
                mCoverImagePagerAdapter.updateThumbnail(this, mFileSpec, mImageViewRef.get(),
                                                        mMaxWidth, mMaxHeight);
            } else {
                mCoverImagePagerAdapter.updateSwitcher(this, mFileSpec);
            }
        }
    }

    /**
     * //ENHANCE: use RecyclerView ? to research....
     * <p>
     * PagerAdapter for gallery. Queues image requests.
     */
    private class CoverImagePagerAdapter
            extends PagerAdapter {

        @DrawableRes
        private final int mGalleryItemBackground;

        /**
         * Constructor.
         */
        CoverImagePagerAdapter() {

            // Setup the background
            TypedArray ta = mActivity.obtainStyledAttributes(R.styleable.CoverGallery);
            mGalleryItemBackground = ta.getResourceId(
                    R.styleable.CoverGallery_android_galleryItemBackground, 0);
            ta.recycle();
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull final ViewGroup container,
                                      final int position) {
            ImageView coverImage = new ImageView(mActivity);
            // If we are shut down, just return a view
            if (mShutdown) {
                return coverImage;
            }

            // Initialize the view
            //coverImage.setScaleType(ImageView.ScaleType.FIT_XY);
            coverImage.setBackgroundResource(mGalleryItemBackground);

            // fetch an image based on the isbn
            final String isbn = mAlternativeEditions.get(position);

            // See if file is present
            File file = mFileManager.getFile(isbn, SearchSites.ImageSizes.SMALL);
            if (file == null) {
                // Not present; request it
                if (!mShutdown) {
                    GetImageTask task = new GetImageTask(CoverImagePagerAdapter.this,
                                                         mFileManager, isbn,
                                                         mPreviewSizeWidth, mPreviewSizeHeight,
                                                         coverImage);
                    addTask(task);
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
                //  and use a placeholder.
                coverImage.setImageResource(R.drawable.ic_image);
            } else {
                // Present, so use it.
                ImageUtils.getImageAndPutIntoView(coverImage, file, mPreviewSizeWidth,
                                                  mPreviewSizeHeight, true);
            }

            coverImage.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(@NonNull final View v) {
                    mImageSwitcherView.setVisibility(View.GONE);

                    // Show status message
                    mSwitcherMessageView.setText(R.string.progress_msg_loading);
                    mSwitcherMessageView.setVisibility(View.VISIBLE);
                    // get the full size image.
                    GetImageTask task = new GetImageTask(CoverImagePagerAdapter.this,
                                                         mFileManager, isbn);
                    addTask(task);
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
            });
            container.addView(coverImage);
            return coverImage;
        }

        /**
         * handle result from the {@link GetImageTask}.
         *
         * @param fileSpec the file we got.
         */
        void updateSwitcher(@NonNull final GetImageTask task,
                            @NonNull final String fileSpec) {
            removeTask(task);

            // Update the ImageSwitcher
            File file = new File(fileSpec);
            if (file.exists() && file.length() > 100) {
                Drawable image = new BitmapDrawable(
                        mActivity.getResources(),
                        ImageUtils.getImage(file,
                                            mPreviewSizeWidth * 4,
                                            mPreviewSizeHeight * 4,
                                            true));
                mImageSwitcherView.setImageDrawable(image);
                mImageSwitcherView.setTag(file.getAbsolutePath());
                mSwitcherMessageView.setVisibility(View.GONE);
                mImageSwitcherView.setVisibility(View.VISIBLE);
            } else {
                mSwitcherMessageView.setVisibility(View.VISIBLE);
                mImageSwitcherView.setVisibility(View.GONE);
                mSwitcherMessageView.setText(R.string.warning_cover_not_found);
            }
        }

        @Override
        public void destroyItem(@NonNull final ViewGroup container,
                                final int position,
                                @NonNull final Object object) {
            container.removeView((ImageView) object);
        }

        @Override
        public int getCount() {
            return mAlternativeEditions.size();
        }

        @Override
        public boolean isViewFromObject(@NonNull final View view,
                                        @NonNull final Object object) {
            return view == object;
        }

        /**
         * handle result from the {@link GetImageTask}.
         *
         * @param fileSpec the file we got.
         */
        void updateThumbnail(@NonNull final GetImageTask task,
                             @NonNull final String fileSpec,
                             @Nullable final ImageView imageView,
                             final int maxWidth,
                             final int maxHeight) {
            removeTask(task);
            // the imageView would be null if during the task, the view would have been destroyed.
            // (but would we even get here then? or are we being paranoid...)
            if (imageView != null) {
                // Load the file and apply to view
                File file = new File(fileSpec);
                file.deleteOnExit();
                ImageUtils.getImageAndPutIntoView(imageView, file, maxWidth, maxHeight, true);
            }
        }
    }
}
