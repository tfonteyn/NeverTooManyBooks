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
package com.hardbacknutter.nevertoomanybooks.covers;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogCoverBrowserBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.searches.SiteList;

/**
 * Displays and manages a cover image browser in a dialog, allowing the user to select
 * an image from a list to use as the (new) book cover image.
 * <p>
 * Displays gallery images using {@link CoverBrowserViewModel#getPriorityExecutor()}.
 * Displays preview image on the UI thread.
 *
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

    /** List of ISBN numbers for alternative editions. The base list for the gallery adapter. */
    @NonNull
    private final ArrayList<String> mEditions = new ArrayList<>();

    /** The adapter for the horizontal scrolling covers list. */
    @Nullable
    private GalleryAdapter mGalleryAdapter;

    /** The max height (and width) to be used for the preview image. */
    private int mPreviewMaxSize;

    /** Indicates cancel has been requested. */
    private boolean mIsCancelled;

    /** The ViewModel. */
    private CoverBrowserViewModel mModel;

    /** View Binding. */
    private DialogCoverBrowserBinding mVb;

    /** Where to send the result. */
    @Nullable
    private WeakReference<OnFileSelected> mListener;

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
        final Bundle args = new Bundle(2);
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

        final int scalePreview = getResources().getInteger(R.integer.covers_browser_preview);
        //noinspection ConstantConditions
        mPreviewMaxSize = ImageScale.getSize(getContext(), scalePreview);

        final int scaleGallery = getResources().getInteger(R.integer.covers_browser_gallery);
        mGalleryAdapter = new GalleryAdapter(scaleGallery);

        mModel = new ViewModelProvider(this).get(CoverBrowserViewModel.class);
        mModel.init(getContext(), requireArguments());

        mModel.onEditionsLoaded().observe(this, this::showGallery);
        mModel.onGalleryImage().observe(this, this::setGalleryImage);
        mModel.onSelectedImage().observe(this, imageFileInfo ->
                setSelectedImage(imageFileInfo.getFile()));
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

        // When the preview image is clicked, send the fileSpec back to the caller and terminate.
        mVb.preview.setOnClickListener(v -> {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
                Log.d(TAG, "preview.onClick|filePath=" + mModel.getSelectedFilePath());
            }
            if (mModel.getSelectedFilePath() != null) {
                if (mListener != null && mListener.get() != null) {
                    mListener.get().onFileSelected(mModel.getImageIndex(),
                                                   mModel.getSelectedFilePath());
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

        return new MaterialAlertDialogBuilder(getContext())
                .setView(mVb.getRoot())
                .create();
    }

    @Override
    public void onCancel(@NonNull final DialogInterface dialog) {
        // prevent new tasks being started.
        mIsCancelled = true;
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
     * <p>
     * Note that after e.g. a screen rotation, we get the full list again.
     * This will re-trigger the downloading of gallery images but
     * we'll only retry images we don't yet have.
     *
     * @param isbnList the list to use.
     */
    private void showGallery(@NonNull final Collection<String> isbnList) {
        mEditions.clear();
        mEditions.addAll(isbnList);

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
     * @param imageFileInfo the file we got, if any
     */
    private void setGalleryImage(@NonNull final ImageFileInfo imageFileInfo) {

        Objects.requireNonNull(mGalleryAdapter, ErrorMsg.NULL_GALLERY_ADAPTER);

        final int editionIndex = mEditions.indexOf(imageFileInfo.isbn);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
            Log.d(TAG, "setGalleryImage"
                       + "|imageFileInfo=" + imageFileInfo
                       + "|editionIndex=" + editionIndex);
        }

        if (editionIndex >= 0) {
            final File tmpFile = imageFileInfo.getFile();
            if (tmpFile != null && tmpFile.exists()) {
                tmpFile.deleteOnExit();
                // Tell the adapter to refresh the entry.
                // It will get the image from the file-manager.
                mGalleryAdapter.notifyItemChanged(editionIndex);
                return;
            }

            // No file. Remove the defunct view from the gallery
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
     * Display the given file in the preview View. Starts a task if needed.
     *
     * @param imageFileInfo to use
     */
    private void setSelectedImage(@NonNull final ImageFileInfo imageFileInfo) {
        final File file = imageFileInfo.getFile();
        // sanity check
        if (file != null) {
            if (ImageFileInfo.Size.Large.equals(imageFileInfo.size)) {
                // we already have a valid large image, so just display it
                setSelectedImage(file);

            } else {
                mVb.preview.setVisibility(View.INVISIBLE);
                mVb.statusMessage.setText(R.string.progress_msg_loading);
                // start a task to fetch a larger image
                mModel.fetchSelectedImage(imageFileInfo);
            }
        }
    }

    /**
     * Display the given file in the preview View.
     *
     * @param file to display
     */
    private void setSelectedImage(@Nullable final File file) {
        // Reset the preview
        mModel.setSelectedFilePath(null);
        mVb.preview.setVisibility(View.INVISIBLE);

        if (ImageUtils.isFileGood(file)) {
            new ImageLoader(mVb.preview, file, mPreviewMaxSize, mPreviewMaxSize, () -> {
                mModel.setSelectedFilePath(file.getAbsolutePath());
                mVb.preview.setVisibility(View.VISIBLE);
                mVb.statusMessage.setText(R.string.txt_tap_on_image_to_select);
            })
                    .executeOnExecutor(mModel.getPriorityExecutor());

        } else {
            Snackbar.make(mVb.preview, R.string.warning_cover_not_found,
                          Snackbar.LENGTH_LONG).show();
            mVb.statusMessage.setText(R.string.txt_tap_on_thumb);
        }
    }

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    public void setListener(@NonNull final OnFileSelected listener) {
        mListener = new WeakReference<>(listener);
    }

    public interface OnFileSelected {

        void onFileSelected(@IntRange(from = 0) int cIdx,
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
        GalleryAdapter(@ImageScale.Scale final int scale) {
            //noinspection ConstantConditions
            final int maxSize = ImageScale.getSize(getContext(), scale);
            mHeight = maxSize;
            mWidth = maxSize;
        }

        @Override
        @NonNull
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final ImageView view = (ImageView) getLayoutInflater()
                    .inflate(R.layout.row_cover_browser_gallery, parent, false);
            view.getLayoutParams().width = mWidth;
            view.getLayoutParams().height = mHeight;
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            if (mIsCancelled) {
                return;
            }

            final String isbn = mEditions.get(position);

            // Get the image file based on the isbn; try the sizes in order as specified here.
            final ImageFileInfo imageFileInfo =
                    mModel.getFileInfo(isbn, ImageFileInfo.Size.SMALL_FIRST);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
                Log.d(TAG, "onBindViewHolder|imageFileInfo=" + imageFileInfo);
            }

            final File file = imageFileInfo.getFile();
            if (ImageUtils.isFileGood(file)) {
                // we have a file, load it into the view.
                new ImageLoader(holder.imageView, file, mWidth, mHeight, null)
                        .executeOnExecutor(mModel.getPriorityExecutor());

            } else {
                // No valid file available; use a placeholder.
                ImageUtils.setPlaceholder(holder.imageView, R.drawable.ic_image, 0, mHeight);
                try {
                    // and queue a request for it.
                    mModel.fetchGalleryImage(isbn);

                } catch (@NonNull final RejectedExecutionException e) {
                    // some books have a LOT of editions... Dr. Asimov
                    if (BuildConfig.DEBUG /* always */) {
                        Log.d(TAG, "onBindViewHolder"
                                   + "|isbn=" + isbn
                                   + "Exception msg=" + e.getMessage());
                    }
                }
            }

            holder.imageView.setOnClickListener(v -> setSelectedImage(imageFileInfo));
        }

        @Override
        public int getItemCount() {
            return mEditions.size();
        }
    }
}
