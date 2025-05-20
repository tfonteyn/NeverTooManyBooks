/*
 * @Copyright 2018-2025 HardBackNutter
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
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.PickVisualMediaContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.TakePictureContract;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.utils.IntListPref;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.dialogs.Tip;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.ZoomedImageDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.menus.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.settings.MenuMode;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuLauncher;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuPopupWindow;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * A delegate class for handling a displayed image.
 * Offers context menus and all operations applicable on an image.
 * There is one instance of this class for each displayed image.
 * <p>
 * <strong>Context/View dependent!</strong>
 * <p>
 * Handles displaying and zooming for images on the details and edit screens.
 * For BoB displaying,
 * see {@code com.hardbacknutter.nevertoomanybooks.booklist.adapter.CoverListHandler}
 * <p>
 * Dev. Note: a bit nasty: we check the type of the ImageOwner to be a Book or not...
 * we really should split this class...
 */
public final class ImageHandler {

    /** Log tag. */
    private static final String TAG = "ImageHandler";
    private static final String RK_MENU = TAG + ":rk:menu";

    private static final String IMAGE_MIME_TYPE = "image/*";
    private static final String ERROR_GENERIC_FILE_PROVIDER = "GenericFileProvider";

    /** Rotate +90 or -90 degrees. */
    private static final int TURN = 90;
    /** Rotate upside down. */
    private static final int FLIP = 180;

    /** Index of the image we're handling. */
    @IntRange(from = 0, to = 1)
    private final int cIdx;

    /** Our host. */
    @NonNull
    private final Fragment fragment;

    /** The owner of the image. We always need the current image. */
    private final Supplier<ImageOwner> imageSupplier;
    /** Callback to tell the owner to reload (and redisplay) the image after a change. */
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
     * We always us the <strong>current</strong> value (e.g. when editing).
     */
    @Nullable
    private final Supplier<String> coverBrowserIsbnSupplier;
    /**
     * Book use only; {@code null} otherwise.
     * We always us the <strong>current</strong> value (e.g. when editing).
     */
    @Nullable
    private final Supplier<String> coverBrowserTitleSupplier;

    /** Main used is to run transformation tasks. Shared among all current ImageHandlers. */
    @NonNull
    private final ImageTransformationViewModel vm;

    private final ExtMenuLauncher menuLauncher;

    /** Optional progress bar to display during operations. */
    @Nullable
    private final CircularProgressIndicator progressIndicator;
    @DrawableRes
    private final int placeholderDrawable;
    /** The fragment root view; used for context, resources, Snackbar. */
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private ActivityResultLauncher<TakePictureContract.Input> takePictureLauncher;
    private ActivityResultLauncher<EditPictureContract.Input> editPictureLauncher;

    private ActivityResultLauncher<CropImageContract.Input> cropImageLauncher;
    private ActivityResultLauncher<String> getFromFileLauncher;

    private ImageHandler(@NonNull final Builder builder) {
        fragment = builder.fragment;
        reloadImageCallback = builder.reloadImage;
        progressIndicator = builder.progressIndicator;
        placeholderDrawable = builder.placeholderDrawable;

        // We could store cIdx in the VM, but there really is no point
        cIdx = builder.cIdx;
        imageSupplier = Objects.requireNonNull(builder.imageSupplier);
        // we distinguish multiple vm in the same fragment by cIdx as the key
        vm = new ViewModelProvider(fragment)
                .get(String.valueOf(this.cIdx), ImageTransformationViewModel.class);

        imageLoader = new ImageViewLoader(ASyncExecutor.MAIN,
                                          ImageView.ScaleType.FIT_START,
                                          ImageViewLoader.MaxSize.Enforce,
                                          builder.maxWidth, builder.maxHeight);

        if (imageSupplier.get() instanceof Book) {
            coverBrowserLauncher = new CoverBrowserLauncher(cIdx, this::onFileSelected);
        } else {
            coverBrowserLauncher = null;
        }

        coverBrowserTitleSupplier = builder.coverBrowserTitleSupplier;
        coverBrowserIsbnSupplier = builder.coverBrowserIsbnSupplier;

        final FragmentManager fm = fragment.getChildFragmentManager();
        // concat the RK with the cIdx as we have more than ImageHandler
        //noinspection StringConcatenationMissingWhitespace
        menuLauncher = new ExtMenuLauncher(RK_MENU + this.cIdx, this::onMenuItemSelected);
        menuLauncher.registerForFragmentResult(fm, fragment);
    }

