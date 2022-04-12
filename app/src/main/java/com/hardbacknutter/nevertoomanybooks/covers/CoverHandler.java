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
import android.content.SharedPreferences;
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
import androidx.preference.PreferenceManager;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
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
    private final int mCIdx;
    @NonNull
    private final CoverHandlerOwner mCoverHandlerOwner;
    @NonNull
    private final CoverBrowserDialogFragment.Launcher mCoverBrowserLauncher;
    /** Main used is to run transformation tasks. Shared among all current CoverHandlers. */
    private final CoverHandlerViewModel mVm;
    @NonNull
    private final ImageViewLoader mImageLoader;
    /** The fragment root view; used for context, resources, Snackbar. */
    private View mFragmentView;
    private ActivityResultLauncher<String> mCameraPermissionLauncher;
    private ActivityResultLauncher<Uri> mTakePictureLauncher;
    private ActivityResultLauncher<CropImageActivity.ResultContract.Input> mCropPictureLauncher;
    private ActivityResultLauncher<String> mGetFromFileLauncher;
    private ActivityResultLauncher<Intent> mEditPictureLauncher;
    private Supplier<Book> mBookSupplier;
    /** Using a Supplier so we can get the <strong>current</strong> value (e.g. when editing). */
    private Supplier<String> mCoverBrowserIsbnSupplier;
    /** Using a Supplier so we can get the <strong>current</strong> value (e.g. when editing). */
    private Supplier<String> mCoverBrowserTitleSupplier;
    /** Optional progress bar to display during operations. */
    @Nullable
    private CircularProgressIndicator mProgressIndicator;

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
        mCoverHandlerOwner = coverHandlerOwner;
        mCIdx = cIdx;

        // We could store idx in the VM, but there really is no point
        mVm = new ViewModelProvider(mCoverHandlerOwner)
                .get(String.valueOf(mCIdx), CoverHandlerViewModel.class);

        mImageLoader = new ImageViewLoader(ASyncExecutor.MAIN, maxWidth, maxHeight);

        mCoverBrowserLauncher = new CoverBrowserDialogFragment.Launcher(RK_COVER_BROWSER + mCIdx) {
            @Override
            public void onResult(@NonNull final String fileSpec) {
                onFileSelected(fileSpec);
            }
        };
    }

    public CoverHandler onFragmentViewCreated(@NonNull final Fragment fragment) {
        mFragmentView = fragment.requireView();

        mCameraPermissionLauncher = ((ActivityResultCaller) fragment).registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        takePicture(true);
                    }
                });
        mTakePictureLauncher = ((ActivityResultCaller) fragment).registerForActivityResult(
                new ActivityResultContracts.TakePicture(), this::onTakePictureResult);

        mGetFromFileLauncher = ((ActivityResultCaller) fragment).registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::onGetContentResult);

        mEditPictureLauncher = ((ActivityResultCaller) fragment).registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), this::onEditPictureResult);

        mCropPictureLauncher = ((ActivityResultCaller) fragment).registerForActivityResult(
                new CropImageActivity.ResultContract(), this::onGetContentResult);


        final LifecycleOwner lifecycleOwner = fragment.getViewLifecycleOwner();

        final FragmentManager fm = fragment.getChildFragmentManager();
        mCoverBrowserLauncher.registerForFragmentResult(fm, lifecycleOwner);

        mVm.onFinished().observe(lifecycleOwner, message -> {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                Log.d(TAG, "mTransFormTaskViewModel.onFinished()|event=" + message);
            }
            hideProgress();
            message.getData().map(TaskResult::requireResult).ifPresent(this::onAfterTransform);
        });

        return this;
    }

    public CoverHandler setProgressView(@Nullable final CircularProgressIndicator progressView) {
        mProgressIndicator = progressView;
        return this;
    }

    public CoverHandler setBookSupplier(@NonNull final Supplier<Book> bookSupplier) {
        mBookSupplier = bookSupplier;
        return this;
    }

    public void onBindView(@NonNull final ImageView view) {
        // dev warning: in NO circumstances keep a reference to the view!
        final File file = mBookSupplier.get().getCoverFile(mCIdx);
        if (file != null) {
            mImageLoader.fromFile(view, file, null);
            view.setBackground(null);
        } else {
            mImageLoader.placeholder(view, R.drawable.ic_baseline_add_a_photo_24);
            view.setBackgroundResource(R.drawable.bg_cover_not_set);
        }
    }

    public void attachOnClickListeners(@NonNull final FragmentManager fm,
                                       @NonNull final ImageView view) {
        // dev warning: in NO circumstances keep a reference to the view!
        view.setOnClickListener(v -> {
            // Allow zooming by clicking on the image;
            final File file = mBookSupplier.get().getCoverFile(mCIdx);
            if (file != null) {
                ZoomedImageDialogFragment.launch(fm, file);
            }
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

        final File uuidCoverFile = mBookSupplier.get().getCoverFile(mCIdx);
        if (uuidCoverFile != null) {
            if (BuildConfig.DEBUG /* always */) {
                // show the size of the image in the title bar
                final BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(uuidCoverFile.getAbsolutePath(), opts);
            }
        } else {
            // there is no current image; only show the replace menu
            final MenuItem menuItem = popupMenu.getMenu().findItem(R.id.SUBMENU_THUMB_REPLACE);
            popupMenu.setMenu(menuItem.getSubMenu());
        }

        // we only support alternative edition covers for the front cover.
        popupMenu.getMenu().findItem(R.id.MENU_THUMB_ADD_FROM_ALT_EDITIONS).setVisible(mCIdx == 0);

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

        final Book book = mBookSupplier.get();
        final Context context = mFragmentView.getContext();

        if (itemId == R.id.MENU_DELETE) {
            book.removeCover(mCIdx);
            mCoverHandlerOwner.reloadImage(mCIdx);
            return true;

        } else if (itemId == R.id.SUBMENU_THUMB_ROTATE) {
            // Just a submenu; skip, but display a hint if user is rotating a camera image
            if (mVm.isShowTipAboutRotating()) {
                TipManager.getInstance()
                          .display(context, R.string.tip_autorotate_camera_images, null);
                mVm.setShowTipAboutRotating(false);
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
                mCropPictureLauncher.launch(new CropImageActivity.ResultContract.Input(
                        // source
                        book.createTempCoverFile(mCIdx),
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
                editPicture(context, book.createTempCoverFile(mCIdx));

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
            mGetFromFileLauncher.launch(IMAGE_MIME_TYPE);
            return true;

        } else if (itemId == R.id.MENU_THUMB_ADD_FROM_ALT_EDITIONS) {
            startCoverBrowser();
            return true;
        }
        return false;
    }

    public CoverHandler setCoverBrowserIsbnSupplier(@NonNull final Supplier<String> supplier) {
        mCoverBrowserIsbnSupplier = supplier;
        return this;
    }

    public CoverHandler setCoverBrowserTitleSupplier(@NonNull final Supplier<String> supplier) {
        mCoverBrowserTitleSupplier = supplier;
        return this;
    }

    /**
     * Use the isbn to fetch other possible images from the internet
     * and present to the user to choose one.
     * <p>
     * The results comes back in {@link #onFileSelected(String)}
     */
    private void startCoverBrowser() {
        final Book book = mBookSupplier.get();

        final String isbnStr;
        if (mCoverBrowserIsbnSupplier != null) {
            isbnStr = mCoverBrowserIsbnSupplier.get();
        } else {
            isbnStr = book.getString(DBKey.KEY_ISBN);
        }

        if (!isbnStr.isEmpty()) {
            final ISBN isbn = ISBN.createISBN(isbnStr);
            if (isbn.isValid(true)) {
                final String bookTitle;
                if (mCoverBrowserTitleSupplier != null) {
                    bookTitle = mCoverBrowserTitleSupplier.get();
                } else {
                    bookTitle = book.getTitle();
                }
                mCoverBrowserLauncher.launch(bookTitle, isbn.asText(), mCIdx);
                return;
            }
        }

        Snackbar.make(mFragmentView, R.string.warning_requires_isbn, Snackbar.LENGTH_LONG).show();
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
                mBookSupplier.get().setCover(mCIdx, srcFile);
            } catch (@NonNull final StorageException | IOException ignore) {
                // safe to ignore, we just checked existence...
            }
        } else {
            mBookSupplier.get().removeCover(mCIdx);
        }

        mCoverHandlerOwner.reloadImage(mCIdx);
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
            Snackbar.make(mFragmentView, R.string.error_no_image_editor, Snackbar.LENGTH_LONG)
                    .show();
        } else {
            // We do not know which app will be used, so need to grant permission to all.
            for (final ResolveInfo resolveInfo : resInfoList) {
                final String packageName = resolveInfo.activityInfo.packageName;
                context.grantUriPermission(packageName, dstUri,
                                           Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }

            final String prompt = context.getString(R.string.whichEditApplication);
            mEditPictureLauncher.launch(Intent.createChooser(intent, prompt));

        }
    }

    private void onEditPictureResult(@NonNull final ActivityResult activityResult) {
        final Context context = mFragmentView.getContext();
        if (activityResult.getResultCode() == Activity.RESULT_OK) {
            try {
                final File file = getTempFile();
                if (file.exists()) {
                    showProgress();
                    mVm.execute(new Transformation(file).setScale(true), file);
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
            final Context context = mFragmentView.getContext();
            try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                // copy the data, and retrieve the (potentially) resolved file
                final File file = ImageUtils.copy(is, getTempFile());

                showProgress();
                mVm.execute(new Transformation(file).setScale(true), file);

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
     *                       i.e. when called from the {@link #mCameraPermissionLauncher}
     */
    private void takePicture(final boolean alreadyGranted) {
        final Context context = mFragmentView.getContext();
        if (alreadyGranted ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {

            try {
                final File dstFile = getTempFile();
                FileUtils.delete(dstFile);
                final Uri uri = GenericFileProvider.createUri(context, dstFile);
                mTakePictureLauncher.launch(uri);

            } catch (@NonNull final StorageException e) {
                StandardDialogs.showError(context, e.getUserMessage(context));
            }

        } else {
            mCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void onTakePictureResult(final boolean result) {
        if (result) {
            final Context context = mFragmentView.getContext();
            File file = null;
            try {
                file = getTempFile();

            } catch (@NonNull final StorageException e) {
                StandardDialogs.showError(context, e.getUserMessage(context));
            }

            if (file != null && file.exists()) {
                final SharedPreferences global = PreferenceManager
                        .getDefaultSharedPreferences(context);

                // Should we apply an explicit rotation angle?
                final int explicitRotation = Prefs
                        .getIntListPref(global, Prefs.pk_camera_image_autorotate, 0);

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
                final NextAction action = NextAction.getLevel(global);

                showProgress();
                mVm.execute(new Transformation(file)
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
        final Context context = mFragmentView.getContext();
        try {
            final File file = mBookSupplier.get().createTempCoverFile(mCIdx);
            showProgress();
            mVm.execute(new Transformation(file).setRotation(angle), file);

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
                        mCropPictureLauncher.launch(new CropImageActivity.ResultContract.Input(
                                result.getFile(), getTempFile()));
                        return;

                    case Edit:
                        editPicture(mFragmentView.getContext(), result.getFile());
                        return;

                    case Done:
                        mBookSupplier.get().setCover(mCIdx, result.getFile());
                        // must use a post to force the View to update.
                        mFragmentView.post(() -> mCoverHandlerOwner.reloadImage(mCIdx));
                        return;
                }
            } catch (@NonNull final StorageException e) {
                final Context context = mFragmentView.getContext();
                StandardDialogs.showError(context, e.getUserMessage(context));

            } catch (@NonNull final IOException e) {
                final Context context = mFragmentView.getContext();
                StandardDialogs.showError(context, ExMsg
                        .map(context, e)
                        .orElse(context.getString(R.string.error_unknown)));
            }
        }

        // transformation failed
        mBookSupplier.get().removeCover(mCIdx);
        // must use a post to force the View to update.
        mFragmentView.post(() -> mCoverHandlerOwner.reloadImage(mCIdx));
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
        return new File(CoverDir.getTemp(mFragmentView.getContext()), TAG + "_" + mCIdx + ".jpg");
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
        if (mProgressIndicator != null) {
            mProgressIndicator.hide();
        }
    }

    private void hideProgress() {
        if (mProgressIndicator != null) {
            mProgressIndicator.hide();
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
         * @param global Global preferences
         *
         * @return Validity level
         */
        @NonNull
        public static NextAction getLevel(@NonNull final SharedPreferences global) {

            final int value = Prefs.getIntListPref(global, Prefs.pk_camera_image_action,
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
