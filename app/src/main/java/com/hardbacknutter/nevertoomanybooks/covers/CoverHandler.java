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

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.dialogs.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.MenuPickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.ZoomedImageDialogFragment;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.GenericFileProvider;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookViewModel;

/**
 * Handler for a displayed Cover ImageView element.
 * Offers context menus and all operations applicable on a Cover image.
 */
public class CoverHandler {

    /** Log tag. */
    private static final String TAG = "CoverHandler";

    /** FragmentResultListener request key. Append the mCIdx value! */
    private static final String RK_MENU_PICKER = TAG + ":rk:" + MenuPickerDialogFragment.TAG;

    /** FragmentResultListener request key. Append the mCIdx value! */
    private static final String RK_COVER_BROWSER = TAG + ":rk:" + CoverBrowserDialogFragment.TAG;

    /**
     * After taking a picture, do nothing. Never change the value.
     * This is stored in user preferences.
     */
    private static final int ACTION_DONE = 0;
    /** After taking a picture, crop. */
    private static final int ACTION_CROP = 1;
    /** After taking a picture, edit. */
    private static final int ACTION_EDIT = 2;

    private static final String IMAGE_MIME_TYPE = "image/*";
    /** The file name we'll use. */
    private final String TEMP_COVER_FILENAME;

    /** The fragment hosting us. Should implement HostingFragment. */
    @NonNull
    private final Fragment mFragment;
    /** Index of the image we're handling. */
    @IntRange(from = 0, to = 1)
    private final int mCIdx;
    private final int mMaxWidth;
    private final int mMaxHeight;

    @NonNull
    private final TransFormTaskViewModel mTransFormTaskViewModel;
    @NonNull
    private final BookViewModel mBookViewModel;

    @NonNull
    private final ActivityResultLauncher<String> mCameraPermissionLauncher;
    @NonNull
    private final ActivityResultLauncher<Uri> mTakePictureLauncher;
    @NonNull
    private final ActivityResultLauncher<CropImageActivity.ResultContract.Input>
            mCropPictureLauncher;
    @NonNull
    private final ActivityResultLauncher<String> mGetFromFileLauncher;
    @NonNull
    private final ActivityResultLauncher<Intent> mEditPictureLauncher;

    @NonNull
    private final CoverBrowserDialogFragment.Launcher mCoverBrowserLauncher =
            new CoverBrowserDialogFragment.Launcher() {
                @Override
                public void onResult(@NonNull final String fileSpec) {
                    onFileSelected(fileSpec);
                }
            };

    private final MenuPickerDialogFragment.Launcher mMenuLauncher =
            new MenuPickerDialogFragment.Launcher() {
                @Override
                public boolean onResult(@IdRes final int menuItemId,
                                        final int position) {
                    return onContextItemSelected(menuItemId, position);
                }
            };

    /** The view for this handler. */
    private ImageView mCoverView;
    /**
     * keep a reference to the ISBN Field, so we can use the *current* value
     * when we're in the book edit fragment.
     */
    private TextView mIsbnView;
    /** Optional progress bar to display during operations. */
    @Nullable
    private ProgressBar mProgressBar;
    /** Used to display a hint if user rotates a camera image. */
    private boolean mShowHintAboutRotating;

