package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.database.sqlite.SQLiteDoneException;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.widget.PopupMenu;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import com.eleybourn.bookcatalogue.cropper.CropImageActivity;
import com.eleybourn.bookcatalogue.cropper.CropImageViewTouchBase;
import com.eleybourn.bookcatalogue.database.CoversDBA;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.PopupMenuDialog;
import com.eleybourn.bookcatalogue.entities.BookManager;
import com.eleybourn.bookcatalogue.searches.Site;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;
import com.eleybourn.bookcatalogue.utils.GenericFileProvider;
import com.eleybourn.bookcatalogue.utils.ISBN;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;
import com.eleybourn.bookcatalogue.dialogs.ZoomedImageDialogFragment;

/**
 * Handler for a displayed Cover ImageView element.
 * Offers context menus and all operations applicable on a Cover image.
 * <p>
 * Layer Type to use for the Cropper.
 * <p>
 * {@link CropImageViewTouchBase} We get 'unsupported feature' crashes if the option
 * to always use GL is turned on.
 * See:
 * http://developer.android.com/guide/topics/graphics/hardware-accel.html
 * http://stackoverflow.com/questions/13676059/android-unsupportedoperationexception-at-canvas-clippath
 * so for API level > 11, we turn it off manually.
 * <p>
 * 2018-11-30: making this a configuration option.
 */
public class CoverHandler {

    /** Counter used to prevent images being reused accidentally. */
    private static int sTempImageCounter;

    @NonNull
    private final FragmentActivity mActivity;
    @NonNull
    private final Fragment mFragment;


    @NonNull
    private final DBA mDb;
    @NonNull
    private final BookManager mBookManager;
    private final Fields.Field mCoverField;

    /**
     * keep a reference to the ISBN Field, so we can use the *current* value.
     * (instead of using getBook())
     */
    private final Fields.Field mIsbnField;

    private final int mMaxWidth;
    private final int mMaxHeight;
    @Nullable
    private CoverBrowser mCoverBrowserFragment;

    /** Used to display a hint if user rotates a camera image. */
    private boolean mGotCameraImage;

    /**
     * Constructor.
     */
    CoverHandler(@NonNull final Fragment fragment,
                 @NonNull final DBA db,
                 @NonNull final BookManager bookManager,
                 @NonNull final Fields.Field isbnField,
                 @NonNull final Fields.Field coverField,
                 final int maxWidth,
                 final int maxHeight) {
        mFragment = fragment;
        mMaxWidth = maxWidth;
        mMaxHeight = maxHeight;
        // cache it to avoid multiple calls.
        mActivity = mFragment.requireActivity();
        mDb = db;
        mBookManager = bookManager;
        mCoverField = coverField;
        mIsbnField = isbnField;

        // add context menu to the cover image
        mCoverField.getView().setOnLongClickListener(v -> {
            prepareCoverContextMenu();
            return true;
        });

        //Allow zooming by clicking on the image
        mCoverField.getView().setOnClickListener(
                v -> ZoomedImageDialogFragment.show(mActivity.getSupportFragmentManager(),
                                                    getCoverFile()));
    }

    /**
     * When the user clicks the switcher in the {@link CoverBrowser}, we take that image and
     * stuff it into the view.
     *
     * @param fileSpec the file
     */
    private void onImageSelected(@NonNull final String fileSpec) {
        if (mCoverBrowserFragment != null) {
            // the new file we got
            File newFile = new File(fileSpec);
            // Get the current file we want to loose
            File bookFile = getCoverFile();
            // copy new file on top of old.
            StorageUtils.renameFile(newFile, bookFile);
            // Update the ImageView with the new image
            updateCoverView();
            mCoverBrowserFragment.dismiss();
            mCoverBrowserFragment = null;
        }
    }

