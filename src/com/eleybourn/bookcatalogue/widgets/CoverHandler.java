package com.eleybourn.bookcatalogue.widgets;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupMenu;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.Fields;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.cropper.CropIImage;
import com.eleybourn.bookcatalogue.cropper.CropImageActivity;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.CoversDbAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.SelectOneDialog;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.BookManager;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

/**
 * Handler for a displayed Cover ImageView element.
 * Offers context menus and all operations applicable on a Cover image.
 */
public class CoverHandler implements SelectOneDialog.hasViewContextMenu {

    public static final String PREF_USE_EXTERNAL_IMAGE_CROPPER = "App.UseExternalImageCropper";
    public static final String PREF_CROP_FRAME_WHOLE_IMAGE = "App.CropFrameWholeImage";
    /** Degrees by which to rotate images automatically */
    public static final String PREF_AUTOROTATE_CAMERA_IMAGES = "App.AutorotateCameraImages";
    public static final int PREF_AUTOROTATE_CAMERA_IMAGES_DEFAULT = 90;
    /** Counter used to prevent images being reused accidentally */
    private static int mTempImageCounter = 0;
    @NonNull
    private final Activity mActivity;
    @NonNull
    private final CatalogueDBAdapter mDb;
    @NonNull
    private final BookManager mBookManager;
    private final Fields.Field mCoverField;

    /** keep a reference to the ISBN Field, so we can use the *current* value (instead of using getBook()) */
    private final Fields.Field mIsbnField;
    private final ImageUtils.ThumbSize mThumbSize;
    @Nullable
    private CoverBrowser mCoverBrowser = null;
    /** Used to display a hint if user rotates a camera image */
    private boolean mGotCameraImage = false;

