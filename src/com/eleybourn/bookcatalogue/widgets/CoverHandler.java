package com.eleybourn.bookcatalogue.widgets;

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
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupMenu;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.cropper.CropImageActivity;
import com.eleybourn.bookcatalogue.cropper.CropImageViewTouchBase;
import com.eleybourn.bookcatalogue.database.CoversDBA;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.SimpleDialog;
import com.eleybourn.bookcatalogue.entities.BookManager;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;

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
public class CoverHandler
        implements SimpleDialog.ViewContextMenu {

    /**
     * request code: use with {@link CoverBrowser#setTargetFragment(Fragment, int)}
     * and in {@link #onActivityResult(int, int, Intent)}.
     */
    static final int REQ_ALT_EDITION = 0;
    /** request code: use internal routines for cropping images. */
    private static final int REQ_CROP_IMAGE_INTERNAL = 1;
    /** request code: start an intent for an external application to do the cropping. */
    private static final int REQ_CROP_IMAGE_EXTERNAL = 2;
    /** request code: start an intent to get an image from the Camera. */
    private static final int REQ_ACTION_IMAGE_CAPTURE = 3;
    /** request code: start an intent to get an image from the an app that provides content. */
    private static final int REQ_ACTION_GET_CONTENT = 4;
    /** Counter used to prevent images being reused accidentally. */
    private static int mTempImageCounter;
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
    private final ImageUtils.ImageSize mImageSize;
    @Nullable
    private CoverBrowser mCoverBrowser;

    /** Used to display a hint if user rotates a camera image. */
    private boolean mGotCameraImage;

    /**
     * Constructor.
     */
    public CoverHandler(@NonNull final Fragment fragment,
                        @NonNull final DBA db,
                        @NonNull final BookManager bookManager,
                        @NonNull final Fields.Field coverField,
                        @NonNull final Fields.Field isbnField) {
        mFragment = fragment;
        // cache it to avoid multiple calls.
        mActivity = mFragment.requireActivity();
        mDb = db;
        mBookManager = bookManager;
        mCoverField = coverField;
        mIsbnField = isbnField;

        mImageSize = ImageUtils.getImageSizes(mActivity);

        if (mCoverField.isVisible()) {
            // add context menu to the cover image
            initContextMenuOnView(mCoverField.getView());
            //Allow zooming by clicking on the image
            mCoverField.getView().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(@NonNull final View v) {
                    ImageUtils.showZoomedImage(mActivity, getCoverFile());
                }
            });
        }
    }

    /**
     * When the user clicks the switcher in the {@link CoverBrowser}, we take that image and
     * stuff it into the view.
     *
     * @param fileSpec the file
     */
    private void onImageSelected(@NonNull final String fileSpec) {
        if (mCoverBrowser != null) {
            // the new file we got
            File newFile = new File(fileSpec);
            // Get the current file we want to loose
            File bookFile = getCoverFile();
            // copy new file on top of old.
            StorageUtils.renameFile(newFile, bookFile);
            // Update the ImageView with the new image
            populateCoverView();
            mCoverBrowser.dismiss();
            mCoverBrowser = null;
        }
    }

    /**
     * Using {@link SimpleDialog#showContextMenu} for context menus.
     */
    @Override
    public void onCreateViewContextMenu(@NonNull final View view,
                                        @NonNull final Menu menu,
                                        @NonNull final SimpleDialog.ContextMenuInfo menuInfo) {

        if (menu.size() > 0) {
            SimpleDialog.showContextMenu(
                    mActivity.getLayoutInflater(), menuInfo.title, menu,
                    new SimpleDialog.OnClickListener() {
                        @Override
                        public void onClick(@NonNull final SimpleDialog.SimpleDialogItem item) {
                            MenuItem menuItem =
                                    ((SimpleDialog.SimpleDialogMenuItem) item).getMenuItem();
                            if (menuItem.hasSubMenu()) {
                                menuInfo.title = menuItem.getTitle().toString();
                                // recursive call for sub-menu
                                onCreateViewContextMenu(view, menuItem.getSubMenu(), menuInfo);
                            } else {
                                onViewContextItemSelected(view, menuItem);
                            }
                        }
                    });
        }
    }

    /**
     * external from {@link #initContextMenuOnView} as we also need this
     * from {@link Fragment#onOptionsItemSelected}.
     */
    public void prepareCoverImageViewContextMenu() {

        String menuTitle = mActivity.getString(R.string.title_cover);

        // legal trick to get an instance of Menu.
        Menu menu = new PopupMenu(mActivity, null).getMenu();
        // custom menuInfo
        SimpleDialog.ContextMenuInfo menuInfo =
                new SimpleDialog.ContextMenuInfo(menuTitle, 0);

        // populate the menu
        menu.add(Menu.NONE, R.id.MENU_THUMB_DELETE, Menu.NONE, R.string.menu_delete)
            .setIcon(R.drawable.ic_delete);

        SubMenu replaceSubmenu = menu.addSubMenu(Menu.NONE, R.id.SUBMENU_THUMB_REPLACE, Menu.NONE,
                                                 R.string.menu_cover_replace);
        replaceSubmenu.setIcon(R.drawable.ic_find_replace);

        replaceSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ADD_FROM_CAMERA, Menu.NONE,
                           R.string.menu_cover_add_from_camera)
                      .setIcon(R.drawable.ic_add_a_photo);
        replaceSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ADD_FROM_GALLERY, Menu.NONE,
                           R.string.menu_cover_add_from_gallery)
                      .setIcon(R.drawable.ic_image);
        replaceSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ADD_ALT_EDITIONS, Menu.NONE,
                           R.string.menu_cover_search_alt_editions)
                      .setIcon(R.drawable.ic_find_replace);

        SubMenu rotateSubmenu = menu.addSubMenu(Menu.NONE, R.id.SUBMENU_THUMB_ROTATE, Menu.NONE,
                                                R.string.menu_cover_rotate);
        rotateSubmenu.setIcon(R.drawable.ic_rotate_right);

        rotateSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ROTATE_CW, Menu.NONE,
                          R.string.menu_cover_rotate_cw)
                     .setIcon(R.drawable.ic_rotate_right);
        rotateSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ROTATE_CCW, Menu.NONE,
                          R.string.menu_cover_rotate_ccw)
                     .setIcon(R.drawable.ic_rotate_left);
        rotateSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ROTATE_180, Menu.NONE,
                          R.string.menu_cover_rotate_180)
                     .setIcon(R.drawable.ic_swap_vert);

        menu.add(Menu.NONE, R.id.MENU_THUMB_CROP, Menu.NONE, R.string.menu_cover_crop)
            .setIcon(R.drawable.ic_crop);

        // display
        onCreateViewContextMenu(mCoverField.getView(), menu, menuInfo);
    }

    @Override
    public void initContextMenuOnView(@NonNull final View view) {
        view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(@NonNull final View v) {
                prepareCoverImageViewContextMenu();
                return true;
            }
        });
    }

    /**
     * Using {@link SimpleDialog#showContextMenu} for context menus.
     */
    @Override
    public boolean onViewContextItemSelected(@NonNull final View view,
                                             @NonNull final MenuItem menuItem) {

        // should not happen for now, but nice to remind ourselves we *could* have multiple views.
        if (view.getId() != R.id.coverImage) {
            return false;
        }

        switch (menuItem.getItemId()) {
            case R.id.MENU_THUMB_DELETE:
                deleteCoverFile();
                populateCoverView();
                return true;

            case R.id.SUBMENU_THUMB_ROTATE:
                // Just a submenu; skip, but display a hint if user is rotating a camera image
                if (mGotCameraImage) {
                    HintManager.displayHint(
                            mActivity.getLayoutInflater(),
                            R.string.hint_autorotate_camera_images,
                            null);
                    mGotCameraImage = false;
                }
                return true;

            case R.id.MENU_THUMB_ROTATE_CW:
                rotateImage(90);
                populateCoverView();
                return true;

            case R.id.MENU_THUMB_ROTATE_CCW:
                rotateImage(-90);
                populateCoverView();
                return true;

            case R.id.MENU_THUMB_ROTATE_180:
                rotateImage(180);
                populateCoverView();
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
     * Load the image into the view, using preset {@link ImageUtils.ImageSize#small} dimensions.
     */
    public void populateCoverView() {
        ImageUtils.getImageAndPutIntoView((ImageView) (mCoverField.getView()), getCoverFile(),
                                          mImageSize.small, mImageSize.small, true);
    }

    /**
     * Load the image into the view, using custom dimensions.
     */
    public void populateCoverView(final int maxWidth,
                                  final int maxHeight) {
        ImageUtils.getImageAndPutIntoView((ImageView) (mCoverField.getView()), getCoverFile(),
                                          maxWidth, maxHeight, true);
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
        } else {
            String uuid = mDb.getBookUuid(mBookManager.getBook().getId());
            return StorageUtils.getCoverFile(uuid);
        }
    }

    /**
     * Use the isbn to fetch other possible images from the internet and present to
     * the user to choose one.
     */
    private void addCoverFromAlternativeEditions() {
        if (LibraryThingManager.noKey()) {
            LibraryThingManager.needLibraryThingAlert(mActivity, true, "cover_browser");
            return;
        }

        String isbn = mIsbnField.getValue().toString();
        if (IsbnUtils.isValid(isbn)) {
            mCoverBrowser = CoverBrowser.newInstance(isbn, SearchSites.Site.SEARCH_ALL);
            // allow a callback when the user clicks on the image they want to use.
            // at least we don't need to travel round to the Activity this way.
            mCoverBrowser.setTargetFragment(mFragment, REQ_ALT_EDITION);
            mCoverBrowser.show(mActivity.getSupportFragmentManager(), CoverBrowser.TAG);
        } else {
            //Snackbar.make(mIsbnField.getView(), R.string.editions_require_isbn,
            //              Snackbar.LENGTH_LONG).show();
            UserMessage.showUserMessage(mActivity, R.string.warning_editions_require_isbn);
        }
    }

    /**
     * Start the camera to get an image.
     */
    private void getCoverFromCamera() {
        // Increment the temp counter and cleanup the temp directory
        mTempImageCounter++;
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
        mFragment.startActivityForResult(intent, REQ_ACTION_IMAGE_CAPTURE);
    }

    /**
     * The camera has captured an image, process it.
     */
    private void addCoverFromCamera(final int requestCode,
                                    final int resultCode,
                                    @NonNull final Bundle bundle) {
        Bitmap bitmap = (Bitmap) bundle.get(CropImageActivity.BKEY_DATA);
        if (bitmap != null && bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
            Matrix m = new Matrix();
            m.postRotate(Prefs.getListPreference(R.string.pk_thumbnails_rotate_auto, 0));
            bitmap = Bitmap.createBitmap(bitmap, 0, 0,
                                         bitmap.getWidth(), bitmap.getHeight(),
                                         m, true);

            File cameraFile = StorageUtils.getTempCoverFile(
                    "camera" + CoverHandler.mTempImageCounter);
            FileOutputStream out;
            // Create a file to copy the image into
            try {
                out = new FileOutputStream(cameraFile.getAbsoluteFile());
            } catch (FileNotFoundException e) {
                Logger.error(e);
                return;
            }

            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);

            cropCoverImage(cameraFile);
            mGotCameraImage = true;
        } else {
            Tracker.handleEvent(this, Tracker.States.Running,
                                "onActivityResult(" + requestCode + ','
                                        + resultCode + ") - camera image empty");
        }
    }

    /**
     * Call out the Intent.ACTION_GET_CONTENT to get an image from (usually) the Gallery app.
     */
    private void getCoverFromGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        mFragment.startActivityForResult(
                Intent.createChooser(intent, mActivity.getString(R.string.title_select_image)),
                REQ_ACTION_GET_CONTENT);
    }

    /**
     * The Intent.ACTION_GET_CONTENT has provided us with an image, process it.
     */
    private void addCoverFromGallery(@NonNull final Intent data) {
        Uri selectedImageUri = data.getData();

        if (selectedImageUri != null) {
            boolean imageOk = false;
            // If no 'content' scheme, then use the content resolver.
            try {
                InputStream in = mActivity.getContentResolver().openInputStream(selectedImageUri);
                imageOk = StorageUtils.saveInputStreamToFile(in, getCoverFile());
            } catch (FileNotFoundException e) {
                Logger.error(e, "Unable to copy content to file");
            }
            if (imageOk) {
                // Update the ImageView with the new image
                populateCoverView();
            } else {
                String s = mActivity.getString(
                        R.string.warning_cover_copy_failed) + ". "
                        + mActivity.getString(R.string.error_if_the_problem_persists);
                UserMessage.showUserMessage(mActivity, s);
            }
        } else {
            /* Deal with the case where the chooser returns a null intent. This seems to happen
             * when the filename is not properly understood by the chooser (eg. an apostrophe in
             * the file name confuses ES File Explorer in the current version as of 23-Sep-2012. */
            UserMessage.showUserMessage(mActivity, R.string.warning_cover_copy_failed);
        }
    }

    /**
     * Rotate the image.
     *
     * @param angle rotate by the specified amount
     */
    private void rotateImage(final long angle) {
        // we'll try it twice with a gc in between
        int attempts = 2;
        while (true) {
            try {
                File file = getCoverFile();
                Bitmap bitmap = ImageUtils.getImage(file,
                                                    mImageSize.large * 2,
                                                    mImageSize.large * 2, true);
                if (bitmap == null) {
                    return;
                }

                Matrix matrix = new Matrix();
                matrix.postRotate(angle);
                Bitmap rotatedBitmap = Bitmap
                        .createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                                      bitmap.getHeight(), matrix, true);
                if (rotatedBitmap != bitmap) {
                    bitmap.recycle();
                }

                /* Create a file to copy the image into */
                FileOutputStream outFos;
                try {
                    outFos = new FileOutputStream(file.getAbsoluteFile());
                } catch (FileNotFoundException e) {
                    Logger.error(e);
                    return;
                }
                rotatedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outFos);
                rotatedBitmap.recycle();
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
        boolean external = Prefs.getBoolean(R.string.pk_thumbnails_external_cropper, false);
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
        boolean cropFrameWholeImage = Prefs.getBoolean(
                R.string.pk_thumbnails_crop_frame_is_whole_image, false);

        // Get the output file spec, and make sure it does not already exist.
        File cropped = getCroppedTempCoverFile();
        StorageUtils.deleteFile(cropped);

        Intent intent = new Intent(mActivity, CropImageActivity.class);
        intent.putExtra(CropImageActivity.REQUEST_KEY_IMAGE_ABSOLUTE_PATH,
                        imageFile.getAbsolutePath());
        intent.putExtra(CropImageActivity.REQUEST_KEY_SCALE, true);
        intent.putExtra(CropImageActivity.REQUEST_KEY_WHOLE_IMAGE, cropFrameWholeImage);
        intent.putExtra(CropImageActivity.REQUEST_KEY_OUTPUT_ABSOLUTE_PATH,
                        cropped.getAbsolutePath());

        mFragment.startActivityForResult(intent, REQ_CROP_IMAGE_INTERNAL);
    }

    /**
     * Crop the image using the standard crop action intent (the device may not support it).
     * <p>
     * Code using hardcoded string on purpose as they are part of the Intent api.
     *
     * @param imageFile to crop
     */
    private void cropCoverImageExternal(@NonNull final File imageFile) {
        Tracker.handleEvent(this, Tracker.States.Enter, "cropCoverImageExternal");
        try {
            File inputFile = new File(imageFile.getAbsolutePath());

            //call the standard crop action intent (the device may not support it)
            Intent intent = new Intent("com.android.camera.action.CROP");
            // image Uri and type
            intent.setDataAndType(Uri.fromFile(inputFile), "image/*");
            // not interested in faces
            intent.putExtra("noFaceDetection", true);
            // <tt>true</tt> to return a Bitmap, <tt>false</tt> to directly save the cropped image
            intent.putExtra("return-data", false);
            //indicate we want to crop
            intent.putExtra("crop", true);
            // and allow scaling
            intent.putExtra("scale", true);

            // other options not needed for now.
//            //indicate aspect of desired crop
//            cropIntent.putExtra("aspectX", 1);
//            cropIntent.putExtra("aspectY", 1);
//            //indicate output X and Y
//            cropIntent.putExtra("outputX", 256);
//            cropIntent.putExtra("outputY", 256);

            // Save output image in uri
            File cropped = getCroppedTempCoverFile();
            StorageUtils.deleteFile(cropped);
            intent.putExtra(MediaStore.EXTRA_OUTPUT,
                            Uri.fromFile(new File(cropped.getAbsolutePath())));

            List<ResolveInfo> list = mActivity.getPackageManager()
                                              .queryIntentActivities(intent, 0);
            if (list.isEmpty()) {
                UserMessage.showUserMessage(mActivity, R.string.error_no_external_crop_app);
            } else {
                mFragment.startActivityForResult(intent, REQ_CROP_IMAGE_EXTERNAL);
            }
        } finally {
            Tracker.handleEvent(this, Tracker.States.Exit, "cropCoverImageExternal");
        }
    }

    /**
     * @return a temp file for cropping result.
     */
    @NonNull
    private File getCroppedTempCoverFile() {
        return StorageUtils.getTempCoverFile("cropped" + mTempImageCounter);
    }

    /**
     * Delete the image.
     */
    private void deleteCoverFile() {
        try {
            File file = getCoverFile();
            StorageUtils.deleteFile(file);
        } catch (RuntimeException e) {
            Logger.error(e);
        }
        invalidateCachedImages();
    }

    /**
     * Ensure that the cached images for this book are deleted (if present).
     */
    private void invalidateCachedImages() {
        final long bookId = mBookManager.getBook().getId();
        if (bookId != 0) {
            try (CoversDBA db = CoversDBA.getInstance()) {
                db.delete(mDb.getBookUuid(bookId));
            } catch (SQLiteDoneException e) {
                Logger.error(e, "SQLiteDoneException cleaning up cached cover images");
            } catch (RuntimeException e) {
                Logger.error(e, "RuntimeException cleaning up cached cover images");
            }
        }
    }

    /**
     * Dismiss the cover browser.
     */
    public void dismissCoverBrowser() {
        if (mCoverBrowser != null) {
            mCoverBrowser.dismiss();
            mCoverBrowser = null;
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
     * @return <tt>true</tt> when handled, <tt>false</tt> if unknown requestCode
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean onActivityResult(final int requestCode,
                                    final int resultCode,
                                    @Nullable final Intent data) {
        switch (requestCode) {
            // coming back CoverBrowser with the selected image.
            case REQ_ALT_EDITION:
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    onImageSelected(data.getStringExtra(UniqueId.BKEY_FILE_SPEC));
                }
                return true;

            case REQ_ACTION_IMAGE_CAPTURE:
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);

                    Bundle extras = data.getExtras();
                    Objects.requireNonNull(extras);
                    addCoverFromCamera(requestCode, resultCode, extras);
                }
                return true;

            case REQ_ACTION_GET_CONTENT:
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    addCoverFromGallery(data);
                }
                return true;

            case REQ_CROP_IMAGE_INTERNAL:
            case REQ_CROP_IMAGE_EXTERNAL:
                if (resultCode == Activity.RESULT_OK) {
                    File cropped = getCroppedTempCoverFile();
                    if (cropped.exists()) {
                        File destination = getCoverFile();
                        StorageUtils.renameFile(cropped, destination);
                        // Update the ImageView with the new image
                        populateCoverView();
                    } else {
                        Tracker.handleEvent(this, Tracker.States.Running,
                                            "onActivityResult(" + requestCode + ','
                                                    + resultCode + ')'
                                                    + " - result OK, but no image file");
                    }
                } else {
                    Tracker.handleEvent(this, Tracker.States.Running,
                                        "onActivityResult(" + requestCode + ','
                                                + resultCode + ") - bad result");
                    StorageUtils.deleteFile(getCroppedTempCoverFile());
                }
                return true;

            default:
                return false;
        }
    }
}
