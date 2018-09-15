package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.widget.ImageView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.CoverBrowser.OnImageSelectedListener;
import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.cropper.CropCropImage;
import com.eleybourn.bookcatalogue.database.CoversDbHelper;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.eleybourn.bookcatalogue.UniqueId.KEY_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_DESCRIPTION;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_FORMAT;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_GENRE;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_ISBN;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_LANGUAGE;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_LIST_PRICE;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_PAGES;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_PUBLISHER;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_SERIES_NAME;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_SIGNED;
import static com.eleybourn.bookcatalogue.UniqueId.KEY_TITLE;

/**
 * Abstract class for creating activities containing book details.
 * Here we define common method for all children: database and background initializing,
 * initializing fields and display metrics and other common tasks.
 *
 * @author n.silin
 */
public abstract class BookDetailsAbstractFragment extends EditBookAbstractFragment {

    public static final Character BOOKSHELF_SEPARATOR = ',';

    private static final int CONTEXT_ID_DELETE = 1;
    private static final int CONTEXT_SUBMENU_REPLACE_THUMB = 2;
    private static final int CONTEXT_ID_SUBMENU_ROTATE_THUMB = 3;
    private static final int CONTEXT_ID_CROP_THUMB = 6;
    private static final int CODE_ADD_PHOTO = 21;
    private static final int CODE_ADD_GALLERY = 22;
    private static final int CONTEXT_ID_SHOW_ALT_COVERS = 23;
    private static final int CONTEXT_ID_ROTATE_THUMB_CW = 31;
    private static final int CONTEXT_ID_ROTATE_THUMB_CCW = 32;
    private static final int CONTEXT_ID_ROTATE_THUMB_180 = 33;
    private static final int CODE_CROP_RESULT_EXTERNAL = 42;
    private static final int CODE_CROP_RESULT_INTERNAL = 43;

