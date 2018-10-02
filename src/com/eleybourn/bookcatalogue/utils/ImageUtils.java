package com.eleybourn.bookcatalogue.utils;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.GetThumbnailTask;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.ThumbnailCacheWriterTask;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.CoversDbHelper;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;

public class ImageUtils {
    // Target size of a thumbnail in edit dialog and zoom dialog
    private static final int MAX_EDIT_THUMBNAIL_SIZE = 256;
    private static final int MAX_ZOOM_THUMBNAIL_SIZE = 1024;

    private ImageUtils() {
    }

    /**
     * Shrinks the image in the passed file to the specified dimensions, and places the image
     * in the passed view.
     *
     * @return The bitmap, or null
     */
    @Nullable
    public static Bitmap fetchFileIntoImageView(@Nullable final ImageView destView, @NonNull final File file,
                                                final int maxWidth, final int maxHeight, final boolean exact) {
        // Get the file, if it exists. Otherwise set 'help' icon and exit.
        if (!file.exists()) {
            if (destView != null)
                destView.setImageResource(R.drawable.ic_help_outline);
            return null;
        }

        return fetchFileIntoImageView(destView, file.getPath(), maxWidth, maxHeight, exact);
    }
    /**
     * Shrinks the passed image file spec into the specified dimensions, and returns the bitmap. If the view
     * is non-null, the image is also placed in the view.
     */
    @SuppressWarnings("WeakerAccess")
    @Nullable
    public static Bitmap fetchFileIntoImageView(@Nullable final ImageView destView, @NonNull final String fileSpec,
                                                final int maxWidth, final int maxHeight, final boolean exact) {

        // Read the file to get file size
        final BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        if (new File(fileSpec).exists()) {
            BitmapFactory.decodeFile(fileSpec, opt);
        }

        // If no size info, or a single pixel, assume file bad and set the 'alert' icon
        if (opt.outHeight <= 0 || opt.outWidth <= 0 || (opt.outHeight == 1 && opt.outWidth == 1)) {
            if (destView != null) {
                destView.setImageResource(R.drawable.ic_warning);
            }
            return null;
        }

        // Next time we don't just want the bounds, we want the file itself
        opt.inJustDecodeBounds = false;

        // Work out how to scale the file to fit in required box
        final float widthRatio = (float) maxWidth / opt.outWidth;
        final float heightRatio = (float) maxHeight / opt.outHeight;

        // Work out scale so that it fit exactly
        float ratio = widthRatio < heightRatio ? widthRatio : heightRatio;

        // Note that inSampleSize seems to ALWAYS be forced to a power of 2, no matter what we
        // specify, so we just work with powers of 2.
        final int idealSampleSize = (int) Math.ceil(1 / ratio); // This is the sample size we want to use
        // Get the nearest *bigger* power of 2.
        final int samplePow2 = (int) Math.pow(2, Math.ceil(Math.log(idealSampleSize) / Math.log(2)));

        if (DEBUG_SWITCHES.IMAGEUTILS && BuildConfig.DEBUG) {
            System.out.println("fetchFileIntoImageView:\n" +
                    " filename = " + fileSpec + "\n" +
                    "  exact       = " + exact + "\n" +
                    "  maxWidth    = " + maxWidth + ", opt.outWidth = " + opt.outWidth + ", widthRatio   = " + widthRatio + "\n" +
                    "  maxHeight   = " + maxHeight + ", opt.outHeight= " + opt.outHeight + ",  heightRatio = " + heightRatio + "\n" +
                    "  ratio            = " + ratio + "\n" +
                    "  idealSampleSize  = " + idealSampleSize + "\n" +
                    "  samplePow2       = " + samplePow2);
        }

        final Bitmap bm;
        try {
            if (exact) {
                // Create one bigger than needed and scale it; this is an attempt to improve quality.
                opt.inSampleSize = samplePow2 / 2;
                if (opt.inSampleSize < 1) {
                    opt.inSampleSize = 1;
                }

                final Bitmap tmpBm = BitmapFactory.decodeFile(fileSpec, opt);
                if (tmpBm == null) {
                    // We ran out of memory, most likely
                    // TODO: Need a way to try loading images after GC(). Otherwise, covers in cover browser wil stay blank.
                    Logger.logError("Unexpectedly failed to decode bitmap; memory exhausted?");
                    return null;
                }

                final android.graphics.Matrix matrix = new android.graphics.Matrix();
                // Fixup ratio based on new sample size and scale it.
                ratio = ratio / (1.0f / opt.inSampleSize);
                matrix.postScale(ratio, ratio);
                bm = Bitmap.createBitmap(tmpBm, 0, 0, opt.outWidth, opt.outHeight, matrix, true);
                // Recycle if original was not returned
                if (bm != tmpBm) {
                    tmpBm.recycle();
                }
            } else {
                // Use a scale that will make image *no larger than* the desired size
                if (ratio < 1.0f) {
                    opt.inSampleSize = samplePow2;
                }
                bm = BitmapFactory.decodeFile(fileSpec, opt);
            }
        } catch (OutOfMemoryError e) {
            Logger.logError(e);
            return null;
        }

        if (DEBUG_SWITCHES.IMAGEUTILS && BuildConfig.DEBUG) {
            System.out.println("\n" +
                    "bm.width = " + bm.getWidth() + "\n" +
                    "bm.height = " + bm.getHeight() + "\n"
            );
        }

        // Set ImageView and return bitmap
        if (destView != null) {
            destView.setImageBitmap(bm);
        }

        return bm;
    }

