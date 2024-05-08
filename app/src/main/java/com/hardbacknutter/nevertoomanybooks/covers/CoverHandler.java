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

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditPictureContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.PickVisualMediaContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.TakePictureContract;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.storage.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.utils.IntListPref;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.ZoomedImageDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.settings.DialogAndMenuMode;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuLauncher;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuPopupWindow;

/**
 * A delegate class for handling a displayed Cover.
 * Offers context menus and all operations applicable on a Cover image.
 * <p>
 * <strong>Context/View dependent!</strong>
 * <p>
 * Handles displaying and zooming for cover-images on the book-details and book-edit screens.
 * <p>
 * There is one instance of this class for each displayed cover.
 * <p>
 * For BoB displaying,
 * see {@code com.hardbacknutter.nevertoomanybooks.booklist.adapter.CoverListHandler}
 */
public class CoverHandler {

    /** Log tag. */
    private static final String TAG = "CoverHandler";
    private static final String RK_MENU = TAG + ":menu";

    private static final String IMAGE_MIME_TYPE = "image/*";

    /** Index of the image we're handling. */
    @IntRange(from = 0, to = 1)
    private final int cIdx;
    @NonNull
    private final Consumer<Integer> coverLoader;
    @NonNull
    private final CoverBrowserLauncher coverBrowserLauncher;
    /** Main used is to run transformation tasks. Shared among all current CoverHandlers. */
    @NonNull
    private final CoverTransformationViewModel vm;
    @NonNull
    private final ImageViewLoader imageLoader;
    private final ExtMenuLauncher menuLauncher;
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
     * <p>
     * Dev. note: the width/height values come from device dp-dependent resource values.
     * (and NOT from the style cover scaling factor)
     *
     * @param fragment    the hosting component
     * @param cIdx        0..n image index
     * @param coverLoader callback to reload the given cIdx
     * @param maxWidth    Maximum width for a cover in pixels
     * @param maxHeight   Maximum height for a cover in pixels
     */
    public CoverHandler(@NonNull final Fragment fragment,
                        @IntRange(from = 0, to = 1) final int cIdx,
                        @NonNull final Consumer<Integer> coverLoader,
                        final int maxWidth,
                        final int maxHeight) {
        this.coverLoader = coverLoader;
        this.cIdx = cIdx;

        // We could store idx in the VM, but there really is no point
        vm = new ViewModelProvider(fragment)
                .get(String.valueOf(this.cIdx), CoverTransformationViewModel.class);

        imageLoader = new ImageViewLoader(ASyncExecutor.MAIN,
                                          ImageView.ScaleType.FIT_START,
                                          ImageViewLoader.MaxSize.Enforce,
                                          maxWidth, maxHeight);

        final FragmentManager fm = fragment.getChildFragmentManager();

        coverBrowserLauncher = new CoverBrowserLauncher(
                // Append the cIdx value!
                DialogLauncher.RK_COVER_BROWSER + cIdx,
                this::onFileSelected);

        // concat the RK with the cIdx as we have more than CoverHandler
        menuLauncher = new ExtMenuLauncher(RK_MENU + this.cIdx, this::onMenuItemSelected);
        menuLauncher.registerForFragmentResult(fm, fragment);
    }

