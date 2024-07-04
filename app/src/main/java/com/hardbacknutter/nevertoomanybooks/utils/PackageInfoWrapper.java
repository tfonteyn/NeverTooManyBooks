/*
 * @Copyright 2018-2024 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * A trivial wrapper for {@link PackageInfoWrapper} to hide the different API versions
 * from the rest of the application.
 */
public final class PackageInfoWrapper {

    private static final String TAG = "PackageInfoWrapper";

    @NonNull
    private final PackageInfo info;

    /**
     * Private constructor. Use the factory methods instead.
     *
     * @param context Current context
     * @param flags   Additional option flags to modify the data returned.
     */
    private PackageInfoWrapper(@NonNull final Context context,
                               final int flags) {
        try {
            info = context.getPackageManager().getPackageInfo(context.getPackageName(), flags);
        } catch (@NonNull final PackageManager.NameNotFoundException ignore) {
            throw new IllegalStateException("no PackageManager?");
        }
    }

    /**
     * Constructor.
     *
     * @param context Current context
     *
     * @return instance
     */
    @NonNull
    public static PackageInfoWrapper create(@NonNull final Context context) {
        return new PackageInfoWrapper(context, 0);
    }

    /**
     * Constructor.
     *
     * @param context Current context
     *
     * @return instance with signing certificates loaded
     */
    @NonNull
    public static PackageInfoWrapper createWithSignatures(@NonNull final Context context) {
        final PackageInfoWrapper info;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info = new PackageInfoWrapper(context, PackageManager.GET_SIGNING_CERTIFICATES);
        } else {
            info = new PackageInfoWrapper(context, PackageManager.GET_SIGNATURES);
        }
        return info;
    }

    /**
     * The name of this package as defined in {@code <manifest name="...">}.
     *
     * @return name
     */
    @NonNull
    public String getPackageName() {
        return info.packageName;
    }

    /**
     * Reads the application version from the manifest.
     *
     * @return the version
     */
    public long getVersionCode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return info.getLongVersionCode();
        } else {
            //noinspection deprecation
            return info.versionCode;
        }
    }

    /**
     * Reads the application version from the manifest.
     *
     * @return the version
     */
    @NonNull
    public String getVersionName() {
        //noinspection DataFlowIssue
        return info.versionName;
    }

    /**
     * Return the SHA256 hash of the public part of the key that signed this app.
     *
     * <pre>
     *     {@code
     *     keytool -list -keystore myKeyStore.jks -storepass myPassword -v
     *      ...
     *      Certificate fingerprints:
     *          ...
     *          SHA256: D4:98:1C:F7:...    <= this one
     *     }
     * </pre>
     *
     * @return human readable hash-code
     */
    @NonNull
    public Optional<String> getSignedBy() {
        if (info.signatures == null) {
            return Optional.empty();
        }

        final StringJoiner signedBy = new StringJoiner("/");
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA256");

            // concat the signature chain.
            Arrays.stream(info.signatures)
                  .filter(Objects::nonNull)
                  .forEach(sig -> {
                      md.reset();
                      final StringJoiner hexString = new StringJoiner(":");
                      for (final byte aPublicKey : md.digest(sig.toByteArray())) {
                          final String byteString = Integer.toHexString(0xFF & aPublicKey);
                          hexString.add(byteString.length() == 1 ? '0' + byteString : byteString);
                      }
                      signedBy.add(hexString.toString());
                  });

        } catch (@NonNull final NoSuchAlgorithmException | RuntimeException e) {
            LoggerFactory.getLogger().e(TAG, e);
            return Optional.empty();
        }
        return Optional.of(signedBy.toString());
    }
}
