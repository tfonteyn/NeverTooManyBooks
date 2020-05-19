/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
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
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.RequestCode;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PIntString;
import com.hardbacknutter.nevertoomanybooks.cropper.CropImageActivity;
import com.hardbacknutter.nevertoomanybooks.database.CoversDAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.dialogs.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.ZoomedImageDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searches.librarything.LibraryThingSearchEngine;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.CameraHelper;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.GenericFileProvider;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;

/**
 * Handler for a displayed Cover ImageView element.
 * Offers context menus and all operations applicable on a Cover image.
 * <p>
 * The fragment using this must implement {@link HostingFragment} if it hosts more then 1
 * instance of this class. See {@link #onContextItemSelected}.
 * It informs the fragment of the cover image index to use in its
 * {@link Fragment#onActivityResult(int, int, Intent)}
 */
public class CoverHandler {

    /** Log tag. */
    private static final String TAG = "CoverHandler";

    /**
     * After taking a picture, do nothing. Never change the value.
     * This is stored in user preferences.
     */
    private static final int ACTION_NOTHING = 0;
    /** After taking a picture, crop. */
    private static final int ACTION_CROP = 1;
    /** After taking a picture, edit. */
    private static final int ACTION_EDIT = 2;

    private static final String IMAGE_MIME_TYPE = "image/*";
    /** The cropper uses a single file. */
    private static final String TEMP_COVER_FILENAME = TAG + ".jpg";

    /** The fragment hosting us. Should implement HostingFragment. */
    @NonNull
    private final Fragment mFragment;
    @NonNull
    private final ImageView mCoverView;
    /** Index of the image we're handling. */
    @IntRange(from = 0)
    private final int mCIdx;
    /** Context from the fragment, cached as used frequently. */
    @NonNull
    private final Context mContext;
    @NonNull
    private final Book mBook;
    /**
     * keep a reference to the ISBN Field, so we can use the *current* value
     * when we're in the book edit fragment.
     */
    @NonNull
    private final TextView mIsbnView;
    @Nullable
    private final ProgressBar mProgressBar;
    private final int mWidth;
    private final int mHeight;
    private final TransFormTaskViewModel mModel;
    /** Used to display a hint if user rotates a camera image. */
    private boolean mShowHintAboutRotating;
    @Nullable
    private CameraHelper mCameraHelper;

    /**
     * Constructor.
     *
     * @param fragment    the hosting fragment, should implement {@link HostingFragment}
     * @param progressBar (optional) a progress bar
     * @param book        the book whose cover we're handling
     * @param isbnView    the view to read the *current* ISBN from
     * @param cIdx        0..n image index
     * @param coverView   the views to populate
     * @param scale       image scale to apply
     */
    public CoverHandler(@NonNull final Fragment fragment,
                        @Nullable final ProgressBar progressBar,
                        @NonNull final Book book,
                        @NonNull final TextView isbnView,
                        @IntRange(from = 0) final int cIdx,
                        @NonNull final ImageView coverView,
                        final int scale) {

        mFragment = fragment;
        //noinspection ConstantConditions
        mContext = mFragment.getContext();

        mProgressBar = progressBar;
        mBook = book;
        mIsbnView = isbnView;
        mCIdx = cIdx;
        mCoverView = coverView;
        //noinspection ConstantConditions
        final int size = ImageScale.getSize(mContext, scale);
        mHeight = size;
        mWidth = size;

        // Allow zooming by clicking on the image;
        // If there is no actual image, bring up the context menu instead.
        mCoverView.setOnClickListener(v -> {
            final File srcFile = getCoverFile();
            if (srcFile.exists()) {
                ZoomedImageDialogFragment
                        .newInstance(srcFile)
                        .show(fragment.getParentFragmentManager(), ZoomedImageDialogFragment.TAG);
            } else {
                onCreateContextMenu();
            }
        });

        mCoverView.setOnLongClickListener(v -> {
            onCreateContextMenu();
            return true;
        });

        mModel = new ViewModelProvider(fragment).get(String.valueOf(cIdx),
                                                     TransFormTaskViewModel.class);
        mModel.getTransformedData().observe(mFragment.getViewLifecycleOwner(), event -> {
            if (event.needsHandling()) {
                // unwrap the event/data here to keep the receiving method unaware.
                final TransFormTask.TransformedData data = event.getData();
                if (data != null) {
                    onAfterTransform(data.bitmap, data.file, data.returnCode);
                }
            }
        });

        // finally load the image.
        setImage(getCoverFile());
    }

