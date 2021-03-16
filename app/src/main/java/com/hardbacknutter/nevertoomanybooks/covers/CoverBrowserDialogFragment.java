/*
 * @Copyright 2018-2021 HardBackNutter
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
import android.content.res.Resources;
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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.Executor;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.FragmentLauncherBase;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogCoverBrowserBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.BaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEditionsTask;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;

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
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";

    /** FragmentResultListener request key to use for our response. */
    private String mRequestKey;

    /** The adapter for the horizontal scrolling covers list. */
    @Nullable
    private GalleryAdapter mGalleryAdapter;

    /** The max width to be used for the preview image. */
    private int mPreviewMaxWidth;
    /** The max height to be used for the preview image. */
    private int mPreviewMaxHeight;

    /** The ViewModel. */
    private CoverBrowserViewModel mVm;

    /** View Binding. */
    private DialogCoverBrowserBinding mVb;

    private ImageViewLoader mPreviewLoader;

    /**
     * No-arg constructor for OS use.
     */
    public CoverBrowserDialogFragment() {
        super(R.layout.dialog_cover_browser);
        setFloatingDialogWidth(R.dimen.floating_dialogs_cover_browser_width);
    }

    /**
     * ENHANCE: pass in a {@link Site.Type#Covers} list / set it on the fly.
     */
    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        mRequestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                             "BKEY_REQUEST_KEY");

        final Resources res = getResources();
        mPreviewMaxWidth = res.getDimensionPixelSize(R.dimen.cover_browser_preview_width);
        mPreviewMaxHeight = res.getDimensionPixelSize(R.dimen.cover_browser_preview_height);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVb = DialogCoverBrowserBinding.bind(view);

        final String bookTitle = Objects.requireNonNull(
                requireArguments().getString(DBKeys.KEY_TITLE));
        mVb.toolbar.setSubtitle(bookTitle);

        mVm = new ViewModelProvider(this).get(CoverBrowserViewModel.class);
        mVm.init(requireArguments());

        // LayoutManager is set in the layout xml
        final LinearLayoutManager galleryLM = Objects.requireNonNull(
                (LinearLayoutManager) mVb.gallery.getLayoutManager(),
                "Missing LinearLayoutManager");
        //noinspection ConstantConditions
        mVb.gallery.addItemDecoration(
                new DividerItemDecoration(getContext(), galleryLM.getOrientation()));
        mGalleryAdapter = new GalleryAdapter(mVm.getGalleryDisplayExecutor());
        mVb.gallery.setAdapter(mGalleryAdapter);

        mVm.onGalleryImage().observe(getViewLifecycleOwner(), this::setGalleryImage);
        mVm.onShowGalleryProgress().observe(getViewLifecycleOwner(), show -> {
            if (show) {
                mVb.progressBar.show();
            } else {
                mVb.progressBar.hide();
            }
        });

        // dismiss silently
        mVm.onSearchEditionsTaskCancelled().observe(getViewLifecycleOwner(), message -> dismiss());
        // the task throws no exceptions; but paranoia... dismiss silently is fine
        mVm.onSearchEditionsTaskFailure().observe(getViewLifecycleOwner(), message -> dismiss());
        mVm.onSearchEditionsTaskFinished().observe(getViewLifecycleOwner(), this::showGallery);

        mVm.onSelectedImage().observe(getViewLifecycleOwner(), this::setSelectedImage);
        mPreviewLoader = new ImageViewLoader(mVm.getPreviewDisplayExecutor(),
                                             mPreviewMaxWidth, mPreviewMaxHeight);

        // When the preview image is clicked, send the fileSpec back to the caller and terminate.
        mVb.preview.setOnClickListener(v -> {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                Log.d(TAG, "preview.onClick|filePath=" + mVm.getSelectedFileAbsPath());
            }

            if (mVm.getSelectedFileAbsPath() != null) {
                Launcher.setResult(this, mRequestKey, mVm.getSelectedFileAbsPath());
            }
            // close the CoverBrowserDialogFragment
            dismiss();
        });
    }

    @Override
    public void onCancel(@NonNull final DialogInterface dialog) {
        mVm.cancelAllTasks();
        super.onCancel(dialog);
    }

    @Override
    public void onResume() {
        super.onResume();
        // if the task is NOT already running and we have no editions loaded before
        if (!mVm.isSearchEditionsTaskRunning()) {
            if (mVm.getEditions().isEmpty()) {
                // start the task
                mVb.statusMessage.setText(R.string.progress_msg_searching_editions);
                mVb.progressBar.show();
                mVm.searchEditions();
            }
        }

        // If currently not shown, set a reasonable size for the preview image
        // so the progress overlay will be shown in the correct position
        if (mVb.preview.getVisibility() != View.VISIBLE) {
            final ViewGroup.LayoutParams previewLp = mVb.preview.getLayoutParams();
            previewLp.width = mPreviewMaxWidth;
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
        Objects.requireNonNull(mGalleryAdapter, "mGalleryAdapter");

        if (message.isNewEvent()) {
            if (message.result == null || message.result.isEmpty()) {
                mVb.progressBar.hide();
                mVb.statusMessage.setText(R.string.warning_no_editions);
                mVb.statusMessage.postDelayed(this::dismiss, BaseActivity.ERROR_DELAY_MS);
                return;
            }

            // set the list and trigger the adapter
            mVm.setEditions(message.result);
            mGalleryAdapter.notifyDataSetChanged();

            // Show help message
            mVb.statusMessage.setText(R.string.txt_tap_on_thumbnail_to_zoom);
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
        Objects.requireNonNull(mGalleryAdapter, "mGalleryAdapter");

        final int editionIndex;
        if (imageFileInfo != null) {
            editionIndex = mVm.getEditions().indexOf(imageFileInfo.getIsbn());

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
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
            mVm.getEditions().remove(editionIndex);
            mGalleryAdapter.notifyItemRemoved(editionIndex);
        }

        // if none left, dismiss.
        if (mGalleryAdapter.getItemCount() == 0) {
            mVb.progressBar.hide();
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
            if (ImageFileInfo.Size.Large == imageFileInfo.getSize()) {
                // the gallery image IS a valid large image, so just display it
                setSelectedImage(imageFileInfo);

            } else {
                mVb.preview.setVisibility(View.INVISIBLE);
                mVb.previewProgressBar.show();

                // start a task to fetch a larger image
                mVm.fetchSelectedImage(imageFileInfo);
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
        mVm.setSelectedFile(null);
        mVb.preview.setVisibility(View.INVISIBLE);
        mVb.previewProgressBar.hide();

        if (imageFileInfo != null) {
            final File file = imageFileInfo.getFile();
            if (file != null && file.exists()) {
                mPreviewLoader.loadAndDisplay(mVb.preview, file, (bitmap) -> {
                    // Set AFTER it was successfully loaded and displayed for maximum reliability
                    mVm.setSelectedFile(file);
                    mVb.preview.setVisibility(View.VISIBLE);
                    mVb.statusMessage.setText(R.string.txt_tap_on_image_to_select);
                });
                return;
            }
        }

        Snackbar.make(mVb.preview, R.string.warning_image_not_found,
                      Snackbar.LENGTH_LONG).show();
        mVb.statusMessage.setText(R.string.txt_tap_on_thumbnail_to_zoom);

    }

    public abstract static class Launcher
            extends FragmentLauncherBase {

        private static final String COVER_FILE_SPEC = "fileSpec";

        public Launcher(@NonNull final String requestKey) {
            super(requestKey);
        }

        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              @NonNull final String fileSpec) {
            final Bundle result = new Bundle(1);
            result.putString(COVER_FILE_SPEC, fileSpec);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        /**
         * Launch the dialog.
         *
         * @param bookTitle to display
         * @param isbn      ISBN of book
         * @param cIdx      0..n image index
         */
        public void launch(@NonNull final String bookTitle,
                           @NonNull final String isbn,
                           @IntRange(from = 0, to = 1) final int cIdx) {

            final Bundle args = new Bundle(3);
            args.putString(BKEY_REQUEST_KEY, mRequestKey);
            args.putString(DBKeys.KEY_TITLE, bookTitle);
            args.putString(DBKeys.KEY_ISBN, isbn);
            args.putInt(CoverBrowserViewModel.BKEY_FILE_INDEX, cIdx);

            final DialogFragment frag = new CoverBrowserDialogFragment();
            frag.setArguments(args);
            frag.show(mFragmentManager, TAG);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            onResult(Objects.requireNonNull(result.getString(COVER_FILE_SPEC)));
        }

        /**
         * Callback handler with the user's selection.
         *
         * @param fileSpec for the selected file
         */
        public abstract void onResult(@NonNull String fileSpec);
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

        @NonNull
        private final ImageViewLoader mImageLoader;

        /**
         * Constructor.
         *
         * @param executor to use for loading images
         */
        @SuppressWarnings("SameParameterValue")
        GalleryAdapter(@NonNull final Executor executor) {
            final Resources res = getResources();
            mMaxWidth = res.getDimensionPixelSize(R.dimen.cover_browser_gallery_width);
            mMaxHeight = res.getDimensionPixelSize(R.dimen.cover_browser_gallery_height);

            mImageLoader = new ImageViewLoader(executor, mMaxWidth, mMaxHeight);
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
            if (mVm.isCancelled()) {
                return;
            }

            final String isbn = mVm.getEditions().get(position);
            final ImageFileInfo imageFileInfo = mVm.getFileInfo(isbn);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                Log.d(TAG, "onBindViewHolder"
                           + "|position=" + position
                           + "|imageFileInfo=" + imageFileInfo);
            }

            if (imageFileInfo == null) {
                // not in the cache,; use a placeholder but preserve the available space
                ImageUtils.setPlaceholder(holder.imageView, mMaxWidth, mMaxHeight,
                                          R.drawable.ic_baseline_image_24, 0);
                // and queue a request for it.
                mVm.fetchGalleryImage(isbn);
                holder.siteView.setText("");

            } else {
                // check if it's good
                final File file = imageFileInfo.getFile();
                if (file != null && file.exists()) {
                    // YES, load it into the view.
                    mImageLoader.loadAndDisplay(holder.imageView, file, null);

                    // keep this statement here, or we would need to call file.exists() twice
                    holder.imageView.setOnClickListener(v -> onGalleryImageSelected(imageFileInfo));

                    holder.siteView.setText(SearchEngineRegistry
                                                    .getInstance()
                                                    .getByEngineId(imageFileInfo.getEngineId())
                                                    .getNameResId());

                } else {
                    // no file. Theoretically we should not get here,
                    // as a failed search should have removed the isbn from the edition list,
                    // but race-conditions + paranoia...
                    ImageUtils.setPlaceholder(holder.imageView, mMaxWidth, mMaxHeight,
                                              R.drawable.ic_baseline_broken_image_24, 0);
                    holder.siteView.setText("");
                }
            }
        }

        @Override
        public int getItemCount() {
            return mVm.getEditions().size();
        }
    }
}
