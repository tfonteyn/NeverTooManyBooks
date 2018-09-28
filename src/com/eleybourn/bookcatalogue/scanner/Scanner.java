package com.eleybourn.bookcatalogue.scanner;

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.NonNull;

/**
 * Interface defining required methods for any external scanner interface.
 *
 * @author pjw
 */
public interface Scanner {
    /** tag used in intent.getStringExtra */
    String SCAN_RESULT = "SCAN_RESULT";

    /** Request a scan */
    void startActivityForResult(@NonNull final Activity activity, final int requestCode);

    /** Get the barcode from the resulting intent */
    String getBarcode(@NonNull final Intent intent);
}
