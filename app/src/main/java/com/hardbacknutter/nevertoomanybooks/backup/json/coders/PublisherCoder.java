/*
 * @Copyright 2020 HardBackNutter
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

import org.json.JSONException;
import org.json.JSONObject;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;

public class PublisherCoder
        implements JsonCoder<Publisher> {

    PublisherCoder() {
    }

    @Override
    @NonNull
    public JSONObject encode(@NonNull final Publisher publisher)
            throws JSONException {
        final JSONObject out = new JSONObject();

        out.put(DBDefinitions.KEY_PK_ID, publisher.getId());
        out.put(DBDefinitions.KEY_PUBLISHER_NAME, publisher.getName());
        return out;
    }

    @Override
    @NonNull
    public Publisher decode(@NonNull final JSONObject data)
            throws JSONException {

        final Publisher publisher = new Publisher(data.getString(DBDefinitions.KEY_PUBLISHER_NAME));
        publisher.setId(data.getLong(DBDefinitions.KEY_PK_ID));
        return publisher;
    }
}
