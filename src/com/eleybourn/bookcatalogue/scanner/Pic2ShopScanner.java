package com.eleybourn.bookcatalogue.scanner;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.debug.Logger;

import java.util.Arrays;

/**
 * Based on the pic2shop client code at github, this object will start pic2shop and extract the data
 * from the resulting intent when the activity completes.
 *
 * https://github.com/VisionSmarts/pic2shop-client
 *
 * It also has a static method to check if the intent is present.
 *
 * @author pjw
 */
public class Pic2ShopScanner implements Scanner {
    /**
     * When a barcode is read, pic2shop returns Activity.RESULT_OK in onActivityResult()
     * of the activity which requested the scan using startActivityForResult().
     * The read barcode can be retrieved with intent.getStringExtra(BARCODE).
     *
     * If the user exits pic2shop by pressing Back before a barcode is read, the
     * result code will be Activity.RESULT_CANCELED in onActivityResult().
     */

    // response Intent
    private static final String BARCODE = "BARCODE";

    /**
     * Check if we have a valid intent available.
     *
     * @return <tt>true</tt>if present
     */
    static boolean isIntentAvailable() {
        return isFreeScannerAppInstalled(BookCatalogueApp.getAppContext())
                || isProScannerAppInstalled(BookCatalogueApp.getAppContext());
    }

    private static boolean isIntentAvailable(@NonNull final Context context, @NonNull final String action) {
        Intent test = new Intent(action);
        return context.getPackageManager().resolveActivity(test, 0) != null;
    }

    private static boolean isFreeScannerAppInstalled(@NonNull final Context context) {
        return isIntentAvailable(context, Free.ACTION);
    }

    private static boolean isProScannerAppInstalled(@NonNull final Context context) {
        return isIntentAvailable(context, Pro.ACTION);
    }

    /**
     * Start the activity with the passed request code.
     *
     * Note that we always send an intent; the caller should have checked that
     * one of the intents is valid, or catch the resulting errors.
     */
    @Override
    public void startActivityForResult(@NonNull final  Activity activity, final int requestCode) {
        Intent intent;
        if (isFreeScannerAppInstalled(activity)) {
            intent = new Intent(Free.ACTION);
        } else {
            intent = new Intent(Pro.ACTION);
            intent.putExtra(Pro.FORMATS, Pro.BARCODE_TYPES);
        }
        activity.startActivityForResult(intent, requestCode);
    }

    /**
     * Extract the barcode from the result
     */
    @Override
    public String getBarcode(@NonNull final Intent intent) {
        String barcode = intent.getStringExtra(BARCODE);
        // only for Pro:
        String barcodeFormat = intent.getStringExtra(Pro.FORMAT);
        if (barcodeFormat != null && !Arrays.asList(Pro.BARCODE_TYPES).contains(barcodeFormat)) {
            throw new RuntimeException("Unexpected format for barcode: " + barcodeFormat);
        }

        return barcode;
    }

    public interface Free {
        String PACKAGE = "com.visionsmarts.pic2shop";
        String ACTION = PACKAGE + ".SCAN";
    }

    /**
     * just for reference: https://en.wikipedia.org/wiki/Barcode#Types_of_barcodes
     * The Pro package does not implement all those.
     * BARCODE_TYPES below was taken from the example code at github.
     */
    public interface Pro {
        String PACKAGE = "com.visionsmarts.pic2shoppro";
        String ACTION = PACKAGE + ".SCAN";
        // request Intent
       //String[] ALL_BARCODE_TYPES = {"EAN13","EAN8","UPCE","ITF","CODE39","CODE128","CODABAR","QR"};
       String[] BARCODE_TYPES = {"EAN13","UPCE"};
       String FORMATS = "formats";
        // response Intent
        String FORMAT = "format";
    }

//    public static void launchMarketToInstallFreeScannerApp(@NonNull final Context context) {
//        launchMarketToInstallApp(context, Free.PACKAGE);
//    }
//
//    public static void launchMarketToInstallProScannerApp(@NonNull final Context context) {
//        launchMarketToInstallApp(context, Pro.PACKAGE);
//    }

//    private static void launchMarketToInstallApp(@NonNull final Context context, @NonNull final String packageName) {
//        try {
//            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName));
//            context.startActivity(intent);
//        } catch (ActivityNotFoundException e) {
//            Logger.logError(e, "Android Market aka Google Play not installed.");
//        }
//    }
}