    public CoverHandler(final @NonNull Activity activity,
                        final @NonNull CatalogueDBAdapter db,
                        final @NonNull BookManager bookManager,
                        final @NonNull Fields.Field coverField,
                        final @NonNull Fields.Field isbnField) {
        mActivity = activity;
        mDb = db;
        mBookManager = bookManager;
        mCoverField = coverField;
        mIsbnField = isbnField;

        mThumbSize = ImageUtils.getThumbSizes(mActivity);

        if (mCoverField.visible) {
            // add context menu to the cover image
            initViewContextMenuListener(mActivity, mCoverField.getView());
            //Allow zooming by clicking on the image
            mCoverField.getView().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ImageUtils.showZoomedThumb(mActivity, getCoverFile());
                }
            });
        }
    }

    /**
     * Using {@link SelectOneDialog#showContextMenuDialog} for context menus
     */
    public void onCreateViewContextMenu(final @NonNull Menu menu,
                                        final @NonNull View view,
                                        final @NonNull SelectOneDialog.SimpleDialogMenuInfo menuInfo) {

        if (menu.size() > 0) {
            SelectOneDialog.showContextMenuDialog(mActivity.getLayoutInflater(), menuInfo, menu,
                    new SelectOneDialog.SimpleDialogOnClickListener() {
                        @Override
                        public void onClick(final @NonNull SelectOneDialog.SimpleDialogItem item) {
                            MenuItem menuItem = ((SelectOneDialog.SimpleDialogMenuItem) item).getMenuItem();
                            if (menuItem.hasSubMenu()) {
                                menuInfo.title = menuItem.getTitle().toString();
                                onCreateViewContextMenu(menuItem.getSubMenu(), view, menuInfo);
                            } else {
                                onViewContextItemSelected(menuItem, view);
                            }
                        }
                    });
        }
    }

    /**
     * external from {@link #initViewContextMenuListener} as we also need this from {@link Fragment#onOptionsItemSelected}
     */
    public void prepareCoverImageViewContextMenu() {

        String menuTitle = mActivity.getString(R.string.title_cover);

        // legal trick to get an instance of Menu.
        Menu menu = new PopupMenu(mActivity, null).getMenu();
        // custom menuInfo
        SelectOneDialog.SimpleDialogMenuInfo menuInfo = new SelectOneDialog.SimpleDialogMenuInfo(menuTitle, mCoverField.getView(), 0);
        // populate the menu

        menu.add(Menu.NONE, R.id.MENU_THUMB_DELETE, 0, R.string.menu_delete)
                .setIcon(R.drawable.ic_delete);

        SubMenu replaceThumbnailSubmenu = menu.addSubMenu(Menu.NONE,
                R.id.SUBMENU_THUMB_REPLACE, 2, R.string.menu_cover_replace);
        replaceThumbnailSubmenu.setIcon(R.drawable.ic_find_replace);

        replaceThumbnailSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ADD_FROM_CAMERA, 1, R.string.menu_cover_add_from_camera)
                .setIcon(R.drawable.ic_add_a_photo);
        replaceThumbnailSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ADD_FROM_GALLERY, 2, R.string.menu_cover_add_from_gallery)
                .setIcon(R.drawable.ic_image);
        replaceThumbnailSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ADD_ALT_EDITIONS, 3, R.string.menu_cover_search_alt_editions)
                .setIcon(R.drawable.ic_find_replace);

        SubMenu rotateThumbnailSubmenu = menu.addSubMenu(Menu.NONE,
                R.id.SUBMENU_THUMB_ROTATE, 3, R.string.menu_cover_rotate);
        rotateThumbnailSubmenu.setIcon(R.drawable.ic_rotate_right);

        rotateThumbnailSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ROTATE_CW, 1, R.string.menu_cover_rotate_cw)
                .setIcon(R.drawable.ic_rotate_right);
        rotateThumbnailSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ROTATE_CCW, 2, R.string.menu_cover_rotate_ccw)
                .setIcon(R.drawable.ic_rotate_left);
        rotateThumbnailSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ROTATE_180, 3, R.string.menu_cover_rotate_180)
                .setIcon(R.drawable.ic_swap_vert);
        menu.add(Menu.NONE, R.id.MENU_THUMB_CROP, 4, R.string.menu_cover_crop)
                .setIcon(R.drawable.ic_crop);

        // display
        onCreateViewContextMenu(menu, mCoverField.getView(), menuInfo);
    }

    public void initViewContextMenuListener(final @NonNull Context context, final @NonNull View view) {
        view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(final @NonNull View view) {
                prepareCoverImageViewContextMenu();
                return true;
            }
        });
    }

    /**
     * Using {@link SelectOneDialog#showContextMenuDialog} for context menus
     */
    public boolean onViewContextItemSelected(final @NonNull MenuItem menuItem,
                                             final @NonNull View view) {

        // should not happen for now, but nice to remind ourselves we *could* have multiple views.
        if (view.getId() != R.id.coverImage) {
            return false;
        }

        switch (menuItem.getItemId()) {
            case R.id.MENU_THUMB_DELETE: {
                deleteCoverFile();
                populateCoverView();
                return true;
            }
            case R.id.SUBMENU_THUMB_ROTATE: {
                // Just a submenu; skip, but display a hint if user is rotating a camera image
                if (mGotCameraImage) {
                    HintManager.displayHint(mActivity.getLayoutInflater(), R.string.hint_autorotate_camera_images, null);
                    mGotCameraImage = false;
                }
                return true;
            }
            case R.id.MENU_THUMB_ROTATE_CW: {
                rotateThumbnail(90);
                populateCoverView();
                return true;
            }
            case R.id.MENU_THUMB_ROTATE_CCW: {
                rotateThumbnail(-90);
                populateCoverView();
                return true;
            }
            case R.id.MENU_THUMB_ROTATE_180: {
                rotateThumbnail(180);
                populateCoverView();
                return true;
            }
            case R.id.MENU_THUMB_CROP: {
                cropCoverImage(getCoverFile());
                return true;
            }
            case R.id.MENU_THUMB_ADD_FROM_CAMERA: {
                getCoverFromCamera();
                return true;
            }
            case R.id.MENU_THUMB_ADD_FROM_GALLERY: {
                getCoverFromGallery();
                return true;
            }
            case R.id.MENU_THUMB_ADD_ALT_EDITIONS: {
                addCoverFromAlternativeEditions();
                return true;
            }
        }

        return false;
    }

    /**
     * Load the image into the view, using preset {@link ImageUtils.ThumbSize#small} dimensions
     */
    public void populateCoverView() {
        ImageUtils.fetchFileIntoImageView((ImageView) (mCoverField.getView()), getCoverFile(),
                mThumbSize.small, mThumbSize.small, true);
    }

    /**
     * Load the image into the view, using custom dimensions
     */
    public void populateCoverView(final int maxWidth, final int maxHeight) {
        ImageUtils.fetchFileIntoImageView((ImageView) (mCoverField.getView()), getCoverFile(),
                maxWidth, maxHeight, true);
    }

    /**
     * Get the File object for the cover of the book we are editing.
     * If the book is new (0), return the standard temp file.
     * If the data is a result from a search, then that standard temp file will be the downloaded file.
     */
    @NonNull
    private File getCoverFile() {
        if (mBookManager.getBook().getBookId() == 0) {
            return StorageUtils.getTempCoverFile();
        } else {
            String uuid = mDb.getBookUuid(mBookManager.getBook().getBookId());
            return StorageUtils.getCoverFile(uuid);
        }
    }

    /**
     * Use the isbn to fetch other possible images from the internet and present to the user to choose one
     */
    private void addCoverFromAlternativeEditions() {
        String isbn = mIsbnField.getValue().toString();
        if (IsbnUtils.isValid(isbn)) {
            mCoverBrowser = new CoverBrowser(mActivity, isbn, new CoverBrowser.OnImageSelectedListener() {
                @Override
                public void onImageSelected(final @NonNull String fileSpec) {
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
            });
            mCoverBrowser.showEditionCovers();
        } else {
            //Snackbar.make(mIsbnField.getView(), R.string.editions_require_isbn, Snackbar.LENGTH_LONG).show();
            StandardDialogs.showUserMessage(mActivity, R.string.warning_editions_require_isbn);
        }
    }

    /**
     * Start the camera to get an image
     */
    private void getCoverFromCamera() {
        // Increment the temp counter and cleanup the temp directory
        mTempImageCounter++;
        StorageUtils.purgeTempStorage();
        Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
    /*
        We don't do the next bit of code here because we have no reliable way to rotate a
        large image without producing memory exhaustion.
        Android does not include a file-based image rotation.

        File f = this.getCameraTempCoverFile();
        StorageUtils.deleteFile(f);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));
    */
        mActivity.startActivityForResult(intent, UniqueId.ACTIVITY_REQUEST_CODE_ANDROID_IMAGE_CAPTURE); /* 0b7027eb-a9da-469b-8ba7-2122f1006e92 */
    }

    /**
     * The camera has captured an image, process it.
     */
    private void addCoverFromCamera(final int requestCode, final int resultCode, final @NonNull Bundle bundle) {
        Bitmap bitmap = (Bitmap) bundle.get(CropIImage.BKEY_DATA);
        if (bitmap != null && bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
            Matrix m = new Matrix();
            m.postRotate(BookCatalogueApp.getIntPreference(PREF_AUTOROTATE_CAMERA_IMAGES, PREF_AUTOROTATE_CAMERA_IMAGES_DEFAULT));
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);

            File cameraFile = StorageUtils.getTempCoverFile("camera", "" + CoverHandler.mTempImageCounter);
            FileOutputStream out;
            // Create a file to copy the thumbnail into
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
            Tracker.handleEvent(this, Tracker.States.Running, "onActivityResult(" + requestCode + "," + resultCode + ") - camera image empty");
        }
    }

    /**
     * Call out the Intent.ACTION_GET_CONTENT to get an image from (usually) the Gallery app.
     */
    private void getCoverFromGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        mActivity.startActivityForResult(Intent.createChooser(intent, mActivity.getString(R.string.title_select_picture)),
                UniqueId.ACTIVITY_REQUEST_CODE_ANDROID_ACTION_GET_CONTENT); /* 27ecaa27-5ed8-4670-8947-112ab4ab0098 */
    }

    /**
     * The Intent.ACTION_GET_CONTENT has provided us with an image, process it.
     */
    private void addCoverFromGallery(final @NonNull Intent data) {
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
                String s = mActivity.getString(R.string.warning_cover_copy_failed) + ". " + mActivity.getString(R.string.error_if_the_problem_persists);
                StandardDialogs.showUserMessage(mActivity, s);
            }
        } else {
            /* Deal with the case where the chooser returns a null intent. This seems to happen
             * when the filename is not properly understood by the choose (eg. an apostrophe in
             * the file name confuses ES File Explorer in the current version as of 23-Sep-2012. */
            StandardDialogs.showUserMessage(mActivity, R.string.warning_cover_copy_failed);
        }
    }


    /**
     * Rotate the thumbnail
     *
     * @param angle by a specified amount
     */
    private void rotateThumbnail(final long angle) {
        // we'll try it twice with a gc in between
        int attempts = 2;
        while (true) {
            try {
                File thumbFile = getCoverFile();

                Bitmap bitmap = ImageUtils.fetchFileIntoImageView(null, thumbFile,
                        mThumbSize.large * 2, mThumbSize.large * 2, true);
                if (bitmap == null) {
                    return;
                }

                Matrix matrix = new Matrix();
                matrix.postRotate(angle);
                Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                if (rotatedBitmap != bitmap) {
                    bitmap.recycle();
                }

                /* Create a file to copy the thumbnail into */
                FileOutputStream outFos;
                try {
                    outFos = new FileOutputStream(thumbFile.getAbsoluteFile());
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


    private void cropCoverImage(final @NonNull File thumbFile) {
        boolean external = BookCatalogueApp.getBooleanPreference(PREF_USE_EXTERNAL_IMAGE_CROPPER, false);
        if (external) {
            cropCoverImageExternal(thumbFile);
        } else {
            cropCoverImageInternal(thumbFile);
        }
    }

    /**
     * Get a temp file for cropping output
     */
    @NonNull
    private File getCroppedTempCoverFile() {
        return StorageUtils.getTempCoverFile("cropped", "" + mTempImageCounter);
    }

    private void cropCoverImageInternal(final @NonNull File thumbFile) {
        boolean cropFrameWholeImage = BookCatalogueApp.getBooleanPreference(PREF_CROP_FRAME_WHOLE_IMAGE, false);

        // Get the output file spec, and make sure it does not already exist.
        File cropped = this.getCroppedTempCoverFile();
        StorageUtils.deleteFile(cropped);

        CropImageActivity.startActivityForResult(mActivity, thumbFile, cropped, cropFrameWholeImage); /* 31c90366-d352-496f-9b7d-3237dd199a77 */
    }

    /**
     * Worked for me...
     */
    private void cropCoverImageExternal(final @NonNull File thumbFile) {
        Tracker.handleEvent(this, Tracker.States.Enter, "cropCoverImageExternal");
        try {
            File inputFile = new File(thumbFile.getAbsolutePath());

            //call the standard crop action intent (the device may not support it)
            Intent cropIntent = new Intent("com.android.camera.action.CROP");
            // image Uri and type
            cropIntent.setDataAndType(Uri.fromFile(inputFile), "image/*");
            // not interested in faces
            cropIntent.putExtra("noFaceDetection", true);
            // True to return a Bitmap, false to directly save the cropped image
            cropIntent.putExtra("return-data", false);
            //indicate we want to crop
            cropIntent.putExtra("crop", true);
            // and allow scaling
            cropIntent.putExtra("scale", true);

            // other options not needed for now.
//            //indicate aspect of desired crop
//            cropIntent.putExtra("aspectX", 1);
//            cropIntent.putExtra("aspectY", 1);
//            //indicate output X and Y
//            cropIntent.putExtra("outputX", 256);
//            cropIntent.putExtra("outputY", 256);

            // Save output image in uri
            File cropped = this.getCroppedTempCoverFile();
            StorageUtils.deleteFile(cropped);
            cropIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(cropped.getAbsolutePath())));

            List<ResolveInfo> list = mActivity.getPackageManager().queryIntentActivities(cropIntent, 0);
            if (list.size() == 0) {
                StandardDialogs.showUserMessage(mActivity, R.string.error_no_external_crop_app);
            } else {
                mActivity.startActivityForResult(cropIntent, UniqueId.ACTIVITY_REQUEST_CODE_EXTERNAL_CROP_IMAGE); /* 28ec93b0-24fb-4a81-ae6d-a282f3a7b918 */
            }
        } finally {
            Tracker.handleEvent(this, Tracker.States.Exit, "cropCoverImageExternal");
        }
    }

    /**
     * Delete the provided thumbnail
     */
    private void deleteCoverFile() {
        try {
            File thumbFile = getCoverFile();
            StorageUtils.deleteFile(thumbFile);
        } catch (Exception e) {
            Logger.error(e);
        }
        invalidateCachedThumbnail();
    }

    /**
     * Ensure that the cached thumbnails for this book are deleted (if present)
     */
    private void invalidateCachedThumbnail() {
        final long bookId = mBookManager.getBook().getBookId();
        if (bookId != 0) {
            try (CoversDbAdapter coversDbAdapter = CoversDbAdapter.getInstance()) {
                coversDbAdapter.deleteBookCover(mDb.getBookUuid(bookId));
            } catch (Exception e) {
                Logger.error(e, "Error cleaning up cached cover images");
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
    protected void finalize() {
        dismissCoverBrowser();
    }

    /**
     * @return true when handled, false if unknown requestCode
     */
    public boolean onActivityResult(final int requestCode, final int resultCode, final @Nullable Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);
        boolean handled = false;
        switch (requestCode) {
            case UniqueId.ACTIVITY_REQUEST_CODE_ANDROID_IMAGE_CAPTURE: /* 0b7027eb-a9da-469b-8ba7-2122f1006e92 */
                if (resultCode == Activity.RESULT_OK) {
                    /* there *has* to be 'data' */
                    Objects.requireNonNull(data);
                    Bundle extras = data.getExtras();
                    /* there *has* to be 'extras' */
                    Objects.requireNonNull(extras);
                    addCoverFromCamera(requestCode, resultCode, extras);
                }
                handled = true;
                break;

            case UniqueId.ACTIVITY_REQUEST_CODE_ANDROID_ACTION_GET_CONTENT: /* 27ecaa27-5ed8-4670-8947-112ab4ab0098 */
                if (resultCode == Activity.RESULT_OK) {
                    /* there *has* to be 'data' */
                    Objects.requireNonNull(data);
                    addCoverFromGallery(data);
                }
                handled = true;
                break;

            case CropImageActivity.REQUEST_CODE: /* 31c90366-d352-496f-9b7d-3237dd199a77 */
            case UniqueId.ACTIVITY_REQUEST_CODE_EXTERNAL_CROP_IMAGE: {/* 28ec93b0-24fb-4a81-ae6d-a282f3a7b918 */
                if (resultCode == Activity.RESULT_OK) {
                    File cropped = getCroppedTempCoverFile();
                    if (cropped.exists()) {
                        File destination = getCoverFile();
                        StorageUtils.renameFile(cropped, destination);
                        // Update the ImageView with the new image
                        populateCoverView();
                    } else {
                        Tracker.handleEvent(this, Tracker.States.Running, "onActivityResult(" + requestCode + "," + resultCode + ") - result OK, but no image file");
                    }
                } else {
                    Tracker.handleEvent(this, Tracker.States.Running, "onActivityResult(" + requestCode + "," + resultCode + ") - bad result");
                    StorageUtils.deleteFile(getCroppedTempCoverFile());
                }
                handled = true;
                break;
            }
            default:
        }
        Tracker.exitOnActivityResult(this);
        return handled;
    }
}
