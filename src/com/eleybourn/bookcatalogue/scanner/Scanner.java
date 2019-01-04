package com.eleybourn.bookcatalogue.scanner;

import android.app.Activity;
import android.content.Intent;
import androidx.annotation.NonNull;

/**
 * Interface defining required methods for any external scanner interface.
 *
 * @author pjw
 */
public interface Scanner {
    /** Request a scan */
    void startActivityForResult(@NonNull final Activity activity, final int requestCode);

    /** Get the barcode from the resulting intent */
    @NonNull
    String getBarcode(@NonNull final Intent data);
}
