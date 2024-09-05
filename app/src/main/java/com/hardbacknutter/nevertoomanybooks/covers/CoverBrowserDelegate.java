/*
 * @Copyright 2018-2024 HardBackNutter
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
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.CoverScale;
import com.hardbacknutter.nevertoomanybooks.core.widgets.insets.InsetsListenerBuilder;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogCoverBrowserContentBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowCoverBrowserGalleryBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogType;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialogDelegate;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEdition;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.utils.Delay;
import com.hardbacknutter.util.logger.LoggerFactory;

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
 * ENHANCE: pass in a {@link Site.Type#Covers} list allow configuring search-sites on the fly
 */
class CoverBrowserDelegate
        implements FlexDialogDelegate {

    /** Fragment/Log tag. */
    public static final String TAG = "CoverBrowserDelegate";

    private static final String ERROR_GALLERY_ADAPTER = "galleryAdapter";
    /** The max width to be used for the preview image. */
    private final int previewMaxWidth;
    /** The max height to be used for the preview image. */
    private final int previewMaxHeight;
    @NonNull
    private final String bookTitle;
    @NonNull
    private final CoverBrowserViewModel vm;
    @NonNull
    private final DialogFragment owner;
    @NonNull
    private final String requestKey;

    /** The adapter for the horizontal scrolling covers list. */
    @Nullable
    private GalleryAdapter galleryAdapter;
    /** View Binding. */
    private DialogCoverBrowserContentBinding vb;
    @Nullable
    private Toolbar toolbar;

    private ImageViewLoader previewLoader;
    private final PositionHandler positionHandler = new PositionHandler() {

        @Override
        public void onGalleryImageSelected(@NonNull final ImageFileInfo imageFileInfo) {
            setSelectedImage(imageFileInfo, true);
        }

        @Override
        public void fetchGalleryImage(@NonNull final AltEdition edition) {
            vm.fetchGalleryImage(edition);
        }

        @Nullable
        @Override
        public ImageFileInfo getFileInfo(@NonNull final AltEdition edition) {
            return vm.getFileInfo(edition);
        }
    };

    CoverBrowserDelegate(@NonNull final DialogFragment owner,
                         @NonNull final Bundle args) {
        this.owner = owner;
        requestKey = Objects.requireNonNull(args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                                            DialogLauncher.BKEY_REQUEST_KEY);
        bookTitle = Objects.requireNonNull(args.getString(DBKey.TITLE), DBKey.TITLE);

        final Resources res = owner.getResources();
        previewMaxWidth = res.getDimensionPixelSize(R.dimen.cover_browser_preview_width);
        previewMaxHeight = (int) (previewMaxWidth / CoverScale.HW_RATIO);

        vm = new ViewModelProvider(owner).get(CoverBrowserViewModel.class);
        vm.init(args);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container) {
        vb = DialogCoverBrowserContentBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    @NonNull
    public View onCreateFullscreen(@NonNull final LayoutInflater inflater,
                                   @Nullable final ViewGroup container) {
        final View view = inflater.inflate(R.layout.dialog_cover_browser, container, false);
        vb = DialogCoverBrowserContentBinding.bind(view.findViewById(R.id.dialog_content));
        return view;
    }

    @Override
    public void setToolbar(@Nullable final Toolbar toolbar) {
        this.toolbar = toolbar;

    }

    @Override
    public void onViewCreated(@NonNull final DialogType dialogType) {
        InsetsListenerBuilder.apply(vb.gallery);

        if (toolbar != null) {
            initToolbar(owner, dialogType, toolbar);
            toolbar.setSubtitle(bookTitle);
        }

        final Context context = vb.getRoot().getContext();
        galleryAdapter = new GalleryAdapter(context, vm.getEditions(),
                                            positionHandler,
                                            vm.getGalleryDisplayExecutor());
        vb.gallery.setAdapter(galleryAdapter);

        vm.onGalleryImage().observe(owner.getViewLifecycleOwner(), this::setGalleryImage);
        vm.onShowGalleryProgress().observe(owner.getViewLifecycleOwner(), show -> {
            if (show) {
                vb.progressBar.show();
            } else {
                vb.progressBar.hide();
            }
        });

        // dismiss silently
        vm.onSearchEditionsTaskCancelled().observe(owner.getViewLifecycleOwner(),
                                                   message -> owner.dismiss());
        // the task throws no exceptions; but paranoia... dismiss silently is fine
        vm.onSearchEditionsTaskFailure().observe(owner.getViewLifecycleOwner(),
                                                 message -> owner.dismiss());
        vm.onSearchEditionsTaskFinished().observe(owner.getViewLifecycleOwner(), message
                -> message.process(this::showGallery));

        vm.onSelectedImage().observe(owner.getViewLifecycleOwner(),
                                     imageFileInfo -> setSelectedImage(imageFileInfo, false));
        previewLoader = new ImageViewLoader(vm.getPreviewDisplayExecutor(),
                                            ImageView.ScaleType.FIT_START,
                                            ImageViewLoader.MaxSize.Enforce,
                                            previewMaxWidth, previewMaxHeight);

        // When the preview image is clicked, send the fileSpec back to the caller and terminate.
        vb.preview.setOnClickListener(v -> {
            if (saveChanges()) {
                owner.dismiss();
            }
        });
    }

    @Override
    public void onCancel(@NonNull final DialogInterface dialog) {
        vm.cancelAllTasks();
    }

    @Override
    public void onResume(@NonNull final LifecycleOwner lifecycleOwner) {
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

    @Override
    public void onToolbarNavigationClick(@NonNull final View v) {
        owner.dismiss();
    }

    @Override
    public boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            final int id = button.getId();
            if (id == R.id.toolbar_btn_save || id == R.id.btn_positive) {
                if (saveChanges()) {
                    owner.dismiss();
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
            CoverBrowserLauncher.setResult(owner, requestKey, vm.getSelectedFileAbsPath());
            return true;
        }
        return false;
    }

    /**
     * Show the user a selection of alternative edition covers and allow selection of a replacement.
     *
     * @param list editions
     */
    @SuppressLint("NotifyDataSetChanged")
    private void showGallery(@Nullable final Collection<AltEdition> list) {
        Objects.requireNonNull(galleryAdapter, ERROR_GALLERY_ADAPTER);

        if (vm.setEditions(list)) {
            galleryAdapter.notifyDataSetChanged();
            vb.statusMessage.setText(R.string.info_tap_on_thumbnail_to_zoom);
        } else {
            vb.progressBar.hide();
            vb.statusMessage.setText(R.string.warning_no_editions);
            vb.statusMessage.postDelayed(owner::dismiss, Delay.LONG_MS);
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
            editionIndex = vm.getEditions().indexOf(imageFileInfo.getEdition());
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

            // If there is only 1 alternative edition with a cover, auto-select it.
            // The user still has to accept it manually.
            if (vm.getEditions().size() == 1) {
                final AltEdition altEdition = vm.getEditions().get(0);
                final ImageFileInfo onlyOne = vm.getFileInfo(altEdition);
                // Sanity check
                if (onlyOne != null && onlyOne.getFile().isPresent()) {
                    // Hide the now useless gallery and fetch/show the single image.
                    // This does mean that if the user saw the gallery image,
                    // and decided it's not the one they want...
                    // we STILL potentially fetch a larger copy. Oh well...
                    vb.statusMessage.setVisibility(View.GONE);
                    vb.gallery.setVisibility(View.GONE);
                    setSelectedImage(onlyOne, true);
                }
            }
        }

        // if none left, flash a warning, and dismiss after a small delay.
        if (vm.getEditions().isEmpty()) {
            vb.progressBar.hide();
            vb.statusMessage.setText(R.string.warning_image_not_found);
            vb.statusMessage.postDelayed(owner::dismiss, Delay.LONG_MS);
        }
    }

    /**
     * Display the given image in the preview View.
     * The 'verify' flag:
     * <ul>
     *     <li>{@code true} if the user clicked on a gallery image,
     *                      but it still needs to be verified and we
     *                      potentially need to search for a better image.</li>
     *     <li>{@code false} if the image is already verified, and
     *                       we should just display it</li>
     * </ul>
     *
     * @param imageFileInfo to display
     * @param verify        flag
     */
    private void setSelectedImage(@Nullable final ImageFileInfo imageFileInfo,
                                  final boolean verify) {
        if (verify) {
            // If the site supports multiple sizes and
            // the gallery image is not already a large image,
            // start a task to fetch a larger image.
            //noinspection DataFlowIssue
            if (imageFileInfo.getEngineId().getConfig().supportsMultipleCoverSizes()
                && Size.Large != imageFileInfo.getSize()) {
                vb.lblPreview.setVisibility(View.INVISIBLE);
                vb.preview.setVisibility(View.INVISIBLE);
                vb.previewProgressBar.show();
                vm.fetchSelectedImage(imageFileInfo);
                return;
            }
        }

        // image is accepted, display it

        // Always reset the preview and hide the progress bar
        vm.setSelectedFile(null);
        vb.lblPreview.setVisibility(View.INVISIBLE);
        vb.preview.setVisibility(View.INVISIBLE);
        vb.previewProgressBar.hide();

        if (imageFileInfo != null) {
            final Optional<File> file = imageFileInfo.getFile();
            if (file.isPresent()) {
                previewLoader.fromFile(vb.preview, file.get(), bitmap -> {
                    // Set AFTER it was successfully loaded and displayed for maximum reliability
                    vm.setSelectedFile(file.get());
                    vb.lblPreview.setVisibility(View.VISIBLE);
                    vb.preview.setVisibility(View.VISIBLE);
                    vb.statusMessage.setText(R.string.info_tap_on_thumbnail_to_zoom);
                }, null);
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
    interface PositionHandler {

        /**
         * The user clicked an image in the gallery. Fetch the info for it.
         *
         * @param imageFileInfo for the image
         */
        void onGalleryImageSelected(@NonNull ImageFileInfo imageFileInfo);

        /**
         * Start a task to fetch a Gallery image.
         *
         * @param edition to search
         */
        void fetchGalleryImage(@NonNull AltEdition edition);

        /**
         * Get the requested ImageFileInfo.
         *
         * @param edition to search
         *
         * @return a {@link ImageFileInfo} object with or without a valid fileSpec,
         *         or {@code null} if there is no cached file at all
         */
        @Nullable
        ImageFileInfo getFileInfo(@NonNull AltEdition edition);
    }

    /**
     * Row ViewHolder for {@link GalleryAdapter}.
     */
    public static class Holder
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
        private final List<AltEdition> items;
        @NonNull
        private final PositionHandler positionHandler;

        /**
         * Constructor.
         *
         * @param context         Current context
         * @param items           editions
         * @param positionHandler Proxy between adapter and ViewModel
         * @param executor        to use for loading images
         */
        @SuppressWarnings("SameParameterValue")
        GalleryAdapter(@NonNull final Context context,
                       @NonNull final List<AltEdition> items,
                       @NonNull final PositionHandler positionHandler,
                       @NonNull final Executor executor) {
            inflater = LayoutInflater.from(context);
            this.items = items;
            this.positionHandler = positionHandler;
            final Resources res = context.getResources();
            maxWidth = res.getDimensionPixelSize(R.dimen.cover_browser_gallery_width);
            maxHeight = (int) (maxWidth / CoverScale.HW_RATIO);

            imageLoader = new ImageViewLoader(executor,
                                              ImageView.ScaleType.FIT_START,
                                              ImageViewLoader.MaxSize.Enforce,
                                              maxWidth, maxHeight);
        }

        @Override
        @NonNull
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            final RowCoverBrowserGalleryBinding vb = RowCoverBrowserGalleryBinding
                    .inflate(inflater, parent, false);
            return new Holder(vb, maxWidth, maxHeight);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            final AltEdition altEdition = items.get(position);

            final ImageFileInfo imageFileInfo = positionHandler.getFileInfo(altEdition);

            if (imageFileInfo == null) {
                // not in the cache, queue a request for it.
                positionHandler.fetchGalleryImage(altEdition);

                // use a placeholder but preserve the available space
                imageLoader.placeholder(holder.vb.coverImage0, R.drawable.image_24px);
                holder.vb.lblSite.setText("");
                holder.vb.coverImage0.setOnClickListener(null);

            } else {
                // We have file information; check if it's any good
                final Optional<File> file = imageFileInfo.getFile();
                if (file.isPresent()) {
                    // YES, load it into the view.
                    imageLoader.fromFile(holder.vb.coverImage0, file.get(), null, null);

                    holder.vb.lblSite.setText(imageFileInfo.getEngineId().getLabelResId());
                    holder.vb.coverImage0.setOnClickListener(
                            v -> positionHandler.onGalleryImageSelected(imageFileInfo));

                } else {
                    // NO file. Theoretically we should not get here,
                    // as a failed search should have removed the edition from the list,
                    // but race-conditions + paranoia...
                    imageLoader.placeholder(holder.vb.coverImage0,
                                            R.drawable.broken_image_24px);
                    holder.vb.lblSite.setText("");
                    holder.vb.coverImage0.setOnClickListener(null);
                }
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
