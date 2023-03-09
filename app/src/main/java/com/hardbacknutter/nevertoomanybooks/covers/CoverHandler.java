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

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditPictureContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.PickVisualMediaContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.TakePictureContract;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.utils.IntListPref;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.ZoomedImageDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtPopupMenu;

/**
 * A delegate class for handling a displayed Cover.
 * Offers context menus and all operations applicable on a Cover image.
 * <p>
 * Context/View dependent!
 */
public class CoverHandler {

    /** Log tag. */
    private static final String TAG = "CoverHandler";

    /** FragmentResultListener request key. Append the cIdx value! */
    private static final String RK_COVER_BROWSER = TAG + ":rk:" + CoverBrowserDialogFragment.TAG;

    private static final String IMAGE_MIME_TYPE = "image/*";

    /** Index of the image we're handling. */
    @IntRange(from = 0, to = 1)
    private final int cIdx;
    @NonNull
    private final CoverHandlerOwner coverHandlerOwner;
    @NonNull
    private final CoverBrowserDialogFragment.Launcher coverBrowserLauncher;
    /** Main used is to run transformation tasks. Shared among all current CoverHandlers. */
    @NonNull
    private final CoverHandlerViewModel vm;
    @NonNull
    private final ImageViewLoader imageLoader;
    /** The fragment root view; used for context, resources, Snackbar. */
    private View fragmentView;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private ActivityResultLauncher<File> takePictureLauncher;
    private ActivityResultLauncher<CropImageActivity.ResultContract.Input> cropPictureLauncher;
    private ActivityResultLauncher<String> getFromFileLauncher;
    private ActivityResultLauncher<EditPictureContract.Input> editPictureLauncher;
    private Supplier<Book> bookSupplier;
    /** Using a Supplier so we can get the <strong>current</strong> value (e.g. when editing). */
    private Supplier<String> coverBrowserIsbnSupplier;
    /** Using a Supplier so we can get the <strong>current</strong> value (e.g. when editing). */
    private Supplier<String> coverBrowserTitleSupplier;
    /** Optional progress bar to display during operations. */
    @Nullable
    private CircularProgressIndicator progressIndicator;

    /**
     * Constructor.
     *
     * @param coverHandlerOwner the hosting component
     * @param cIdx              0..n image index
     * @param maxWidth          the maximum width for the cover
     * @param maxHeight         the maximum height for the cover
     */
    public CoverHandler(@NonNull final CoverHandlerOwner coverHandlerOwner,
                        @IntRange(from = 0, to = 1) final int cIdx,
                        final int maxWidth,
                        final int maxHeight) {
        this.coverHandlerOwner = coverHandlerOwner;
        this.cIdx = cIdx;

        // We could store idx in the VM, but there really is no point
        vm = new ViewModelProvider(this.coverHandlerOwner)
                .get(String.valueOf(this.cIdx), CoverHandlerViewModel.class);

        imageLoader = new ImageViewLoader(ASyncExecutor.MAIN, maxWidth, maxHeight);

        coverBrowserLauncher = new CoverBrowserDialogFragment.Launcher() {
            @Override
            public void onResult(@NonNull final String fileSpec) {
                onFileSelected(fileSpec);
            }
        };
    }