    /**
     * DO NOT CALL FROM CONSTRUCTOR.
     */
    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    private void onFragmentViewCreated() {

        cameraPermissionLauncher = fragment.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        takePicture(true);
                    }
                });

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

        vm.onFinished().observe(viewLifecycleOwner, message -> {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGES) {
                LoggerFactory.getLogger().d(TAG, "onFragmentViewCreated",
                                            "vm.onFinished()|event=" + message);
            }
            hideProgress();
            message.process(this::onAfterTransform);
        });
    }

    /**
     * Populate the view.
     *
     * @param view to update
     */
    public void onBindView(@NonNull final ImageView view) {
        // dev warning: in NO circumstances keep a reference to the view!
        final Optional<File> file = imageSupplier.get().getImage(view.getContext(), cIdx);
        if (file.isPresent()) {
            imageLoader.fromFile(view, file.get(), null, null);
            view.setBackground(null);
        } else {
            imageLoader.placeholder(view, placeholderDrawable);
            view.setBackgroundResource(R.drawable.bg_cover_not_set);
        }
    }

    /**
     * Set the click-listeners on the view.
     *
     * @param fragmentManager The FragmentManager
     * @param view            to update
     */
    public void attachOnClickListeners(@NonNull final FragmentManager fragmentManager,
                                       @NonNull final ImageView view) {
        // dev warning: in NO circumstances keep a reference to the view!
        view.setOnClickListener(v -> {
            // Allow zooming by clicking on the image;
            imageSupplier.get().getImage(view.getContext(), cIdx).ifPresent(
                    file -> ZoomedImageDialogFragment.launch(fragmentManager, file));
        });

        view.setOnLongClickListener(this::onCreateContextMenu);
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

        final MenuMode menuMode = MenuMode.getMode(context, menu);
        if (menuMode.isPopup()) {
            new ExtMenuPopupWindow(context)
                    .setListener(this::onMenuItemSelected)
                    .setMenuOwner(cIdx)
                    .setMenu(menu, true)
                    .show(anchor, menuMode);
        } else {
            menuLauncher.launch(context, null, null, cIdx, menu, true);
        }

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
    private boolean onMenuItemSelected(@IntRange(from = 0, to = 1) final int cIdx,
                                       @IdRes final int menuItemId) {

        final ImageOwner imageOwner = imageSupplier.get();
        final Context context = fragment.requireContext();

        if (menuItemId == R.id.MENU_DELETE) {
            imageOwner.removeImage(context, cIdx);
            reloadImageCallback.accept(cIdx);
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
            try {
                cropImageLauncher.launch(new CropImageContract.Input(
                        createTempImageFile(imageOwner),
                        ServiceLocator.getInstance().getCoverStorage().getTempFile()));

            } catch (@NonNull final CoverStorageException e) {
                ErrorDialog.show(context, TAG, e);
            } catch (@NonNull final IOException e) {
                ErrorDialog.show(context, TAG, e);
            }
            return true;

        } else if (menuItemId == R.id.MENU_EDIT) {
            try {
                editPicture(createTempImageFile(imageOwner));

            } catch (@NonNull final CoverStorageException e) {
                ErrorDialog.show(context, TAG, e);
            } catch (@NonNull final IOException e) {
                ErrorDialog.show(context, TAG, e);
            }
            return true;

        } else if (menuItemId == R.id.MENU_THUMB_ADD_FROM_CAMERA) {
            takePicture(false);
            return true;

        } else if (menuItemId == R.id.MENU_THUMB_ADD_FROM_FILE_SYSTEM) {
            getFromFileLauncher.launch(IMAGE_MIME_TYPE);
            return true;

        } else if (menuItemId == R.id.MENU_THUMB_ADD_FROM_ALT_EDITIONS) {
            startBookCoverBrowser();
            return true;

        } else if (menuItemId == R.id.MENU_UNDO) {
            try {
                if (imageOwner.getImageUuid().isPresent()) {
                    if (ServiceLocator.getInstance().getCoverStorage()
                                      .restore(imageOwner.getImageUuid().get(), cIdx)) {
                        reloadImageCallback.accept(cIdx);
                    }
                }
            } catch (@NonNull final IOException e) {
                ErrorDialog.show(context, TAG, e);
            }
            return true;
        }
        return false;
    }

    /**
     * Create a temporary File for the given {@link ImageOwner}.
     * <p>
     * If there is a permanent image, we get a <strong>copy of that one</strong>.
     * If there is no image, we get a new File object.
     * Either way, the File returned will have a new temporary name.
     *
     * @param imageOwner for which we want a image
     *
     * @return the File
     *
     * @throws CoverStorageException The images directory is not available
     * @throws IOException           on failure to make a copy of the permanent file
     */
    @NonNull
    private File createTempImageFile(@NonNull final ImageOwner imageOwner)
            throws CoverStorageException, IOException {

        // the temp file we'll return
        final File tmpFile = ServiceLocator.getInstance().getCoverStorage().getTempFile();

        // If we have a permanent file, copy it into the temp location
        //noinspection DataFlowIssue
        final Optional<File> uuidFile = imageOwner.getImage(fragment.getContext(), cIdx);
        if (uuidFile.isPresent()) {
            FileUtils.copy(uuidFile.get(), tmpFile);
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGES) {
            LoggerFactory.getLogger()
                         .e("TAG", new Throwable("createTempImageFile"),
                            "imageOwner.id=" + imageOwner.getId()
                            + "|cIdx=" + cIdx
                            + "|exists=" + tmpFile.exists()
                            + "|file=" + tmpFile.getAbsolutePath()
                         );
        }
        return tmpFile;
    }


    /**
     * Use the isbn to fetch other possible images from the internet
     * and present to the user to choose one.
     * <p>
     * The results comes back in {@link #onFileSelected(String)}
     */
    private void startBookCoverBrowser() {
        Objects.requireNonNull(coverBrowserIsbnSupplier, "coverBrowserIsbnSupplier");
        Objects.requireNonNull(coverBrowserTitleSupplier, "coverBrowserTitleSupplier");

        final String isbnStr = coverBrowserIsbnSupplier.get();
        if (!isbnStr.isEmpty()) {
            final ISBN isbn = new ISBN(isbnStr, true);
            if (isbn.isValid(true)) {
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
    private void onFileSelected(@NonNull final String fileSpec) {
        if (fileSpec.isEmpty()) {
            throw new IllegalArgumentException("fileSpec.isEmpty()");
        }

        final File srcFile = new File(fileSpec);
        final Context context = fragment.getContext();
        if (srcFile.exists()) {
            try {
                //noinspection DataFlowIssue
                imageSupplier.get().setImage(context, cIdx, srcFile);
            } catch (@NonNull final StorageException | IOException ignore) {
                // safe to ignore, we just checked existence...
            }
        } else {
            //noinspection DataFlowIssue
            imageSupplier.get().removeImage(context, cIdx);
        }

        reloadImageCallback.accept(cIdx);
    }

    /**
     * Edit the image using an external application.
     *
     * @param srcFile to edit
     *
     * @throws CoverStorageException The images directory is not available
     */
    private void editPicture(@NonNull final File srcFile)
            throws CoverStorageException {
        final Context context = fragment.requireContext();
        try {
            final File tempFile = ServiceLocator.getInstance().getCoverStorage().getTempFile();
            final EditPictureContract.Input input =
                    EditPictureContract.createInput(context, srcFile, tempFile);
            editPictureLauncher.launch(input);

        } catch (@NonNull final IllegalArgumentException e) {
            // This is a bug; a permission issue with the GenericFileProvider
            ErrorDialog.show(context, TAG, new CoverStorageException(
                    ERROR_GENERIC_FILE_PROVIDER, e));

        } catch (@NonNull final ActivityNotFoundException e) {
            //noinspection DataFlowIssue
            Snackbar.make(fragment.getView(), R.string.error_no_image_editor,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Called when the user edited an image.
     *
     * @param file edited image file
     */
    private void onPictureResult(@NonNull final File file) {
        if (file.exists()) {
            showProgress();
            vm.execute(new Transformation()
                               .setSource(file)
                               .setScale(true),
                       file);
        }
    }

    /**
     * Called when the user selected an image from storage.
     *
     * @param uri to load the new image from
     */
    private void onPictureResult(@NonNull final Uri uri) {
        final Context context = fragment.getContext();
        //noinspection DataFlowIssue
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            showProgress();

            // copy the data to a temporary file
            final File tmpFile = ServiceLocator.getInstance().getCoverStorage()
                                               .writeTempFile(is);

            vm.execute(new Transformation()
                               .setSource(tmpFile)
                               .setScale(true),
                       tmpFile);

        } catch (@NonNull final CoverStorageException e) {
            ErrorDialog.show(context, TAG, e);
        } catch (@NonNull final IOException e) {
            // Don't call generic IOException; we *know* what went wrong
            ErrorDialog.show(context, TAG, e,
                             context.getString(R.string.error_storage_not_writable),
                             context.getString(R.string.warning_image_copy_failed));
        }
    }

    /**
     * Start the camera to get an image.
     *
     * @param alreadyGranted set to {@code true} if we already got granted access.
     *                       i.e. when called from the {@link #cameraPermissionLauncher}
     */
    private void takePicture(final boolean alreadyGranted) {
        final Context context = fragment.requireContext();
        if (alreadyGranted
            || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
               == PackageManager.PERMISSION_GRANTED) {

            try {
                final File tempFile = ServiceLocator.getInstance().getCoverStorage().getTempFile();
                final TakePictureContract.Input input =
                        TakePictureContract.createInput(context, tempFile);
                takePictureLauncher.launch(input);

            } catch (@NonNull final CoverStorageException e) {
                ErrorDialog.show(context, TAG, e);

            } catch (@NonNull final IllegalArgumentException e) {
                // This is a bug; a permission issue with the GenericFileProvider
                ErrorDialog.show(context, TAG, new CoverStorageException(
                        ERROR_GENERIC_FILE_PROVIDER, e));

            } catch (@NonNull final ActivityNotFoundException e) {
                // No Camera? we should not get here as we should not have been
                // to call this method in the first place... flw
                // Fake an IOException...
                ErrorDialog.show(context, TAG,
                                 new IOException(context.getString(R.string.error_unexpected),
                                                 e));
            }

        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void onTakePictureResult(@NonNull final File file) {
        if (file.exists()) {
            final Context context = fragment.getContext();

            final int surfaceRotation;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                //noinspection DataFlowIssue
                surfaceRotation = context.getDisplay().getRotation();
            } else {
                //noinspection DataFlowIssue
                final WindowManager wm = (WindowManager)
                        context.getSystemService(Context.WINDOW_SERVICE);
                surfaceRotation = wm.getDefaultDisplay().getRotation();
            }

            // Should we apply an explicit rotation angle?
            final int explicitRotation = IntListPref
                    .getInt(context, Prefs.PK_CAMERA_IMAGE_AUTOROTATE, 0);

            // What action (if any) should we take after we're done?
            final NextAction action = NextAction.getAction(context);

            showProgress();
            vm.execute(new Transformation()
                               .setSource(file)
                               .setScale(true)
                               .setSurfaceRotation(surfaceRotation)
                               .setRotation(explicitRotation),
                       file,
                       action);
        }
    }

    /**
     * Rotate the image by the given angle.
     *
     * @param angle to rotate.
     */
    private void startRotation(final int angle) {
        try {
            final File file = createTempImageFile(imageSupplier.get());
            showProgress();
            vm.execute(new Transformation()
                               .setSource(file)
                               .setRotation(angle),
                       file);

        } catch (@NonNull final CoverStorageException e) {
            //noinspection DataFlowIssue
            ErrorDialog.show(fragment.getContext(), TAG, e);
        } catch (@NonNull final IOException e) {
            //noinspection DataFlowIssue
            ErrorDialog.show(fragment.getContext(), TAG, e);
        }
    }

    private void onAfterTransform(@NonNull final TransformationTask.TransformedData result) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGES) {
            LoggerFactory.getLogger().d(TAG, "onAfterTransform", result);
        }
        final Context context = fragment.getContext();

        final File file = result.getFile();
        if (file != null) {

            try {
                switch (result.getNextAction()) {
                    case Crop: {
                        cropImageLauncher.launch(new CropImageContract.Input(
                                file,
                                ServiceLocator.getInstance().getCoverStorage().getTempFile()));
                        return;
                    }
                    case Edit: {
                        editPicture(file);
                        return;
                    }
                    case Done: {
                        //noinspection DataFlowIssue
                        imageSupplier.get().setImage(context, cIdx, file);
                        // must use a post to force the View to update.
                        //noinspection DataFlowIssue
                        fragment.getView().post(() -> reloadImageCallback.accept(cIdx));
                        return;
                    }
                }
            } catch (@NonNull final StorageException e) {
                //noinspection DataFlowIssue
                ErrorDialog.show(context, TAG, e);
            } catch (@NonNull final IOException e) {
                ErrorDialog.show(context, TAG, e);
            }
        }

        // transformation failed
        //noinspection DataFlowIssue
        imageSupplier.get().removeImage(context, cIdx);
        // must use a post to force the View to update.
        //noinspection DataFlowIssue
        fragment.getView().post(() -> reloadImageCallback.accept(cIdx));
    }

    private void showProgress() {
        if (progressIndicator != null) {
            progressIndicator.hide();
        }
    }

    private void hideProgress() {
        if (progressIndicator != null) {
            progressIndicator.hide();
        }
    }

    public enum NextAction {
        /** After taking a picture, do nothing. */
        Done(0),
        /** After taking a picture, crop. */
        Crop(1),
        /** After taking a picture, start an editor. */
        Edit(2);

        private final int value;

        NextAction(final int value) {
            this.value = value;
        }

        /**
         * Get the user default action to take after taking a picture.
         *
         * @param context Current context
         *
         * @return next action
         */
        @NonNull
        static NextAction getAction(@NonNull final Context context) {

            final int value = IntListPref.getInt(context, Prefs.PK_CAMERA_IMAGE_ACTION,
                                                 Done.value);
            switch (value) {
                case 2:
                    return Edit;
                case 1:
                    return Crop;
                case 0:
                default:
                    return Done;
            }
        }
    }

    public static class Builder {

        @NonNull
        private final Fragment fragment;
        private final int cIdx;
        private final int maxWidth;
        private final int maxHeight;

        private Consumer<Integer> reloadImage;
        private Supplier<ImageOwner> imageSupplier;
        @Nullable
        private CircularProgressIndicator progressIndicator;
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
                       @IntRange(from = 0, to = 1) final int cIdx,
                       final int maxWidth,
                       final int maxHeight) {
            this.fragment = fragment;
            this.cIdx = cIdx;
            this.maxWidth = maxWidth;
            this.maxHeight = maxHeight;

            placeholderDrawable = R.drawable.add_a_photo_24px;
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
         * Optional - Set the progress View to use.
         *
         * @param view to use
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder setProgressIndicator(@Nullable final CircularProgressIndicator view) {
            this.progressIndicator = view;
            return this;
        }

        /**
         * Optional - <strong>Only set if {@link #setImageOwner} returns a book</strong>.
         * <p>
         * Tell the handler where it can get the current ISBN from.
         * This is normally a Supplier which reads it from a TextView on the screen.
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
         * Optional - <strong>Only set if {@link #setImageOwner} returns a book</strong>.
         * <p>
         * Tell the handler where it can get the current book-title from.
         * This is normally a Supplier which reads it from a TextView on the screen.
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

            if (imageSupplier.get() instanceof Book) {
                if (coverBrowserTitleSupplier == null) {
                    coverBrowserTitleSupplier = () -> ((Book) (imageSupplier.get())).getTitle();
                }
                if (coverBrowserIsbnSupplier == null) {
                    coverBrowserIsbnSupplier = () -> ((Book) (imageSupplier.get())).getIsbn();
                }
            }

            final ImageHandler imageHandler = new ImageHandler(this);
            imageHandler.onFragmentViewCreated();
            return imageHandler;
        }
    }
}