    /**
     * Set the progress View to use.
     *
     * @param progressView to use
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public CoverHandler setProgressView(@Nullable final CircularProgressIndicator progressView) {
        progressIndicator = progressView;
        return this;
    }

    /**
     * Tell the handler where it can get the current Book from.
     *
     * @param supplier which can provide the current Book
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public CoverHandler setBookSupplier(@NonNull final Supplier<Book> supplier) {
        this.bookSupplier = supplier;
        return this;
    }

    /**
     * Tell the handler where it can get the current ISBN from.
     * This is normally a Supplier which reads it from a TextView on the screen.
     *
     * @param supplier which can provide the current ISBN
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public CoverHandler setCoverBrowserIsbnSupplier(@NonNull final Supplier<String> supplier) {
        coverBrowserIsbnSupplier = supplier;
        return this;
    }

    /**
     * Tell the handler where it can get the current book-title from.
     * This is normally a Supplier which reads it from a TextView on the screen.
     *
     * @param supplier which can provide the current book-title
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public CoverHandler setCoverBrowserTitleSupplier(@NonNull final Supplier<String> supplier) {
        coverBrowserTitleSupplier = supplier;
        return this;
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
                new EditPictureContract(), o -> o.ifPresent(this::onPictureResult));

        cropPictureLauncher = ((ActivityResultCaller) fragment).registerForActivityResult(
                new CropImageActivity.ResultContract(), o -> o.ifPresent(this::onPictureResult));


        final LifecycleOwner viewLifecycleOwner = fragment.getViewLifecycleOwner();

        // TODO: should we not just use the fragment itself as the 2nd param?
        coverBrowserLauncher.registerForFragmentResult(fragment.getChildFragmentManager(),
                                                       viewLifecycleOwner);

        vm.onFinished().observe(viewLifecycleOwner, message -> {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                LoggerFactory.getLogger().d(TAG, "onFragmentViewCreated",
                                            "vm.onFinished()|event=" + message);
            }
            hideProgress();
            message.process(this::onAfterTransform);
        });

        return this;
    }

    /**
     * Populate the view.
     *
     * @param view to update
     */
    public void onBindView(@NonNull final ImageView view) {
        // dev warning: in NO circumstances keep a reference to the view!
        final Optional<File> file = bookSupplier.get().getCover(cIdx);
        if (file.isPresent()) {
            imageLoader.fromFile(view, file.get(), null, null);
            view.setBackground(null);
        } else {
            imageLoader.placeholder(view, R.drawable.ic_baseline_add_a_photo_24);
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
            bookSupplier.get().getCover(cIdx).ifPresent(
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

        final Book book = bookSupplier.get();
        final Optional<File> coverFile = book.getCover(cIdx);

        if (coverFile.isPresent()) {
            if (BuildConfig.DEBUG /* always */) {
                // show the size of the image in the title bar
                final BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(coverFile.get().getAbsolutePath(), opts);
            }
        } else {
            // there is no current image; only show the replace menu
            final MenuItem menuItem = Objects.requireNonNull(
                    menu.findItem(R.id.SUBMENU_THUMB_REPLACE), "R.id.SUBMENU_THUMB_REPLACE");
            menu = Objects.requireNonNull(menuItem.getSubMenu(), "getSubMenu");
        }

        // we only support alternative edition covers for the front cover.
        menu.findItem(R.id.MENU_THUMB_ADD_FROM_ALT_EDITIONS).setVisible(cIdx == 0);

        // Add the potential undo-menu
        if (ServiceLocator.getInstance().getCoverStorage()
                          .isUndoEnabled(book.getString(DBKey.BOOK_UUID), cIdx)) {
            menu.add(R.id.MENU_GROUP_UNDO, R.id.MENU_UNDO, 0, R.string.option_restore_cover)
                .setIcon(R.drawable.ic_baseline_undo_24);
        }

        final DialogAndMenuMode menuMode = DialogAndMenuMode.getMenuMode(context, menu);
        if (menuMode.isPopup()) {
            new ExtMenuPopupWindow(context)
                    .setListener(this::onMenuItemSelected)
                    .setPosition(cIdx)
                    .setMenu(menu, true)
                    .show(anchor, menuMode);
        } else {
            menuLauncher.launch(context, cIdx, null, null, menu, true);
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

        final Book book = bookSupplier.get();
        final Context context = fragmentView.getContext();

        if (menuItemId == R.id.MENU_DELETE) {
            book.removeCover(cIdx);
            coverLoader.accept(cIdx);
            return true;

        } else if (menuItemId == R.id.SUBMENU_THUMB_ROTATE) {
            // TODO: this code is never reached since the menu popup/launcher handles
            //  sub-menus directly. Leaving this here as we should move this tip elsewhere.
            // Just a submenu; skip, but display a hint if user is rotating a camera image
            TipManager.getInstance()
                      .display(context, R.string.tip_autorotate_camera_images, null);
            return true;

        } else if (menuItemId == R.id.MENU_THUMB_ROTATE_CW) {
            startRotation(90);
            return true;

        } else if (menuItemId == R.id.MENU_THUMB_ROTATE_CCW) {
            startRotation(-90);
            return true;

        } else if (menuItemId == R.id.MENU_THUMB_ROTATE_180) {
            startRotation(180);
            return true;

        } else if (menuItemId == R.id.MENU_THUMB_CROP) {
            try {
                cropPictureLauncher.launch(new CropImageActivity.ResultContract.Input(
                        createTempCoverFile(book),
                        ServiceLocator.getInstance().getCoverStorage().getTempFile()));

            } catch (@NonNull final CoverStorageException e) {
                ErrorDialog.show(context, TAG, e);
            } catch (@NonNull final IOException e) {
                ErrorDialog.show(context, TAG, e);
            }
            return true;

        } else if (menuItemId == R.id.MENU_EDIT) {
            try {
                editPicture(createTempCoverFile(book));

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
            startCoverBrowser();
            return true;

        } else if (menuItemId == R.id.MENU_UNDO) {
            try {
                if (ServiceLocator.getInstance().getCoverStorage()
                                  .restore(book.getString(DBKey.BOOK_UUID), cIdx)) {
                    coverLoader.accept(cIdx);
                }
            } catch (@NonNull final IOException e) {
                ErrorDialog.show(context, TAG, e);
            }
            return true;
        }
        return false;
    }

    /**
     * Create a temporary cover File for the given book.
     * <p>
     * If there is a permanent cover, we get a <strong>copy of that one</strong>.
     * If there is no cover, we get a new File object.
     * Either way, the File returned will have a new temporary name.
     *
     * @param book for which we want a cover
     *
     * @return the File
     *
     * @throws CoverStorageException The covers directory is not available
     * @throws IOException           on failure to make a copy of the permanent file
     */
    @NonNull
    private File createTempCoverFile(@NonNull final Book book)
            throws CoverStorageException, IOException {

        // the temp file we'll return
        // do NOT set BKEY_TMP_FILE_SPEC on the book in this method.
        final File tmpFile = ServiceLocator.getInstance().getCoverStorage().getTempFile();

        // If we have a permanent file, copy it into the temp location
        final Optional<File> uuidFile = book.getCover(cIdx);
        if (uuidFile.isPresent()) {
            FileUtils.copy(uuidFile.get(), tmpFile);
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
            LoggerFactory.getLogger()
                         .e("TAG", new Throwable("createTempCoverFile"),
                            "bookId=" + book.getId()
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
                coverBrowserLauncher.launch(fragmentView.getContext(),
                                            bookTitle, isbn.asText(), cIdx);
                return;
            }
        }

        Snackbar.make(fragmentView, R.string.warning_requires_isbn, Snackbar.LENGTH_LONG).show();
    }

    /**
     * Called when the user clicks the large preview in the {@link CoverBrowserDialogFragment}.
     *
     * @param fileSpec the selected image
     *
     * @throws IllegalArgumentException if the fileSpec is invalid
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

        coverLoader.accept(cIdx);
    }

    /**
     * Edit the image using an external application.
     *
     * @param srcFile to edit
     *
     * @throws CoverStorageException The covers directory is not available
     */
    private void editPicture(@NonNull final File srcFile)
            throws CoverStorageException {
        try {
            editPictureLauncher.launch(new EditPictureContract.Input(
                    srcFile,
                    ServiceLocator.getInstance().getCoverStorage().getTempFile()));
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
        final Context context = fragmentView.getContext();
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
        final Context context = fragmentView.getContext();
        if (alreadyGranted
            || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
               == PackageManager.PERMISSION_GRANTED) {

            try {
                takePictureLauncher.launch(
                        ServiceLocator.getInstance().getCoverStorage().getTempFile());

            } catch (@NonNull final CoverStorageException e) {
                ErrorDialog.show(context, TAG, e);
            } catch (@NonNull final ActivityNotFoundException e) {
                // No Camera? we should not get here... flw
                Snackbar.make(fragmentView, R.string.error_unexpected, Snackbar.LENGTH_LONG)
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
                //noinspection DataFlowIssue
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
            final File file = createTempCoverFile(bookSupplier.get());
            showProgress();
            vm.execute(new Transformation()
                               .setSource(file)
                               .setRotation(angle),
                       file);

        } catch (@NonNull final CoverStorageException e) {
            ErrorDialog.show(fragmentView.getContext(), TAG, e);
        } catch (@NonNull final IOException e) {
            ErrorDialog.show(fragmentView.getContext(), TAG, e);
        }
    }

    private void onAfterTransform(@NonNull final TransformationTask.TransformedData result) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
            LoggerFactory.getLogger().d(TAG, "onAfterTransform", result);
        }

        final File file = result.getFile();
        if (file != null) {
            try {
                switch (result.getNextAction()) {
                    case Crop: {
                        cropPictureLauncher.launch(new CropImageActivity.ResultContract.Input(
                                file,
                                ServiceLocator.getInstance().getCoverStorage().getTempFile()));
                        return;
                    }
                    case Edit: {
                        editPicture(file);
                        return;
                    }
                    case Done: {
                        bookSupplier.get().setCover(cIdx, file);
                        // must use a post to force the View to update.
                        fragmentView.post(() -> coverLoader.accept(cIdx));
                        return;
                    }
                }
            } catch (@NonNull final StorageException e) {
                ErrorDialog.show(fragmentView.getContext(), TAG, e);
            } catch (@NonNull final IOException e) {
                ErrorDialog.show(fragmentView.getContext(), TAG, e);
            }
        }

        // transformation failed
        bookSupplier.get().removeCover(cIdx);
        // must use a post to force the View to update.
        fragmentView.post(() -> coverLoader.accept(cIdx));
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
}
