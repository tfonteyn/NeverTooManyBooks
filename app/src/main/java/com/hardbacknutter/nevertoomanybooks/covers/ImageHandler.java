/*
 * @Copyright 2018-2026 HardBackNutter
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

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.CropImageContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditPictureContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.PermissionRequester;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.PickVisualMediaContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.TakePictureContract;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.core.utils.Code;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.dialogs.Tip;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.ZoomedImageDialogFragment;
import com.hardbacknutter.nevertoomanybooks.menus.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuLauncher;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * A delegate class for handling a displayed image.
 * Offers context menus and all operations applicable on an image.
 * There is one instance of this class for each displayed image.
 * <p>
 * <strong>Context/View dependent!</strong>
 * <p>
 * Handles displaying and zooming for images on the details/edit screens.
 * For BoB displaying,
 * see {@code com.hardbacknutter.nevertoomanybooks.booklist.adapter.CoverHelper}
 */
public final class ImageHandler {

    /** Log tag. */
    private static final String TAG = "ImageHandler";

    private static final String RK_MENU = TAG + ":rk:menu";
    private static final String IMAGE_MIME_TYPE = "image/*";
    /** Rotate +90 or -90 degrees. */
    private static final int TURN = 90;
    /** Rotate upside down. */
    private static final int FLIP = 180;
    private static final float PROGRESS_INDICATOR_ELEVATION = 10f;
    private static final float PROGRESS_INDICATOR_ALPHA = 0.8f;
    /** Index of the image we're handling. */
    @IntRange(from = 0, to = 3)
    private final int cIdx;

    /** Our host. */
    @NonNull
    private final Fragment fragment;
    @NonNull
    private final ImageView imageView;
    @NonNull
    private final ConstraintLayout imageViewParent;

    /** The owner of the image. We always need the current image. */
    private final Supplier<ImageOwner> imageSupplier;
    /**
     * Callback to tell the owner to reload (and redisplay) the image after a change.
     * <p>
     * Argument: the {@link #cIdx}
     */
    @NonNull
    private final Consumer<Integer> reloadImageCallback;
    /** The local helper for loading/displaying images. */
    @NonNull
    private final ImageViewLoader imageLoader;

    /** Book use only; {@code null} otherwise. */
    @Nullable
    private final CoverBrowserLauncher coverBrowserLauncher;
    /**
     * Book use only; {@code null} otherwise.
     * We always use the <strong>current</strong> value (e.g. when editing).
     */
    @Nullable
    private final Supplier<String> coverBrowserIsbnSupplier;
    /**
     * Book use only; {@code null} otherwise.
     * We always use the <strong>current</strong> value (e.g. when editing).
     */
    @Nullable
    private final Supplier<String> coverBrowserTitleSupplier;

    /** Private to this handler. */
    @NonNull
    private final ImageHandlerViewModel vm;

    private final ExtMenuLauncher menuLauncher;
    @DrawableRes
    private final int placeholderDrawable;
    /** progress bar displayed during operations. */
    @Nullable
    private CircularProgressIndicator progressIndicator;
    private PermissionRequester permissionRequester;
    private ActivityResultLauncher<TakePictureContract.Input> takePictureLauncher;
    private ActivityResultLauncher<EditPictureContract.Input> editPictureLauncher;
    private ActivityResultLauncher<CropImageContract.Input> cropImageLauncher;
    private ActivityResultLauncher<String> getFromFileLauncher;

