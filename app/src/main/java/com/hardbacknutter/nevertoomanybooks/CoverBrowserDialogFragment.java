/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks;

import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageSwitcher;
import android.widget.ImageView;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogCoverBrowserBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.SiteList;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.viewmodels.CoverBrowserViewModel;

/**
 * Displays and manages a cover image browser in a dialog, allowing the user to select
 * an image from a list to use as the (new) book cover image.
 * <p>
 * ENHANCE: allow configuring search-sites on the fly
 * ENHANCE: currently supports only a front-cover. Add back-cover support
 * <p>
 * TODO: The current implementation of this class and related classes is far from optimal.
 * Something to do on a rainy day.
 */
public class CoverBrowserDialogFragment
        extends DialogFragment {

    /** Log tag. */
    public static final String TAG = "CoverBrowserFragment";

    /** Populated by {@link #showGallery} AND savedInstanceState. */
    @NonNull
    private final ArrayList<IsbnFileInfo> mEditions = new ArrayList<>();
    /** The adapter for the horizontal scrolling covers list. */
    @Nullable
    private GalleryAdapter mGalleryAdapter;
    /** Indicates dismiss() has been requested. */
    private boolean mDismissing;

    /** The ViewModel. */
    private CoverBrowserViewModel mModel;

    /** View Binding. */
    private DialogCoverBrowserBinding mVb;
    /** Where to send the result. */
    @Nullable
    private WeakReference<OnFileSpecResult> mListener;

    /**
     * Constructor.
     *
     * @param isbn ISBN of book
     * @param cIdx 0..n image index
     *
     * @return instance
     */
    @NonNull
    public static DialogFragment newInstance(@NonNull final String isbn,
                                             @IntRange(from = 0) final int cIdx) {
        final DialogFragment frag = new CoverBrowserDialogFragment();
        final Bundle args = new Bundle(1);
        args.putString(DBDefinitions.KEY_ISBN, isbn);
        args.putInt(CoverBrowserViewModel.BKEY_FILE_INDEX, cIdx);
        frag.setArguments(args);
        return frag;
    }

    /**
     * ENHANCE: pass in a {@link SiteList.Type#Covers} list / set it on the fly.
     */
    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mGalleryAdapter = new GalleryAdapter(ImageUtils.SCALE_MEDIUM);

        mModel = new ViewModelProvider(this).get(CoverBrowserViewModel.class);
        //noinspection ConstantConditions
        mModel.init(getContext(), requireArguments());

        mModel.onEditionsLoaded().observe(this, this::showGallery);
        mModel.onGalleryImage().observe(this, this::setGalleryImage);
        mModel.onGalleryImageSelected().observe(this, fileInfo ->
                setSwitcherImage(fileInfo.getFile()));
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        mVb = DialogCoverBrowserBinding.inflate(getLayoutInflater());

        // The gallery displays a list of images, one for each edition.
        final LinearLayoutManager galleryLayoutManager = new LinearLayoutManager(getContext());
        galleryLayoutManager.setOrientation(RecyclerView.HORIZONTAL);
        mVb.gallery.setLayoutManager(galleryLayoutManager);
        //noinspection ConstantConditions
        mVb.gallery.addItemDecoration(
                new DividerItemDecoration(getContext(), galleryLayoutManager.getOrientation()));
        mVb.gallery.setAdapter(mGalleryAdapter);

        // setup the switcher.
        mVb.switcher.setFactory(() -> {
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
        mVb.switcher.setOnClickListener(v -> {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
                Log.d(TAG, "mImageSwitcherView.onClick|fileSpec=" + mModel.getSelectedFileSpec());
            }
            if (mModel.getSelectedFileSpec() != null) {
                if (mListener != null && mListener.get() != null) {
                    mListener.get().onFileSpecResult(mModel.getImageIndex(),
                                                     mModel.getSelectedFileSpec());
                } else {
                    if (BuildConfig.DEBUG /* always */) {
                        Log.w(TAG, "onFileSpecResult|"
                                   + (mListener == null ? ErrorMsg.LISTENER_WAS_NULL
                                                        : ErrorMsg.LISTENER_WAS_DEAD));
                    }
                }
            }
            // close the CoverBrowserFragment
            dismiss();
        });

        //noinspection ConstantConditions
        return new MaterialAlertDialogBuilder(getContext())
                .setView(mVb.getRoot())
                .create();
    }

    @Override
    public void onCancel(@NonNull final DialogInterface dialog) {
        // prevent new tasks being started.
        mDismissing = true;
        super.onCancel(dialog);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mEditions.isEmpty()) {
            mVb.statusMessage.setText(R.string.progress_msg_finding_editions);
            mModel.fetchEditions();
        }
    }

    /**
     * Called with the results from the edition search.
     * Show the user a selection of other covers and allow selection of a replacement.
     *
     * @param isbnList the list to use.
     */
    private void showGallery(@Nullable final Iterable<String> isbnList) {
        mEditions.clear();
        if (isbnList != null) {
            for (String isbn : isbnList) {
                mEditions.add(new IsbnFileInfo(isbn));
            }
        }

        if (mEditions.isEmpty()) {
            Snackbar.make(mVb.statusMessage, R.string.warning_no_editions,
                          Snackbar.LENGTH_LONG).show();
            dismiss();
            return;
        }

        Objects.requireNonNull(mGalleryAdapter, ErrorMsg.NULL_GALLERY_ADAPTER);
        mGalleryAdapter.notifyDataSetChanged();

        // Show help message
        mVb.statusMessage.setText(R.string.txt_tap_on_thumb);
    }

    /**
     * handle result from the {@link CoverBrowserViewModel} GetGalleryImageTask.
     * <p>
     * TODO: pass the data via a MutableLiveData object and use a local FIFO queue.
     *
     * @param fileInfo the file we got, if any
     */
    private void setGalleryImage(@NonNull final CoverBrowserViewModel.FileInfo fileInfo) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
            Log.d(TAG, "setGalleryImage"
                       + "|fileInfo=" + fileInfo);
        }
        Objects.requireNonNull(mGalleryAdapter, ErrorMsg.NULL_GALLERY_ADAPTER);

        int editionIndex = -1;
        for (int i = 0; i < mEditions.size(); i++) {
            if (mEditions.get(i).isbn.equals(fileInfo.isbn)) {
                editionIndex = i;
                break;
            }
        }

        if (editionIndex >= 0) {
            final File tmpFile = fileInfo.getFile();
            if (tmpFile != null && tmpFile.exists()) {
                tmpFile.deleteOnExit();
                // tell the adapter to refresh the entry which now has a fileSpec
                mGalleryAdapter.notifyItemChanged(editionIndex);
                return;
            }

            // Remove the defunct view from the gallery
            mEditions.remove(editionIndex);
            mGalleryAdapter.notifyItemRemoved(editionIndex);
        }

        // if none left, dismiss.
        if (mGalleryAdapter.getItemCount() == 0) {
            Snackbar.make(mVb.statusMessage, R.string.warning_cover_not_found,
                          Snackbar.LENGTH_LONG).show();
            dismiss();
        }
    }

    /**
     * Display the given file in the switcher View. Starts a task if needed.
     *
     * @param isbnFileInfo to use
     */
    private void setSwitcherImage(@NonNull final IsbnFileInfo isbnFileInfo) {
        final File file = isbnFileInfo.getFile();
        // sanity check
        if (file != null) {
            //noinspection ConstantConditions
            if (SearchEngine.CoverByIsbn.ImageSize.Large.equals(isbnFileInfo.fileInfo.size)) {
                // we already have a valid large image, so just display it
                setSwitcherImage(file);

            } else {
                // start a task to fetch a larger image
                mVb.switcher.setImageResource(R.drawable.ic_image);
                mVb.switcher.setVisibility(View.VISIBLE);
                mVb.statusMessage.setText(R.string.progress_msg_loading);
                mModel.fetchSelectedImage(isbnFileInfo.fileInfo);
            }
        }
    }

    /**
     * Display the given file in the switcher View.
     *
     * @param file to display
     */
    private void setSwitcherImage(@Nullable final File file) {
        if (file != null && file.length() > ImageUtils.MIN_IMAGE_FILE_SIZE) {
            // store the path. It will be send back to the caller.
            mModel.setSelectedFileSpec(file.getAbsolutePath());
            // yes, on the UI thread... bad
            //noinspection ConstantConditions
            @Nullable
            final Bitmap bm = ImageUtils.createScaledBitmap(getContext(), file,
                                                            ImageUtils.SCALE_X_LARGE);
            if (bm != null) {
                mVb.switcher.setImageDrawable(new BitmapDrawable(getResources(), bm));
                mVb.switcher.setVisibility(View.VISIBLE);
                mVb.statusMessage.setText(R.string.txt_tap_on_image_to_select);
                return;
            }
        }

        // Reset the switcher and tell user.
        mVb.switcher.setVisibility(View.GONE);
        Snackbar.make(mVb.switcher, R.string.warning_cover_not_found,
                      Snackbar.LENGTH_LONG).show();
        mVb.statusMessage.setText(R.string.txt_tap_on_thumb);
    }

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    public void setListener(@NonNull final OnFileSpecResult listener) {
        mListener = new WeakReference<>(listener);
    }

    public interface OnFileSpecResult {

        void onFileSpecResult(@IntRange(from = 0) int cIdx,
                              @NonNull String fileSpec);
    }

    /**
     * Holder pattern for {@link GalleryAdapter}.
     */
    private static class Holder
            extends RecyclerView.ViewHolder {

        /** Keep an extra copy, to avoid casting. */
        @NonNull
        final ImageView imageView;

        Holder(@NonNull final ImageView itemView) {
            super(itemView);
            imageView = itemView;
        }
    }

    public static class IsbnFileInfo {

        @NonNull
        final String isbn;
        @Nullable
        CoverBrowserViewModel.FileInfo fileInfo;

        IsbnFileInfo(@NonNull final String isbn) {
            this.isbn = isbn;
        }

        @Nullable
        File getFile() {
            if (fileInfo != null) {
                return fileInfo.getFile();
            }
            return null;
        }

        @Override
        @NonNull
        public String toString() {
            return "IsbnFileInfo{"
                   + "isbn=`" + isbn + '`'
                   + ", fileInfo=" + fileInfo
                   + '}';
        }
    }

    private class GalleryAdapter
            extends RecyclerView.Adapter<Holder> {

        /** A single image fixed width. */
        private final int mWidth;
        /** A single image fixed height. */
        private final int mHeight;

        /**
         * Constructor.
         *
         * @param scale id
         */
        @SuppressWarnings("SameParameterValue")
        GalleryAdapter(@ImageUtils.Scale final int scale) {
            //noinspection ConstantConditions
            final int maxSize = ImageUtils.getMaxImageSize(getContext(), scale);
            mHeight = maxSize;
            mWidth = maxSize;
        }

        @Override
        @NonNull
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            final ImageView imageView = new ImageView(parent.getContext());
            // Deliberately keep sizes fixed (square) to prevent gallery constantly changing size.
            imageView.setLayoutParams(new ViewGroup.LayoutParams(mWidth, mHeight));
            return new Holder(imageView);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            final IsbnFileInfo edition = mEditions.get(position);

            // Get the image file based on the isbn; try the sizes in order as specified here.
            edition.fileInfo = mModel.getFileInfo(edition.isbn,
                                                  SearchEngine.CoverByIsbn.ImageSize.Small,
                                                  SearchEngine.CoverByIsbn.ImageSize.Medium,
                                                  SearchEngine.CoverByIsbn.ImageSize.Large);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
                Log.d(TAG, "onBindViewHolder|edition=" + edition);
            }

            final File file = edition.getFile();
            if (file != null && file.length() > ImageUtils.MIN_IMAGE_FILE_SIZE) {
                // we have a file, load it into the view.
                new ImageUtils.ImageLoader(holder.imageView, file, mWidth, mHeight, true)
                        .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

            } else {
                // No valid file available; use a placeholder.
                holder.imageView.setImageResource(R.drawable.ic_image);
                // and queue a request for it.
                if (!mDismissing) {
                    try {
                        mModel.fetchGalleryImage(edition.isbn);

                    } catch (@NonNull final RejectedExecutionException e) {
                        // some books have a LOT of editions... Dr. Asimov
                        if (BuildConfig.DEBUG /* always */) {
                            Log.d(TAG, "onBindViewHolder"
                                       + "|isbn=" + edition.isbn
                                       + "Exception msg=" + e.getMessage());
                        }
                    }
                }
            }

            holder.imageView.setOnClickListener(v -> setSwitcherImage(edition));
        }

        @Override
        public int getItemCount() {
            return mEditions.size();
        }
    }
}
