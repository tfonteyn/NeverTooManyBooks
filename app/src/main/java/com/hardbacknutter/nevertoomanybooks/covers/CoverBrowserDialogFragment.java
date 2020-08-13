/*
 * @Copyright 2020 HardBackNutter
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

import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.Collection;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogCoverBrowserBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.dialogs.BaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEditionsTask;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookViewModel;

/**
 * Displays and manages a cover image browser in a dialog, allowing the user to select
 * an image from a list to use as the (new) book cover image.
 * <p>
 * Displays gallery images using {@link CoverBrowserViewModel#getGalleryDisplayExecutor()}.
 * Displays preview image on the UI thread.
 * <p>
 * The progress bar is visible while fetching the edition list and the selected image.
 * It's not visible while gallery pictures are loading.
 * <p>
 * ENHANCE: allow configuring search-sites on the fly
 */
public class CoverBrowserDialogFragment
        extends BaseDialogFragment {

    /** Log tag. */
    public static final String TAG = "CoverBrowserFragment";
    public static final String REQUEST_KEY = TAG + ":rk";

    /** The adapter for the horizontal scrolling covers list. */
    @Nullable
    private GalleryAdapter mGalleryAdapter;

    /** The max width to be used for the preview image. */
    private int mPreviewMaxWidth;
    /** The max height to be used for the preview image. */
    private int mPreviewMaxHeight;

    /** The book. Must be in the Activity scope. */
    @SuppressWarnings("FieldCanBeLocal")
    private BookViewModel mBookViewModel;

    /** The ViewModel. */
    private CoverBrowserViewModel mModel;
    /** Editions. */
    private SearchEditionsTask mSearchEditionsTask;

    /** View Binding. */
    private DialogCoverBrowserBinding mVb;

    /**
     * No-arg constructor for OS use.
     */
    public CoverBrowserDialogFragment() {
        super(R.layout.dialog_cover_browser);
    }

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
     * ENHANCE: pass in a {@link Site.Type#Covers} list / set it on the fly.
     */
    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final int scalePreview = getResources().getInteger(R.integer.cover_scale_browser_preview);
        //noinspection ConstantConditions
        final int longestSide = ImageScale.toPixels(getContext(), scalePreview);
        mPreviewMaxWidth = longestSide;
        mPreviewMaxHeight = longestSide;
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVb = DialogCoverBrowserBinding.bind(view);

        //noinspection ConstantConditions
        mBookViewModel = new ViewModelProvider(getActivity()).get(BookViewModel.class);
        //noinspection ConstantConditions
        mBookViewModel.init(getContext(), getArguments());

        mVb.toolbar.setSubtitle(mBookViewModel.getBook().getTitle());
        mVb.toolbar.setNavigationOnClickListener(v -> dismiss());

        // LayoutManager is set in the layout xml
        final LinearLayoutManager galleryLM = (LinearLayoutManager) mVb.gallery.getLayoutManager();
        Objects.requireNonNull(galleryLM, ErrorMsg.NULL_LAYOUT_MANAGER);
        mVb.gallery.addItemDecoration(
                new DividerItemDecoration(getContext(), galleryLM.getOrientation()));
        mGalleryAdapter = new GalleryAdapter();
        mVb.gallery.setAdapter(mGalleryAdapter);

        // When the preview image is clicked, send the fileSpec back to the caller and terminate.
        mVb.preview.setOnClickListener(v -> {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
                Log.d(TAG, "preview.onClick|filePath=" + mModel.getSelectedFileAbsPath());
            }

            if (mModel.getSelectedFileAbsPath() != null) {
                OnResultListener.sendResult(this, REQUEST_KEY, mModel.getImageIndex(),
                                            mModel.getSelectedFileAbsPath());
            }
            // close the CoverBrowserDialogFragment
            dismiss();
        });


        mSearchEditionsTask = new ViewModelProvider(this).get(SearchEditionsTask.class);
        // dismiss silently
        mSearchEditionsTask.onCancelled().observe(getViewLifecycleOwner(), message -> dismiss());
        // the task throws no exceptions; but paranoia... dismiss silently is fine
        mSearchEditionsTask.onFailure().observe(getViewLifecycleOwner(), message -> dismiss());
        mSearchEditionsTask.onFinished().observe(getViewLifecycleOwner(), this::showGallery);

        mModel = new ViewModelProvider(this).get(CoverBrowserViewModel.class);
        mModel.init(requireArguments());
        mModel.onGalleryImage().observe(getViewLifecycleOwner(), this::setGalleryImage);
        mModel.onSelectedImage().observe(getViewLifecycleOwner(), this::setSelectedImage);
        mModel.onShowGalleryProgress().observe(getViewLifecycleOwner(), show ->
                mVb.progressBar.setVisibility(show ? View.VISIBLE : View.INVISIBLE));
    }

    @Override
    public void onCancel(@NonNull final DialogInterface dialog) {
        mModel.cancelAllTasks();
        super.onCancel(dialog);
    }

    @Override
    public void onResume() {
        super.onResume();
        // if the task is NOT already running and we have no editions loaded before
        if (!mSearchEditionsTask.isRunning()) {
            if (mModel.getEditions().isEmpty()) {
                // start the task
                mVb.statusMessage.setText(R.string.progress_msg_searching_editions);
                mVb.progressBar.setVisibility(View.VISIBLE);
                mSearchEditionsTask.startTask(mModel.getBaseIsbn());
            }
        }

        // If currently not shown, set a reasonable size for the preview image
        // so the progress overlay will be shown in the correct position
        if (mVb.preview.getVisibility() != View.VISIBLE) {
            final ViewGroup.LayoutParams previewLp = mVb.preview.getLayoutParams();
            previewLp.width = (int) (mPreviewMaxHeight * ImageUtils.HW_RATIO);
            previewLp.height = mPreviewMaxHeight;
            mVb.preview.setLayoutParams(previewLp);
        }
    }

    /**
     * Show the user a selection of other covers and allow selection of a replacement.
     *
     * @param message the result of {@link SearchEditionsTask}
     */
    private void showGallery(@NonNull final FinishedMessage<Collection<String>> message) {
        Objects.requireNonNull(mGalleryAdapter, ErrorMsg.NULL_GALLERY_ADAPTER);

        if (message.isNewEvent()) {
            if (message.result == null || message.result.isEmpty()) {
                mVb.progressBar.setVisibility(View.INVISIBLE);
                mVb.statusMessage.setText(R.string.warning_no_editions);
                mVb.statusMessage.postDelayed(this::dismiss, BaseActivity.ERROR_DELAY_MS);
                return;
            }

            // set the list and trigger the adapter
            mModel.setEditions(message.result);
            mGalleryAdapter.notifyDataSetChanged();

            // Show help message
            mVb.statusMessage.setText(R.string.txt_tap_on_thumb);
        }
    }

    /**
     * Display the given image in the gallery View.
     * If it's invalid in any way, the placeholder/edition will be removed.
     * <p>
     * (Dev note: we should do the non-view processing in the model but having it here
     * makes it uniform with {@link #setSelectedImage}.)
     *
     * @param imageFileInfo to display
     */
    private void setGalleryImage(@Nullable final ImageFileInfo imageFileInfo) {
        Objects.requireNonNull(mGalleryAdapter, ErrorMsg.NULL_GALLERY_ADAPTER);

        final int editionIndex;
        if (imageFileInfo != null) {
            editionIndex = mModel.getEditions().indexOf(imageFileInfo.isbn);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
                Log.d(TAG, "setGalleryImage"
                           + "|editionIndex=" + editionIndex
                           + "|" + imageFileInfo);
            }
        } else {
            editionIndex = -1;
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
            mModel.getEditions().remove(editionIndex);
            mGalleryAdapter.notifyItemRemoved(editionIndex);
        }

        // if none left, dismiss.
        if (mGalleryAdapter.getItemCount() == 0) {
            mVb.progressBar.setVisibility(View.INVISIBLE);
            mVb.statusMessage.setText(R.string.warning_image_not_found);
            mVb.statusMessage.postDelayed(this::dismiss, BaseActivity.ERROR_DELAY_MS);
        }
    }

    /**
     * The user clicked an image in the gallery.
     * Display the given file in the preview View.
     * Starts a task to fetch a large(r) image if needed.
     *
     * @param imageFileInfo to use
     */
    private void onGalleryImageSelected(@NonNull final ImageFileInfo imageFileInfo) {
        final File file = imageFileInfo.getFile();
        // sanity check
        if (file != null) {
            if (ImageFileInfo.Size.Large.equals(imageFileInfo.size)) {
                // the gallery image IS a valid large image, so just display it
                setSelectedImage(imageFileInfo);

            } else {
                mVb.preview.setVisibility(View.INVISIBLE);
                mVb.previewProgressBar.setVisibility(View.VISIBLE);

                // start a task to fetch a larger image
                mModel.fetchSelectedImage(imageFileInfo);
            }
        }
    }

    /**
     * Display the given image in the preview View.
     *
     * @param imageFileInfo to display
     */
    private void setSelectedImage(@Nullable final ImageFileInfo imageFileInfo) {
        // Always reset the preview and hide the progress bar
        mModel.setSelectedFile(null);
        mVb.preview.setVisibility(View.INVISIBLE);
        mVb.previewProgressBar.setVisibility(View.INVISIBLE);

        if (imageFileInfo != null) {
            final File file = imageFileInfo.getFile();
            if (ImageUtils.isFileGood(file, false)) {
                new ImageLoader(mVb.preview, file, mPreviewMaxWidth, mPreviewMaxHeight, () -> {
                    // Set AFTER it was successfully loaded and displayed for maximum reliability
                    mModel.setSelectedFile(file);
                    mVb.preview.setVisibility(View.VISIBLE);
                    mVb.statusMessage.setText(R.string.txt_tap_on_image_to_select);
                })
                        // use the default executor which is free right now
                        .execute();
                return;
            }
        }

        Snackbar.make(mVb.preview, R.string.warning_image_not_found,
                      Snackbar.LENGTH_LONG).show();
        mVb.statusMessage.setText(R.string.txt_tap_on_thumb);

    }

    public interface OnResultListener
            extends FragmentResultListener {

        /* private. */ String COVER_INDEX = "cIdx";
        /* private. */ String COVER_FILE_SPEC = "fileSpec";

        static void sendResult(@NonNull final Fragment fragment,
                               @NonNull final String requestKey,
                               @IntRange(from = 0) final int cIdx,
                               @NonNull final String fileSpec) {
            final Bundle result = new Bundle();
            result.putInt(COVER_INDEX, cIdx);
            result.putString(COVER_FILE_SPEC, fileSpec);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        @Override
        default void onFragmentResult(@NonNull final String requestKey,
                                      @NonNull final Bundle result) {
            onResult(result.getInt(COVER_INDEX),
                     Objects.requireNonNull(result.getString(COVER_FILE_SPEC)));
        }

        /**
         * Callback handler with the user's selection.
         *
         * @param cIdx     cover index as passed in
         * @param fileSpec for the selected file
         */
        void onResult(@IntRange(from = 0) int cIdx,
                      @NonNull String fileSpec);
    }

    /**
     * Row ViewHolder for {@link GalleryAdapter}.
     */
    private static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        final ImageView imageView;
        @NonNull
        final TextView siteView;

        Holder(@NonNull final View itemView,
               final int maxWidth,
               final int maxHeight) {
            super(itemView);

            siteView = itemView.findViewById(R.id.lbl_site);
            imageView = itemView.findViewById(R.id.coverImage0);
            imageView.getLayoutParams().width = maxWidth;
            imageView.getLayoutParams().height = maxHeight;
        }
    }

    private class GalleryAdapter
            extends RecyclerView.Adapter<Holder> {

        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        private static final String TAG = "GalleryAdapter";

        /** A single image fixed width. */
        private final int mMaxWidth;
        /** A single image fixed height. */
        private final int mMaxHeight;

        /**
         * Constructor.
         */
        @SuppressWarnings("SameParameterValue")
        GalleryAdapter() {
            final int scale = getResources().getInteger(R.integer.cover_scale_browser_gallery);
            //noinspection ConstantConditions
            final int longestSide = ImageScale.toPixels(getContext(), scale);
            mMaxWidth = longestSide;
            mMaxHeight = longestSide;
        }

        @Override
        @NonNull
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final View view = getLayoutInflater()
                    .inflate(R.layout.row_cover_browser_gallery, parent, false);
            return new Holder(view, mMaxWidth, mMaxHeight);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            if (mModel.isCancelled()) {
                return;
            }

            final String isbn = mModel.getEditions().get(position);
            final ImageFileInfo imageFileInfo = mModel.getFileInfo(isbn);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
                Log.d(TAG, "onBindViewHolder"
                           + "|position=" + position
                           + "|imageFileInfo=" + imageFileInfo);
            }

            if (imageFileInfo == null) {
                // not in the cache,; use a placeholder but preserve the available space
                ImageUtils.setPlaceholder(holder.imageView, R.drawable.ic_image, 0,
                                          (int) (mMaxHeight * ImageUtils.HW_RATIO), mMaxHeight);
                // and queue a request for it.
                mModel.fetchGalleryImage(isbn);
                holder.siteView.setText("");

            } else {
                // check if it's good
                final File file = imageFileInfo.getFile();
                if (ImageUtils.isFileGood(file, false)) {
                    // YES, load it into the view.
                    new ImageLoader(holder.imageView, file, mMaxWidth, mMaxHeight, null)
                            .executeOnExecutor(mModel.getGalleryDisplayExecutor());

                    holder.imageView.setOnClickListener(v -> onGalleryImageSelected(imageFileInfo));

                    //noinspection ConstantConditions
                    holder.siteView.setText(
                            SearchEngineRegistry.getByEngineId(imageFileInfo.engineId)
                                                .getNameResId());

                } else {
                    // no file. Theoretically we should not get here,
                    // as a failed search should have removed the isbn from the edition list,
                    // but race-conditions + paranoia...
                    ImageUtils.setPlaceholder(holder.imageView, R.drawable.ic_broken_image, 0,
                                              (int) (mMaxHeight * ImageUtils.HW_RATIO), mMaxHeight);
                    holder.siteView.setText("");
                }
            }
        }

        @Override
        public int getItemCount() {
            return mModel.getEditions().size();
        }
    }
}