    private static final String BKEY_IMAGE_PATH = "image-path";
    private static final String BKEY_SCALE = "scale";
    private static final String BKEY_WHOLE_IMAGE = "whole-image";
    private static final String BKEY_OUTPUT = "output";
    private static final String BKEY_CROP = "crop";
    private static final String BKEY_NO_FACE_DETECTION = "noFaceDetection";
    private static final String BKEY_RETURN_DATA = "return-data";
    private static final String BKEY_DATA = "data";
    private static final String BOOKSHELF_TEXT = "bookshelf_text";
    private static final String THUMBNAIL = "thumbnail";
    /**
     * Counter used to prevent images being reused accidentally
     */
    private static int mTempImageCounter = 0;
    /**
     * Listener for creating context menu for book thumbnail.
     */
    private final OnCreateContextMenuListener mCreateBookThumbContextMenuListener = new OnCreateContextMenuListener() {
        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            MenuItem delete = menu.add(0, CONTEXT_ID_DELETE, 0, R.string.menu_delete_thumb);
            delete.setIcon(android.R.drawable.ic_menu_delete);

            // Creating submenu item for rotate
            SubMenu replaceSubmenu = menu.addSubMenu(0, CONTEXT_SUBMENU_REPLACE_THUMB, 2, R.string.menu_replace_thumb);
            replaceSubmenu.setIcon(android.R.drawable.ic_menu_gallery);

            MenuItem add_photo = replaceSubmenu.add(0, CODE_ADD_PHOTO, 1, R.string.menu_add_thumb_photo);
            add_photo.setIcon(android.R.drawable.ic_menu_camera);
            MenuItem add_gallery = replaceSubmenu.add(0, CODE_ADD_GALLERY, 2, R.string.menu_add_thumb_gallery);
            add_gallery.setIcon(android.R.drawable.ic_menu_gallery);
            MenuItem alt_covers = replaceSubmenu.add(0, CONTEXT_ID_SHOW_ALT_COVERS, 3, R.string.menu_thumb_alt_editions);
            alt_covers.setIcon(android.R.drawable.ic_menu_zoom);

            // Implementing submenu for rotate
            SubMenu submenu = menu.addSubMenu(0, CONTEXT_ID_SUBMENU_ROTATE_THUMB, 3, R.string.menu_rotate_thumb);
            add_gallery.setIcon(android.R.drawable.ic_menu_rotate);

            MenuItem rotate_photo_cw = submenu.add(0, CONTEXT_ID_ROTATE_THUMB_CW, 1, R.string.menu_rotate_thumb_cw);
            rotate_photo_cw.setIcon(android.R.drawable.ic_menu_rotate);
            MenuItem rotate_photo_ccw = submenu.add(0, CONTEXT_ID_ROTATE_THUMB_CCW, 2, R.string.menu_rotate_thumb_ccw);
            rotate_photo_ccw.setIcon(android.R.drawable.ic_menu_rotate);
            MenuItem rotate_photo_180 = submenu.add(0, CONTEXT_ID_ROTATE_THUMB_180, 3, R.string.menu_rotate_thumb_180);
            rotate_photo_180.setIcon(android.R.drawable.ic_menu_rotate);

            MenuItem crop_thumb = menu.add(0, CONTEXT_ID_CROP_THUMB, 4, R.string.menu_crop_thumb);
            crop_thumb.setIcon(android.R.drawable.ic_menu_crop);
        }
    };

    /**
     * Minimum of MAX_EDIT_THUMBNAIL_SIZE and 1/3rd of largest screen dimension
     */
    protected Integer mThumbEditSize;
    /**
     * Zoom size is minimum of MAX_ZOOM_THUMBNAIL_SIZE and largest screen dimension.
     */
    private Integer mThumbZoomSize;
    private CoverBrowser mCoverBrowser = null;
    /**
     * Handler to process a cover selected from the CoverBrowser.
     */
    private final OnImageSelectedListener mOnImageSelectedListener = new OnImageSelectedListener() {
        @Override
        public void onImageSelected(String fileSpec) {
            if (mCoverBrowser != null && fileSpec != null) {
                // Get the current file
                File bookFile = getCoverFile(mEditManager.getBookData().getRowId());
                // Get the new file
                File newFile = new File(fileSpec);
                // Overwrite with new file
                //noinspection ResultOfMethodCallIgnored
                newFile.renameTo(bookFile);
                // update current activity
                setCoverImage();
            }
            if (mCoverBrowser != null)
                mCoverBrowser.dismiss();
            mCoverBrowser = null;
        }
    };
    /**
     * Used to display a hint if user rotates a camera image
     */
    private boolean mGotCameraImage = false;

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        Tracker.enterOnActivityResult(this, requestCode,resultCode);
        try {
            super.onActivityResult(requestCode, resultCode, intent);
            switch (requestCode) {
                case CODE_ADD_PHOTO:
                    if (resultCode == Activity.RESULT_OK && intent != null && intent.getExtras() != null) {
                        File cameraFile = getCameraImageFile();
                        Bitmap x = (Bitmap) intent.getExtras().get(BKEY_DATA);
                        if (x != null && x.getWidth() > 0 && x.getHeight() > 0) {
                            Matrix m = new Matrix();
                            m.postRotate(BCPreferences.getAutoRotateCameraImagesInDegrees());
                            x = Bitmap.createBitmap(x, 0, 0, x.getWidth(), x.getHeight(), m, true);
                            // Create a file to copy the thumbnail into
                            FileOutputStream f;
                            try {
                                f = new FileOutputStream(cameraFile.getAbsoluteFile());
                            } catch (FileNotFoundException e) {
                                Logger.logError(e);
                                return;
                            }

                            x.compress(Bitmap.CompressFormat.PNG, 100, f);

                            cropCoverImage(cameraFile);
                            mGotCameraImage = true;
                        } else {
                            Tracker.handleEvent(this, "onActivityResult(" + requestCode + "," + resultCode + ") - camera image empty", Tracker.States.Running);
                        }
                    }
                    return;
                case CODE_ADD_GALLERY:
                    if (resultCode == Activity.RESULT_OK) {
                        Uri selectedImageUri = intent.getData();

                        if (selectedImageUri != null) {
                            boolean imageOk = false;
                            // If no 'content' scheme, then use the content resolver.
                            try {
                                InputStream in = getActivity().getContentResolver().openInputStream(selectedImageUri);
                                imageOk = Utils.saveInputToFile(Objects.requireNonNull(in), getCoverFile(mEditManager.getBookData().getRowId()));
                            } catch (FileNotFoundException e) {
                                Logger.logError(e, "Unable to copy content to file");
                            }
                            if (imageOk) {
                                // Update the ImageView with the new image
                                setCoverImage();
                            } else {
                                String s = getResources().getString(R.string.could_not_copy_image) + ". " + getResources().getString(R.string.if_the_problem_persists);
                                Toast.makeText(getActivity(), s, Toast.LENGTH_LONG).show();
                            }
                        } else {
                            /* Deal with the case where the chooser returns a null intent. This seems to happen
                             * when the filename is not properly understood by the choose (eg. an apostrophe in
                             * the file name confuses ES File Explorer in the current version as of 23-Sep-2012. */
                            Toast.makeText(getActivity(), R.string.could_not_copy_image, Toast.LENGTH_LONG).show();
                        }
                    }
                    return;
                case CODE_CROP_RESULT_EXTERNAL: {
                    File thumbFile = getCoverFile(mEditManager.getBookData().getRowId());
                    File cropped = this.getCroppedImageFileName();
                    if (resultCode == Activity.RESULT_OK) {
                        if (cropped.exists()) {
                            // Update the ImageView with the new image
                            //noinspection ResultOfMethodCallIgnored
                            cropped.renameTo(thumbFile);
                            setCoverImage();
                        } else {
                            Tracker.handleEvent(this, "onActivityResult(" + requestCode + "," + resultCode + ") - result OK, no image file", Tracker.States.Running);
                        }
                    } else {
                        Tracker.handleEvent(this, "onActivityResult(" + requestCode + "," + resultCode + ") - bad result", Tracker.States.Running);
                        if (cropped.exists())
                            //noinspection ResultOfMethodCallIgnored
                            cropped.delete();
                    }
                    return;
                }
                case CODE_CROP_RESULT_INTERNAL: {
                    File thumbFile = getCoverFile(mEditManager.getBookData().getRowId());
                    File cropped = this.getCroppedImageFileName();
                    if (resultCode == Activity.RESULT_OK) {
                        if (cropped.exists()) {
                            //noinspection ResultOfMethodCallIgnored
                            cropped.renameTo(thumbFile);
                            // Update the ImageView with the new image
                            setCoverImage();
                        }
                    }
                    return;
                }
            }
        } finally {
            Tracker.exitOnActivityResult(this,requestCode,resultCode);
        }
    }

    /* Note that you should use setContentView() method in descendant before running this.
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // See how big the display is and use that to set bitmap sizes
        Integer[] thumbSizes = ImageUtils.getThumbSizes(getActivity());
        mThumbEditSize = thumbSizes[0];
        mThumbZoomSize = thumbSizes[1];

        initFields();

        //Set zooming by default on clicking on image
        getView().findViewById(R.id.row_img).setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                long rowId = mEditManager.getBookData().getRowId();
                ImageUtils.showZoomedThumb(getActivity(), getCoverFile(rowId));
            }
        });
    }

    @Override
    public void onPause() {
        Tracker.enterOnPause(this);
        super.onPause();

        // Close down the cover browser.
        if (mCoverBrowser != null) {
            mCoverBrowser.dismiss();
            mCoverBrowser = null;
        }
        Tracker.exitOnPause(this);
    }

    @Override
    public void onResume() {
        Tracker.enterOnResume(this);
        super.onResume();
        Tracker.exitOnResume(this);
    }

    @Override
    public void onDestroy() {
        Tracker.enterOnDestroy(this);
        super.onDestroy();
        mDb.close();
        Tracker.exitOnDestroy(this);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        Tracker.handleEvent(this, "Context Menu Item " + item.getItemId(), Tracker.States.Enter);

        try {
            ImageView iv = getView().findViewById(R.id.row_img);
            File thumbFile = getCoverFile(mEditManager.getBookData().getRowId());

            switch (item.getItemId()) {
                case CONTEXT_ID_DELETE:
                    deleteThumbnail();
                    ImageUtils.fetchFileIntoImageView(iv, thumbFile, mThumbEditSize, mThumbEditSize, true);
                    return true;
                case CONTEXT_ID_SUBMENU_ROTATE_THUMB:
                    // Just a submenu; skip, but display a hint if user is rotating a camera image
                    if (mGotCameraImage) {
                        HintManager.displayHint(getActivity(), R.string.hint_autorotate_camera_images, null);
                        mGotCameraImage = false;
                    }
                    return true;
                case CONTEXT_ID_ROTATE_THUMB_CW:
                    rotateThumbnail(90);
                    ImageUtils.fetchFileIntoImageView(iv, thumbFile, mThumbEditSize, mThumbEditSize, true);
                    return true;
                case CONTEXT_ID_ROTATE_THUMB_CCW:
                    rotateThumbnail(-90);
                    ImageUtils.fetchFileIntoImageView(iv, thumbFile, mThumbEditSize, mThumbEditSize, true);
                    return true;
                case CONTEXT_ID_ROTATE_THUMB_180:
                    rotateThumbnail(180);
                    ImageUtils.fetchFileIntoImageView(iv, thumbFile, mThumbEditSize, mThumbEditSize, true);
                    return true;
                case CODE_ADD_PHOTO:
                    // Increment the temp counter and cleanup the temp directory
                    mTempImageCounter++;
                    cleanupTempImages();
                    Intent pintent;
                    // Get a photo
                    pintent = new Intent("android.media.action.IMAGE_CAPTURE");
//				We don't do this because we have no reliable way to rotate a large image
//				without producing memory exhaustion; Android does not include a file-based
//				image rotation.
//				File f = this.getCameraImageFile();
//				if (f.exists())
//					f.delete();
//				pintent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));
                    startActivityForResult(pintent, CODE_ADD_PHOTO);
                    return true;
                case CODE_ADD_GALLERY:
                    Intent gintent = new Intent();
                    gintent.setType("image/*");
                    gintent.setAction(Intent.ACTION_GET_CONTENT);
                    startActivityForResult(Intent.createChooser(gintent, getResources().getString(R.string.select_picture)), CODE_ADD_GALLERY);
                    return true;
                case CONTEXT_ID_CROP_THUMB:
                    cropCoverImage(thumbFile);
                    return true;
                case CONTEXT_ID_SHOW_ALT_COVERS:
                    String isbn = mFields.getField(R.id.isbn).getValue().toString();
                    if (isbn == null || isbn.trim().isEmpty()) {
                        Toast.makeText(getActivity(), getResources().getString(R.string.editions_require_isbn), Toast.LENGTH_LONG).show();
                    } else {
                        mCoverBrowser = new CoverBrowser(getActivity(), isbn, mOnImageSelectedListener);
                        mCoverBrowser.showEditionCovers();
                    }
                    return true;
            }
            return super.onContextItemSelected(item);
        } finally {
            Tracker.handleEvent(this, "Context Menu Item " + item.getItemId(), Tracker.States.Exit);
        }
    }

    /**
     * Delete everything in the temp file directory
     */
    private void cleanupTempImages() {
        File dir = StorageUtils.getTempImageDirectory();
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }
    }

    @SuppressWarnings("unused")
    private void copyFile(File src, File dst) throws IOException {
        FileInputStream fis = new FileInputStream(src);
        FileOutputStream fos = new FileOutputStream(dst);
        FileChannel inChannel = fis.getChannel();
        FileChannel outChannel = fos.getChannel();

        try {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } finally {
            if (inChannel != null) {
                inChannel.close();
            }
            outChannel.close();
            fis.close();
            fos.close();
        }
    }

    private void cropCoverImage(File thumbFile) {
        if (BCPreferences.getUseExternalImageCropper()) {
            cropCoverImageExternal(thumbFile);
        } else {
            cropCoverImageInternal(thumbFile);
        }
    }

    private void cropCoverImageInternal(File thumbFile) {
        Intent crop_intent = new Intent(getActivity(), CropCropImage.class);
        // here you have to pass absolute path to your file
        crop_intent.putExtra(BKEY_IMAGE_PATH, thumbFile.getAbsolutePath());
        crop_intent.putExtra(BKEY_SCALE, true);
        crop_intent.putExtra(BKEY_WHOLE_IMAGE, BCPreferences.getCropFrameWholeImage());
        // Get and set the output file spec, and make sure it does not already exist.
        File cropped = this.getCroppedImageFileName();
        if (cropped.exists()) {
            //noinspection ResultOfMethodCallIgnored
            cropped.delete();
        }
        crop_intent.putExtra(BKEY_OUTPUT, cropped.getAbsolutePath());
        startActivityForResult(crop_intent, CODE_CROP_RESULT_INTERNAL);
    }

    private void cropCoverImageExternal(File thumbFile) {
        Tracker.handleEvent(this, "cropCoverImageExternal", Tracker.States.Enter);
        try {
            Intent intent = new Intent("com.android.camera.action.CROP");
            // this will open any image file
            intent.setDataAndType(Uri.fromFile(new File(thumbFile.getAbsolutePath())), "image/*");
            //TODO: does not seem to be used... find out what is it/was for and remove.
            intent.putExtra(BKEY_CROP, "true");

//          // This defines the aspect ratio
//			intent.putExtra("aspectX", 1);
//			intent.putExtra("aspectY", 1);
//          // This defines the output bitmap size
//			intent.putExtra("outputX", 3264);
//			intent.putExtra("outputY", 2448);
//			intent.putExtra("outputX", mThumbZoomSize*2);
//			intent.putExtra("outputY", mThumbZoomSize*2);

            intent.putExtra(BKEY_SCALE, true);
            intent.putExtra(BKEY_NO_FACE_DETECTION, true);
            // True to return a Bitmap, false to directly save the cropped iamge
            intent.putExtra(BKEY_RETURN_DATA, false);
            // Save output image in uri
            File cropped = this.getCroppedImageFileName();
            if (cropped.exists())
                //noinspection ResultOfMethodCallIgnored
                cropped.delete();
            intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(cropped.getAbsolutePath())));

            List<ResolveInfo> list = getActivity().getPackageManager().queryIntentActivities(intent, 0);
            int size = list.size();
            if (size == 0) {
                Toast.makeText(getActivity(), "Can not find image crop app", Toast.LENGTH_SHORT).show();
            } else {
                startActivityForResult(intent, CODE_CROP_RESULT_EXTERNAL);
            }
        } finally {
            Tracker.handleEvent(this, "cropCoverImageExternal", Tracker.States.Exit);
        }
    }

    /**
     * Delete the provided thumbnail from the sdcard
     */
    private void deleteThumbnail() {
        try {
            File thumbFile = getCoverFile(mEditManager.getBookData().getRowId());
            if (thumbFile != null && thumbFile.exists())
                //noinspection ResultOfMethodCallIgnored
                thumbFile.delete();
        } catch (Exception e) {
            Logger.logError(e);
        }
        // Make sure the cached thumbnails (if present) are deleted
        invalidateCachedThumbnail();
    }

    /**
     * Get the File object for the cover of the book we are editing. If the book
     * is new, return the standard temp file.
     */
    private File getCoverFile(Long rowId) {
        if (rowId == null || rowId == 0)
            return StorageUtils.getTempThumbnail();
        else
            return StorageUtils.getThumbnailByUuid(mDb.getBookUuid(rowId));
    }

    /**
     * Get a temp file for camera images
     */
    private File getCameraImageFile() {
        return StorageUtils.getTempImageFile("camera" + mTempImageCounter + ".jpg");
    }

    /**
     * Get a temp file for cropping output
     */
    private File getCroppedImageFileName() {
        return StorageUtils.getTempImageFile("cropped" + mTempImageCounter + ".jpg");
    }

    /**
     * Populate Author field
     * If there is no data shows "Set author" text defined in resources.
     */
    protected void populateAuthorListField() {
        String newText = mEditManager.getBookData().getAuthorTextShort();
        if (newText == null || newText.isEmpty()) {
            newText = getResources().getString(R.string.set_authors);
        }
        mFields.getField(R.id.author).setValue(newText);
    }

    /**
     * Populate Series field by data
     * If there is no data shows "Set series..."
     */
    protected void populateSeriesListField() {
        String newText;
        int size;
        ArrayList<Series> list = mEditManager.getBookData().getSeries();
        try {
            size = list.size();
        } catch (NullPointerException e) {
            size = 0;
        }
        if (size == 0) {
            newText = getResources().getString(R.string.set_series);
        } else {
            boolean trimmed = Utils.pruneSeriesList(list);
            trimmed |= Utils.pruneList(mDb, list);
            if (trimmed) {
                mEditManager.getBookData().setSeriesList(list);
            }
            newText = list.get(0).getDisplayName();
            if (list.size() > 1)
                newText += " " + getResources().getString(R.string.and_others);
        }
        mFields.getField(R.id.series).setValue(newText);
    }

    /**
     * Rotate the thumbnail
     *
     * @param angle by a specified amount
     */
    private void rotateThumbnail(long angle) {
        boolean retry = true;
        while (retry) {
            try {
                File thumbFile = getCoverFile(mEditManager.getBookData().getRowId());

                Bitmap origBm = ImageUtils.fetchFileIntoImageView(null, thumbFile, mThumbZoomSize * 2, mThumbZoomSize * 2, true);
                if (origBm == null)
                    return;

                Matrix m = new Matrix();
                m.postRotate(angle);
                Bitmap rotBm = Bitmap.createBitmap(origBm, 0, 0, origBm.getWidth(), origBm.getHeight(), m, true);
                if (rotBm != origBm) {
                    origBm.recycle();
                }

                /* Create a file to copy the thumbnail into */
                FileOutputStream f;
                try {
                    f = new FileOutputStream(thumbFile.getAbsoluteFile());
                } catch (FileNotFoundException e) {
                    Logger.logError(e);
                    return;
                }
                rotBm.compress(Bitmap.CompressFormat.PNG, 100, f);
                rotBm.recycle();
            } catch (java.lang.OutOfMemoryError e) {
                if (retry) {
                    System.gc();
                } else {
                    throw new RuntimeException(e);
                }
            }
            retry = false;
        }
    }

    /**
     * Ensure that the cached thumbnails for this book are deleted (if present)
     */
    private void invalidateCachedThumbnail() {
        final Long rowId = mEditManager.getBookData().getRowId();
        if (rowId != 0) {
            try (CoversDbHelper coversDbHelper = CoversDbHelper.getInstance(this.getContext())) {
                coversDbHelper.deleteBookCover(mDb.getBookUuid(rowId));
            } catch (Exception e) {
                Logger.logError(e, "Error cleaning up cached cover images");
            }
        }
    }

    /**
     * Add all book fields with corresponding validators.
     */
    private void initFields() {
        final View root = getView();

        /* Title has some post-processing on the text, to move leading 'A', 'The' etc to the end.
         * While we could do it in a formatter, it it not really a display-oriented function and
         * is handled in preprocessing in the database layer since it also needs to be applied
         * to imported record etc. */
        mFields.add(R.id.title, KEY_TITLE, null);

        /* Anthology needs special handling, and we use a formatter to do this. If the original
         * value was 0 or 1, then setting/clearing it here should just set the new value to 0 or 1.
         * However...if if the original value was 2, then we want setting/clearing to alternate
         * between 2 and 0, not 1 and 0.
         * So, despite if being a checkbox, we use an integerValidator and use a special formatter.
         * We also store it in the tag field so that it is automatically serialized with the
         * activity. */
        mFields.add(R.id.anthology, BookData.KEY_ANTHOLOGY, null);

        mFields.add(R.id.author, "", KEY_AUTHOR_FORMATTED, null);
        mFields.add(R.id.isbn, KEY_ISBN, null);

        if (root.findViewById(R.id.publisher) != null)
            mFields.add(R.id.publisher, KEY_PUBLISHER, null);

        if (root.findViewById(R.id.date_published) != null)
            mFields.add(R.id.date_published, KEY_DATE_PUBLISHED, KEY_DATE_PUBLISHED,
                    null, new Fields.DateFieldFormatter());

        mFields.add(R.id.series, KEY_SERIES_NAME, KEY_SERIES_NAME, null);
        mFields.add(R.id.list_price, KEY_LIST_PRICE, null);
        mFields.add(R.id.pages, KEY_PAGES, null);
        mFields.add(R.id.format, KEY_FORMAT, null);
        //mFields.add(R.id.bookshelf, KEY_BOOKSHELF, null);
        mFields.add(R.id.description, KEY_DESCRIPTION, null)
                .setShowHtml(true);
        mFields.add(R.id.genre, KEY_GENRE, null);
        mFields.add(R.id.language, KEY_LANGUAGE, null);

        mFields.add(R.id.row_img, "", THUMBNAIL, null);
        mFields.getField(R.id.row_img).getView().setOnCreateContextMenuListener(mCreateBookThumbContextMenuListener);

        mFields.add(R.id.format_button, "", KEY_FORMAT, null);
        mFields.add(R.id.bookshelf, BOOKSHELF_TEXT, null).doNoFetch = true; // Output-only field
        mFields.add(R.id.signed, KEY_SIGNED, null);
    }

    /**
     * Populate all fields (See {@link #mFields} ) except of authors and series fields with
     * data from database. To set authors and series fields use {@link #populateAuthorListField()}
     * and {@link #populateSeriesListField()} methods.<br>
     * Data defined by its _id in db.
     */
    protected void populateFieldsFromBook(BookData book) {
        // From the database (edit)
        try {

            populateBookDetailsFields(book);
            setBookThumbnail(book.getRowId(), mThumbEditSize, mThumbEditSize);

        } catch (Exception e) {
            Logger.logError(e);
        }

        populateBookshelvesField(mFields, book);

    }

    /**
     * Inflates all fields with data from cursor and populates UI fields with it.
     * Also set thumbnail of the book.
     */
    protected void populateBookDetailsFields(BookData book) {
        //Set anthology field
        Integer anthNo = book.getInt(BookData.KEY_ANTHOLOGY);
        mFields.getField(R.id.anthology).setValue(anthNo.toString()); // Set checked if anthNo != 0
    }

    /**
     * Sets book thumbnail
     */
    protected void setBookThumbnail(Long rowId, int maxWidth, int maxHeight) {
        // Sets book thumbnail
        ImageView iv = getView().findViewById(R.id.row_img);
        ImageUtils.fetchFileIntoImageView(iv, getCoverFile(rowId), maxWidth, maxHeight, true);
    }

    // unused in Context itself. But IS used in android\support\v7\view\ContextThemeWrapper.java
