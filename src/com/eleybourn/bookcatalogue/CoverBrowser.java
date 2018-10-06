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
import android.content.DialogInterface.OnDismissListener;
import android.content.res.TypedArray;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
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
import android.widget.Toast;
import android.widget.ViewSwitcher.ViewFactory;

import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.searches.SearchManager;
import com.eleybourn.bookcatalogue.searches.googlebooks.GoogleBooksManager;
import com.eleybourn.bookcatalogue.searches.isfdb.ISFDBManager;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager.ImageSizes;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.ViewTagger;
import com.eleybourn.bookcatalogue.widgets.PagerLayout;

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
    private final OnImageSelectedListener mOnImageSelectedListener;
    /** ISBN of book to lookup */
    private final String mIsbn;
    /** Calling context */
    private final Activity mActivity;
    private final int mPreviewSizeWidth;
    private final int mPreviewSizeHeight;
    /** The Dialog */
    private final Dialog mDialog;
    private final android.util.DisplayMetrics mMetric;
    /** Task queue for images */
    private SimpleTaskQueue mImageFetcher = null;
    /** List of all editions for the given ISBN */
    private List<String> mEditions;
    /** Object to ensure files are cleaned up. */
    private FileManager mFileManager;
    /** Indicates a 'shutdown()' has been requested */
    private boolean mShutdown = false;


    /**
     * Constructor
     *
     * @param a                       Calling context
     * @param isbn                    ISBN of book
     * @param onImageSelectedListener Handler to call when book selected
     */
    CoverBrowser(@NonNull final Activity a,
                 @NonNull final String isbn,
                 @NonNull final OnImageSelectedListener onImageSelectedListener) {
        mActivity = a;
        mIsbn = isbn;
        mOnImageSelectedListener = onImageSelectedListener;

        mMetric = ImageUtils.getDisplayMetrics(a);

        // Calculate some image sizes to display
        int previewSize = Math.max(mMetric.widthPixels, mMetric.heightPixels) / 5;
        // for code clarity kept as two variable names.
        mPreviewSizeWidth = previewSize;
        mPreviewSizeHeight = previewSize;
        // Create an object to manage the downloaded files
        mFileManager = new FileManager();

        mDialog = new StandardDialogs.BasicDialog(mActivity);
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

        if (mDialog != null) {
            mDialog.dismiss();
        }
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

        LibraryThingManager mLibraryThing = new LibraryThingManager(mActivity);
        if (!mLibraryThing.isAvailable()) {
            StandardDialogs.needLibraryThingAlert(mActivity, true, "cover_browser");
            return;
        }

        if (!IsbnUtils.isValid(mIsbn)) {
            Toast.makeText(mActivity, R.string.no_isbn_no_editions, Toast.LENGTH_LONG).show();
            shutdown();
            return;
        }

        // Setup the background fetcher
        if (mImageFetcher == null) {
            mImageFetcher = new SimpleTaskQueue("cover-browser");
        }

        mImageFetcher.enqueue(new GetEditionsTask(mIsbn));

        // Setup the basic dialog
        mDialog.setContentView(R.layout.dialog_select_edition_cover);
        mDialog.setTitle(R.string.finding_editions);
        mDialog.show();
    }

    private void showDialogDetails() {
        mDialog.setTitle(R.string.select_cover);

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
        msgVw.setText(R.string.click_on_thumb);
        msgVw.setVisibility(View.VISIBLE);

        // When the large image is clicked, send it back to the caller and terminate.
        switcher.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Object newSpec = ViewTagger.getTag(switcher);
                if (newSpec != null) {
                    if (mOnImageSelectedListener != null) {
                        mOnImageSelectedListener.onImageSelected((String) newSpec);
                    }
                }
                mDialog.dismiss();
            }
        });

        // Required object. Just create an ImageView
        switcher.setFactory(new ViewFactory() {
            @Override
            public View makeView() {
                ImageView i = new ImageView(mActivity);
                i.setBackgroundColor(0xFF000000);
                i.setScaleType(ImageView.ScaleType.FIT_CENTER);
                i.setLayoutParams(new ImageSwitcher.LayoutParams(
                        ImageSwitcher.LayoutParams.WRAP_CONTENT,
                        ImageSwitcher.LayoutParams.WRAP_CONTENT));
                i.setImageResource(R.drawable.ic_warning);
                return i;
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
        void onImageSelected(@NonNull final String fileSpec);
    }

    /**
     * //FIXME: use RecyclerView ? to research....
     *
     * ImageAdapter for gallery. Queues image requests.
     *
     * @author Philip Warner
     */
    private class CoverImagePagerAdapter extends PagerAdapter {
        private final int mGalleryItemBackground;
        private final ImageSwitcher mSwitcher;

        CoverImagePagerAdapter(@NonNull final ImageSwitcher switcher) {
            mSwitcher = switcher;
            // Setup the background
            TypedArray a = mActivity.obtainStyledAttributes(R.styleable.CoverGallery);
            mGalleryItemBackground = a.getResourceId(R.styleable.CoverGallery_android_galleryItemBackground, 0);
            a.recycle();
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull final ViewGroup collection, final int position) {
            ImageView coverImage = new ImageView(mActivity);

            // If we are shutdown, just return a view
            if (mShutdown) {
                return coverImage;
            }

            // Initialize the view
            //coverImage.setScaleType(ImageView.ScaleType.FIT_XY);
            coverImage.setBackgroundResource(mGalleryItemBackground);

            // now go fetch an image based on the isbn
            String isbn = mEditions.get(position);

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
                coverImage.setImageResource(R.drawable.ic_warning);
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
                    msgVw.setText(R.string.loading);
                    msgVw.setVisibility(View.VISIBLE);

                    GetFullImageTask task = new GetFullImageTask(position, mSwitcher);
                    mImageFetcher.enqueue(task);
                }
            });
            collection.addView(coverImage);
            return coverImage;
        }

        @Override
        public void destroyItem(@NonNull final ViewGroup collection, final int position, @NonNull final Object view) {
            collection.removeView((ImageView) view);
        }

        @Override
        public int getCount() {
            return mEditions.size();
        }

        @Override
        public boolean isViewFromObject(@NonNull final View view, @NonNull final Object object) {
            return view == object;
        }
    }

    /**
     * SimpleTask to fetch a thumbnail image and apply it to an ImageView
     *
     * @author Philip Warner
     */
    private class GetEditionsTask implements SimpleTask {
        final String isbn;

        /**
         * Constructor
         */
        GetEditionsTask(@NonNull final String isbn) {
            this.isbn = isbn;
        }

        @Override
        public void run(@NonNull final SimpleTaskContext taskContext) {
            // Get some editions
            // ENHANCE: the list of editions should be expanded to somehow include Amazon and Google. As well
            // as the alternate user-contributed images from LibraryThing. The latter are often the best
            // source but at present could only be obtained by HTML scraping.
            try {
                mEditions = LibraryThingManager.searchEditions(isbn);
            } catch (Exception e) {
                mEditions = null;
            }
        }

        @Override
        public void onFinish(@Nullable final Exception e) {
            if (mEditions.isEmpty()) {
                Toast.makeText(mActivity, R.string.no_editions, Toast.LENGTH_LONG).show();
                shutdown();
                return;
            }
            showDialogDetails();
        }

    }

    /**
     * SimpleTask to fetch a thumbnail image and apply it to an ImageView
     *
     * @author Philip Warner
     */
    private class GetThumbnailTask implements SimpleTask {
        private final ImageView mImageView;
        private final int mMaxWidth;
        private final int mMaxHeight;
        private final String mIsbn;
        private String mFilename;

        /**
         * @param isbn ISBN on requested cover.
         * @param view to update
         */
        GetThumbnailTask(@NonNull final String isbn,
                         @NonNull final ImageView view,
                         final int maxWidth,
                         final int maxHeight) {
            mImageView = view;
            mMaxWidth = maxWidth;
            mMaxHeight = maxHeight;
            mIsbn = isbn;
        }

        @Override
        public void run(@NonNull final SimpleTaskContext taskContext) {
            // Try SMALL
            mFilename = mFileManager.download(mIsbn, ImageSizes.SMALL);
            if (new File(mFilename).length() >= 50) {
                return;
            }

            // Try LARGE (or silently give up)
            mFilename = mFileManager.download(mIsbn, ImageSizes.LARGE);
        }

        @Override
        public void onFinish(@Nullable final Exception e) {
            // Load the file and apply to view
            File file = new File(mFilename);
            file.deleteOnExit();
            ImageUtils.fetchFileIntoImageView(mImageView, file, mMaxWidth, mMaxHeight, true);
        }
    }

    /**
     * SimpleTask to download an image and apply it to the ImageSwitcher.
     *
     * @author Philip Warner
     */
    private class GetFullImageTask implements SimpleTask {
        // Switcher to use
        private final ImageSwitcher mSwitcher;
        // ISBN
        private final String mIsbn;
        // Resulting file
        private String mFileSpec;

        /**
         * Constructor
         *
         * @param position Position f ISBN
         * @param switcher ImageSwitcher to update
         */
        GetFullImageTask(final int position, @NonNull final ImageSwitcher switcher) {
            mSwitcher = switcher;
            mIsbn = mEditions.get(position);
        }

        @Override
        public void run(@NonNull final SimpleTaskContext taskContext) {
            // If we are shutdown, just return
            if (mShutdown) {
                taskContext.setRequiresFinish(false);
                return;
            }

            // Download the file, try LARGE first
            mFileSpec = mFileManager.download(mIsbn, ImageSizes.LARGE);
            if (new File(mFileSpec).length() >= 50) {
                return;
            }
            // Try SMALL (or silently give up)
            mFileSpec = mFileManager.download(mIsbn, ImageSizes.SMALL);
        }

        @Override
        public void onFinish(@Nullable final Exception e) {
            if (mShutdown) {
                return;
            }
            // Update the ImageSwitcher
            File file = new File(mFileSpec);
            TextView msgVw = mDialog.findViewById(R.id.switcherStatus);
            if (file.exists() && file.length() > 100) {
                Drawable image = new BitmapDrawable(mActivity.getResources(),
                        ImageUtils.fetchFileIntoImageView(null, file,
                                mPreviewSizeWidth * 4, mPreviewSizeHeight * 4, true));
                mSwitcher.setImageDrawable(image);
                ViewTagger.setTag(mSwitcher, file.getAbsolutePath());
                msgVw.setVisibility(View.GONE);
                mSwitcher.setVisibility(View.VISIBLE);
            } else {
                msgVw.setVisibility(View.VISIBLE);
                mSwitcher.setVisibility(View.GONE);
                msgVw.setText(R.string.image_not_found);
            }
        }
    }

    /**
     * Simple utility class to (try) to cleanup and prevent files from accumulating.
     *
     * @author Philip Warner
     */
    private class FileManager {
        final LibraryThingManager mLibraryThing = new LibraryThingManager(mActivity);
        private final Bundle mFiles = new Bundle();

        private boolean isGood(@NonNull final File file) {
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
                    Logger.logError(e, "Unable to decode thumbnail");
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
        @NonNull
        public String download(@NonNull final String isbn, @NonNull final ImageSizes size) {
            String fileSpec;
            String key = isbn + "_" + size;

            synchronized (mFiles) {
                fileSpec = mFiles.getString(key);
            }

            // Is the file present && good ?
            if ((fileSpec != null) && !fileSpec.isEmpty() && isGood(new File(fileSpec))) {
                return fileSpec;
            } else {
                mFiles.remove(key);
            }

            // allow the user to prioritize the order
            for (SearchManager.SearchSite site : SearchManager.getSiteCoverSearchOrder()) {
                if (site.enabled) {
                    switch (site.id) {
                        case SearchManager.SEARCH_LIBRARY_THING: {
                            File file = mLibraryThing.getCoverImage(isbn, null, size);
                            if (file != null && isGood(file)) {
                                fileSpec = file.getAbsolutePath();
                                synchronized (mFiles) {
                                    mFiles.putString(key, fileSpec);
                                    return fileSpec;
                                }
                            }
                            break;
                        }
                        case SearchManager.SEARCH_GOOGLE: {
                            File file = GoogleBooksManager.getCoverImage(isbn);
                            if (file != null && isGood(file)) {
                                fileSpec = file.getAbsolutePath();
                                synchronized (mFiles) {
                                    mFiles.putString(key, fileSpec);
                                    return fileSpec;
                                }
                            }
                            break;
                        }
                        case SearchManager.SEARCH_ISFDB: {
                            File file = ISFDBManager.getCoverImage(isbn);
                            if (file != null && isGood(file)) {
                                fileSpec = file.getAbsolutePath();
                                synchronized (mFiles) {
                                    mFiles.putString(key, fileSpec);
                                    return fileSpec;
                                }
                            }
                            break;
                        }
                    }
                }
            }

            // give up
            mFiles.remove(key);
            return ""; //don't return null.
        }

        /**
         * Get the requested file, if available, otherwise return null.
         */
        @Nullable
        public File getFile(@NonNull final String isbn, @NonNull final ImageSizes size) {
            String key = isbn + "_" + size;
            String fileSpec;
            synchronized (mFiles) {
                fileSpec = mFiles.getString(key);
            }

            if (fileSpec == null || fileSpec.isEmpty()) {
                return null;
            }

            return new File(fileSpec);
        }

        /**
         * Clean up all files.
         */
        public void purge() {
            try {
                for (String k : mFiles.keySet()) {
                    String fileSpec = mFiles.getString(k);
                    if (fileSpec != null) {
                        StorageUtils.deleteFile(new File(fileSpec));
                    }
                }
                mFiles.clear();
            } catch (Exception e) {
                Logger.logError(e);
            }
        }
    }
}