    /**
     * Context menu for the image.
     */
    private void prepareCoverContextMenu() {

        // legal trick to get an instance of Menu.
        Menu menu = new PopupMenu(mActivity, null).getMenu();
        menu.add(Menu.NONE, R.id.MENU_THUMB_DELETE, 0, R.string.menu_delete)
            .setIcon(R.drawable.ic_delete);

        SubMenu replaceSubmenu = menu.addSubMenu(Menu.NONE, R.id.SUBMENU_THUMB_REPLACE, 0,
                                                 R.string.menu_cover_replace)
                                     .setIcon(R.drawable.ic_find_replace);

        replaceSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ADD_FROM_CAMERA, 0,
                           R.string.menu_cover_add_from_camera)
                      .setIcon(R.drawable.ic_add_a_photo);
        replaceSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ADD_FROM_GALLERY, 0,
                           R.string.menu_cover_add_from_gallery)
                      .setIcon(R.drawable.ic_image);
        replaceSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ADD_ALT_EDITIONS, 0,
                           R.string.menu_cover_search_alt_editions)
                      .setIcon(R.drawable.ic_find_replace);

        SubMenu rotateSubmenu = menu.addSubMenu(Menu.NONE, R.id.SUBMENU_THUMB_ROTATE, 0,
                                                R.string.menu_cover_rotate)
                                    .setIcon(R.drawable.ic_rotate_right);

        rotateSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ROTATE_CW, 0,
                          R.string.menu_cover_rotate_cw)
                     .setIcon(R.drawable.ic_rotate_right);
        rotateSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ROTATE_CCW, 0,
                          R.string.menu_cover_rotate_ccw)
                     .setIcon(R.drawable.ic_rotate_left);
        rotateSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ROTATE_180, 0,
                          R.string.menu_cover_rotate_180)
                     .setIcon(R.drawable.ic_swap_vert);

        menu.add(Menu.NONE, R.id.MENU_THUMB_CROP, 0, R.string.menu_cover_crop)
            .setIcon(R.drawable.ic_crop);

        // display
        String menuTitle = mActivity.getString(R.string.title_cover);
        PopupMenuDialog.showContextMenu(mActivity, menuTitle, menu, R.id.coverImage,
                                        this::onViewContextItemSelected);
    }

    /**
     * Using {@link PopupMenuDialog} for context menus.
     * Reminder: the 'menuItem' here *is* the 'item'.
     *
     * @param menuItem that the user selected
     * @param fieldId  the field the menu belongs to
     *
     * @return {@code true} if handled here.
     */
    private boolean onViewContextItemSelected(@NonNull final MenuItem menuItem,
                                              @NonNull final Integer fieldId) {

        // should not happen for now, but nice to remind ourselves we *could* have multiple views.
        if (!fieldId.equals(R.id.coverImage)) {
            return false;
        }

        switch (menuItem.getItemId()) {
            case R.id.MENU_THUMB_DELETE:
                deleteCoverFile();
                return true;

            case R.id.SUBMENU_THUMB_ROTATE:
                // Just a submenu; skip, but display a hint if user is rotating a camera image
                if (mGotCameraImage) {
                    HintManager.displayHint(mActivity.getLayoutInflater(),
                                            R.string.hint_autorotate_camera_images,
                                            null);
                    mGotCameraImage = false;
                }
                return true;

            case R.id.MENU_THUMB_ROTATE_CW:
                rotateImage(90);
                return true;

            case R.id.MENU_THUMB_ROTATE_CCW:
                rotateImage(-90);
                return true;

            case R.id.MENU_THUMB_ROTATE_180:
                rotateImage(180);
                return true;

            case R.id.MENU_THUMB_CROP:
                cropCoverImage(getCoverFile());
                return true;

            case R.id.MENU_THUMB_ADD_FROM_CAMERA:
                getCoverFromCamera();
                return true;

            case R.id.MENU_THUMB_ADD_FROM_GALLERY:
                getCoverFromGallery();
                return true;

            case R.id.MENU_THUMB_ADD_ALT_EDITIONS:
                addCoverFromAlternativeEditions();
                return true;

            default:
                return false;
        }
    }

    /**
     * (re)load the image into the view.
     */
    void updateCoverView() {
        ImageUtils.setImageView(mCoverField.getView(), getCoverFile(), mMaxWidth, mMaxHeight, true);
    }

    /**
     * Get the File object for the cover of the book we are editing.
     * If the book is new (0), return the standard temp file.
     * If the data is a result from a search, then that standard temp file will
     * be the downloaded file.
     */
    @NonNull
    private File getCoverFile() {
        if (mBookManager.getBook().getId() == 0) {
            return StorageUtils.getTempCoverFile();
        }
        return StorageUtils.getCoverFile(getUuid());
    }

    /**
     * We *should* have the uuid on a tag on the over field view,
     * but if not, we'll get it from the database.
     *
     * @return the uuid, or {@code null} if the book has no uuid.
     */
    @NonNull
    private String getUuid() {
        String uuid = (String) mCoverField.getView().getTag(R.id.TAG_UUID);
        // if we forgot to set it in some bad code... log the fact, and make a trip to the db.
        if (uuid == null) {
            Logger.debugWithStackTrace(this, "getUuid", "UUID was not available on the view t");
            uuid = mDb.getBookUuid(mBookManager.getBook().getId());
        }
        return uuid;
    }

    /**
     * Use the isbn to fetch other possible images from the internet
     * and present to the user to choose one.
     */
    private void addCoverFromAlternativeEditions() {
        // this is essential, as we only get alternative editions from LibraryThing for now.
        if (LibraryThingManager.noKey()) {
            LibraryThingManager.needLibraryThingAlert(mActivity, true, "cover_browser");
            return;
        }

        String isbn = mIsbnField.getValue().toString();

        if (ISBN.isValid(isbn)) {
            // we must use the same fragment manager as the hosting fragment.
            FragmentManager fm = mFragment.requireFragmentManager();
            mCoverBrowserFragment = (CoverBrowser) fm.findFragmentByTag(CoverBrowser.TAG);
            if (mCoverBrowserFragment == null) {
                mCoverBrowserFragment = CoverBrowser.newInstance(isbn, Site.SEARCH_ALL);
                mCoverBrowserFragment.show(fm, CoverBrowser.TAG);
            }
            // allow a callback when the user clicks on the image they want to use.
            // at least we don't need to travel round to the Activity this way.
            mCoverBrowserFragment.setTargetFragment(mFragment, UniqueId.REQ_ALT_EDITION);
        } else {
            UserMessage.showUserMessage(mIsbnField.getView(),
                                        R.string.warning_editions_require_isbn);
        }
    }

    /**
     * Start the camera to get an image.
     */
    private void getCoverFromCamera() {
        // Increment the temp counter and cleanup the temp directory
        sTempImageCounter++;
        StorageUtils.purgeTempStorage();
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        /*
        We don't do the next bit of code here because we have no reliable way to rotate a
        large image without producing memory exhaustion.
        Android does not include a file-based image rotation.

        File f = getCameraTempCoverFile();
        StorageUtils.deleteFile(f);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));
        */
        mFragment.startActivityForResult(intent, UniqueId.REQ_ACTION_IMAGE_CAPTURE);
    }

    /**
     * The camera has captured an image, process it.
     */
    private void addCoverFromCamera(final int requestCode,
                                    final int resultCode,
                                    @NonNull final Bundle bundle) {
        Bitmap bitmap = (Bitmap) bundle.get(CropImageActivity.BKEY_DATA);
        if (bitmap != null && bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(App.getListPreference(Prefs.pk_thumbnails_rotate_auto, 0));
            bitmap = Bitmap.createBitmap(bitmap, 0, 0,
                                         bitmap.getWidth(), bitmap.getHeight(),
                                         matrix, true);

            File cameraFile = StorageUtils.getTempCoverFile("camera" + sTempImageCounter);
            // Create a file to copy the image into
            try (OutputStream out = new FileOutputStream(cameraFile.getAbsoluteFile())) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (@SuppressWarnings("OverlyBroadCatchBlock") IOException e) {
                Logger.error(this, e);
                return;
            }

            cropCoverImage(cameraFile);
            mGotCameraImage = true;

        } else {
            if (BuildConfig.DEBUG  /* WARN */) {
                Logger.warn(this, "addCoverFromCamera",
                            "camera image empty", "onActivityResult",
                            "requestCode=" + requestCode,
                            "resultCode=" + resultCode);
            }
        }
    }

    /**
     * Call out the Intent.ACTION_GET_CONTENT to get an image from (usually) the Gallery app.
     */
    private void getCoverFromGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
                .setType("image/*");
        mFragment.startActivityForResult(
                Intent.createChooser(intent, mActivity.getString(R.string.title_select_image)),
                UniqueId.REQ_ACTION_GET_CONTENT);
    }

    /**
     * The Intent.ACTION_GET_CONTENT has provided us with an image, process it.
     */
    private void addCoverFromGallery(@NonNull final Intent data) {
        Uri selectedImageUri = data.getData();

        if (selectedImageUri != null) {
            boolean imageOk = false;
            // If no 'content' scheme, then use the content resolver.
            try (InputStream in = mActivity.getContentResolver().openInputStream(
                    selectedImageUri)) {
                imageOk = StorageUtils.saveInputStreamToFile(in, getCoverFile());

            } catch (@SuppressWarnings("OverlyBroadCatchBlock") IOException e) {
                Logger.error(this, e, "Unable to copy content to file");
            }

            if (imageOk) {
                // Update the ImageView with the new image
                updateCoverView();
            } else {
                String msg = mActivity.getString(R.string.warning_cover_copy_failed) + ". "
                        + mActivity.getString(R.string.error_if_the_problem_persists);
                UserMessage.showUserMessage(mCoverField.getView(), msg);
            }
        } else {
            /* Deal with the case where the chooser returns a null intent. This seems to happen
             * when the filename is not properly understood by the chooser (eg. an apostrophe in
             * the file name confuses ES File Explorer in the current version as of 23-Sep-2012. */
            UserMessage.showUserMessage(mCoverField.getView(), R.string.warning_cover_copy_failed);
        }
    }

    /**
     * Rotate the image.
     *
     * @param angle rotate by the specified amount
     */
    private void rotateImage(final long angle) {

        File file = getCoverFile();
        if (!file.exists()) {
            return;
        }

        // We load the file and first scale it to twice the display size.
        // Keep in mind this means it could be up- or downscaled from the original !
        int imageSize = ImageUtils.getDisplaySizes(mActivity).large * 2;

        // we'll try it twice with a gc in between
        int attempts = 2;
        while (true) {
            try {
                Bitmap bm = ImageUtils.createScaledBitmap(file.getPath(), imageSize, imageSize,
                                                          true);
                if (bm == null) {
                    return;
                }

                Matrix matrix = new Matrix();
                matrix.postRotate(angle);
                Bitmap rotatedBitmap = Bitmap.createBitmap(bm, 0, 0,
                                                           bm.getWidth(), bm.getHeight(),
                                                           matrix, true);
                // if rotation worked, clean up the old one right now to save memory.
                if (rotatedBitmap != bm) {
                    bm.recycle();
                }

                // Write back to the file
                try (OutputStream out = new FileOutputStream(file.getAbsoluteFile())) {
                    rotatedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);

                } catch (@SuppressWarnings("OverlyBroadCatchBlock") IOException e) {
                    Logger.error(this, e);
                    return;
                } finally {
                    rotatedBitmap.recycle();
                }

                // put the new image on screen.
                updateCoverView();
                return;

            } catch (OutOfMemoryError e) {
                attempts--;
                if (attempts > 1) {
                    System.gc();
                } else {
                    throw new RuntimeException(e);
                }
            }
        }
    }


    private void cropCoverImage(@NonNull final File imageFile) {
        boolean external = App.getPrefs().getBoolean(Prefs.pk_thumbnails_external_cropper, false);
        if (external) {
            cropCoverImageExternal(imageFile);
        } else {
            cropCoverImageInternal(imageFile);
        }
    }

    /**
     * Crop the image using our internal code in {@link CropImageActivity}.
     *
     * @param imageFile to crop
     */
    private void cropCoverImageInternal(@NonNull final File imageFile) {
        boolean cropFrameWholeImage = App.getPrefs().getBoolean(
                Prefs.pk_thumbnails_crop_frame_is_whole_image,
                false);

        // Get the output file spec, and make sure it does not already exist.
        File cropped = getCroppedTempCoverFile();
        StorageUtils.deleteFile(cropped);

        Intent intent = new Intent(mActivity, CropImageActivity.class)
                .putExtra(CropImageActivity.BKEY_IMAGE_ABSOLUTE_PATH,
                          imageFile.getAbsolutePath())
                .putExtra(CropImageActivity.BKEY_OUTPUT_ABSOLUTE_PATH,
                          cropped.getAbsolutePath())
                .putExtra(CropImageActivity.BKEY_SCALE, true)
                .putExtra(CropImageActivity.BKEY_WHOLE_IMAGE, cropFrameWholeImage);

        mFragment.startActivityForResult(intent, UniqueId.REQ_CROP_IMAGE_INTERNAL);
    }

    /**
     * Crop the image using the standard crop action intent (the device may not support it).
     * FIXME: Google search says that "com.android.camera.action.CROP" is from Android 1.x/2.x and should not be used today.
     * <p>
     * Code using hardcoded string on purpose as they are part of the Intent api.
     *
     * @param imageFile to crop
     */
    private void cropCoverImageExternal(@NonNull final File imageFile) {

        Uri inputURI = FileProvider.getUriForFile(mActivity,
                                                  GenericFileProvider.AUTHORITY,
                                                  imageFile);
        File cropped = getCroppedTempCoverFile();
        // make sure any left-over file is removed.
        StorageUtils.deleteFile(cropped);
        Uri outputURI = Uri.fromFile(cropped);
//                FileProvider.getUriForFile(mActivity,
//                                                   GenericFileProvider.AUTHORITY,
//                                                   cropped);

        //call the standard crop action intent (the device may not support it)
        Intent intent = new Intent("com.android.camera.action.CROP")
                // image Uri and type
                .setDataAndType(inputURI, "image/*")
                // not interested in faces
                .putExtra("noFaceDetection", true)
                // {@code true} to return a Bitmap,
                // {@code false} to directly save the cropped image
                .putExtra("return-data", false)
                //indicate we want to crop
                .putExtra("crop", true)
                // and allow scaling
                .putExtra("scale", true);

        // other options not needed for now.
//            //indicate aspect of desired crop
//            intent.putExtra("aspectX", 1);
//            intent.putExtra("aspectY", 1);
//            //indicate output X and Y
//            intent.putExtra("outputX", 256);
//            intent.putExtra("outputY", 256);

        // Save output image in uri
        intent.putExtra(MediaStore.EXTRA_OUTPUT, outputURI);

        List<ResolveInfo> list = mActivity.getPackageManager().queryIntentActivities(intent, 0);
        if (list.isEmpty()) {
            //noinspection ConstantConditions
            UserMessage.showUserMessage(mFragment.getView(), R.string.error_no_external_crop_app);
        } else {
            mFragment.startActivityForResult(intent, UniqueId.REQ_CROP_IMAGE_EXTERNAL);
        }

    }

    /**
     * @return a temp file for cropping result.
     */
    @NonNull
    private File getCroppedTempCoverFile() {
        return StorageUtils.getTempCoverFile("cropped" + sTempImageCounter);
    }

    /**
     * Delete the image.
     */
    private void deleteCoverFile() {
        try {
            File file = getCoverFile();
            StorageUtils.deleteFile(file);
        } catch (RuntimeException e) {
            Logger.error(this, e);
        }
        invalidateCachedImages();
        // replace the old image with a placeholder.
        updateCoverView();
    }

    /**
     * Ensure that the cached images for this book are deleted (if present).
     */
    private void invalidateCachedImages() {
        if (mBookManager.getBook().getId() != 0) {
            try (CoversDBA db = CoversDBA.getInstance()) {
                db.delete(getUuid());
            } catch (SQLiteDoneException e) {
                Logger.error(this, e, "SQLiteDoneException cleaning up cached cover images");
            } catch (RuntimeException e) {
                Logger.error(this, e, "RuntimeException cleaning up cached cover images");
            }
        }
    }

    /**
     * Dismiss the cover browser.
     */
    void dismissCoverBrowser() {
        if (mCoverBrowserFragment != null) {
            mCoverBrowserFragment.dismiss();
            mCoverBrowserFragment = null;
        }
    }

    @Override
    @CallSuper
    protected void finalize()
            throws Throwable {
        dismissCoverBrowser();
        super.finalize();
    }

    /**
     * Handles results from Camera, Image Gallery, Cropping.
     * <p>
     * Note: rotating is done locally in {@link #rotateImage(long)}.
     *
     * @return {@code true} when handled, {@code false} if unknown requestCode
     */
    public boolean onActivityResult(final int requestCode,
                                    final int resultCode,
                                    @Nullable final Intent data) {
        switch (requestCode) {
            // coming back from CoverBrowser with the selected image.
            case UniqueId.REQ_ALT_EDITION:
                if (resultCode == Activity.RESULT_OK) {
                    //noinspection ConstantConditions
                    onImageSelected(data.getStringExtra(UniqueId.BKEY_FILE_SPEC));
                }
                return true;

            case UniqueId.REQ_ACTION_IMAGE_CAPTURE:
                if (resultCode == Activity.RESULT_OK) {
                    //noinspection ConstantConditions
                    addCoverFromCamera(requestCode, resultCode, data.getExtras());
                }
                return true;

            case UniqueId.REQ_ACTION_GET_CONTENT:
                if (resultCode == Activity.RESULT_OK) {
                    //noinspection ConstantConditions
                    addCoverFromGallery(data);
                }
                return true;

            case UniqueId.REQ_CROP_IMAGE_INTERNAL:
            case UniqueId.REQ_CROP_IMAGE_EXTERNAL:
                if (resultCode == Activity.RESULT_OK) {
                    File cropped = getCroppedTempCoverFile();
                    if (cropped.exists()) {
                        File destination = getCoverFile();
                        StorageUtils.renameFile(cropped, destination);
                        // Update the ImageView with the new image
                        updateCoverView();
                    } else {
                        if (BuildConfig.DEBUG /* WARN */) {
                            Logger.warn(this, "onActivityResult",
                                        "RESULT_OK, but no image file?",
                                        "requestCode=" + requestCode,
                                        "resultCode=" + resultCode);
                        }
                    }
                } else {
                    if (BuildConfig.DEBUG /* WARN */) {
                        Logger.warn(this, "onActivityResult",
                                    "FAILED",
                                    "requestCode=" + requestCode,
                                    "resultCode=" + resultCode);
                    }
                    StorageUtils.deleteFile(getCroppedTempCoverFile());
                }
                return true;

            default:
                return false;
        }
    }
}