//    private int getThemeResId() {
//        try {
//            Class<?> wrapper = Context.class;
//            Method method = wrapper.getMethod("getThemeResId");
//            method.setAccessible(true);
//            return (Integer) method.invoke(this);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return 0;
//    }

    /**
     * Gets all bookshelves for the book from database and populate corresponding filed with them.
     *
     * @param fields Fields containing book information
     * @param book   the book
     *
     * @return true if populated, false otherwise
     */
    protected boolean populateBookshelvesField(Fields fields, BookData book) {
        boolean result = false;
        try {
            // Display the selected bookshelves
            Field bookshelfTextFe = fields.getField(R.id.bookshelf);
            String text = book.getBookshelfText();
            bookshelfTextFe.setValue(text);
            if (!text.isEmpty()) {
                result = true; // One or more bookshelves have been set
            }
        } catch (Exception e) {
            Logger.logError(e);
        }
        return result;
    }

    protected void setCoverImage() {
        ImageView iv = getView().findViewById(R.id.row_img);
        ImageUtils.fetchFileIntoImageView(iv, getCoverFile(mEditManager.getBookData().getRowId()), mThumbEditSize, mThumbEditSize, true);
        // Make sure the cached thumbnails (if present) are deleted
        invalidateCachedThumbnail();
    }
}
