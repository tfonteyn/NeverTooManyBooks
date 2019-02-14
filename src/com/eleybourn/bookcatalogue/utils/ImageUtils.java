package com.eleybourn.bookcatalogue.utils;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialog;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.CoversDBA;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.tasks.GetImageTask;
import com.eleybourn.bookcatalogue.tasks.ImageCacheWriterTask;
import com.eleybourn.bookcatalogue.tasks.simpletasks.Terminator;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public final class ImageUtils {

    private static final int BUFFER_SIZE = 65536;

    private ImageUtils() {
    }

    /**
     * Shrinks the image in the passed file to the specified dimensions.
     * <p>
     * If the view is non-null, the image is placed in the view.
     *
     * @param destView  The ImageView to load with the bitmap or an appropriate icon
     * @param file      The file of the image
     * @param maxWidth  Maximum desired width of the image
     * @param maxHeight Maximum desired height of the image
     * @param exact     if true, the image will be proportionally scaled to fit box.
     *
     * @return The bitmap, or null
     */
    @Nullable
    public static Bitmap getImageAndPutIntoView(@Nullable final ImageView destView,
                                                @NonNull final File file,
                                                final int maxWidth,
                                                final int maxHeight,
                                                final boolean exact) {
        // Get the file, if it exists. Otherwise set 'broken image' icon and exit.
        if (!file.exists()) {
            if (destView != null) {
                destView.setImageResource(R.drawable.ic_broken_image);
            }
            return null;
        }

        return getImageAndPutIntoView(destView, file.getPath(), maxWidth, maxHeight, exact);
    }

    /**
     * Called in the UI thread, will either use a cached cover OR start a background task
     * to create and load it.
     * <p>
     * If a cached image is used a background task is still started to check the file date vs
     * the cache date. If the cached image date is < the file, it is rebuilt.
     *
     * @param destView        View to populate
     * @param uuid            ID of book to retrieve.
     * @param maxWidth        Max width of resulting image
     * @param maxHeight       Max height of resulting image
     * @param exact           Whether to fit dimensions exactly
     * @param checkCache      Indicates if cache should be checked for this cover
     * @param allowBackground Indicates if request can be put in background task.
     *
     * @return Bitmap (if cached) or null (if done in background)
     */
    @Nullable
    public static Bitmap getImageAndPutIntoView(@Nullable final ImageView destView,
                                                @NonNull final String uuid,
                                                final int maxWidth,
                                                final int maxHeight,
                                                final boolean exact,
                                                final boolean checkCache,
                                                final boolean allowBackground) {

        //* Get the original file so we can use the modification date, path etc */
        final File coverFile = StorageUtils.getCoverFile(uuid);

        boolean cacheWasChecked = false;

        // If we want to check the cache, AND we don't have cache building happening,
        // then check it.
        if (checkCache && destView != null
                && !GetImageTask.hasActiveTasks()
                && !ImageCacheWriterTask.hasActiveTasks()) {
            try (CoversDBA coversDBAdapter = CoversDBA.getInstance()) {
                final Bitmap bm = coversDBAdapter.getImageAndPutIntoView(coverFile, uuid,
                                                                         maxWidth, maxHeight,
                                                                         destView);
                if (bm != null) {
                    return bm;
                }
            }
            cacheWasChecked = true;
        }

        // If we get here, the image is not in the cache but the original exists.
        // See if we can queue it.
        if (allowBackground && destView != null) {
            destView.setImageBitmap(null);
            GetImageTask.getImage(uuid, destView, maxWidth, maxHeight, cacheWasChecked);
            return null;
        }

        // File is not in cache, original exists, we are in the background task
        // (or not allowed to queue request)
        return getImageAndPutIntoView(destView, coverFile.getPath(), maxWidth, maxHeight, exact);
    }

    /**
     * Shrinks the passed image file spec into the specified dimensions.
     * <p>
     * If the view is non-null, the image is placed in the view.
     *
     * @return The bitmap, or null
     */
    @Nullable
    private static Bitmap getImageAndPutIntoView(@Nullable final ImageView destView,
                                                 @NonNull final String fileSpec,
                                                 final int maxWidth,
                                                 final int maxHeight,
                                                 final boolean exact) {

        // Read the file to get file size
        final BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        if (new File(fileSpec).exists()) {
            BitmapFactory.decodeFile(fileSpec, opt);
        }

        // If no size info, or a single pixel, assume file bad and set 'broken image' icon
        if (opt.outHeight <= 0 || opt.outWidth <= 0 || (opt.outHeight == 1 && opt.outWidth == 1)) {
            if (destView != null) {
                destView.setImageResource(R.drawable.ic_broken_image);
            }
            return null;
        }

        // Next time we don't just want the bounds, we want the file itself
        opt.inJustDecodeBounds = false;

        // Work out how to SCALE the file to fit in required box
        final float widthRatio = (float) maxWidth / opt.outWidth;
        final float heightRatio = (float) maxHeight / opt.outHeight;

        // Work out SCALE so that it fits exactly
        float ratio = (widthRatio < heightRatio) ? widthRatio : heightRatio;

        // Note that inSampleSize seems to ALWAYS be forced to a power of 2, no matter what we
        // specify, so we just work with powers of 2.
        final int idealSampleSize = (int) Math.ceil(1 / ratio);
        // Get the nearest *bigger* power of 2.
        final int samplePow2 =
                (int) Math.pow(2, Math.ceil(Math.log(idealSampleSize) / Math.log(2)));

        if (DEBUG_SWITCHES.IMAGE_UTILS && BuildConfig.DEBUG) {
            Logger.info(ImageUtils.class, "getImageAndPutIntoView:\n"
                    + " filename = " + fileSpec + '\n'
                    + "  exact       = " + exact + '\n'
                    + "  maxWidth    = " + maxWidth + ", opt.outWidth = " + opt.outWidth
                    + ", widthRatio   = " + widthRatio + '\n'
                    + "  maxHeight   = " + maxHeight + ", opt.outHeight= " + opt.outHeight
                    + ",  heightRatio = " + heightRatio + '\n'
                    + "  ratio            = " + ratio + '\n'
                    + "  idealSampleSize  = " + idealSampleSize + '\n'
                    + "  samplePow2       = " + samplePow2);
        }

        final Bitmap bm;
        try {
            if (exact) {
                // Create one bigger than needed and SCALE it;
                // this is an attempt to improve quality.
                opt.inSampleSize = samplePow2 / 2;
                if (opt.inSampleSize < 1) {
                    opt.inSampleSize = 1;
                }

                final Bitmap tmpBm = BitmapFactory.decodeFile(fileSpec, opt);
                if (tmpBm == null) {
                    // We ran out of memory, most likely
                    // TODO: Need a way to try loading images after GC().
                    // Otherwise, covers in cover browser will stay blank.
                    Logger.error("Unexpectedly failed to decode bitmap; memory exhausted?");
                    return null;
                }

                final android.graphics.Matrix matrix = new android.graphics.Matrix();
                // Fixup ratio based on new sample size and SCALE it.
                ratio = ratio / (1.0f / opt.inSampleSize);
                matrix.postScale(ratio, ratio);
                bm = Bitmap.createBitmap(tmpBm, 0, 0, opt.outWidth, opt.outHeight, matrix, true);
                // Recycle if original was not returned
                if (bm != tmpBm) {
                    tmpBm.recycle();
                }
            } else {
                // Use a SCALE that will make image *no larger than* the desired size
                if (ratio < 1.0f) {
                    opt.inSampleSize = samplePow2;
                }
                bm = BitmapFactory.decodeFile(fileSpec, opt);
            }
        } catch (OutOfMemoryError e) {
            Logger.error(e);
            return null;
        }

        if (DEBUG_SWITCHES.IMAGE_UTILS && BuildConfig.DEBUG) {
            Logger.info(ImageUtils.class,
                        "bm.width = " + bm.getWidth() + ", bm.height = " + bm.getHeight());
        }

        // Set ImageView and return bitmap
        if (destView != null) {
            destView.setImageBitmap(bm);
        }

        return bm;
    }

    /**
     * Given a URL, get an image and save to a file.
     *
     * @param url  Image file URL
     * @param name for the file.
     *
     * @return Downloaded fileSpec, or null on failure
     */
    @Nullable
    public static String saveImage(@NonNull final String url,
                                   @NonNull final String name) {
        boolean success = false;
        final File file = StorageUtils.getTempCoverFile(name);
        try (Terminator.WrappedConnection con = Terminator.getConnection(url)) {
            success = StorageUtils.saveInputStreamToFile(con.inputStream, file);
        } catch (@NonNull final IOException e) {
            Logger.error(e);
        }

        return success ? file.getAbsolutePath() : null;
    }

    /**
     * Given a URL, get an image and return as a byte array.
     *
     * @param url Image file URL
     *
     * @return Downloaded byte[] or null upon failure
     */
    @Nullable
    public static byte[] getBytes(@NonNull final String url) {
        try (Terminator.WrappedConnection con = Terminator.getConnection(url)) {
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                // Save the output to a byte output stream
                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                while ((len = con.inputStream.read(buffer)) >= 0) {
                    out.write(buffer, 0, len);
                }
                return out.toByteArray();
            }
        } catch (@NonNull final IOException e) {
            Logger.error(e);
        }
        return null;
    }

    /**
     * Given byte array that represents an image (jpg, png etc), return as a bitmap.
     *
     * @param bytes Raw byte data
     *
     * @return bitmap
     */
    @Nullable
    public static Bitmap getBitmap(@NonNull final byte[] bytes) {
        if (bytes.length == 0) {
            return null;
        }

        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length,
                                                      new BitmapFactory.Options());

        if (DEBUG_SWITCHES.IMAGE_UTILS && BuildConfig.DEBUG) {
            Logger.info(ImageUtils.class, "Array " + bytes.length + " bytes, bitmap "
                    + bitmap.getHeight() + 'x' + bitmap.getWidth());
        }
        return bitmap;
    }

    /**
     * Read {@link UniqueId#BKEY_FILE_SPEC_ARRAY}.
     * If there are images, pick the largest one, rename it, and delete the others.
     * Finally, remove the key and set {@link UniqueId#BKEY_THUMBNAIL} to true
     */
    public static void cleanupImages(@Nullable final Bundle /* in/out */ bookData) {
        if (bookData == null) {
            return;
        }

        ArrayList<String> imageList =
                bookData.getStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY);
        if (imageList == null || imageList.isEmpty()) {
            return;
        }

        cleanupImages(imageList);

        // Finally, cleanup the data
        bookData.remove(UniqueId.BKEY_FILE_SPEC_ARRAY);
        // and indicate we got a file with the default name
        bookData.putBoolean(UniqueId.BKEY_THUMBNAIL, true);
    }

    /**
     * If there are images, pick the largest one, rename it, and delete the others.
     */
    private static void cleanupImages(@NonNull final ArrayList<String> imageList) {

        long bestFileSize = -1;
        int bestFileIndex = -1;

        // Just read the image files to get file size
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;

        // Loop, finding biggest image
        for (int i = 0; i < imageList.size(); i++) {
            String fileSpec = imageList.get(i);
            if (new File(fileSpec).exists()) {
                BitmapFactory.decodeFile(fileSpec, opt);
                // If no size info, assume file bad and skip
                if (opt.outHeight > 0 && opt.outWidth > 0) {
                    long size = opt.outHeight * opt.outWidth;
                    if (size > bestFileSize) {
                        bestFileSize = size;
                        bestFileIndex = i;
                    }
                }
            }
        }

        // Delete all but the best one. Note there *may* be no best one,
        // so all would be deleted. We do this first in case the list
        // contains a file with the same name as the target of our rename.
        for (int i = 0; i < imageList.size(); i++) {
            if (i != bestFileIndex) {
                StorageUtils.deleteFile(new File(imageList.get(i)));
            }
        }
        // Get the best file (if present) and rename it.
        if (bestFileIndex >= 0) {
            File source = new File(imageList.get(bestFileIndex));
            File destination = StorageUtils.getTempCoverFile();
            StorageUtils.renameFile(source, destination);
        }
    }

    /**
     * Show large image in dialog. Closed by click on image area.
     */
    public static void showZoomedImage(@NonNull final Activity activity,
                                       @Nullable final File image) {

        final ImageSize imageSizes = getImageSizes(activity);

        // Check if we have a file and/or it is valid
        if (image == null || !image.exists()) {
            StandardDialogs.showUserMessage(activity, R.string.warning_cover_field_not_set);
        } else {
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(image.getAbsolutePath(), opt);

            // If no size info, assume file bad and return appropriate icon
            if (opt.outHeight <= 0 || opt.outWidth <= 0) {
                StandardDialogs.showUserMessage(activity, R.string.warning_cover_corrupt);
            } else {
//                final Dialog dialog = new AlertDialog.Builder(activity, R.style.zoomedCoverImage)
//                        .create();
                final Dialog dialog = new AppCompatDialog(activity, R.style.zoomedCoverImage);

                final ImageView cover = new ImageView(activity);
                getImageAndPutIntoView(cover, image, imageSizes.large, imageSizes.large, true);
                cover.setAdjustViewBounds(true);
                cover.setBackgroundResource(R.drawable.border);
                cover.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(@NonNull final View v) {
                        dialog.dismiss();
                    }
                });

                final ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                dialog.setContentView(cover, lp);
                dialog.show();
            }
        }
    }

    @NonNull
    public static ImageSize getImageSizes(@NonNull final Activity activity) {
        return new ImageSize(activity);
    }

    @NonNull
    public static DisplayMetrics getDisplayMetrics(@NonNull final Activity activity) {
        final DisplayMetrics metrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        return metrics;
    }

    /**
     * NEWKIND: if we need more sizes, add a field here and set it in {@link #getImageSizes}.
     * <p>
     * small:  Minimum of MAX_SIZE_SMALL and 1/3rd of largest screen dimension
     * standard: Minimum of MAX_SIZE_STANDARD and 2/3rd of largest screen dimension
     * large:  Minimum of MAX_SIZE_LARGE and largest screen dimension.
     */
    public static class ImageSize {

        /** Target size of an image - on the Edit Screens. */
        private static final int MAX_SIZE_SMALL = 256;
        /** on the View Screens. */
        private static final int MAX_SIZE_STANDARD = 512;
        /** in zoomed mode. */
        private static final int MAX_SIZE_LARGE = 1024;

        public final int small;
        public final int standard;
        public final int large;

        ImageSize(@NonNull final Activity activity) {
            DisplayMetrics metrics = getDisplayMetrics(activity);
            int maxMetric = Math.max(metrics.widthPixels, metrics.heightPixels);
            small = Math.min(MAX_SIZE_SMALL, maxMetric / 3);
            standard = Math.min(MAX_SIZE_STANDARD, maxMetric * 2 / 3);
            large = Math.min(MAX_SIZE_LARGE, maxMetric);
        }
    }
}
