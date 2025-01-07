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
package com.hardbacknutter.nevertoomanybooks.backup.json.coders;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoCollectionData;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

public class StripInfoDataCoder
        implements JsonCoder<StripInfoCollectionData> {

    @Override
    @NonNull
    public JSONObject encode(@NonNull final StripInfoCollectionData field)
            throws JSONException {
        final JSONObject out = new JSONObject();

        out.put(DBKey.STRIP_INFO_BOOK_ID, field.getSid());
        out.put(DBKey.STRIP_INFO_COLLECTION_ID, field.getCollectionId());
        out.put(DBKey.STRIP_INFO_WANTED, field.isWanted());
        out.put(DBKey.STRIP_INFO_OWNED, field.isOwned());
        out.put(DBKey.STRIP_INFO_DIGITAL, field.isDigital());
        out.put(DBKey.STRIP_INFO_AMOUNT, field.getAmount());
        final String lastSync = field.getLastSync();
        if (lastSync != null) {
            out.put(DBKey.STRIP_INFO_LAST_SYNC_DATE__UTC, lastSync);
        }
        return out;
    }

    @Override
    @NonNull
    public StripInfoCollectionData decode(@NonNull final JSONObject data)
            throws JSONException {

        return new StripInfoCollectionData(
                data.getLong(DBKey.STRIP_INFO_BOOK_ID),
                data.getLong(DBKey.STRIP_INFO_COLLECTION_ID),
                data.getBoolean(DBKey.STRIP_INFO_WANTED),
                data.getBoolean(DBKey.STRIP_INFO_OWNED),
                data.getBoolean(DBKey.STRIP_INFO_DIGITAL),
                data.getInt(DBKey.STRIP_INFO_AMOUNT),
                data.optString(DBKey.STRIP_INFO_LAST_SYNC_DATE__UTC, null));
    }
}
