/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.sync.stripinfo;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import java.io.IOException;
import java.net.HttpCookie;
import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.tasks.VMTask;

/**
 * A simple task wrapping {@link ImportCollection}.
 * <p>
 * No options for now, just fetch all books which the user has marked owned ("In bezit")
 * on the site.
 */
public class StripInfoFetchCollectionTask
        extends VMTask<List<ImportCollection.ColData>> {

    /**
     * Start the task.
     */
    @UiThread
    public void start() {
        execute(R.id.TASK_ID_IMPORT);
    }

    @Nullable
    @Override
    protected List<ImportCollection.ColData> doWork(@NonNull final Context context)
            throws IOException {
        final LoginHelper loginHelper = new LoginHelper();
        final Optional<HttpCookie> cookie = loginHelper.login();
        if (cookie.isPresent()) {
            final String userId = loginHelper.getUserId();
            if (userId != null && !userId.isEmpty()) {
                final ImportCollection ic = new ImportCollection(userId);
                return ic.fetch(context);
            }
        }

        return null;
    }
}