    /**
     * Delete any orphaned temporary cover files.
     *
     * @param context Current context
     */
    public static void deleteOrphanedCoverFiles(@NonNull final Context context) {
        for (int cIdx = 0; cIdx < 2; cIdx++) {
            FileUtils.delete(AppDir.Cache.getFile(context, cIdx + ".jpg"));
        }
    }

    /**
     * Context menu for the image.
     */
    private void onCreateContextMenu() {
        Menu menu = MenuPicker.createMenu(mContext);
        new MenuInflater(mContext).inflate(R.menu.image, menu);

        final CharSequence title;
        File file = getCoverFile();
        if (file.exists()) {
            if (BuildConfig.DEBUG) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
                title = "w=" + opts.outWidth + ", h=" + opts.outHeight;
            } else {
                title = mContext.getString(R.string.lbl_cover_long);
            }
        } else {
            // there is no current image; only show the replace menu
            final MenuItem menuItem = menu.findItem(R.id.SUBMENU_THUMB_REPLACE);
            menu = menuItem.getSubMenu();
            title = menuItem.getTitle();
        }

        // we only support alternative edition covers for the front cover.
        menu.findItem(R.id.MENU_THUMB_ADD_ALT_EDITIONS).setVisible(mCIdx == 0);

        new MenuPicker(mContext, title, menu, mCIdx, this::onContextItemSelected)
                .show();
    }

    /**
     * Using {@link MenuPicker} for context menus.
     *
     * @param menuItem that was selected
     * @param position in the list (i.e. mCIdx)
     *
     * @return {@code true} if handled.
     */
    private boolean onContextItemSelected(@IdRes final int menuItem,
                                          final int position) {

        // prepare the fragment for results.
        // This is needed as for example when we start the camera, we can't tell afterwards
        // which index we're handling. So let the fragment know BEFORE.
        if (mFragment instanceof HostingFragment) {
            ((HostingFragment) mFragment).setCurrentCoverIndex(mCIdx);
        }

        switch (menuItem) {
            case R.id.MENU_DELETE: {
                deleteCoverFile(mContext);
                return true;
            }
            case R.id.SUBMENU_THUMB_ROTATE: {
                // Just a submenu; skip, but display a hint if user is rotating a camera image
                if (mShowHintAboutRotating) {
                    TipManager.display(mContext, R.string.tip_autorotate_camera_images, null);
                    mShowHintAboutRotating = false;
                }
                return true;
            }
            case R.id.MENU_THUMB_ROTATE_CW: {
                new TransFormTask(getCoverFile(), mModel, mProgressBar)
                        .setScale(false)
                        .setRotate(90)
                        .execute();
                return true;
            }
            case R.id.MENU_THUMB_ROTATE_CCW: {
                new TransFormTask(getCoverFile(), mModel, mProgressBar)
                        .setScale(false)
                        .setRotate(-90)
                        .execute();
                return true;
            }
            case R.id.MENU_THUMB_ROTATE_180: {
                new TransFormTask(getCoverFile(), mModel, mProgressBar)
                        .setScale(false)
                        .setRotate(180)
                        .execute();
                return true;
            }
            case R.id.MENU_THUMB_CROP: {
                cropCoverFile(getCoverFile());
                return true;
            }
            case R.id.MENU_EDIT: {
                editCoverFile(getCoverFile());
                return true;
            }
            case R.id.MENU_THUMB_ADD_FROM_CAMERA: {
                startCamera();
                return true;
            }
            case R.id.MENU_THUMB_ADD_FROM_GALLERY: {
                startChooser();
                return true;
            }
            case R.id.MENU_THUMB_ADD_ALT_EDITIONS: {
                startCoverBrowser();
                return true;
            }
            default: {
                return false;
            }
        }
    }

    /**
     * Handles results from Camera, Picture Gallery and editing (incl. internal cropper).
     *
     * @return {@code true} when handled, {@code false} if unknown requestCode
     */
    public boolean onActivityResult(final int requestCode,
                                    final int resultCode,
                                    @Nullable final Intent data) {
        switch (requestCode) {
            case RequestCode.ACTION_GET_CONTENT: {
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
                    final Uri uri = data.getData();
                    if (uri != null) {
                        try (InputStream is = mContext.getContentResolver().openInputStream(uri)) {
                            File dstFile = FileUtils.copyInputStream(mContext, is, getCoverFile());
                            if (dstFile != null) {
                                new TransFormTask(dstFile, mModel, mProgressBar)
                                        .setScale(true)
                                        .execute();
                                return true;
                            }
                        } catch (@NonNull final IOException e) {
                            if (BuildConfig.DEBUG /* always */) {
                                Log.d(TAG, "Unable to copy content to file", e);
                            }
                        }
                    }

                    StandardDialogs.showBadError(mContext, R.string.warning_cover_copy_failed);
                }
                return true;
            }
            case RequestCode.ACTION_IMAGE_CAPTURE: {
                if (resultCode == Activity.RESULT_OK) {
                    //noinspection ConstantConditions
                    final File srcFile = mCameraHelper.getFile(mContext);
                    if (srcFile != null && srcFile.exists()) {
                        final File dstFile = getCoverFile();
                        FileUtils.rename(srcFile, dstFile);

                        // Should we apply an explicit rotation angle?
                        // (which would overrule the setWindowManager call)
                        final int angle = PIntString
                                .getListPreference(mContext, Prefs.pk_camera_image_autorotate, 0);
                        // What action should we take after we're done?
                        @NextAction
                        final int action = PIntString
                                .getListPreference(mContext, Prefs.pk_camera_image_action,
                                                   ACTION_NOTHING);

                        //noinspection ConstantConditions
                        new TransFormTask(dstFile, mModel, mProgressBar)
                                .setScale(true)
                                .setWindowManager(mFragment.getActivity().getWindowManager())
                                .setRotate(angle)
                                .setReturnCode(action)
                                .execute();
                        return true;
                    }
                }
                // remove orphans
                CameraHelper.deleteCameraFile(mContext);
                return true;
            }
            case RequestCode.CROP_IMAGE:
            case RequestCode.EDIT_IMAGE: {
                if (resultCode == Activity.RESULT_OK) {
                    final File srcFile = AppDir.Cache.getFile(mContext, TEMP_COVER_FILENAME);
                    final File dstFile = getCoverFile();
                    FileUtils.rename(srcFile, dstFile);
                    new TransFormTask(dstFile, mModel, mProgressBar)
                            .setScale(true)
                            .execute();
                    return true;
                }
                // remove orphans
                FileUtils.delete(AppDir.Cache.getFile(mContext, TEMP_COVER_FILENAME));
                return true;
            }
            default:
                return false;
        }
    }

    /**
     * Called when the user clicks the large preview in the {@link CoverBrowserDialogFragment}.
     *
     * @param fileSpec the selected image
     */
    public void onFileSelected(@Nullable final String fileSpec) {
        if (fileSpec != null && !fileSpec.isEmpty()) {
            final File srcFile = new File(fileSpec);
            final File dstFile = getCoverFile();
            FileUtils.rename(srcFile, dstFile);
            setImage(dstFile);
        }
    }

    private void onAfterTransform(@Nullable final Bitmap bitmap,
                                  @Nullable final File file,
                                  @NextAction final int returnCode) {

        // satisfy lint: if the bitmap is good, then the file will be good.
        if (bitmap != null && file != null) {
            mBook.putString(Book.BKEY_FILE_SPEC[mCIdx], file.getAbsolutePath());

            ImageUtils.setImageView(mCoverView, mWidth, mHeight, bitmap, 0);
            mCoverView.setBackground(null);

            switch (returnCode) {
                case ACTION_CROP:
                    cropCoverFile(file);
                    break;

                case ACTION_EDIT:
                    editCoverFile(file);
                    break;

                case ACTION_NOTHING:
                default:
                    break;
            }

        } else {
            clearImage(file);
        }
    }

    /**
     * Put the cover image on screen, <strong>and update the book</strong> with the file name.
     *
     * @param file to use
     */
    private void setImage(@Nullable final File file) {
        if (ImageUtils.isFileGood(file)) {
            mBook.putString(Book.BKEY_FILE_SPEC[mCIdx], file.getAbsolutePath());

            new ImageLoader(mCoverView, file, mWidth, mHeight, null)
                    .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            mCoverView.setBackground(null);

        } else {
            clearImage(file);
        }
    }

    /**
     * Remove the image from the book, and put a place holder on screen.
     *
     * @param file to determine the type of placeholder to use
     */
    private void clearImage(@Nullable final File file) {
        mBook.remove(Book.BKEY_FILE_SPEC[mCIdx]);

        if (file == null || file.length() == 0) {
            ImageUtils.setPlaceholder(mCoverView, R.drawable.ic_add_a_photo,
                                      R.drawable.outline_rounded);
        } else {
            ImageUtils.setPlaceholder(mCoverView, R.drawable.ic_broken_image,
                                      R.drawable.outline_rounded);
        }
    }


    /**
     * Call out the Intent.ACTION_GET_CONTENT to get an image from an external app.
     */
    private void startChooser() {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
                .setType(IMAGE_MIME_TYPE);
        mFragment.startActivityForResult(
                Intent.createChooser(intent, mContext.getString(R.string.lbl_select_image)),
                RequestCode.ACTION_GET_CONTENT);
    }

    private void startCamera() {
        if (mCameraHelper == null) {
            mCameraHelper = new CameraHelper();
        }
        mCameraHelper.startCamera(mFragment, RequestCode.ACTION_IMAGE_CAPTURE);
    }

    /**
     * Use the isbn to fetch other possible images from the internet
     * and present to the user to choose one.
     */
    private void startCoverBrowser() {
        // getting alternative editions is limited to LibraryThing and ISFDB for now.
        // ISFDB is obviously limited to their specific genre,
        // so remind the user that LT is rather essential.
        if (!LibraryThingSearchEngine.hasKey(mContext)) {
            LibraryThingSearchEngine.alertRegistrationNeeded(mContext, false, "cover_browser");
            return;
        }

        final String isbnStr = mIsbnView.getText().toString();
        if (!isbnStr.isEmpty()) {
            final ISBN isbn = ISBN.createISBN(isbnStr);
            if (isbn.isValid(true)) {
                final DialogFragment frag = CoverBrowserDialogFragment
                        .newInstance(isbn.asText(), mCIdx);
                frag.show(mFragment.getChildFragmentManager(), CoverBrowserDialogFragment.TAG);
                return;
            }
        }

        Snackbar.make(mCoverView, R.string.warning_requires_isbn,
                      Snackbar.LENGTH_LONG).show();
    }

    /**
     * Get the File object for the cover of the book we are editing.
     *
     * @return the file
     */
    @NonNull
    private File getCoverFile() {
        // for existing books, we use the UUID and the index to get the stored file.
        final String uuid = mBook.getString(DBDefinitions.KEY_BOOK_UUID);
        if (!uuid.isEmpty()) {
            return AppDir.getCoverFile(mContext, uuid, mCIdx);
        }

        // for new books, check the bundle.
        final String fileSpec = mBook.getString(Book.BKEY_FILE_SPEC[mCIdx]);
        if (!fileSpec.isEmpty()) {
            return new File(fileSpec);
        }

        // return a new File object
        return AppDir.Cache.getFile(mContext, mCIdx + ".jpg");
    }

    /**
     * Delete the image.
     *
     * @param context Current context
     */
    private void deleteCoverFile(@NonNull final Context context) {
        FileUtils.delete(getCoverFile());

        // Ensure that the cached images for this book are deleted (if present).
        // Yes, this means we also delete the ones where != index, but we don't care; it's a cache.
        final String uuid = mBook.getString(DBDefinitions.KEY_BOOK_UUID);
        if (!uuid.isEmpty()) {
            CoversDAO.delete(context, uuid);
        }
        ImageUtils
                .setPlaceholder(mCoverView, R.drawable.ic_add_a_photo, R.drawable.outline_rounded);
    }

    /**
     * Crop the image using our internal code in {@link CropImageActivity}.
     *
     * @param srcFile to use
     */
    private void cropCoverFile(@NonNull final File srcFile) {
        final File dstFile = AppDir.Cache.getFile(mContext, TEMP_COVER_FILENAME);
        // delete any orphaned file.
        FileUtils.delete(dstFile);

        final boolean wholeImage = PreferenceManager
                .getDefaultSharedPreferences(mContext)
                .getBoolean(Prefs.pk_image_cropper_frame_whole, false);

        final Intent intent = new Intent(mContext, CropImageActivity.class)
                .putExtra(CropImageActivity.BKEY_IMAGE_ABSOLUTE_PATH,
                          srcFile.getAbsolutePath())
                .putExtra(CropImageActivity.BKEY_OUTPUT_ABSOLUTE_PATH,
                          dstFile.getAbsolutePath())
                .putExtra(CropImageActivity.BKEY_WHOLE_IMAGE, wholeImage);

        mFragment.startActivityForResult(intent, RequestCode.CROP_IMAGE);
    }

    /**
     * Edit the image using an external application.
     *
     * @param srcFile to use
     */
    private void editCoverFile(@NonNull final File srcFile) {
        final File dstFile = AppDir.Cache.getFile(mContext, TEMP_COVER_FILENAME);

        //TODO: we really should revoke the permissions afterwards
        final Uri srcUri = GenericFileProvider.getUriForFile(mContext, srcFile);
        final Uri dstUri = GenericFileProvider.getUriForFile(mContext, dstFile);

        Intent intent = new Intent(Intent.ACTION_EDIT)
                .setDataAndType(srcUri, IMAGE_MIME_TYPE)
                // read access to the input uri
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // write access see below
                .putExtra(MediaStore.EXTRA_OUTPUT, dstUri);

        final List<ResolveInfo> resInfoList =
                mContext.getPackageManager()
                        .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (!resInfoList.isEmpty()) {
            // We do not know which app will be used, so need to grant permission to all.
            for (ResolveInfo resolveInfo : resInfoList) {
                String packageName = resolveInfo.activityInfo.packageName;
                mContext.grantUriPermission(packageName, dstUri,
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }

            mFragment.startActivityForResult(
                    Intent.createChooser(intent, mContext.getString(R.string.action_edit)),
                    RequestCode.EDIT_IMAGE);

        } else {
            Snackbar.make(mCoverView, mContext.getString(R.string.error_no_image_editor),
                          Snackbar.LENGTH_LONG).show();
        }
    }

    public interface HostingFragment {

        void setCurrentCoverIndex(@IntRange(from = 0) int cIdx);
    }

    @IntDef({ACTION_NOTHING, ACTION_CROP, ACTION_EDIT})
    @Retention(RetentionPolicy.SOURCE)
    @interface NextAction {

    }
}
