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

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.activity.result.ActivityResult;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.ZoomedImageDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.GenericFileProvider;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;
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

    /** FragmentResultListener request key. Append the mCIdx value! */
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
    private final CoverHandlerViewModel vm;
    @NonNull
    private final ImageViewLoader imageLoader;
    /** The fragment root view; used for context, resources, Snackbar. */
    private View fragmentView;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ActivityResultLauncher<CropImageActivity.ResultContract.Input> cropPictureLauncher;
    private ActivityResultLauncher<String> getFromFileLauncher;
    private ActivityResultLauncher<Intent> editPictureLauncher;
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

    public CoverHandler onFragmentViewCreated(@NonNull final Fragment fragment) {
        fragmentView = fragment.requireView();

        cameraPermissionLauncher = ((ActivityResultCaller) fragment).registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        takePicture(true);
                    }
                });
        takePictureLauncher = ((ActivityResultCaller) fragment).registerForActivityResult(
                new ActivityResultContracts.TakePicture(), this::onTakePictureResult);

        getFromFileLauncher = ((ActivityResultCaller) fragment).registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::onGetContentResult);

        editPictureLauncher = ((ActivityResultCaller) fragment).registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), this::onEditPictureResult);

        cropPictureLauncher = ((ActivityResultCaller) fragment).registerForActivityResult(
                new CropImageActivity.ResultContract(), this::onGetContentResult);


        final LifecycleOwner lifecycleOwner = fragment.getViewLifecycleOwner();

        final FragmentManager fm = fragment.getChildFragmentManager();
        coverBrowserLauncher.registerForFragmentResult(fm, RK_COVER_BROWSER + cIdx,
                                                       lifecycleOwner);

        vm.onFinished().observe(lifecycleOwner, message -> {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                Log.d(TAG, "mTransFormTaskViewModel.onFinished()|event=" + message);
            }
            hideProgress();
            message.getData().map(TaskResult::requireResult).ifPresent(this::onAfterTransform);
        });

        return this;
    }

    public CoverHandler setProgressView(@Nullable final CircularProgressIndicator progressView) {
        progressIndicator = progressView;
        return this;
    }

    public CoverHandler setBookSupplier(@NonNull final Supplier<Book> bookSupplier) {
        this.bookSupplier = bookSupplier;
        return this;
    }

    public void onBindView(@NonNull final ImageView view) {
        // dev warning: in NO circumstances keep a reference to the view!
        final Optional<File> file = bookSupplier.get().getCoverFile(cIdx);
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
            bookSupplier.get()
                        .getCoverFile(cIdx).
                        ifPresent(file -> ZoomedImageDialogFragment.launch(fm, file));
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

        final Optional<File> uuidCoverFile = bookSupplier.get().getCoverFile(cIdx);
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
                        book.createTempCoverFile(cIdx),
                        // destination
                        getTempFile()));

            } catch (@NonNull final StorageException e) {
                StandardDialogs.showError(context, e.getUserMessage(context));

            } catch (@NonNull final IOException e) {
                StandardDialogs.showError(context, ExMsg
                        .map(context, e)
                        .orElse(context.getString(R.string.error_unknown)));
            }
            return true;

        } else if (itemId == R.id.MENU_EDIT) {
            try {
                editPicture(context, book.createTempCoverFile(cIdx));

            } catch (@NonNull final StorageException e) {
                StandardDialogs.showError(context, e.getUserMessage(context));

            } catch (@NonNull final IOException e) {
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

    public CoverHandler setCoverBrowserIsbnSupplier(@NonNull final Supplier<String> supplier) {
        coverBrowserIsbnSupplier = supplier;
        return this;
    }

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
            final ISBN isbn = ISBN.createISBN(isbnStr);
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
        SanityCheck.requireValue(fileSpec, "fileSpec");

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
     * @param context Current context
     * @param srcFile to edit
     *
     * @throws StorageException The covers directory is not available
     */
    private void editPicture(@NonNull final Context context,
                             @NonNull final File srcFile)
            throws StorageException {

        final File dstFile = getTempFile();
        FileUtils.delete(dstFile);

        //TODO: we really should revoke the permissions afterwards
        final Uri srcUri = GenericFileProvider.createUri(context, srcFile);
        final Uri dstUri = GenericFileProvider.createUri(context, dstFile);

        // <manifest ...>
        //
        // Needed since Android 11:
        // https://developer.android.com/training/basics/intents/package-visibility#intent-signature
        //       <queries>
        //        <intent>
        //            <action android:name="android.intent.action.EDIT" />
        //            <data android:mimeType="image/*" />
        //        </intent>
        //    </queries>
        //    <application ...>
        //
        // Needed for all Android versions:
        // Do NOT set exported="true"; the app will fail to start.
        // We handle this using grantUriPermissions.
        //
        //        <provider
        //            android:name=".utils.GenericFileProvider"
        //            android:authorities="${applicationId}.GenericFileProvider"
        //            android:exported="false"
        //            android:grantUriPermissions="true">
        //            <meta-data
        //                android:name="android.support.FILE_PROVIDER_PATHS"
        //                android:resource="@xml/provider_paths" />
        //        </provider>

        final Intent intent = new Intent(Intent.ACTION_EDIT)
                .setDataAndType(srcUri, IMAGE_MIME_TYPE)
                // read access to the input uri
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // write access see below
                .putExtra(MediaStore.EXTRA_OUTPUT, dstUri);

        final List<ResolveInfo> resInfoList =
                context.getPackageManager()
                       .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (resInfoList.isEmpty()) {
            Snackbar.make(fragmentView, R.string.error_no_image_editor, Snackbar.LENGTH_LONG)
                    .show();
        } else {
            // We do not know which app will be used, so need to grant permission to all.
            for (final ResolveInfo resolveInfo : resInfoList) {
                final String packageName = resolveInfo.activityInfo.packageName;
                context.grantUriPermission(packageName, dstUri,
                                           Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }

            final String prompt = context.getString(R.string.whichEditApplication);
            editPictureLauncher.launch(Intent.createChooser(intent, prompt));

        }
    }

    private void onEditPictureResult(@NonNull final ActivityResult activityResult) {
        final Context context = fragmentView.getContext();
        if (activityResult.getResultCode() == Activity.RESULT_OK) {
            try {
                final File file = getTempFile();
                if (file.exists()) {
                    showProgress();
                    vm.execute(new Transformation(file).setScale(true), file);
                    return;
                }
            } catch (@NonNull final StorageException e) {
                StandardDialogs.showError(context, e.getUserMessage(context));
            }
        }

        removeTempFile();
    }

    /**
     * Called when the user selected an image from storage,
     * or after the cropping an image.
     *
     * @param uri to load the image from
     */
    private void onGetContentResult(@Nullable final Uri uri) {
        if (uri != null) {
            final Context context = fragmentView.getContext();
            try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                // copy the data, and retrieve the (potentially) resolved file
                final File file = ImageUtils.copy(is, getTempFile());

                showProgress();
                vm.execute(new Transformation(file).setScale(true), file);

            } catch (@NonNull final StorageException e) {
                StandardDialogs.showError(context, e.getUserMessage(context));

            } catch (@NonNull final IOException e) {
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "Unable to copy content to file", e);
                }

                StandardDialogs.showError(context, ExMsg
                        .map(context, e)
                        .orElse(context.getString(R.string.warning_image_copy_failed)));
            }
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
        if (alreadyGranted ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {

            try {
                final File dstFile = getTempFile();
                FileUtils.delete(dstFile);
                final Uri uri = GenericFileProvider.createUri(context, dstFile);
                takePictureLauncher.launch(uri);

            } catch (@NonNull final StorageException e) {
                StandardDialogs.showError(context, e.getUserMessage(context));
            }

        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void onTakePictureResult(final boolean result) {
        if (result) {
            final Context context = fragmentView.getContext();
            File file = null;
            try {
                file = getTempFile();

            } catch (@NonNull final StorageException e) {
                StandardDialogs.showError(context, e.getUserMessage(context));
            }

            if (file != null && file.exists()) {
                // Should we apply an explicit rotation angle?
                final int explicitRotation = Prefs
                        .getIntListPref(context, Prefs.pk_camera_image_autorotate, 0);

                final int surfaceRotation;
                if (Build.VERSION.SDK_INT >= 30) {
                    //noinspection ConstantConditions
                    surfaceRotation = context.getDisplay().getRotation();
                } else {
                    final WindowManager wm = (WindowManager)
                            context.getSystemService(Context.WINDOW_SERVICE);
                    surfaceRotation = wm.getDefaultDisplay().getRotation();
                }

                // What action (if any) should we take after we're done?
                final NextAction action = NextAction.getLevel(context);

                showProgress();
                vm.execute(new Transformation(file)
                                   .setScale(true)
                                   .setSurfaceRotation(surfaceRotation)
                                   .setRotation(explicitRotation),
                           file,
                           action);
            }
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
            final File file = bookSupplier.get().createTempCoverFile(cIdx);
            showProgress();
            vm.execute(new Transformation(file).setRotation(angle), file);

        } catch (@NonNull final StorageException e) {
            StandardDialogs.showError(context, e.getUserMessage(context));

        } catch (@NonNull final IOException e) {
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
                        editPicture(fragmentView.getContext(), result.getFile());
                        return;

                    case Done:
                        bookSupplier.get().setCover(cIdx, result.getFile());
                        // must use a post to force the View to update.
                        fragmentView.post(() -> coverHandlerOwner.reloadImage(cIdx));
                        return;
                }
            } catch (@NonNull final StorageException e) {
                final Context context = fragmentView.getContext();
                StandardDialogs.showError(context, e.getUserMessage(context));

            } catch (@NonNull final IOException e) {
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

        public final int value;

        NextAction(final int value) {
            this.value = value;
        }

        /**
         * Get the user preferred ISBN validity level check for (by the user) editing ISBN codes.
         *
         * @param context Current context
         *
         * @return Validity level
         */
        @NonNull
        public static NextAction getLevel(@NonNull final Context context) {

            final int value = Prefs.getIntListPref(context, Prefs.pk_camera_image_action,
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
