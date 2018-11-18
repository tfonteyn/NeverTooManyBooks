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

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.res.TypedArray;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewSwitcher.ViewFactory;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.searches.googlebooks.GoogleBooksManager;
import com.eleybourn.bookcatalogue.searches.isfdb.ISFDBManager;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager.ImageSizes;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.io.File;
import java.util.List;

/**
 * Class to display and manage a cover image browser in a dialog.
 *
 * ENHANCE: For each ISBN returned by LT, add TWO images and get the second from GoodReads
 * ENHANCE: (Somehow) remove non-existent images from ImageSelector. Probably start with 1 image and GROW it.
 *
 * @author Philip Warner
 */
public class CoverBrowser {
    /** Handler when an image is finally selected. */
    @NonNull
    private final OnImageSelectedListener mOnImageSelectedListener;
    /** ISBN of book to lookup */
    @NonNull
    private final String mIsbn;
    /** Calling context */
    @NonNull
    private final Activity mActivity;

    private final int mPreviewSizeWidth;
    private final int mPreviewSizeHeight;
    @ColorInt
    private final int mImageBackgroundColor;

    /** The Dialog */
    @NonNull
    private final Dialog mDialog;
    @NonNull
    private final android.util.DisplayMetrics mMetric;
    /** Task queue for images */
    private SimpleTaskQueue mImageFetcher = null;
    /** List of all editions for the given ISBN */
    private List<String> mAlternativeEditions;
    /** Handles downloading, checking and cleanup of files. */
    private FileManager mFileManager;
    /** Indicates a 'shutdown()' has been requested */
    private boolean mShutdown = false;


    /**
     * Constructor
     *
     * @param activity                Calling context
     * @param isbn                    ISBN of book
     * @param onImageSelectedListener Handler to call when book selected
     */
    public CoverBrowser(final @NonNull Activity activity,
                        final @NonNull String isbn,
                        final @NonNull OnImageSelectedListener onImageSelectedListener) {
        mActivity = activity;
        mIsbn = isbn;
        mOnImageSelectedListener = onImageSelectedListener;

        mMetric = ImageUtils.getDisplayMetrics(activity);

        // Calculate some image sizes to display
        int previewSize = Math.max(mMetric.widthPixels, mMetric.heightPixels) / 5;
        // for code clarity kept as two variable names.
        mPreviewSizeWidth = previewSize;
        mPreviewSizeHeight = previewSize;
        mImageBackgroundColor = mActivity.getResources().getColor(R.color.CoverBrowser_background);

        // Create an object to manage the downloaded files
        mFileManager = new FileManager();

        mDialog = new StandardDialogs.BasicDialog(mActivity);
//        mDialog = new AlertDialog.Builder(mActivity).create();
    }

    /**
     * Close and abort everything
     */
    public void dismiss() {
        shutdown();
    }

    /**
     * Close down everything
     */
    private void shutdown() {
        mShutdown = true;

        mDialog.dismiss();

        if (mImageFetcher != null) {
            mImageFetcher.finish();
            mImageFetcher = null;
        }
        if (mFileManager != null) {
            mFileManager.purge();
            mFileManager = null;
        }
    }

    /**
     * Show the user a selection of other covers and allow selection of a replacement.
     */
    public void showEditionCovers() {

        LibraryThingManager mLibraryThing = new LibraryThingManager();
        if (!mLibraryThing.isAvailable()) {
            StandardDialogs.needLibraryThingAlert(mActivity, true, "cover_browser");
            return;
        }

        if (!IsbnUtils.isValid(mIsbn)) {
            StandardDialogs.showUserMessage(mActivity, R.string.warning_no_isbn_no_editions);
            shutdown();
            return;
        }

        // Setup the background fetcher
        if (mImageFetcher == null) {
            mImageFetcher = new SimpleTaskQueue("cover-browser");
        }

        mImageFetcher.enqueue(new GetEditionsTask(mIsbn));

        // Setup the basic dialog
        mDialog.setContentView(R.layout.dialog_cover_browser);
        mDialog.setTitle(R.string.title_finding_editions);
        mDialog.show();
    }