    private ImageHandler(@NonNull final Builder builder) {
        // We could store cIdx in the VM, but there really is no point
        cIdx = builder.cIdx;

        fragment = Objects.requireNonNull(builder.fragment);
        imageView = Objects.requireNonNull(builder.imageView);
        imageViewParent = Objects.requireNonNull(builder.imageViewParent);
        imageSupplier = Objects.requireNonNull(builder.imageSupplier);

        reloadImageCallback = builder.reloadImage;
        placeholderDrawable = builder.placeholderDrawable;
        coverBrowserTitleSupplier = builder.coverBrowserTitleSupplier;
        coverBrowserIsbnSupplier = builder.coverBrowserIsbnSupplier;
        // Minor hack...  if we have a title/isbn supplier, then we have a Book
        // and will need a coverBrowserLauncher.
        if (coverBrowserTitleSupplier != null) {
            coverBrowserLauncher = new CoverBrowserLauncher(cIdx, this::onPictureSelected);
        } else {
            coverBrowserLauncher = null;
        }
        // we distinguish multiple vm in the same fragment by cIdx as the key
        vm = new ViewModelProvider(fragment)
                .get(String.valueOf(this.cIdx), ImageHandlerViewModel.class);

        imageLoader = new ImageViewLoader(ASyncExecutor.IMAGES,
                                          ImageView.ScaleType.FIT_START,
                                          ImageViewLoader.ApplySizing.Constrained,
                                          builder.maxWidth, builder.maxHeight);

        attachOnClickListeners();

        final FragmentManager fm = fragment.getChildFragmentManager();
        // concat the RK with the cIdx as we have more than ImageHandler
        //noinspection StringConcatenationMissingWhitespace
        menuLauncher = new ExtMenuLauncher(RK_MENU + this.cIdx, this::onMenuItemSelected);
        menuLauncher.registerForFragmentResult(fm, fragment);
    }

    /**
     * Set the click-listeners on the view.
     */
    private void attachOnClickListeners() {
        imageView.setOnClickListener(v -> {
            // Allow zooming by clicking on the image;
            imageSupplier.get().getImage(imageView.getContext(), cIdx).ifPresent(
                    file -> ZoomedImageDialogFragment
                            .launch(fragment.getChildFragmentManager(), file));
        });

        imageView.setOnLongClickListener(this::onCreateContextMenu);
    }

