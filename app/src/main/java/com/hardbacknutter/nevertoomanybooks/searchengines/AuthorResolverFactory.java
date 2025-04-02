/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque.BedethequeAuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.databazeknih.DatabazeKnihAuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.dnb.DnbAuthorResolver;

/**
 * ENHANCE the use of AuthorResolver to let them access the website Author API/page
 *  (providing they have this) and fetch extra information about the author
 *  (born/died, language, country... i.e. not limited to the pen-name)
 */
public final class AuthorResolverFactory {

    private AuthorResolverFactory() {
    }

    /**
     * Get a list of the supported resolvers for the given engine.
     *
     * @param context      Current context
     * @param searchEngine to use
     *
     * @return list
     */
    @NonNull
    public static List<AuthorResolver> getEuroComicResolvers(
            @NonNull final Context context,
            @NonNull final SearchEngine searchEngine) {

        // For now, we only support a single resolver, so the last part is hardcoded
        final String key = searchEngine.getEngineId().getPreferenceKey()
                           + AuthorResolver.PK_RESOLVE_AUTHORS
                           + EngineId.Bedetheque.getPreferenceKey();

        if (ServiceLocator.getInstance().isFieldEnabled(DBKey.FK_AUTHOR_REAL_AUTHOR)
            && PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(key, false)) {

            return List.of(BedethequeAuthorResolver.create(context, searchEngine));
        } else {
            return List.of();
        }
    }

    @NonNull
    public static List<AuthorResolver> getResolvers(@NonNull final Context context,
                                                    @NonNull final SearchEngine searchEngine) {
        final String pk = searchEngine.getEngineId().getPreferenceKey();
        // For now, we only support a single resolver,
        // so the last part is the same as the first
        final String key = pk + AuthorResolver.PK_RESOLVE_AUTHORS + pk;

        if (ServiceLocator.getInstance().isFieldEnabled(DBKey.FK_AUTHOR_REAL_AUTHOR)
            && PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(key, false)) {

            switch (searchEngine.getEngineId()) {
                case DatabazeKnih:
                    return List.of(DatabazeKnihAuthorResolver.create(context, searchEngine));
                case Dnb:
                    return List.of(DnbAuthorResolver.create(context, searchEngine));
                case Isfdb:
                    // URGENT: 2025-03-25. ongoing site issues SSLProtocolException
                    // Read error: ssl=0x7a6b6d87b598: Failure in SSL library, usually a protocol error
                    // error:1e000065:Cipher functions:OPENSSL_internal:BAD_DECRYPT
                    // (external/boringssl/src/crypto/cipher_extra/e_chacha20poly1305.c:259
                    // 0x7a69b00547fb:0x00000000)
                    // error:1000008b:SSL routines:OPENSSL_internal:DECRYPTION_FAILED_OR_BAD_RECORD_MAC
                    // (external/boringssl/src/ssl/tls_record.cc:294 0x7a69b00547fb:0x00000000)
                    // |docRequestUrl="https://www.isfdb.org/cgi-bin/ea.cgi?5"

                    //return List.of(IsfdbAuthorResolver.create(context, searchEngine));
                    return List.of();
            }
        }
        return List.of();
    }
}
