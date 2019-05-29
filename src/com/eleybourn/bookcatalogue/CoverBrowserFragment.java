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
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.SearchEngine;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;
import com.eleybourn.bookcatalogue.viewmodels.CoverBrowserViewModel;

/**
 * Displays and manages a cover image browser in a dialog, allowing the user to select
 * an image from a list to use as the (new) book cover image.
 * <p>
 * Will survive a rotation, but not a killed activity.
 * Uses setTargetFragment/getTargetFragment and returns result to {@link Fragment#onActivityResult}.
 * <p>
 * ENHANCE: allow configuring search-sites on the fly
 * ENHANCE: For each edition, try to get TWO images from a different site each.
 */
public class CoverBrowserFragment
        extends DialogFragment {

    /** Fragment manager tag. */
    public static final String TAG = CoverBrowserFragment.class.getSimpleName();

    /** {@code ArrayList<String>} with edition isbn's. */
    private static final String BKEY_EDITION_LIST = TAG + ":editions";
    private static final String BKEY_SWITCHER_FILE = TAG + ":coverImage";

    /** Cache the calculated standard sizes. */
    private ImageUtils.DisplaySizes mDisplaySizes;

    /** Indicates dismiss() has been requested. */
    private boolean mDismissing;

    /** The gallery displays a list of images, one for each edition. */
    private RecyclerView mListView;

    @Nullable
    private GalleryAdapter mGalleryAdapter;

    /** The switcher will be used to display larger versions. */
    private ImageSwitcher mImageSwitcherView;

    /** Prior to showing a preview, the switcher can show text updates. */
    private TextView mStatusTextView;

    private CoverBrowserViewModel mModel;

    /** Populated by {@link #showGallery(ArrayList)} AND savedInstanceState. */
    @Nullable
    private ArrayList<String> mAlternativeEditions;
    /** Populated by {@link #setSwitcherImage(String)} AND savedInstanceState. */
    @Nullable
    private String mSwitcherImageFileSpec;

    /**
     * WARNING: LibraryThing is in fact the only site searched for alternative editions!
     * See {@link CoverBrowserViewModel.GetEditionsTask}.
     * <p>
     * Images themselves are searched from the 'searchSites' as usual.
     *
     * @param isbn        ISBN of book
     * @param searchSites bitmask with sites to search, see {@link SearchSites#SEARCH_ALL}.
     *
     * @return the instance
     */
    @NonNull
    public static CoverBrowserFragment newInstance(@NonNull final String isbn,
                                                   final int searchSites) {

        if (LibraryThingManager.noKey()) {
            throw new IllegalStateException("LibraryThing Key must be tested before calling this");
        }

        CoverBrowserFragment frag = new CoverBrowserFragment();
        Bundle args = new Bundle();
        args.putInt(UniqueId.BKEY_SEARCH_SITES, searchSites);
        args.putString(DBDefinitions.KEY_ISBN, isbn);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mModel = ViewModelProviders.of(this).get(CoverBrowserViewModel.class);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final Fragment targetFragment = Objects.requireNonNull(getTargetFragment());

        Bundle args = requireArguments();
        String isbn = Objects.requireNonNull(args.getString(DBDefinitions.KEY_ISBN));
        int initialSearchSites = args.getInt(UniqueId.BKEY_SEARCH_SITES, SearchSites.SEARCH_ALL);

        if (savedInstanceState != null) {
            mAlternativeEditions = savedInstanceState.getStringArrayList(BKEY_EDITION_LIST);
            mSwitcherImageFileSpec = savedInstanceState.getString(BKEY_SWITCHER_FILE);
        }

        mModel.init(isbn, initialSearchSites);
        mModel.getEditions().observe(this, this::showGallery);
        mModel.getSwitcherImageFileSpec().observe(this, this::setSwitcherImage);

        @SuppressWarnings("ConstantConditions")
        View root = getActivity().getLayoutInflater().inflate(R.layout.dialog_cover_browser, null);

        // keep the user informed.
        mStatusTextView = root.findViewById(R.id.statusMessage);

        //noinspection ConstantConditions
        mDisplaySizes = ImageUtils.getDisplaySizes(getContext());

        // setup the gallery.
        mListView = root.findViewById(R.id.gallery);
        LinearLayoutManager galleryLayoutManager = new LinearLayoutManager(getContext());
        galleryLayoutManager.setOrientation(RecyclerView.HORIZONTAL);
        mListView.setLayoutManager(galleryLayoutManager);
        mListView.addItemDecoration(
                new DividerItemDecoration(getContext(), galleryLayoutManager.getOrientation()));

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
            // When the image was loaded, the filename was stored in the tag.
            String fileSpec = (String) mImageSwitcherView.getTag(R.id.TAG_ITEM);
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
                Logger.debug(CoverBrowserFragment.this, "mImageSwitcherView.onClick",
                             "fileSpec=" + fileSpec);
            }
            if (fileSpec != null) {
                Intent data = new Intent().putExtra(UniqueId.BKEY_FILE_SPEC, fileSpec);
                targetFragment.onActivityResult(getTargetRequestCode(),
                                                Activity.RESULT_OK, data);
            }
            // close the CoverBrowserFragment
            dismiss();
        });

        //noinspection ConstantConditions
        return new AlertDialog.Builder(getContext())
                .setView(root)
                .create();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mAlternativeEditions == null) {
            mStatusTextView.setText(R.string.progress_msg_finding_editions);
            mModel.fetchEditions();

        } else {
            showGallery(mAlternativeEditions);
            if (mSwitcherImageFileSpec != null) {
                setSwitcherImage(mSwitcherImageFileSpec);
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
            Logger.debugExit(this, "onResume");
        }
    }

    @Override
    public void onCancel(@NonNull final DialogInterface dialog) {
        // prevent new tasks being started.
        mDismissing = true;

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
            Logger.debugExit(this, "onCancel");
        }
        super.onCancel(dialog);
    }

    /**
     * Called with the results from the edition search.
     * Show the user a selection of other covers and allow selection of a replacement.
     *
     * @param editions the list to use.
     */
    private void showGallery(@Nullable final ArrayList<String> editions) {
        mAlternativeEditions = editions;

        if (editions == null || editions.isEmpty()) {
            dismiss();
            UserMessage.showUserMessage(mStatusTextView, R.string.warning_no_editions);
            return;
        }

        // Show help message
        mStatusTextView.setText(R.string.info_tap_on_thumb);

        mGalleryAdapter = new GalleryAdapter(mDisplaySizes.small, mDisplaySizes.small);
        mListView.setAdapter(mGalleryAdapter);
    }

    /**
     * handle result from the {@link CoverBrowserViewModel.GetGalleryImageTask}.
     *
     * TODO: pass the data via a MutableLiveData object and use a local FIFO queue.
     *
     * @param task     the task that finished
     * @param position for which we got an image (or can confirm no image was available)
     * @param fileSpec the file we got, or {@code null}.
     */
    public void updateGallery(@NonNull final AsyncTask task,
                              final int position,
                              @Nullable final String fileSpec) {
        mModel.removeTask(task);
        Objects.requireNonNull(mGalleryAdapter);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
            Logger.debug(this, "update",
                         "position=" + position,
                         "fileSpec=" + fileSpec);
        }

        if (fileSpec != null && !fileSpec.isEmpty()) {
            // Load the temp file and apply to the gallery view
            File imageFile = new File(fileSpec);
            if (imageFile.exists()) {
                imageFile.deleteOnExit();
                mGalleryAdapter.notifyItemChanged(position);
                return;
            }
        }

        // Remove the defunct view from the gallery
        // sanity check...
        if (position >= 0) {
            //noinspection ConstantConditions
            mAlternativeEditions.remove(position);
            mGalleryAdapter.notifyItemRemoved(position);
        }

        // and if none left, dismiss.
        if (mGalleryAdapter.getItemCount() == 0) {
            UserMessage.showUserMessage(mStatusTextView, R.string.warning_cover_not_found);
            dismiss();
        }
    }

    /**
     * handle result from the {@link CoverBrowserViewModel.GetSwitcherImageTask}.
     *
     * @param switcherImageFileSpec the file we got.
     */
    private void setSwitcherImage(@Nullable final String switcherImageFileSpec) {
        mSwitcherImageFileSpec = switcherImageFileSpec;

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
            Logger.debug(this, "setSwitcherImage", "fileSpec=" + switcherImageFileSpec);
        }

        if (switcherImageFileSpec != null && !switcherImageFileSpec.isEmpty()) {
            // Load the temp file and apply to he switcher
            File file = new File(switcherImageFileSpec);
            if (file.exists() && file.length() > 100) {

                // store the path. It will be send back to the caller.
                mImageSwitcherView.setTag(R.id.TAG_ITEM, file.getAbsolutePath());

                Bitmap bm = ImageUtils.createScaledBitmap(
                        BitmapFactory.decodeFile(file.getPath()),
                        mDisplaySizes.large, mDisplaySizes.large);

                // ImageSwitcher does not accept a bitmap; wants a Drawable instead.
                mImageSwitcherView.setImageDrawable(new BitmapDrawable(getResources(), bm));

                mImageSwitcherView.setVisibility(View.VISIBLE);
                mStatusTextView.setText(R.string.info_tap_on_image_to_select);
                return;
            }
        }

        // Reset the switcher and info the user.
        mImageSwitcherView.setVisibility(View.GONE);
        UserMessage.showUserMessage(mImageSwitcherView, R.string.warning_cover_not_found);
        mStatusTextView.setText(R.string.info_tap_on_thumb);
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mAlternativeEditions != null && !mAlternativeEditions.isEmpty()) {
            outState.putStringArrayList(BKEY_EDITION_LIST, mAlternativeEditions);
        }
        if (mSwitcherImageFileSpec != null) {
            outState.putString(BKEY_SWITCHER_FILE, mSwitcherImageFileSpec);
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
            Logger.debugExit(this, "onSaveInstanceState", outState);
        }
    }

    /** Stores and recycles views as they are scrolled off screen. */
    private static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        final ImageView imageView;

        Holder(@NonNull final ImageView itemView) {
            super(itemView);
            imageView = itemView;
        }
    }

    public class GalleryAdapter
            extends RecyclerView.Adapter<Holder> {

        private final AtomicInteger debugNewViewCounter = new AtomicInteger();
        private final AtomicInteger debugBindViewCounter = new AtomicInteger();

        private final int mWidth;
        private final int mHeight;

        GalleryAdapter(final int width,
                       final int height) {
            mWidth = width;
            mHeight = height;
        }

        @Override
        @NonNull
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            if (BuildConfig.DEBUG) {
                debugNewViewCounter.incrementAndGet();
                Logger.debug(this, "onCreateViewHolder",
                             "debugNewViewCounter=" + debugNewViewCounter.get(),
                             "viewType=" + viewType);
            }

            ImageView imageView = new ImageView(parent.getContext());
            imageView.setLayoutParams(new ViewGroup.LayoutParams(mWidth, mHeight));
            imageView.setBackgroundResource(R.drawable.border);
            return new Holder(imageView);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            if (BuildConfig.DEBUG) {
                debugBindViewCounter.incrementAndGet();
                Logger.debug(this, "onBindViewHolder",
                             "debugBindViewCounter=" + debugBindViewCounter.get());
            }

            // fetch an image based on the isbn
            @SuppressWarnings("ConstantConditions")
            String isbn = mAlternativeEditions.get(position);

            // Get the image file; try the sizes in order as specified here.
            File imageFile = mModel.getFileManager().getFile(isbn,
                                                             SearchEngine.ImageSizes.SMALL,
                                                             SearchEngine.ImageSizes.MEDIUM,
                                                             SearchEngine.ImageSizes.LARGE);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
                Logger.debug(this, "onBindViewHolder",
                             "position=" + position,
                             "isbn=" + isbn,
                             "fileSpec=" + imageFile);
            }

            // See if file is present.
            if (imageFile != null) {
                ImageUtils.setImageView(holder.imageView, imageFile, mWidth, mHeight, true);

            } else {
                // Not present; use a placeholder.
                holder.imageView.setImageResource(R.drawable.ic_image);
                // and queue a request for it.
                if (!mDismissing) {
                    try {
                        mModel.fetchGalleryImages(CoverBrowserFragment.this, position, isbn);

                    } catch (RejectedExecutionException e) {
                        // some books have a LOT of editions... Dr. Asimov
                        if (BuildConfig.DEBUG /* always */) {
                            Logger.debug(this, "onBindViewHolder",
                                         "position=" + position,
                                         "isbn=" + isbn,
                                         "Exception msg=" + e.getLocalizedMessage());
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
                // start a task to fetch the image
                mModel.fetchSwitcherImage(isbn);
            });
        }

        @Override
        public int getItemCount() {
            //noinspection ConstantConditions
            return mAlternativeEditions.size();
        }
    }
}