    /**
     * Constructor.
     * <p>
     * Should be called from {@link Fragment#onCreate(Bundle)}.
     *
     * @param fragment      the hosting fragment
     * @param bookViewModel access to the book
     * @param cIdx          0..n image index
     * @param maxWidth      the maximum width for the cover
     * @param maxHeight     the maximum height for the cover
     */
    public CoverHandler(@NonNull final Fragment fragment,
                        @NonNull final BookViewModel bookViewModel,
                        @IntRange(from = 0, to = 1) final int cIdx,
                        final int maxWidth,
                        final int maxHeight) {

        TEMP_COVER_FILENAME = TAG + "_" + cIdx + ".jpg";

        mFragment = fragment;
        mBookViewModel = bookViewModel;
        mCIdx = cIdx;
        mMaxWidth = maxWidth;
        mMaxHeight = maxHeight;

        mCameraPermissionLauncher = mFragment.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), (isGranted) -> {
                    if (isGranted) {
                        takePicture(true);
                    }
                });
        mTakePictureLauncher = mFragment.registerForActivityResult(
                new ActivityResultContracts.TakePicture(), this::onTakePictureResult);

        mGetFromFileLauncher = mFragment.registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::onGetContentResult);

        mEditPictureLauncher = mFragment.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), this::onEditPictureResult);

        mCropPictureLauncher = mFragment.registerForActivityResult(
                new CropImageActivity.ResultContract(), this::onGetContentResult);

        mCoverBrowserLauncher.register(mFragment, RK_COVER_BROWSER + mCIdx);

        if (BuildConfig.MENU_PICKER_USES_FRAGMENT) {
            mMenuLauncher.register(mFragment, RK_MENU_PICKER + mCIdx);
        }

        mTransFormTaskViewModel = new ViewModelProvider(mFragment)
                .get(String.valueOf(cIdx), TransFormTaskViewModel.class);
    }

    /**
     * Should be called from {@link Fragment#onCreateView(LayoutInflater, ViewGroup, Bundle)}.
     *
     * <strong>Why isbnView</strong>: using the view will allow this code to work
     * with both read-only screens (passing the isbn itself would be enough)
     * AND with edit-screens (need the CURRENT code).
     *
     * @param coverView   the views to populate
     * @param isbnView    the view to read the *current* ISBN from
     * @param progressBar (optional) a progress bar
     */
    public void onCreateView(@NonNull final ImageView coverView,
                             @NonNull final TextView isbnView,
                             @Nullable final ProgressBar progressBar) {

        mCoverView = coverView;
        mIsbnView = isbnView;
        mProgressBar = progressBar;

        mTransFormTaskViewModel.onFinished().observe(mFragment.getViewLifecycleOwner(), event -> {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                Log.d(TAG, "mTransFormTaskViewModel.onFinished()"
                           + "|event=" + event);
            }
            showProgress(false);
            if (event.isNewEvent()) {
                Objects.requireNonNull(event.result, FinishedMessage.MISSING_TASK_RESULTS);
                onAfterTransform(event.result);
            }
        });

        // Allow zooming by clicking on the image;
        mCoverView.setOnClickListener(v -> {
            final File uuidCoverFile = mBookViewModel.getCoverFile(mFragment.requireContext(),
                                                                   mCIdx);
            if (uuidCoverFile != null) {
                ZoomedImageDialogFragment
                        .newInstance(uuidCoverFile)
                        .show(mFragment.getChildFragmentManager(), ZoomedImageDialogFragment.TAG);
            }
            //else {
            // If there is no actual image, bring up the context menu instead.
            // 2020-08-03: disabled, since making the placeholder bigger it
            // was to easy to accidentally trigger this
            //onCreateContextMenu();
            //}
        });

        mCoverView.setOnLongClickListener(v -> {
            onCreateContextMenu();
            return true;
        });
    }

    public void onPopulateView() {
        //noinspection ConstantConditions
        final File file = mBookViewModel.getCoverFile(mFragment.getContext(), mCIdx);
        if (file != null) {
            new ImageLoader(mCoverView, mMaxWidth, mMaxHeight, file, null)
                    .execute();
            mCoverView.setBackground(null);

        } else {
            setPlaceholder();
        }
    }


    /**
     * Context menu for the image.
     */
    private void onCreateContextMenu() {
        final Context context = mFragment.requireContext();

        Menu menu = MenuPicker.createMenu(context);
        new MenuInflater(context).inflate(R.menu.image, menu);

        final String title;
        final File uuidCoverFile = mBookViewModel.getCoverFile(context, mCIdx);
        if (uuidCoverFile != null) {
            if (BuildConfig.DEBUG /* always */) {
                // show the size of the image in the title bar
                final BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(uuidCoverFile.getAbsolutePath(), opts);
                title = "" + opts.outWidth + "x" + opts.outHeight;
            } else {
                title = context.getString(R.string.lbl_cover_long);
            }
        } else {
            // there is no current image; only show the replace menu
            final MenuItem menuItem = menu.findItem(R.id.SUBMENU_THUMB_REPLACE);
            menu = menuItem.getSubMenu();
            title = menuItem.getTitle().toString();
        }

        // we only support alternative edition covers for the front cover.
        menu.findItem(R.id.MENU_THUMB_ADD_FROM_ALT_EDITIONS).setVisible(mCIdx == 0);

        if (BuildConfig.MENU_PICKER_USES_FRAGMENT) {
            mMenuLauncher.launch(title, menu, mCIdx);
        } else {
            new MenuPicker(context, title, menu, mCIdx, this::onContextItemSelected)
                    .show();
        }
    }

    /**
     * Using {@link MenuPicker} for context menus.
     *
     * @param itemId   which was selected
     * @param position in the list (i.e. mCIdx)
     *
     * @return {@code true} if handled.
     */
    private boolean onContextItemSelected(@IdRes final int itemId,
                                          final int position) {
        final Context context = mFragment.requireContext();

        if (itemId == R.id.MENU_DELETE) {
            mBookViewModel.setCover(context, mCIdx, null);
            setPlaceholder();
            return true;

        } else if (itemId == R.id.SUBMENU_THUMB_ROTATE) {
            // Just a submenu; skip, but display a hint if user is rotating a camera image
            if (mShowHintAboutRotating) {
                TipManager.display(context, R.string.tip_autorotate_camera_images, null);
                mShowHintAboutRotating = false;
            }
            return true;

        } else if (itemId == R.id.MENU_THUMB_ROTATE_CW) {
            showProgress(true);
            startRotation(90);
            return true;

        } else if (itemId == R.id.MENU_THUMB_ROTATE_CCW) {
            showProgress(true);
            startRotation(-90);
            return true;

        } else if (itemId == R.id.MENU_THUMB_ROTATE_180) {
            showProgress(true);
            startRotation(180);
            return true;

        } else if (itemId == R.id.MENU_THUMB_CROP) {
            try {
                mCropPictureLauncher.launch(new CropImageActivity.ResultContract.Input(
                        mBookViewModel.createTempCoverFile(context, mCIdx),
                        getTempFile()));

            } catch (@NonNull final IOException e) {
                Snackbar.make(mCoverView, R.string.error_storage_not_writable,
                              Snackbar.LENGTH_LONG).show();
            }
            return true;

        } else if (itemId == R.id.MENU_EDIT) {
            try {
                editPicture(mBookViewModel.createTempCoverFile(context, mCIdx));

            } catch (@NonNull final IOException e) {
                Snackbar.make(mCoverView, R.string.error_storage_not_writable,
                              Snackbar.LENGTH_LONG).show();
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


    /**
     * Use the isbn to fetch other possible images from the internet
     * and present to the user to choose one.
     * <p>
     * The results comes back in {@link #onFileSelected(String)}
     */
    private void startCoverBrowser() {
        final String isbnStr = mIsbnView.getText().toString();
        if (!isbnStr.isEmpty()) {
            final ISBN isbn = ISBN.createISBN(isbnStr);
            if (isbn.isValid(true)) {
                mCoverBrowserLauncher.launch(isbn.asText(), mCIdx);
                return;
            }
        }

        Snackbar.make(mCoverView, R.string.warning_requires_isbn,
                      Snackbar.LENGTH_LONG).show();
    }

    /**
     * Called when the user clicks the large preview in the {@link CoverBrowserDialogFragment}.
     *
     * @param fileSpec the selected image
     */
    private void onFileSelected(@NonNull final String fileSpec) {
        SanityCheck.requireValue(fileSpec, "fileSpec");

        final Context context = mFragment.requireContext();
        File srcFile = new File(fileSpec);
        if (srcFile.exists()) {
            srcFile = mBookViewModel.setCover(context, mCIdx, srcFile);
            if (srcFile != null) {
                new ImageLoader(mCoverView, mMaxWidth, mMaxHeight, srcFile, null)
                        .execute();
                mCoverView.setBackground(null);
                return;
            }
        }

        mBookViewModel.setCover(context, mCIdx, null);
        setPlaceholder();
    }


    /**
     * Edit the image using an external application.
     *
     * @param srcFile to edit
     */
    private void editPicture(@NonNull final File srcFile) {
        final Context context = mFragment.requireContext();

        final File dstFile = getTempFile();
        FileUtils.delete(dstFile);

        //TODO: we really should revoke the permissions afterwards
        final Uri srcUri = GenericFileProvider.createUri(context, srcFile);
        final Uri dstUri = GenericFileProvider.createUri(context, dstFile);

        final Intent intent = new Intent(Intent.ACTION_EDIT)
                .setDataAndType(srcUri, IMAGE_MIME_TYPE)
                // read access to the input uri
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // write access see below
                .putExtra(MediaStore.EXTRA_OUTPUT, dstUri);

        final List<ResolveInfo> resInfoList =
                context.getPackageManager()
                       .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (!resInfoList.isEmpty()) {
            // We do not know which app will be used, so need to grant permission to all.
            for (final ResolveInfo resolveInfo : resInfoList) {
                final String packageName = resolveInfo.activityInfo.packageName;
                context.grantUriPermission(packageName, dstUri,
                                           Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }

            final String prompt = context.getString(R.string.action_edit);
            mEditPictureLauncher.launch(Intent.createChooser(intent, prompt));

        } else {
            Snackbar.make(mCoverView, context.getString(R.string.error_no_image_editor),
                          Snackbar.LENGTH_LONG).show();
        }
    }

    private void onEditPictureResult(@NonNull final ActivityResult activityResult) {
        if (activityResult.getResultCode() == Activity.RESULT_OK) {
            final File file = getTempFile();
            if (file.exists()) {
                showProgress(true);
                mTransFormTaskViewModel.startTask(
                        new TransFormTaskViewModel.Transformation(file)
                                .setScale(true));
                return;
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
        final Context context = mFragment.requireContext();
        if (uri != null) {
            File file = getTempFile();
            try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                // copy the data, and retrieve the (potentially) resolved file
                file = FileUtils.copyInputStream(context, is, file);
            } catch (@NonNull final IOException e) {
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "Unable to copy content to file", e);
                }
            }

            if (file != null) {
                showProgress(true);
                mTransFormTaskViewModel.startTask(
                        new TransFormTaskViewModel.Transformation(file)
                                .setScale(true));
            } else {
                StandardDialogs.showError(context, R.string.warning_image_copy_failed);
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
        //noinspection ConstantConditions
        if (alreadyGranted ||
            ContextCompat.checkSelfPermission(mFragment.getContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {

            final File dstFile = getTempFile();
            FileUtils.delete(dstFile);
            //noinspection ConstantConditions
            final Uri uri = GenericFileProvider.createUri(mFragment.getContext(), dstFile);
            mTakePictureLauncher.launch(uri);

        } else {
            mCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void onTakePictureResult(final boolean result) {
        if (result) {
            final File file = getTempFile();
            if (file.exists()) {
                //noinspection ConstantConditions
                final SharedPreferences prefs = PreferenceManager
                        .getDefaultSharedPreferences(mFragment.getContext());

                // Should we apply an explicit rotation angle?
                // (which would overrule the setWindowManager call)
                final int angle = ParseUtils
                        .getIntListPref(prefs, Prefs.pk_camera_image_autorotate, 0);

                // What action (if any) should we take after we're done?
                @NextAction
                final int action = ParseUtils
                        .getIntListPref(prefs, Prefs.pk_camera_image_action, ACTION_DONE);

                showProgress(true);
                //noinspection ConstantConditions
                mTransFormTaskViewModel.startTask(
                        new TransFormTaskViewModel.Transformation(file)
                                .setScale(true)
                                // we'll try to guess a rotation angle
                                .setWindowManager(mFragment.getActivity().getWindowManager())
                                // or apply an explicit angle
                                .setRotate(angle)
                                .setReturnCode(action));
            }
        }
    }


    /**
     * Rotate the image by the given angle.
     *
     * @param angle to rotate.
     */
    private void startRotation(final int angle) {
        final File srcFile;
        try {
            //noinspection ConstantConditions
            srcFile = mBookViewModel.createTempCoverFile(mFragment.getContext(), mCIdx);
        } catch (@NonNull final IOException e) {
            Snackbar.make(mCoverView, R.string.error_storage_not_writable,
                          Snackbar.LENGTH_LONG).show();
            return;
        }

        showProgress(true);
        mTransFormTaskViewModel.startTask(
                new TransFormTaskViewModel.Transformation(srcFile)
                        .setRotate(angle));
    }


    private void onAfterTransform(@NonNull final TransFormTaskViewModel.TransformedData result) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
            Log.d(TAG, "onAfterTransform"
                       + "|returnCode=" + result.getReturnCode()
                       + "|bitmap=" + (result.getBitmap() != null)
                       + "|file=" + result.getFile().getAbsolutePath());
        }

        final Context context = mFragment.requireContext();

        // The bitmap != null decides if the operation was successful.
        if (result.getBitmap() != null) {
            // sanity check: if the bitmap was good, the file will be good.
            Objects.requireNonNull(result.getFile(), "file");

            switch (result.getReturnCode()) {
                case ACTION_CROP:
                    mCropPictureLauncher.launch(new CropImageActivity.ResultContract.Input(
                            result.getFile(),
                            getTempFile()));
                    break;

                case ACTION_EDIT:
                    editPicture(result.getFile());
                    break;

                case ACTION_DONE:
                default:
                    mBookViewModel.setCover(context, mCIdx, result.getFile());
                    // must use a post to force the View to update.
                    mCoverView.post(() -> {
                        ImageUtils.setImageView(mCoverView, mMaxWidth, mMaxHeight,
                                                result.getBitmap(), 0);
                        mCoverView.setBackground(null);
                    });
                    break;
            }

        } else {
            // transformation failed
            mBookViewModel.setCover(context, mCIdx, null);
            // must use a post to force the View to update.
            mCoverView.post(() -> setPlaceholder(result.getFile()));
        }
    }


    /**
     * Put a placeholder on screen.
     *
     * @param file to determine the type of placeholder to use
     */
    private void setPlaceholder(@Nullable final File file) {
        if (file == null || file.length() == 0) {
            setPlaceholder();
        } else {
            ImageUtils.setPlaceholder(mCoverView, mMaxWidth, mMaxHeight,
                                      R.drawable.ic_broken_image, R.drawable.outline_rounded);
        }
    }

    /**
     * Put a standard placeholder.
     */
    private void setPlaceholder() {
        ImageUtils.setPlaceholder(mCoverView, mMaxWidth, mMaxHeight,
                                  R.drawable.ic_add_a_photo, R.drawable.outline_rounded);
    }

    /**
     * Get the temporary file.
     *
     * @return file
     */
    @NonNull
    private File getTempFile() {
        //noinspection ConstantConditions
        return AppDir.Cache.getFile(mFragment.getContext(), TEMP_COVER_FILENAME);
    }

    /**
     * remove any orphaned file.
     */
    private void removeTempFile() {
        FileUtils.delete(getTempFile());
    }

    private void showProgress(final boolean show) {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    @IntDef({ACTION_DONE, ACTION_CROP, ACTION_EDIT})
    @Retention(RetentionPolicy.SOURCE)
    @interface NextAction {

    }
}