    private void showGallery() {
        mDialog.setTitle(R.string.title_select_cover);

        // Setup the Gallery.
        final PagerLayout container = mDialog.findViewById(R.id.gallery);
        container.setMinimumWidth(mMetric.widthPixels);

        final ViewPager gallery = container.getViewPager();

        // The switcher will be used to display larger versions; needed for onItemClick().
        final ImageSwitcher switcher = mDialog.findViewById(R.id.switcher);

        // Use our custom adapter to load images
        final PagerAdapter adapter = new CoverImagePagerAdapter(switcher);
        gallery.setAdapter(adapter);

        //Necessary or the pager will only have one extra page to show
        // make this at least however many pages you can see
        gallery.setOffscreenPageLimit(adapter.getCount());

        //If hardware acceleration is enabled, you should also remove
        // clipping on the pager for its children.
        gallery.setClipChildren(false);

        // dev note: if we set width to MATCH_PARENT, the page (cover image) IN the PageView is maximized.
        // we don't want that... we want PageView maximized, but page itself as is.
        gallery.setLayoutParams(new PagerLayout.LayoutParams(mPreviewSizeWidth, mPreviewSizeHeight));

        // Show help message
        final TextView msgVw = mDialog.findViewById(R.id.switcherStatus);
        msgVw.setText(R.string.info_click_on_thumb);
        msgVw.setVisibility(View.VISIBLE);

        // When the large image is clicked, send it back to the caller and terminate.
        switcher.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Object newSpec = ViewTagger.getTag(switcher);
                if (newSpec != null) {
                    mOnImageSelectedListener.onImageSelected((String) newSpec);
                }
                mDialog.dismiss();
            }
        });

        // Required object. Just create an ImageView
        switcher.setFactory(new ViewFactory() {
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

        // When the dialog is closed, delete the files and terminated the SimpleTaskQueue.
        mDialog.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                shutdown();
            }
        });
    }

    /**
     * Interface called when image is selected.
     *
     * @author Philip Warner
     */
    public interface OnImageSelectedListener {
        void onImageSelected(final @NonNull String fileSpec);
    }

    /**
     * //ENHANCE: use RecyclerView ? to research....
     *
     * PagerAdapter for gallery. Queues image requests.
     */
    private class CoverImagePagerAdapter extends PagerAdapter {
        @DrawableRes
        private final int mGalleryItemBackground;
        @NonNull
        private final ImageSwitcher mSwitcher;

        CoverImagePagerAdapter(final @NonNull ImageSwitcher switcher) {
            mSwitcher = switcher;
            // Setup the background
            TypedArray ta = mActivity.obtainStyledAttributes(R.styleable.CoverGallery);
            mGalleryItemBackground = ta.getResourceId(R.styleable.CoverGallery_android_galleryItemBackground, 0);
            ta.recycle();
        }

        @NonNull
        @Override
        public Object instantiateItem(final @NonNull ViewGroup collection, final int position) {
            ImageView coverImage = new ImageView(mActivity);
            // If we are shutdown, just return a view
            if (mShutdown) {
                return coverImage;
            }

            // Initialize the view
            //coverImage.setScaleType(ImageView.ScaleType.FIT_XY);
            coverImage.setBackgroundResource(mGalleryItemBackground);

            // now go fetch an image based on the isbn
            final String isbn = mAlternativeEditions.get(position);

            // See if file is present
            File file = null;
            try {
                file = mFileManager.getFile(isbn, ImageSizes.SMALL);
            } catch (NullPointerException ignore) {
                //file did not exist. Dealt with later.
            }

            if (file == null) {
                // Not present; request it
                mImageFetcher.enqueue(new GetThumbnailTask(isbn, coverImage, mPreviewSizeWidth, mPreviewSizeHeight));
                //  and use a placeholder.
                coverImage.setImageResource(R.drawable.ic_image);
            } else {
                // Present, so use it.
                ImageUtils.fetchFileIntoImageView(coverImage, file, mPreviewSizeWidth, mPreviewSizeHeight, true);
            }

            coverImage.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mSwitcher.setVisibility(View.GONE);

                    // Show status message
                    final TextView msgVw = mDialog.findViewById(R.id.switcherStatus);
                    msgVw.setText(R.string.progress_msg_loading);
                    msgVw.setVisibility(View.VISIBLE);

                    GetFullImageTask task = new GetFullImageTask(isbn, mSwitcher);
                    mImageFetcher.enqueue(task);
                }
            });
            collection.addView(coverImage);
            return coverImage;
        }

        @Override
        public void destroyItem(final @NonNull ViewGroup collection, final int position, final @NonNull Object view) {
            collection.removeView((ImageView) view);
        }

        @Override
        public int getCount() {
            return mAlternativeEditions.size();
        }

        @Override
        public boolean isViewFromObject(final @NonNull View view, final @NonNull Object object) {
            return view == object;
        }
    }

    /**
     * SimpleTask to fetch all alternative edition isbn's from LibraryThing
     *
     * @author Philip Warner
     */
    private class GetEditionsTask implements SimpleTaskQueue.SimpleTask {
        @NonNull
        final String isbn;

        /**
         * Constructor
         */
        GetEditionsTask(final @NonNull String isbn) {
            this.isbn = isbn;
        }

        @Override
        public void run(final @NonNull SimpleTaskContext taskContext) {
            // Get some editions
            // ENHANCE: the list of editions should be expanded to somehow include other sites.
            // As well as the alternate user-contributed images from LibraryThing. The latter are
            // often the best source but at present could only be obtained by HTML scraping.
            try {
                mAlternativeEditions = LibraryThingManager.searchEditions(isbn);
            } catch (Exception e) {
                mAlternativeEditions = null;
            }
        }

        @Override
        public void onFinish(final @Nullable Exception e) {
            if (mAlternativeEditions == null || mAlternativeEditions.isEmpty()) {
                StandardDialogs.showUserMessage(mActivity, R.string.warning_no_editions);
                shutdown();
                return;
            }
            showGallery();
        }
    }

    /**
     * SimpleTask to fetch a thumbnail image and apply it to an ImageView
     *
     * @author Philip Warner
     */
    private class GetThumbnailTask implements SimpleTaskQueue.SimpleTask {
        @NonNull
        private final ImageView imageView;
        private final int maxWidth;
        private final int maxHeight;
        @NonNull
        private final String isbn;
        @Nullable
        private String fileSpec;

        /**
         * @param isbn for requested cover.
         * @param view to update
         */
        GetThumbnailTask(final @NonNull String isbn,
                         final @NonNull ImageView view,
                         final int maxWidth,
                         final int maxHeight) {
            imageView = view;
            this.maxWidth = maxWidth;
            this.maxHeight = maxHeight;
            this.isbn = isbn;
        }

        @Override
        public void run(final @NonNull SimpleTaskContext taskContext) {
            // If we are shutdown, just return
            if (mShutdown) {
                taskContext.setRequiresFinish(false);
                return;
            }

            // Try SMALL
            fileSpec = mFileManager.download(isbn, ImageSizes.SMALL);
            if (fileSpec != null && new File(fileSpec).length() >= 50) {
                return;
            }

            // Try LARGE (or silently give up)
            fileSpec = mFileManager.download(isbn, ImageSizes.LARGE);
        }

        @Override
        public void onFinish(final @Nullable Exception e) {
            if (mShutdown || fileSpec == null || fileSpec.isEmpty()) {
                return;
            }

            // Load the file and apply to view
            File file = new File(fileSpec);
            file.deleteOnExit();
            ImageUtils.fetchFileIntoImageView(imageView, file, maxWidth, maxHeight, true);

        }
    }

    /**
     * SimpleTask to download an image and apply it to the ImageSwitcher.
     *
     * @author Philip Warner
     */
    private class GetFullImageTask implements SimpleTaskQueue.SimpleTask {
        // Switcher to use
        @NonNull
        private final ImageSwitcher switcher;
        // ISBN
        private final String isbn;
        // Resulting file
        @Nullable
        private String fileSpec;

        /**
         * Constructor
         *
         * @param isbn     in the editions list for the ISBN to use
         * @param switcher ImageSwitcher to update
         */
        GetFullImageTask(final @NonNull String isbn, final @NonNull ImageSwitcher switcher) {
            this.switcher = switcher;
            this.isbn = isbn;
        }

        @Override
        public void run(final @NonNull SimpleTaskContext taskContext) {
            // If we are shutdown, just return
            if (mShutdown) {
                taskContext.setRequiresFinish(false);
                return;
            }

            // Download the file, try LARGE first
            fileSpec = mFileManager.download(isbn, ImageSizes.LARGE);
            if (fileSpec != null && new File(fileSpec).length() >= 50) {
                return;
            }
            // Try SMALL (or silently give up)
            fileSpec = mFileManager.download(isbn, ImageSizes.SMALL);
        }

        @Override
        public void onFinish(final @Nullable Exception e) {
            if (mShutdown || fileSpec == null || fileSpec.isEmpty()) {
                return;
            }

            // Update the ImageSwitcher
            File file = new File(fileSpec);
            TextView msgVw = mDialog.findViewById(R.id.switcherStatus);
            if (file.exists() && file.length() > 100) {
                Drawable image = new BitmapDrawable(mActivity.getResources(),
                        ImageUtils.fetchFileIntoImageView(null, file,
                                mPreviewSizeWidth * 4, mPreviewSizeHeight * 4, true));
                switcher.setImageDrawable(image);
                ViewTagger.setTag(switcher, file.getAbsolutePath());
                msgVw.setVisibility(View.GONE);
                switcher.setVisibility(View.VISIBLE);
            } else {
                msgVw.setVisibility(View.VISIBLE);
                switcher.setVisibility(View.GONE);
                msgVw.setText(R.string.warning_cover_not_found);
            }
        }
    }

    /**
     * Handles downloading, checking and cleanup of files
     *
     * @author Philip Warner
     */
    private class FileManager {
        final LibraryThingManager libraryThingManager = new LibraryThingManager();

        /** key = isbn + "_" + size */
        private final Bundle files = new Bundle();

        private boolean isGood(final @NonNull File file) {
            boolean ok = false;

            if (file.exists() && file.length() != 0) {
                try {
                    // Just read the image files to get file size
                    BitmapFactory.Options opt = new BitmapFactory.Options();
                    opt.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(file.getAbsolutePath(), opt);
                    // If too small, it's no good
                    ok = (opt.outHeight >= 8 && opt.outWidth >= 8);
                } catch (Exception e) {
                    // Failed to decode; probably not an image
                    ok = false;
                    Logger.error(e, "Unable to decode thumbnail");
                }
            }

            if (!ok) {
                StorageUtils.deleteFile(file);
            }
            return ok;
        }

        /**
         * Download a file if not present and keep a record of it.
         *
         * @param isbn ISBN of file
         * @param size Size of image required
         *
         * @return the fileSpec, or null when not found
         */
        @Nullable
        public String download(final @NonNull String isbn, final @NonNull ImageSizes size) {
            String fileSpec;
            String key = isbn + "_" + size;

            synchronized (files) {
                fileSpec = files.getString(key);
            }

            // Is the file present && good ?
            if ((fileSpec != null) && !fileSpec.isEmpty() && isGood(new File(fileSpec))) {
                return fileSpec;
            } else {
                files.remove(key);
            }

            //Reminder: the for() loop will bailout (return) as soon as a cover file is found.
            // it does not collect covers from all sites; just from the first one found.
            // ENHANCE: allow the user to prioritize the order on the fly.
            for (SearchSites.Site site : SearchSites.getSiteCoverSearchOrder()) {
                if (site.enabled) {
                    File file = null;
                    switch (site.id) {
                        case SearchSites.SEARCH_LIBRARY_THING: {
                            file = libraryThingManager.getCoverImage(isbn, size);
                            break;
                        }
                        case SearchSites.SEARCH_GOOGLE: {
                            file = GoogleBooksManager.getCoverImage(isbn);
                            break;
                        }
                        case SearchSites.SEARCH_ISFDB: {
                            file = ISFDBManager.getCoverImage(isbn);
                            break;
                        }
                    }

                    if (file != null && isGood(file)) {
                        fileSpec = file.getAbsolutePath();
                        synchronized (files) {
                            files.putString(key, fileSpec);
                            return fileSpec;
                        }
                    }
                }
            }

            // give up
            files.remove(key);
            return null;
        }

        /**
         * Get the requested file, if available, otherwise return null.
         */
        @Nullable
        public File getFile(final @NonNull String isbn, final @NonNull ImageSizes size) {
            String key = isbn + "_" + size;
            String fileSpec;
            synchronized (files) {
                fileSpec = files.getString(key);
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
            try {
                for (String key : files.keySet()) {
                    String fileSpec = files.getString(key);
                    if (fileSpec != null) {
                        StorageUtils.deleteFile(new File(fileSpec));
                    }
                }
                files.clear();
            } catch (Exception e) {
                Logger.error(e);
            }
        }
    }
}