    /**
     * Called in the UI thread, will either use a cached cover OR start a background task to create and load it.
     * <p>
     * If a cached image is used a background task is still started to check the file date vs the cache date. If the
     * cached image date is < the file, it is rebuilt.
     *
     * @param destView        View to populate
     * @param coverUUID       ID of book to retrieve.
     * @param maxWidth        Max width of resulting image
     * @param maxHeight       Max height of resulting image
     * @param exact           Whether to fit dimensions exactly
     * @param checkCache      Indicates if cache should be checked for this cover
     * @param allowBackground Indicates if request can be put in background task.
     *
     * @return Bitmap (if cached) or null (if done in background)
     */
    @Nullable
    public static Bitmap fetchBookCoverIntoImageView(@Nullable final ImageView destView, @NonNull final String coverUUID,
                                                     final int maxWidth, final int maxHeight, final boolean exact,
                                                     final boolean checkCache, final boolean allowBackground) {

        //* Get the original file so we can use the modification date, path etc */
        final File coverFile = StorageUtils.getCoverFile(coverUUID);

        boolean cacheWasChecked = false;

        /* If we want to check the cache, AND we don't have cache building happening, then check it. */
        if (checkCache && destView != null
                && !GetThumbnailTask.hasActiveTasks() && !ThumbnailCacheWriterTask.hasActiveTasks()) {
            try (CoversDbHelper coversDbHelper = CoversDbHelper.getInstance(destView.getContext())) {
                final Bitmap bm = coversDbHelper.fetchCachedImageIntoImageView(coverFile, destView, coverUUID, maxWidth, maxHeight);
                if (bm != null) {
                    return bm;
                }
            }
            cacheWasChecked = true;
        }

        // If we get here, the image is not in the cache but the original exists. See if we can queue it.
        if (allowBackground && destView != null) {
            destView.setImageBitmap(null);
            GetThumbnailTask.getThumbnail(destView.getContext(), coverUUID, destView, maxWidth, maxHeight, cacheWasChecked);
            return null;
        }

        // File is not in cache, original exists, we are in the background task (or not allowed to queue request)
        return fetchFileIntoImageView(destView, coverFile.getPath(), maxWidth, maxHeight, exact);
    }