    /**
     * DO NOT CALL FROM CONSTRUCTOR.
     */
    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    private void onFragmentViewCreated() {
        //noinspection DataFlowIssue
        permissionRequester = new PermissionRequester(fragment.getActivity(), fragment);
        permissionRequester.addPermission(Manifest.permission.CAMERA, fragment.getString(
                R.string.warning_camera_permission_required), true);

        takePictureLauncher = fragment.registerForActivityResult(
                new TakePictureContract(), o -> o.ifPresent(this::onTakePictureResult));

        getFromFileLauncher = fragment.registerForActivityResult(
                new PickVisualMediaContract(), o -> o.ifPresent(this::onPictureResult));

        editPictureLauncher = fragment.registerForActivityResult(
                new EditPictureContract(), o -> o.ifPresent(this::onPictureResult));

        cropImageLauncher = fragment.registerForActivityResult(
                new CropImageContract(), o -> o.ifPresent(this::onPictureResult));


        final LifecycleOwner viewLifecycleOwner = fragment.getViewLifecycleOwner();

        if (coverBrowserLauncher != null) {
            // TODO: should we not just use the fragment itself as the 2nd param?
            coverBrowserLauncher.registerForFragmentResult(fragment.getChildFragmentManager(),
                                                           viewLifecycleOwner);
        }

        vm.onInvalidImage().observe(viewLifecycleOwner, message -> {
            hideProgress();
            message.process(this::onInvalidImage);
        });
        vm.onError().observe(viewLifecycleOwner, message -> {
            hideProgress();
            message.process(this::onError);
        });

        vm.onTransformationResult().observe(viewLifecycleOwner, message -> {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGES) {
                LoggerFactory.getLogger().d(TAG, "vm.onTransformationResult()|event=" + message);
            }
            hideProgress();
            message.process(this::onAfterTransform);
        });
        vm.onStartEdit().observe(viewLifecycleOwner, message
                -> message.process(this::editPicture));
        vm.onStartCropper().observe(viewLifecycleOwner, message
                -> message.process(input -> cropImageLauncher.launch(input)));
        vm.onStartTakePicture().observe(viewLifecycleOwner, message
                -> message.process(this::takePicture));
        vm.onRestore().observe(viewLifecycleOwner, message
                -> message.process(restored -> reloadImageCallback.accept(cIdx)));
        vm.onReloadImage().observe(viewLifecycleOwner, aVoid -> {
            // must use a post to force the View to update.
            //noinspection DataFlowIssue
            fragment.getView().post(() -> reloadImageCallback.accept(cIdx));
        });
    }

    /**
     * Populate the view.
     */
    public void onBindView() {
        final Optional<File> file = imageSupplier.get().getImage(imageView.getContext(), cIdx);
        if (file.isPresent()) {
            imageLoader.fromFile(null, cIdx, file.get(), imageView, null);
            imageView.setBackground(null);
        } else {
            imageLoader.placeholder(imageView, placeholderDrawable);
            imageView.setBackgroundResource(R.drawable.bg_cover_not_set);
        }
    }

    /**
     * Context menu for the image.
     *
     * @param anchor The view that was clicked and held.
     *
     * @return {@code true} for compatibility with setOnLongClickListener
     */
    private boolean onCreateContextMenu(@NonNull final View anchor) {

        final Context context = anchor.getContext();

        @NonNull
        Menu menu = MenuUtils.create(context, R.menu.image);

        final ImageOwner imageOwner = imageSupplier.get();
        final Optional<File> file = imageOwner.getImage(context, cIdx);

        if (file.isPresent()) {
            if (BuildConfig.DEBUG /* always */) {
                // show the size of the image in the title bar
                final BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.get().getAbsolutePath(), opts);
            }
        } else {
            // there is no current image; only show the replace menu
            final MenuItem menuItem = Objects.requireNonNull(
                    menu.findItem(R.id.SUBMENU_THUMB_REPLACE), "R.id.SUBMENU_THUMB_REPLACE");
            menu = Objects.requireNonNull(menuItem.getSubMenu(), "getSubMenu");
        }

        // we only support alternative edition covers for a Book front cover.
        menu.findItem(R.id.MENU_THUMB_ADD_FROM_ALT_EDITIONS)
            .setVisible(coverBrowserLauncher != null && cIdx == 0);

        // Add the potential undo-menu
        if (imageOwner.getImageUuid().isPresent()) {
            if (ServiceLocator.getInstance().getCoverStorage()
                              .isUndoEnabled(imageOwner.getImageUuid().get(), cIdx)) {
                menu.add(R.id.MENU_GROUP_UNDO, R.id.MENU_UNDO, 0, R.string.option_image_restore)
                    .setIcon(R.drawable.undo_24px);
            }
        }

        menuLauncher.launch(anchor, null, null, cIdx, menu);

        return true;
    }

    /**
     * Menu selection listener.
     *
     * @param cIdx       0..n image index
     * @param menuItemId The menu item that was invoked.
     *
     * @return {@code true} if handled.
     */
    private boolean onMenuItemSelected(@IntRange(from = 0, to = 3) final int cIdx,
                                       @IdRes final int menuItemId) {

        final ImageOwner imageOwner = imageSupplier.get();
        final Context context = fragment.requireContext();

        if (menuItemId == R.id.MENU_DELETE) {
            vm.removeImage(imageOwner, cIdx);
            return true;

        } else if (menuItemId == R.id.MENU_THUMB_ROTATE_CW) {
            TipManager.getInstance().show(context, Tip.CAMERA_AUTOROTATE_IMAGES,
                                          () -> startRotation(TURN));
            return true;

        } else if (menuItemId == R.id.MENU_THUMB_ROTATE_CCW) {
            TipManager.getInstance().show(context, Tip.CAMERA_AUTOROTATE_IMAGES,
                                          () -> startRotation(-TURN));
            return true;

        } else if (menuItemId == R.id.MENU_THUMB_ROTATE_180) {
            TipManager.getInstance().show(context, Tip.CAMERA_AUTOROTATE_IMAGES,
                                          () -> startRotation(FLIP));
            return true;

        } else if (menuItemId == R.id.MENU_THUMB_CROP) {
            vm.prepareCropper(imageOwner, cIdx);
            return true;

        } else if (menuItemId == R.id.MENU_EDIT) {
            vm.prepareEditor(imageOwner, cIdx);
            return true;

        } else if (menuItemId == R.id.MENU_THUMB_ADD_FROM_CAMERA) {
            permissionRequester.request(Manifest.permission.CAMERA, isGranted -> {
                if (isGranted) {
                    vm.prepareTakePicture();
                }
            });
            return true;

        } else if (menuItemId == R.id.MENU_THUMB_ADD_FROM_FILE_SYSTEM) {
            getFromFileLauncher.launch(IMAGE_MIME_TYPE);
            return true;

        } else if (menuItemId == R.id.MENU_THUMB_ADD_FROM_ALT_EDITIONS) {
            startBookCoverBrowser();
            return true;

        } else if (menuItemId == R.id.MENU_UNDO) {
            imageOwner.getImageUuid().ifPresent(uuid -> vm.restore(uuid, cIdx));
            return true;
        }
        return false;
    }

    /**
     * Use the isbn to fetch other possible images from the internet
     * and present to the user to choose one.
     * <p>
     * The results come back in {@link #onPictureSelected(String)}
     */
    private void startBookCoverBrowser() {
        Objects.requireNonNull(coverBrowserIsbnSupplier, "coverBrowserIsbnSupplier");
        Objects.requireNonNull(coverBrowserTitleSupplier, "coverBrowserTitleSupplier");

        final String isbnStr = coverBrowserIsbnSupplier.get();
        if (!isbnStr.isEmpty()) {
            final Code isbn = new ISBN(isbnStr, true);
            if (isbn.isValid()) {
                //noinspection DataFlowIssue
                coverBrowserLauncher.launch(fragment.getContext(),
                                            coverBrowserTitleSupplier.get(),
                                            isbn.asText(), cIdx);
                return;
            }
        }

        //noinspection DataFlowIssue
        Snackbar.make(fragment.getView(), R.string.warning_requires_isbn,
                      Snackbar.LENGTH_LONG).show();
    }

    /**
     * Called when the user clicks the large preview in the {@link CoverBrowserDialogFragment}.
     *
     * @param fileSpec the selected image
     *
     * @throws IllegalArgumentException (debug) if the fileSpec is invalid
     */
    private void onPictureSelected(@NonNull final String fileSpec) {
        vm.onPictureSelected(imageSupplier.get(), cIdx, fileSpec);
    }

    /**
     * Edit the image using an external application.
     *
     * @param input for the launcher
     */
    private void editPicture(@NonNull final EditPictureContract.Input input) {
        try {
            editPictureLauncher.launch(input);
        } catch (@NonNull final ActivityNotFoundException e) {
            //noinspection DataFlowIssue
            Snackbar.make(fragment.getView(), R.string.error_no_image_editor,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    // @RequiresPermission(Manifest.permission.CAMERA)
    private void takePicture(@NonNull final TakePictureContract.Input input) {
        try {
            takePictureLauncher.launch(input);
        } catch (@NonNull final ActivityNotFoundException e) {
            // No Camera? we should not get here as we should not have been
            // to call this method in the first place... flw
            // Fake an IOException...
            final Context context = fragment.requireContext();
            ErrorDialog.show(context, TAG,
                             new IOException(context.getString(R.string.error_unexpected), e));
        }
    }

    /**
     * Called when the user edited an image.
     *
     * @param file edited image file
     */
    private void onPictureResult(@NonNull final File file) {
        showProgress();
        vm.onPictureResult(file);
    }

    /**
     * Called when the user selected an image from storage.
     *
     * @param uri to load the new image from
     */
    private void onPictureResult(@NonNull final Uri uri) {
        showProgress();
        vm.onPictureResult(uri);
    }

    /**
     * Called when the user used their camera to take a picture.
     *
     * @param file the image
     */
    private void onTakePictureResult(@NonNull final File file) {
        showProgress();
        //noinspection DataFlowIssue
        vm.onTakePictureResult(fragment.getContext(), file);
    }

    /**
     * Rotate the image by the given angle.
     *
     * @param angle to rotate.
     */
    private void startRotation(final int angle) {
        showProgress();
        vm.startRotation(imageSupplier.get(), cIdx, angle);
    }

    private void onAfterTransform(@NonNull final TransformationResult result) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGES) {
            LoggerFactory.getLogger().d(TAG, "onAfterTransform", result);
        }

        final File file = result.getFile();
        if (file != null) {
            switch (result.getNextAction()) {
                case Crop: {
                    vm.prepareCropper(file);
                    return;
                }
                case Edit: {
                    vm.prepareEditor(file);
                    return;
                }
                case Done: {
                    vm.setImage(imageSupplier.get(), cIdx, file);
                    return;
                }
            }
        }

        // transformation failed
        vm.removeImage(imageSupplier.get(), cIdx);
    }

    private void onError(@Nullable final Throwable e) {
        if (e == null) {
            //noinspection DataFlowIssue
            Snackbar.make(fragment.getView(), R.string.warning_image_invalid,
                          Snackbar.LENGTH_LONG).show();
        } else {
            //noinspection DataFlowIssue
            ErrorDialog.show(fragment.getContext(), TAG, e);
        }
    }

    private void onInvalidImage(@Nullable final Throwable e) {
        if (e == null) {
            // No actual error occurred; the image was simply deemed not usable.
            //noinspection DataFlowIssue
            Snackbar.make(fragment.getView(), R.string.warning_image_invalid,
                          Snackbar.LENGTH_LONG).show();

        } else if (e instanceof IOException) {
            // Handle IOException directly; we *know* writing to storage went wrong
            final Context context = fragment.getContext();
            //noinspection DataFlowIssue
            ErrorDialog.show(context, TAG, e,
                             context.getString(R.string.warning_image_copy_failed),
                             context.getString(R.string.error_storage_not_writable));
        } else {
            // CoverStorageException is unlikely but possible.
            // Others very unlikely.
            //noinspection DataFlowIssue
            ErrorDialog.show(fragment.getContext(), TAG, e);
        }
    }

    private void showProgress() {
        if (progressIndicator != null && progressIndicator.getParent() != null) {
            return;
        }

        progressIndicator = new CircularProgressIndicator(imageView.getContext());
        progressIndicator.setIndeterminate(true);
        progressIndicator.setAlpha(PROGRESS_INDICATOR_ALPHA);
        progressIndicator.setElevation(PROGRESS_INDICATOR_ELEVATION);

        // Set size and constraints
        final ConstraintLayout.LayoutParams params =
                new ConstraintLayout.LayoutParams(imageView.getWidth(),
                                                  imageView.getHeight());
        params.topToTop = imageView.getId();
        params.bottomToBottom = imageView.getId();
        params.startToStart = imageView.getId();
        params.endToEnd = imageView.getId();

        progressIndicator.setLayoutParams(params);
        imageViewParent.addView(progressIndicator);
    }

    private void hideProgress() {
        if (progressIndicator != null && progressIndicator.getParent() != null) {
            imageViewParent.removeView(progressIndicator);
            progressIndicator = null;
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static class Builder {

        @NonNull
        private final Fragment fragment;
        @IntRange(from = 0, to = 3)
        private final int cIdx;
        private final int maxWidth;
        private final int maxHeight;

        private ImageView imageView;
        private ConstraintLayout imageViewParent;

        private Consumer<Integer> reloadImage;
        private Supplier<ImageOwner> imageSupplier;
        @Nullable
        private Supplier<String> coverBrowserIsbnSupplier;
        @Nullable
        private Supplier<String> coverBrowserTitleSupplier;

        @DrawableRes
        private int placeholderDrawable;

        /**
         * Constructor.
         * <p>
         * Dev. note: the width/height values come from device dp-dependent resource values.
         * (and NOT from the style image scaling factor)
         *
         * @param fragment  the hosting component
         * @param cIdx      0..n image index
         * @param maxWidth  Maximum width for an image in pixels
         * @param maxHeight Maximum height for an image in pixels
         */
        public Builder(@NonNull final Fragment fragment,
                       @IntRange(from = 0, to = 3) final int cIdx,
                       final int maxWidth,
                       final int maxHeight) {
            this.fragment = fragment;
            this.cIdx = cIdx;
            this.maxWidth = maxWidth;
            this.maxHeight = maxHeight;

            placeholderDrawable = R.drawable.add_a_photo_24px;
        }

        /**
         * Mandatory - Set the {@code ImageView}.
         * Alternatively use {@link #setImageView(ImageView, ConstraintLayout)}.
         *
         * @param imageView to use
         *
         * @return {@code this} (for chaining)
         *
         * @throws IllegalStateException (debug) if the parent is not a {@code ConstraintLayout}
         * @see #setImageView(ImageView, ConstraintLayout)
         */
        @NonNull
        public Builder setImageView(@NonNull final ImageView imageView) {
            this.imageView = imageView;
            final ViewParent parent = imageView.getParent();
            if (parent instanceof ConstraintLayout) {
                imageViewParent = (ConstraintLayout) parent;
            } else {
                throw new IllegalStateException("Parent is not a ConstraintLayout");
            }
            return this;
        }

        /**
         * Mandatory - Set the {@code ImageView}.
         * Alternatively use {@link #setImageView(ImageView)}.
         *
         * @param imageView       to use
         * @param imageViewParent to use
         *
         * @return {@code this} (for chaining)
         *
         * @throws IllegalStateException (debug) if the parent is not a {@code ConstraintLayout}
         * @noinspection WeakerAccess
         * @see #setImageView(ImageView)
         */
        @NonNull
        public Builder setImageView(@NonNull final ImageView imageView,
                                    @NonNull final ConstraintLayout imageViewParent) {
            this.imageView = imageView;
            this.imageViewParent = imageViewParent;
            return this;
        }

        /**
         * Mandatory - Tell the handler where it can get the current {@link ImageOwner} from.
         *
         * @param supplier which can provide the current {@link ImageOwner}
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder setImageOwner(@NonNull final Supplier<ImageOwner> supplier) {
            this.imageSupplier = supplier;
            return this;
        }

        /**
         * Mandatory - A callback to the host telling it to reload an image.
         *
         * @param consumer callback to reload the image at the given cIdx
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder setOnReloadImage(@NonNull final Consumer<Integer> consumer) {
            this.reloadImage = consumer;
            return this;
        }

        /**
         * <strong>Mandatory if the {@link #setImageOwner} supplier returns a book</strong>.
         * <p>
         * Tell the handler where it can get the current ISBN from.
         * This can either be directly from the book,
         * or via a Supplier which reads it from a TextView on the screen.
         *
         * @param supplier which can provide the current ISBN
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder setCoverBrowserIsbnSupplier(@Nullable final Supplier<String> supplier) {
            this.coverBrowserIsbnSupplier = supplier;
            return this;
        }

        /**
         * <strong>Mandatory if the {@link #setImageOwner} supplier returns a book</strong>.
         * <p>
         * Tell the handler where it can get the current book-title from.
         * This can either be directly from the book,
         * or via a Supplier which reads it from a TextView on the screen.
         *
         * @param supplier which can provide the current book-title
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder setCoverBrowserTitleSupplier(@Nullable final Supplier<String> supplier) {
            this.coverBrowserTitleSupplier = supplier;
            return this;
        }

        /**
         * Optional - set the drawable to use when there is no image.
         * <p>
         * Defaults to {@code R.drawable.add_a_photo_24px}.
         *
         * @param placeholderDrawable resource id
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder setPlaceholderDrawable(@DrawableRes final int placeholderDrawable) {
            this.placeholderDrawable = placeholderDrawable;
            return this;
        }

        /**
         * Build the ImageHandler. This may only be called after the Fragment View
         * is fully created.
         *
         * @return new handler
         */
        @NonNull
        public ImageHandler build() {
            Objects.requireNonNull(imageSupplier, "imageSupplier");
            Objects.requireNonNull(reloadImage, "ownerReloadCallback");

            final ImageHandler imageHandler = new ImageHandler(this);
            imageHandler.onFragmentViewCreated();
            return imageHandler;
        }
    }
}
