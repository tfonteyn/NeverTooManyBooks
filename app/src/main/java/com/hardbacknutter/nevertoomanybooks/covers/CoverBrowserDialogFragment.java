/*
 * @Copyright 2018-2023 HardBackNutter
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogCoverBrowserContentBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowCoverBrowserGalleryBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.utils.WindowSizeClass;

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
        extends FFBaseDialogFragment {

    /** Log tag. */
    public static final String TAG = "CoverBrowserFragment";
    private static final String ERROR_GALLERY_ADAPTER = "galleryAdapter";

    /** The adapter for the horizontal scrolling covers list. */
    @Nullable
    private GalleryAdapter galleryAdapter;

    /** The max width to be used for the preview image. */
    private int previewMaxWidth;
    /** The max height to be used for the preview image. */
    private int previewMaxHeight;

    /** The ViewModel. */
    private CoverBrowserViewModel vm;

    /** View Binding. */
    private DialogCoverBrowserContentBinding vb;

    private ImageViewLoader previewLoader;
    private final PositionHandler positionHandler = new PositionHandler() {

        /**
         * Display the given file in the preview View.
         * Starts a task to fetch a large(r) image if needed.
         *
         * @param imageFileInfo for the image
         */
        @Override
        public void onGalleryImageSelected(@NonNull final ImageFileInfo imageFileInfo) {
            if (Size.Large == imageFileInfo.getSize()) {
                // the gallery image IS a valid large image, so just display it
                setSelectedImage(imageFileInfo);
            } else {
                vb.preview.setVisibility(View.INVISIBLE);
                vb.previewProgressBar.show();

                // start a task to fetch a larger image
                vm.fetchSelectedImage(imageFileInfo);
            }
        }

        @Override
        public void fetchGalleryImage(@NonNull final String isbn) {
            vm.fetchGalleryImage(isbn);
        }

        @Nullable
        @Override
        public ImageFileInfo getFileInfo(@NonNull final String isbn) {
            return vm.getFileInfo(isbn);
        }
    };

    /**
     * No-arg constructor for OS use.
     */
    public CoverBrowserDialogFragment() {
        super(R.layout.dialog_cover_browser,
              R.layout.dialog_cover_browser_content,
              // Fullscreen on Medium screens
              EnumSet.of(WindowSizeClass.Medium),
              EnumSet.of(WindowSizeClass.Medium));
    }

    /**
     * ENHANCE: pass in a {@link Site.Type#Covers} list / set it on the fly.
     */
    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(CoverBrowserViewModel.class);
        vm.init(requireArguments());

        final Resources res = getResources();
        previewMaxWidth = res.getDimensionPixelSize(R.dimen.cover_browser_preview_width);
        previewMaxHeight = res.getDimensionPixelSize(R.dimen.cover_browser_preview_height);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vb = DialogCoverBrowserContentBinding.bind(view.findViewById(R.id.dialog_content));

        final String bookTitle = Objects.requireNonNull(
                requireArguments().getString(DBKey.TITLE), DBKey.TITLE);
        setSubtitle(bookTitle);

        //noinspection DataFlowIssue
        galleryAdapter = new GalleryAdapter(getContext(), vm.getEditions(),
                                            positionHandler,
                                            vm.getGalleryDisplayExecutor());
        vb.gallery.setAdapter(galleryAdapter);

        vm.onGalleryImage().observe(getViewLifecycleOwner(), this::setGalleryImage);
        vm.onShowGalleryProgress().observe(getViewLifecycleOwner(), show -> {
            if (show) {
                vb.progressBar.show();
            } else {
                vb.progressBar.hide();
            }
        });

        // dismiss silently
        vm.onSearchEditionsTaskCancelled().observe(getViewLifecycleOwner(), message -> dismiss());
        // the task throws no exceptions; but paranoia... dismiss silently is fine
        vm.onSearchEditionsTaskFailure().observe(getViewLifecycleOwner(), message -> dismiss());
        vm.onSearchEditionsTaskFinished().observe(getViewLifecycleOwner(), message
                -> message.getData().ifPresent(data -> showGallery(data.getResult())));

        vm.onSelectedImage().observe(getViewLifecycleOwner(), this::setSelectedImage);
        previewLoader = new ImageViewLoader(vm.getPreviewDisplayExecutor(),
                                            previewMaxWidth, previewMaxHeight);

        // When the preview image is clicked, send the fileSpec back to the caller and terminate.
        vb.preview.setOnClickListener(v -> {
            if (saveChanges()) {
                dismiss();
            }
        });

        // Don't give it the gallery! We're auto-adapting in this class already
        adjustWindowSize(null, 0);
    }

    @Override
    protected boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            final int id = button.getId();
            if (id == R.id.btn_save || id == R.id.btn_positive) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
        }
        return false;
    }

    private boolean saveChanges() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
            LoggerFactory.getLogger().d(TAG, "saveChanges",
                                        "filePath=" + vm.getSelectedFileAbsPath());
        }

        if (vm.getSelectedFileAbsPath() != null) {
            Launcher.setResult(this, vm.getRequestKey(), vm.getSelectedFileAbsPath());
            return true;
        }
        return false;
    }

    @Override
    public void onCancel(@NonNull final DialogInterface dialog) {
        vm.cancelAllTasks();
        super.onCancel(dialog);
    }

    @Override
    public void onResume() {
        super.onResume();
        // if the task is NOT already running and we have no editions loaded before
        if (!vm.isSearchEditionsTaskRunning() && vm.getEditions().isEmpty()) {
            // start the task
            vb.statusMessage.setText(R.string.progress_msg_searching_editions);
            vb.progressBar.show();
            vm.searchEditions();
        }

        // If currently not shown, set a reasonable size for the preview image
        // so the progress overlay will be shown in the correct position
        if (vb.preview.getVisibility() != View.VISIBLE) {
            final ViewGroup.LayoutParams previewLp = vb.preview.getLayoutParams();
            previewLp.width = previewMaxWidth;
            previewLp.height = previewMaxHeight;
            vb.preview.setLayoutParams(previewLp);
        }
    }

    /**
     * Show the user a selection of other covers and allow selection of a replacement.
     *
     * @param editionsList a list with ISBN numbers
     */
    @SuppressLint("NotifyDataSetChanged")
    private void showGallery(@Nullable final Collection<String> editionsList) {
        Objects.requireNonNull(galleryAdapter, ERROR_GALLERY_ADAPTER);

        if (editionsList == null || editionsList.isEmpty()) {
            vb.progressBar.hide();
            vb.statusMessage.setText(R.string.warning_no_editions);
            vb.statusMessage.postDelayed(this::dismiss, BaseActivity.DELAY_LONG_MS);
        } else {
            // set the list and trigger the adapter
            vm.setEditions(editionsList);
            galleryAdapter.notifyDataSetChanged();
            // Show help message
            vb.statusMessage.setText(R.string.info_tap_on_thumbnail_to_zoom);
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
        Objects.requireNonNull(galleryAdapter, ERROR_GALLERY_ADAPTER);

        final int editionIndex;
        if (imageFileInfo != null) {
            editionIndex = vm.getEditions().indexOf(imageFileInfo.getIsbn());
        } else {
            editionIndex = -1;
        }

        if (editionIndex >= 0) {
            final Optional<File> tmpFile = imageFileInfo.getFile();
            if (tmpFile.isPresent()) {
                tmpFile.get().deleteOnExit();
                // Tell the adapter to refresh the entry.
                // It will get the image from the file-manager.
                galleryAdapter.notifyItemChanged(editionIndex);
                return;
            }

            // No file. Remove the defunct view from the gallery
            vm.getEditions().remove(editionIndex);
            galleryAdapter.notifyItemRemoved(editionIndex);
        }

        // if none left, dismiss.
        if (galleryAdapter.getItemCount() == 0) {
            vb.progressBar.hide();
            vb.statusMessage.setText(R.string.warning_image_not_found);
            vb.statusMessage.postDelayed(this::dismiss, BaseActivity.DELAY_LONG_MS);
        }
    }

    /**
     * Display the given image in the preview View.
     *
     * @param imageFileInfo to display
     */
    private void setSelectedImage(@Nullable final ImageFileInfo imageFileInfo) {
        // Always reset the preview and hide the progress bar
        vm.setSelectedFile(null);
        vb.preview.setVisibility(View.INVISIBLE);
        vb.previewProgressBar.hide();

        if (imageFileInfo != null) {
            final Optional<File> file = imageFileInfo.getFile();
            if (file.isPresent()) {
                previewLoader.fromFile(vb.preview, file.get(), bitmap -> {
                    // Set AFTER it was successfully loaded and displayed for maximum reliability
                    vm.setSelectedFile(file.get());
                    vb.preview.setVisibility(View.VISIBLE);
                    vb.statusMessage.setText(R.string.info_tap_on_image_to_select);
                });
                return;
            }
        }

        Snackbar.make(vb.preview, R.string.warning_image_not_found,
                      Snackbar.LENGTH_LONG).show();
        vb.statusMessage.setText(R.string.info_tap_on_thumbnail_to_zoom);
    }

    /**
     * Proxy between adapter and ViewModel.
     */
    private interface PositionHandler {

        /**
         * The user clicked an image in the gallery.
         *
         * @param imageFileInfo for the image
         */
        void onGalleryImageSelected(@NonNull ImageFileInfo imageFileInfo);

        void fetchGalleryImage(@NonNull String isbn);

        @Nullable
        ImageFileInfo getFileInfo(@NonNull String isbn);
    }

    public static class Launcher
            implements FragmentResultListener {

        private static final String COVER_FILE_SPEC = "fileSpec";
        @NonNull
        private final String requestKey;
        @NonNull
        private final ResultListener resultListener;
        private FragmentManager fragmentManager;

        public Launcher(@NonNull final String requestKey,
                        @NonNull final ResultListener resultListener) {
            this.requestKey = requestKey;
            this.resultListener = resultListener;
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

            final Bundle args = new Bundle(4);
            args.putString(CoverBrowserViewModel.BKEY_REQUEST_KEY, requestKey);
            args.putString(DBKey.TITLE, bookTitle);
            args.putString(DBKey.BOOK_ISBN, isbn);
            args.putInt(CoverBrowserViewModel.BKEY_FILE_INDEX, cIdx);

            final DialogFragment fragment = new CoverBrowserDialogFragment();
            fragment.setArguments(args);
            fragment.show(fragmentManager, TAG);
        }

        public void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                              @NonNull final LifecycleOwner lifecycleOwner) {
            this.fragmentManager = fragmentManager;
            this.fragmentManager.setFragmentResultListener(this.requestKey, lifecycleOwner, this);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            resultListener.onResult(
                    Objects.requireNonNull(result.getString(COVER_FILE_SPEC), COVER_FILE_SPEC));
        }

        @FunctionalInterface
        public interface ResultListener {
            /**
             * Callback handler with the user's selection.
             *
             * @param fileSpec for the selected file
             */
            void onResult(@NonNull String fileSpec);
        }
    }

    /**
     * Row ViewHolder for {@link GalleryAdapter}.
     */
    private static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        private final RowCoverBrowserGalleryBinding vb;

        Holder(@NonNull final RowCoverBrowserGalleryBinding vb,
               final int maxWidth,
               final int maxHeight) {
            super(vb.getRoot());
            this.vb = vb;

            vb.coverImage0.getLayoutParams().width = maxWidth;
            vb.coverImage0.getLayoutParams().height = maxHeight;
        }
    }

    private static class GalleryAdapter
            extends RecyclerView.Adapter<Holder> {

        /** A single image fixed width. */
        private final int maxWidth;
        /** A single image fixed height. */
        private final int maxHeight;

        @NonNull
        private final ImageViewLoader imageLoader;
        private final LayoutInflater inflater;
        @NonNull
        private final List<String> editionsList;
        @NonNull
        private final PositionHandler positionHandler;

        /**
         * Constructor.
         *
         * @param context         Current context
         * @param editionsList    a list with ISBN numbers
         * @param positionHandler Proxy between adapter and ViewModel
         * @param executor        to use for loading images
         */
        @SuppressWarnings("SameParameterValue")
        GalleryAdapter(@NonNull final Context context,
                       @NonNull final List<String> editionsList,
                       @NonNull final PositionHandler positionHandler,
                       @NonNull final Executor executor) {
            inflater = LayoutInflater.from(context);
            this.editionsList = editionsList;
            this.positionHandler = positionHandler;
            final Resources res = context.getResources();
            maxWidth = res.getDimensionPixelSize(R.dimen.cover_browser_gallery_width);
            maxHeight = res.getDimensionPixelSize(R.dimen.cover_browser_gallery_height);

            imageLoader = new ImageViewLoader(executor, maxWidth, maxHeight);
        }

        @Override
        @NonNull
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            return new Holder(RowCoverBrowserGalleryBinding.inflate(inflater, parent, false),
                              maxWidth, maxHeight);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            final String isbn = editionsList.get(position);
            final ImageFileInfo imageFileInfo = positionHandler.getFileInfo(isbn);

            if (imageFileInfo == null) {
                // not in the cache,; use a placeholder but preserve the available space
                imageLoader.placeholder(holder.vb.coverImage0,
                                        R.drawable.ic_baseline_image_24);
                // and queue a request for it.
                positionHandler.fetchGalleryImage(isbn);
                holder.vb.lblSite.setText("");
                holder.vb.coverImage0.setOnClickListener(null);

            } else {
                // check if it's good
                final Optional<File> file = imageFileInfo.getFile();
                if (file.isPresent()) {
                    // YES, load it into the view.
                    imageLoader.fromFile(holder.vb.coverImage0, file.get(), null);

                    holder.vb.lblSite.setText(imageFileInfo.getEngineId().getLabelResId());
                    holder.vb.coverImage0.setOnClickListener(
                            v -> positionHandler.onGalleryImageSelected(imageFileInfo));
                } else {
                    // no file. Theoretically we should not get here,
                    // as a failed search should have removed the isbn from the edition list,
                    // but race-conditions + paranoia...
                    imageLoader.placeholder(holder.vb.coverImage0,
                                            R.drawable.ic_baseline_broken_image_24);
                    holder.vb.lblSite.setText("");
                    holder.vb.coverImage0.setOnClickListener(null);
                }
            }
        }

        @Override
        public int getItemCount() {
            return editionsList.size();
        }
    }
}