    /**
     * Given a URL, get an image and save to a file, optionally appending a suffix to the file.
     *
     * @param urlText        Image file URL
     * @param filenameSuffix Suffix to add
     *
     * @return Downloaded fileSpec
     */
    @NonNull
    public static String saveThumbnailFromUrl(@NonNull final String urlText, @NonNull final String filenameSuffix) {

        InputStream in;
        try {
            final URL u = new URL(urlText);

            final HttpGet httpRequest = new HttpGet(u.toURI());
            final HttpClient httpclient = new DefaultHttpClient();
            final HttpResponse response = httpclient.execute(httpRequest);
            final HttpEntity entity = response.getEntity();
            final BufferedHttpEntity bufHttpEntity = new BufferedHttpEntity(entity);
            in = bufHttpEntity.getContent();

            // The default URL fetcher does not cope well with pages that have not content
            // header (including goodreads images!). So use the more advanced one.
            //c = (HttpURLConnection) u.openConnection();
            //c.setConnectTimeout(30000);
            //c.setRequestMethod("GET");
            //c.setDoOutput(true);
            //c.connect();
            //in = c.getInputStream();
        } catch (IOException | URISyntaxException e) {
            Logger.logError(e);
            return "";
        }

        // Get the output file
        final File file = StorageUtils.getTempCoverFile(filenameSuffix);
        // Save to file
        StorageUtils.saveInputStreamToFile(in, file);
        // Return new file path
        return file.getAbsolutePath();
    }

    /**
     * Given byte array that represents an image (jpg, png etc), return as a bitmap.
     *
     * @param bytes Raw byte data
     *
     * @return bitmap
     */
    @Nullable
    public static Bitmap getBitmapFromBytes(@Nullable final byte[] bytes) {
        if (bytes == null || bytes.length == 0)
            return null;

        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, new BitmapFactory.Options());

