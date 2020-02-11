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

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.SiteList;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.viewmodels.CoverBrowserViewModel;

/**
 * Displays and manages a cover image browser in a dialog, allowing the user to select
 * an image from a list to use as the (new) book cover image.
 * <p>
 * Will survive a rotation, but not a killed activity.
 * Uses setTargetFragment/getTargetFragment and returns result to {@link Fragment#onActivityResult}.
 * <p>
 * ENHANCE: allow configuring search-sites on the fly
 * ENHANCE: currently supports only a front-cover. Add back-cover support
 */
public class CoverBrowserFragment
        extends DialogFragment {

    public static final String TAG = "CoverBrowserFragment";

    /** Populated by {@link #showGallery} AND savedInstanceState. */
    @NonNull
    private final ArrayList<String> mAlternativeEditions = new ArrayList<>();
    @Nullable
    private GalleryAdapter mGalleryAdapter;
    /** Indicates dismiss() has been requested. */
    private boolean mDismissing;
    /** The switcher will be used to display larger versions. */
    private ImageSwitcher mImageSwitcherView;

    /** Prior to showing a preview, the switcher can show text updates. */
    private TextView mStatusTextView;
    /** The ViewModel. */
    private CoverBrowserViewModel mModel;
    /** Absolute file path of the selected image. */
    @Nullable
    private String mSelectedFileSpec;

    /**
     * Constructor.
     *
     * @param isbn ISBN of book
     * @param cIdx 0..n image index
     *
     * @return the instance
     */
    @NonNull
    public static CoverBrowserFragment newInstance(@NonNull final String isbn,
                                                   final int cIdx) {
        final CoverBrowserFragment frag = new CoverBrowserFragment();
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

        Objects.requireNonNull(getTargetFragment(), ErrorMsg.NO_TARGET_FRAGMENT_SET);

        mGalleryAdapter = new GalleryAdapter(ImageUtils.SCALE_MEDIUM);

        mModel = new ViewModelProvider(this).get(CoverBrowserViewModel.class);
        //noinspection ConstantConditions
        mModel.init(getContext(), requireArguments());

        mModel.getEditions().observe(this, this::showGallery);
        mModel.getGalleryImage().observe(this, this::setGalleryImage);
        mModel.getSelectedImage().observe(this, this::setSwitcherImage);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        // Reminder: *always* use the activity inflater here.
        //noinspection ConstantConditions
        final LayoutInflater layoutInflater = getActivity().getLayoutInflater();
        final View root = layoutInflater.inflate(R.layout.dialog_cover_browser, null);

        // keep the user informed.
        mStatusTextView = root.findViewById(R.id.statusMessage);

        // The gallery displays a list of images, one for each edition.
        final RecyclerView listView = root.findViewById(R.id.gallery);
        final LinearLayoutManager galleryLayoutManager = new LinearLayoutManager(getContext());
        galleryLayoutManager.setOrientation(RecyclerView.HORIZONTAL);
        listView.setLayoutManager(galleryLayoutManager);
        //noinspection ConstantConditions
        listView.addItemDecoration(
                new DividerItemDecoration(getContext(), galleryLayoutManager.getOrientation()));
        listView.setAdapter(mGalleryAdapter);

        // setup the switcher.
        mImageSwitcherView = root.findViewById(R.id.switcher);
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
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
                Log.d(TAG, "mImageSwitcherView.onClick|fileSpec=" + mSelectedFileSpec);
            }
            if (mSelectedFileSpec != null) {
                int cIdx = mModel.getImageIndex();
                Intent resultData = new Intent()
                        .putExtra(UniqueId.BKEY_FILE_SPEC[cIdx], mSelectedFileSpec);
                //noinspection ConstantConditions
                getTargetFragment()
                        .onActivityResult(getTargetRequestCode(), Activity.RESULT_OK, resultData);
            }
            // close the CoverBrowserFragment
            dismiss();
        });

        //noinspection ConstantConditions
        return new MaterialAlertDialogBuilder(getContext())
                .setView(root)
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
        if (mAlternativeEditions.isEmpty()) {
            mStatusTextView.setText(R.string.progress_msg_finding_editions);
            mModel.fetchEditions();
        }
    }

    /**
     * Called with the results from the edition search.
     * Show the user a selection of other covers and allow selection of a replacement.
     *
     * @param editions the list to use.
     */
    private void showGallery(@Nullable final Collection<String> editions) {
        mAlternativeEditions.clear();
        if (editions != null) {
            mAlternativeEditions.addAll(editions);
        }

        if (mAlternativeEditions.isEmpty()) {
            Snackbar.make(mStatusTextView, R.string.warning_no_editions, Snackbar.LENGTH_LONG)
                    .show();
            dismiss();
            return;
        }

        Objects.requireNonNull(mGalleryAdapter, ErrorMsg.NULL_GALLERY_ADAPTER);
        mGalleryAdapter.notifyDataSetChanged();

        // Show help message
        mStatusTextView.setText(R.string.info_tap_on_thumb);
    }

    /**
     * handle result from the {@link CoverBrowserViewModel} GetGalleryImageTask.
     * <p>
     * TODO: pass the data via a MutableLiveData object and use a local FIFO queue.
     *
     * @param fileInfo the file we got, if any
     */
    private void setGalleryImage(@NonNull final CoverBrowserViewModel.FileInfo fileInfo) {
        Objects.requireNonNull(mGalleryAdapter, ErrorMsg.NULL_GALLERY_ADAPTER);

        final int editionIndex = mAlternativeEditions.indexOf(fileInfo.isbn);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
            Log.d(TAG, "setGalleryImage"
                       + "|fileInfo=" + fileInfo);
        }

        if (fileInfo.fileSpec != null && !fileInfo.fileSpec.isEmpty()) {
            // Load the temp file and apply to the gallery view
            final File tmpFile = new File(fileInfo.fileSpec);
            if (tmpFile.exists()) {
                tmpFile.deleteOnExit();
                mGalleryAdapter.notifyItemChanged(editionIndex);
                return;
            }
        }

        // Remove the defunct view from the gallery
        if (editionIndex >= 0) {
            mAlternativeEditions.remove(fileInfo.isbn);
            mGalleryAdapter.notifyItemRemoved(editionIndex);
        }

        // and if none left, dismiss.
        if (mGalleryAdapter.getItemCount() == 0) {
            Snackbar.make(mStatusTextView, R.string.warning_cover_not_found, Snackbar.LENGTH_LONG)
                    .show();
            dismiss();
        }
    }

    /**
     * handle result from the {@link CoverBrowserViewModel} GetSwitcherImageTask.
     *
     * @param fileInfo the file we got.
     */
    private void setSwitcherImage(@NonNull final CoverBrowserViewModel.FileInfo fileInfo) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
            Log.d(TAG, "setSwitcherImage"
                       + "|fileInfo=" + fileInfo);
        }

        if (fileInfo.fileSpec != null && !fileInfo.fileSpec.isEmpty()) {
            // Load the temp file and apply to the switcher
            final File file = new File(fileInfo.fileSpec);
            if (file.length() > ImageUtils.MIN_IMAGE_FILE_SIZE) {
                // store the path. It will be send back to the caller.
                mSelectedFileSpec = file.getAbsolutePath();

                //noinspection ConstantConditions
                @Nullable
                final Bitmap bm = ImageUtils.createScaledBitmap(getContext(), file,
                                                                ImageUtils.SCALE_X_LARGE);
                if (bm != null) {
                    mImageSwitcherView.setImageDrawable(new BitmapDrawable(getResources(), bm));
                    mImageSwitcherView.setVisibility(View.VISIBLE);
                    mStatusTextView.setText(R.string.info_tap_on_image_to_select);
                    return;
                }
            }
        }

        // Reset the switcher and info the user.
        mImageSwitcherView.setVisibility(View.GONE);
        Snackbar.make(mImageSwitcherView, R.string.warning_cover_not_found, Snackbar.LENGTH_LONG)
                .show();
        mStatusTextView.setText(R.string.info_tap_on_thumb);
    }

    /** Stores and recycles views as they are scrolled off screen. */
    private static class Holder
            extends RecyclerView.ViewHolder {

        /** Keep an extra copy, to avoid casting. */
        @NonNull
        final ImageView imageView;

        @Nullable
        CoverBrowserViewModel.FileInfo fileInfo;

        Holder(@NonNull final ImageView itemView) {
            super(itemView);
            imageView = itemView;
        }
    }

    class GalleryAdapter
            extends RecyclerView.Adapter<Holder> {

        private final int mWidth;
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
            imageView.setBackgroundResource(R.drawable.border);
            return new Holder(imageView);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            // fetch an image based on the isbn
            final String isbn = mAlternativeEditions.get(position);

            // Get the image file; try the sizes in order as specified here.
            holder.fileInfo = mModel.getFileManager()
                                    .getFile(isbn,
                                             SearchEngine.CoverByIsbn.ImageSize.Small,
                                             SearchEngine.CoverByIsbn.ImageSize.Medium,
                                             SearchEngine.CoverByIsbn.ImageSize.Large);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
                Log.d(TAG, "onBindViewHolder"
                           + "|fileInfo=" + holder.fileInfo);
            }

            File imageFile = null;
            if (holder.fileInfo.fileSpec != null && !holder.fileInfo.fileSpec.isEmpty()) {
                imageFile = new File(holder.fileInfo.fileSpec);
            }

            // See if file is present.
            if (imageFile != null && imageFile.length() > ImageUtils.MIN_IMAGE_FILE_SIZE) {
                new ImageUtils.ImageLoader(holder.imageView, imageFile, mWidth, mHeight, true)
                        .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                // Not present; use a placeholder.
                holder.imageView.setImageResource(R.drawable.ic_image);
                // and queue a request for it.
                if (!mDismissing) {
                    try {
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
            }

            // image from gallery clicked -> load it into the larger preview (imageSwitcher).
            holder.imageView.setOnClickListener(v -> {
                // check if we actually have a preview in the gallery
                final String fileSpec = holder.fileInfo.fileSpec;
                if (fileSpec != null && !fileSpec.isEmpty()) {
                    //noinspection ConstantConditions
                    if (holder.fileInfo.size.equals(SearchEngine.CoverByIsbn.ImageSize.Large)) {
                        // we know the file is valid, so just display it
                        new ImageUtils.ImageLoader(holder.imageView, new File(fileSpec),
                                                   mWidth, mHeight, true)
                                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    } else {
                        // check for a larger image.

                        // set & show the placeholder.
                        mImageSwitcherView.setImageResource(R.drawable.ic_image);
                        mImageSwitcherView.setVisibility(View.VISIBLE);
                        mStatusTextView.setText(R.string.progress_msg_loading);
                        // start a task to fetch the image
                        mModel.fetchSelectedImage(holder.fileInfo);
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return mAlternativeEditions.size();
        }
    }
}