    @NonNull
    public CoverHandler onFragmentViewCreated(@NonNull final Fragment fragment) {
        fragmentView = fragment.requireView();

        cameraPermissionLauncher = ((ActivityResultCaller) fragment).registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        takePicture(true);
                    }
                });

        takePictureLauncher = ((ActivityResultCaller) fragment).registerForActivityResult(
                new TakePictureContract(), o -> o.ifPresent(this::onTakePictureResult));

        getFromFileLauncher = ((ActivityResultCaller) fragment).registerForActivityResult(
                new PickVisualMediaContract(), o -> o.ifPresent(this::onPictureResult));

        editPictureLauncher = ((ActivityResultCaller) fragment).registerForActivityResult(
                new EditPictureContract(), o -> o.ifPresent(this::onEditPictureResult));

        cropPictureLauncher = ((ActivityResultCaller) fragment).registerForActivityResult(
                new CropImageActivity.ResultContract(), o -> o.ifPresent(this::onPictureResult));


        final LifecycleOwner lifecycleOwner = fragment.getViewLifecycleOwner();

        final FragmentManager fm = fragment.getChildFragmentManager();
        coverBrowserLauncher.registerForFragmentResult(fm, RK_COVER_BROWSER + cIdx,
                                                       lifecycleOwner);

        vm.onFinished().observe(lifecycleOwner, message -> {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                Log.d(TAG, "vm.onFinished()|event=" + message);
            }
            hideProgress();
            message.getData().map(TaskResult::requireResult).ifPresent(this::onAfterTransform);
        });

        return this;
    }

    @NonNull
    public CoverHandler setProgressView(@Nullable final CircularProgressIndicator progressView) {
        progressIndicator = progressView;
        return this;
    }

    @NonNull
    public CoverHandler setBookSupplier(@NonNull final Supplier<Book> bookSupplier) {
        this.bookSupplier = bookSupplier;
        return this;
    }

    public void onBindView(@NonNull final ImageView view) {
        // dev warning: in NO circumstances keep a reference to the view!
        final Optional<File> file = bookSupplier.get().getCover(cIdx);
        if (file.isPresent()) {
            imageLoader.fromFile(view, file.get(), null);
            view.setBackground(null);
        } else {
            imageLoader.placeholder(view, R.drawable.ic_baseline_add_a_photo_24);
            view.setBackgroundResource(R.drawable.bg_cover_not_set);
        }
    }

    public void attachOnClickListeners(@NonNull final FragmentManager fm,
                                       @NonNull final ImageView view) {
        // dev warning: in NO circumstances keep a reference to the view!
        view.setOnClickListener(v -> {
            // Allow zooming by clicking on the image;
            bookSupplier.get().getCover(cIdx)
                        .ifPresent(file -> ZoomedImageDialogFragment.launch(fm, file));
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

        final ExtPopupMenu popupMenu = new ExtPopupMenu(anchor.getContext())
                .inflate(R.menu.image);

        final Optional<File> uuidCoverFile = bookSupplier.get().getCover(cIdx);
        if (uuidCoverFile.isPresent()) {
            if (BuildConfig.DEBUG /* always */) {
                // show the size of the image in the title bar
                final BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(uuidCoverFile.get().getAbsolutePath(), opts);
            }
        } else {
            // there is no current image; only show the replace menu
            final MenuItem menuItem = popupMenu.getMenu().findItem(R.id.SUBMENU_THUMB_REPLACE);
            //noinspection ConstantConditions
            popupMenu.setMenu(menuItem.getSubMenu());
        }

        // we only support alternative edition covers for the front cover.
        popupMenu.getMenu().findItem(R.id.MENU_THUMB_ADD_FROM_ALT_EDITIONS).setVisible(cIdx == 0);

        popupMenu.showAsDropDown(anchor, this::onMenuItemSelected);

        return true;
    }

    /**
     * Using {@link ExtPopupMenu} for context menus.
     *
     * @param menuItem that was selected
     *
     * @return {@code true} if handled.
     */
    private boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
        final int itemId = menuItem.getItemId();

        final Book book = bookSupplier.get();
        final Context context = fragmentView.getContext();

        if (itemId == R.id.MENU_DELETE) {
            book.removeCover(cIdx);
            coverHandlerOwner.reloadImage(cIdx);
            return true;

        } else if (itemId == R.id.SUBMENU_THUMB_ROTATE) {
            // Just a submenu; skip, but display a hint if user is rotating a camera image
            if (vm.isShowTipAboutRotating()) {
                TipManager.getInstance()
                          .display(context, R.string.tip_autorotate_camera_images, null);
                vm.setShowTipAboutRotating(false);
            }
            return true;

        } else if (itemId == R.id.MENU_THUMB_ROTATE_CW) {
            startRotation(90);
            return true;

        } else if (itemId == R.id.MENU_THUMB_ROTATE_CCW) {
            startRotation(-90);
            return true;

        } else if (itemId == R.id.MENU_THUMB_ROTATE_180) {
            startRotation(180);
            return true;

        } else if (itemId == R.id.MENU_THUMB_CROP) {
            try {
                cropPictureLauncher.launch(new CropImageActivity.ResultContract.Input(
                        // source
                        createTempCoverFile(book),
                        // destination
                        getTempFile()));

            } catch (@NonNull final StorageException | IOException e) {
                StandardDialogs.showError(context, ExMsg
                        .map(context, e)
                        .orElse(context.getString(R.string.error_unknown)));

            }
            return true;

        } else if (itemId == R.id.MENU_EDIT) {
            try {
                editPicture(createTempCoverFile(book));

            } catch (@NonNull final StorageException | IOException e) {
                StandardDialogs.showError(context, ExMsg
                        .map(context, e)
                        .orElse(context.getString(R.string.error_unknown)));
            }
            return true;

        } else if (itemId == R.id.MENU_THUMB_ADD_FROM_CAMERA) {
            takePicture(false);
            return true;

        } else if (itemId == R.id.MENU_THUMB_ADD_FROM_FILE_SYSTEM) {
            getFromFileLauncher.launch(IMAGE_MIME_TYPE);
            return true;

        } else if (itemId == R.id.MENU_THUMB_ADD_FROM_ALT_EDITIONS) {
            startCoverBrowser();
            return true;
        }
        return false;
    }

    /**
     * Create a temporary cover file for the given book.
     * If there is a permanent cover, we get a <strong>copy of that one</strong>.
     * If there is no cover, we get a new File object with a temporary name.
     *
     * @return the File
     *
     * @throws StorageException The covers directory is not available
     * @throws IOException      on failure to make a copy of the permanent file
     */
    @NonNull
    private File createTempCoverFile(@NonNull final Book book)
            throws StorageException, IOException {

        // the temp file we'll return
        // do NOT set BKEY_TMP_FILE_SPEC on the book in this method.
        final File coverFile = new File(CoverDir.getTemp(fragmentView.getContext()),
                                        System.nanoTime() + ".jpg");

        // If we have a permanent file, copy it into the temp location
        final Optional<File> uuidFile = book.getCover(cIdx);
        if (uuidFile.isPresent()) {
            FileUtils.copy(uuidFile.get(), coverFile);
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
            LoggerFactory.getLogger()
                         .e("TAG", new Throwable("createTempCoverFile"),
                            "bookId=" + book.getId()
                            + "|cIdx=" + cIdx
                            + "|exists=" + coverFile.exists()
                            + "|file=" + coverFile.getAbsolutePath()
                         );
        }
        return coverFile;
    }

    @NonNull
    public CoverHandler setCoverBrowserIsbnSupplier(@NonNull final Supplier<String> supplier) {
        coverBrowserIsbnSupplier = supplier;
        return this;
    }

    @NonNull
    public CoverHandler setCoverBrowserTitleSupplier(@NonNull final Supplier<String> supplier) {
        coverBrowserTitleSupplier = supplier;
        return this;
    }

    /**
     * Use the isbn to fetch other possible images from the internet
     * and present to the user to choose one.
     * <p>
     * The results comes back in {@link #onFileSelected(String)}
     */
    private void startCoverBrowser() {
        final Book book = bookSupplier.get();

        final String isbnStr;
        if (coverBrowserIsbnSupplier != null) {
            isbnStr = coverBrowserIsbnSupplier.get();
        } else {
            isbnStr = book.getString(DBKey.BOOK_ISBN);
        }

        if (!isbnStr.isEmpty()) {
            final ISBN isbn = new ISBN(isbnStr, true);
            if (isbn.isValid(true)) {
                final String bookTitle;
                if (coverBrowserTitleSupplier != null) {
                    bookTitle = coverBrowserTitleSupplier.get();
                } else {
                    bookTitle = book.getTitle();
                }
                coverBrowserLauncher.launch(bookTitle, isbn.asText(), cIdx);
                return;
            }
        }

        Snackbar.make(fragmentView, R.string.warning_requires_isbn, Snackbar.LENGTH_LONG).show();
    }

    /**
     * Called when the user clicks the large preview in the {@link CoverBrowserDialogFragment}.
     *
     * @param fileSpec the selected image
     */
    private void onFileSelected(@NonNull final String fileSpec) {
        if (fileSpec.isEmpty()) {
            throw new IllegalArgumentException("fileSpec.isEmpty()");
        }

        final File srcFile = new File(fileSpec);
        if (srcFile.exists()) {
            try {
                bookSupplier.get().setCover(cIdx, srcFile);
            } catch (@NonNull final StorageException | IOException ignore) {
                // safe to ignore, we just checked existence...
            }
        } else {
            bookSupplier.get().removeCover(cIdx);
        }

        coverHandlerOwner.reloadImage(cIdx);
    }

    /**
     * Edit the image using an external application.
     *
     * @param srcFile to edit
     *
     * @throws StorageException The covers directory is not available
     */
    private void editPicture(@NonNull final File srcFile)
            throws StorageException {

        final File dstFile = getTempFile();
        FileUtils.delete(dstFile);
        try {
            editPictureLauncher.launch(new EditPictureContract.Input(srcFile, dstFile));
        } catch (@NonNull final ActivityNotFoundException e) {
            Snackbar.make(fragmentView, R.string.error_no_image_editor, Snackbar.LENGTH_LONG)
                    .show();
        }
    }

    /**
     * Called when the user edited an image.
     *
     * @param file edited image file
     */
    private void onEditPictureResult(@NonNull final File file) {
        if (file.exists()) {
            showProgress();
            vm.execute(new Transformation(file).setScale(true), file);
            return;
        }

        removeTempFile();
    }

    /**
     * Called when the user selected an image from storage,
     * or after cropping an image.
     *
     * @param uri to load the new image from
     */
    private void onPictureResult(@NonNull final Uri uri) {
        final Context context = fragmentView.getContext();
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            // copy the data, and retrieve the (potentially) resolved file
            final File file = ImageUtils.copy(is, getTempFile());

            showProgress();
            vm.execute(new Transformation(file).setScale(true), file);

        } catch (@NonNull final StorageException | IOException e) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "Unable to copy content to file", e);
            }

            StandardDialogs.showError(context, ExMsg
                    .map(context, e)
                    .orElse(context.getString(R.string.warning_image_copy_failed)));
        }
    }

    /**
     * Start the camera to get an image.
     *
     * @param alreadyGranted set to {@code true} if we already got granted access.
     *                       i.e. when called from the {@link #cameraPermissionLauncher}
     */
    private void takePicture(final boolean alreadyGranted) {
        final Context context = fragmentView.getContext();
        if (alreadyGranted
            || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
               == PackageManager.PERMISSION_GRANTED) {

            try {
                final File dstFile = getTempFile();
                FileUtils.delete(dstFile);
                takePictureLauncher.launch(dstFile);

            } catch (@NonNull final StorageException e) {
                StandardDialogs.showError(context, ExMsg
                        .map(context, e)
                        .orElse(context.getString(R.string.error_unknown)));

            } catch (@NonNull final ActivityNotFoundException e) {
                // No Camera? we should not get here... flw
                Snackbar.make(fragmentView, R.string.error_unknown, Snackbar.LENGTH_LONG)
                        .show();
            }

        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void onTakePictureResult(@NonNull final File file) {
        if (file.exists()) {
            final Context context = fragmentView.getContext();

            final int surfaceRotation;
            if (Build.VERSION.SDK_INT >= 30) {
                //noinspection ConstantConditions
                surfaceRotation = context.getDisplay().getRotation();
            } else {
                final WindowManager wm = (WindowManager)
                        context.getSystemService(Context.WINDOW_SERVICE);
                surfaceRotation = wm.getDefaultDisplay().getRotation();
            }

            // Should we apply an explicit rotation angle?
            final int explicitRotation = IntListPref
                    .getInt(context, Prefs.pk_camera_image_autorotate, 0);

            // What action (if any) should we take after we're done?
            final NextAction action = NextAction.getAction(context);

            showProgress();
            vm.execute(new Transformation(file)
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
        final Context context = fragmentView.getContext();
        try {
            final File file = createTempCoverFile(bookSupplier.get());
            showProgress();
            vm.execute(new Transformation(file).setRotation(angle), file);

        } catch (@NonNull final StorageException | IOException e) {
            StandardDialogs.showError(context, ExMsg
                    .map(context, e)
                    .orElse(context.getString(R.string.error_unknown)));
        }
    }

    private void onAfterTransform(@NonNull final TransFormTask.TransformedData result) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
            Log.d(TAG, "onAfterTransform: " + result);
        }

        // The bitmap != null decides if the operation was successful.
        if (null != result.getBitmap()) {
            // sanity check: if the bitmap was good, the file will be good.
            Objects.requireNonNull(result.getFile(), "file");
            try {
                switch (result.getNextAction()) {
                    case Crop:
                        cropPictureLauncher.launch(new CropImageActivity.ResultContract.Input(
                                result.getFile(), getTempFile()));
                        return;

                    case Edit:
                        editPicture(result.getFile());
                        return;

                    case Done:
                        bookSupplier.get().setCover(cIdx, result.getFile());
                        // must use a post to force the View to update.
                        fragmentView.post(() -> coverHandlerOwner.reloadImage(cIdx));
                        return;
                }
            } catch (@NonNull final StorageException | IOException e) {
                final Context context = fragmentView.getContext();
                StandardDialogs.showError(context, ExMsg
                        .map(context, e)
                        .orElse(context.getString(R.string.error_unknown)));
            }
        }

        // transformation failed
        bookSupplier.get().removeCover(cIdx);
        // must use a post to force the View to update.
        fragmentView.post(() -> coverHandlerOwner.reloadImage(cIdx));
    }

    /**
     * Get the temporary file.
     *
     * @return file
     *
     * @throws StorageException The covers directory is not available
     */
    @NonNull
    private File getTempFile()
            throws StorageException {
        return new File(CoverDir.getTemp(fragmentView.getContext()), TAG + "_" + cIdx + ".jpg");
    }

    /**
     * remove any orphaned file.
     */
    private void removeTempFile() {
        try {
            FileUtils.delete(getTempFile());
        } catch (@NonNull final StorageException ignore) {
            // safe to ignore, just a delete
        }
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

            final int value = IntListPref.getInt(context, Prefs.pk_camera_image_action,
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

    public interface CoverHandlerOwner
            extends ViewModelStoreOwner {

        /**
         * Reload the given image into its View.
         *
         * @param cIdx 0..n image index
         */
        void reloadImage(int cIdx);
    }
}