        if (BuildConfig.DEBUG) {
            System.out.println("Array " + bytes.length + " bytes, bitmap " + bitmap.getHeight() + "x" + bitmap.getWidth());
        }
        return bitmap;
    }

    /**
     * If there is a {@link UniqueId#BKEY_THUMBNAIL_USCORE} key, pick the largest image, rename it
     * and delete the others. Finally, remove the key.
     */
    public static void cleanupThumbnails(@Nullable final Bundle result) {
        if (result == null || !result.containsKey(UniqueId.BKEY_THUMBNAIL_USCORE)) {
            return;
        }

        String s = result.getString(UniqueId.BKEY_THUMBNAIL_USCORE);
        if (s == null) {
            return;
        }

        // Parse the list
        ArrayList<String> files = ArrayUtils.decodeList('|', s);

        long best = -1;
        int bestFile = -1;

        // Just read the image files to get file size
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;

        // Scan, finding biggest image
        for (int i = 0; i < files.size(); i++) {
            String fileSpec = files.get(i);
            if (new File(fileSpec).exists()) {
                BitmapFactory.decodeFile(fileSpec, opt);
                // If no size info, assume file bad and skip
                if (opt.outHeight > 0 && opt.outWidth > 0) {
                    long size = opt.outHeight * opt.outWidth;
                    if (size > best) {
                        best = size;
                        bestFile = i;
                    }
                }
            }
        }

        // Delete all but the best one. Note there *may* be no best one,
        // so all would be deleted. We do this first in case the list
        // contains a file with the same name as the target of our rename.
        for (int i = 0; i < files.size(); i++) {
            if (i != bestFile) {
                StorageUtils.deleteFile(new File(files.get(i)));
            }
        }
        // Get the best file (if present) and rename it.
        if (bestFile >= 0) {
            StorageUtils.renameFile(new File(files.get(bestFile)), StorageUtils.getTempCoverFile());
        }
        // Finally, cleanup the data
        result.remove(UniqueId.BKEY_THUMBNAIL_USCORE);
        result.putBoolean(UniqueId.BKEY_THUMBNAIL, true);
    }

    /**
     * Given a URL, get an image and return as a byte array.
     *
     * @param urlText Image file URL
     *
     * @return Downloaded byte[]
     */
    @Nullable
    public static byte[] getBytesFromUrl(@Nullable final String urlText) {
        if (urlText == null) {
            return null;
        }

        // Request it from the network
        InputStream in;
        try {
            final URL u = new URL(urlText);
            final HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(30000);
            c.setReadTimeout(30000);
            c.setRequestMethod("GET");
            c.setDoInput(true);
            c.setUseCaches(false);
            c.connect();
            in = c.getInputStream();
            if (c.getResponseCode() >= 300) {
                Logger.logError("URL lookup failed: " + c.getResponseCode() +
                        " " + c.getResponseMessage() + ", URL: " + u);
                return null;
            }

            // Save the output to a byte output stream
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[65536];
            int len;
            while ((len = in.read(buffer)) >= 0) {
                out.write(buffer, 0, len);
            }
            out.close();
            return out.toByteArray();

        } catch (IOException e) {
            Logger.logError(e);
            return null;
        }
    }

    /**
     * Show zoomed thumbnail in dialog. Closed by click on image area.
     */
    public static void showZoomedThumb(@NonNull final Activity activity, @Nullable final File thumbFile) {

        final ThumbSize thumper = getThumbSizes(activity);

        // Check if we have a file and/or it is valid
        if (thumbFile == null || !thumbFile.exists()) {
            Toast.makeText(activity, R.string.cover_not_set, Toast.LENGTH_SHORT).show();
        } else {
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(thumbFile.getAbsolutePath(), opt);

            // If no size info, assume file bad and return appropriate icon
            if (opt.outHeight <= 0 || opt.outWidth <= 0) {
                Toast.makeText(activity, R.string.cover_corrupt, Toast.LENGTH_LONG).show();
            } else {
                final Dialog dialog = new StandardDialogs.BasicDialog(activity, false);
                dialog.setContentView(R.layout.dialog_zoom_thumb);

                final ImageView cover = new ImageView(activity);
                fetchFileIntoImageView(cover, thumbFile, thumper.zoomed, thumper.zoomed, true);
                cover.setAdjustViewBounds(true);
                cover.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                    }
                });

                final ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                dialog.addContentView(cover, lp);
                dialog.show();
            }
        }
    }
    /**
     * NEWKIND: if we ever need more sizes, add a field here and set it in {@link #getThumbSizes}
     */
    public static class ThumbSize {
        public int normal;
        public int zoomed;
    }

    /**
     * normal:  Minimum of MAX_EDIT_THUMBNAIL_SIZE and 1/3rd of largest screen dimension
     * zoomed:  Minimum of MAX_ZOOM_THUMBNAIL_SIZE and largest screen dimension.
     */
    @NonNull
    public static ThumbSize getThumbSizes(@NonNull final Activity activity) {
        final DisplayMetrics metrics = getDisplayMetrics(activity);

        ThumbSize tump = new ThumbSize();
        tump.normal = Math.min(MAX_EDIT_THUMBNAIL_SIZE, Math.max(metrics.widthPixels, metrics.heightPixels) / 3);
        tump.zoomed = Math.min(MAX_ZOOM_THUMBNAIL_SIZE, Math.max(metrics.widthPixels, metrics.heightPixels));
        return tump;
    }

    @NonNull
    public static DisplayMetrics getDisplayMetrics(@NonNull final Activity activity) {
        final DisplayMetrics metrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        return metrics;
    }


//    /**
//     * This function will load the thumbnail bitmap with a guaranteed maximum size; it
//     * prevents OutOfMemory exceptions on large files and reduces memory usage in lists.
//     * It can also scale images to the exact requested size.
//     *
//     * @param destView  The ImageView to load with the bitmap or an appropriate icon
//     * @param uuid      The id of the book
//     * @param maxWidth  Maximum desired width of the image
//     * @param maxHeight Maximum desired height of the image
//     * @param exact     if true, the image will be proportionally scaled to fit bbox.
//     *
//     * @return The scaled bitmap for the file, or null if no file or bad file.
//     */
//    @Nullable
//    public static Bitmap fetchThumbnailIntoImageView(@Nullable final ImageView destView, @NonNull final String uuid,
//                                                     final int maxWidth, final int maxHeight, final boolean exact) {
//        try {
//            return fetchFileIntoImageView(destView, StorageUtils.getCoverFile(uuid), maxWidth, maxHeight, exact);
//        } catch (IllegalArgumentException e) {
//            Logger.logError(e);
//            return null;
//        }
//    }
}
