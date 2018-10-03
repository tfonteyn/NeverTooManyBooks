/*
 * @copyright 2010 Evan Leybourn
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.baseactivity.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.DebugReport;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.AnthologyTitle;
import com.eleybourn.bookcatalogue.searches.isfdb.ISFDBBook;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.List;

/**
 * This is the Administration page. It contains details about the app, links
 * to my website and email, functions to export and import books and functions to
 * manage bookshelves.
 *
 * @author Evan Leybourn
 */
public class Help extends BookCatalogueActivity {
    private Resources res;
    private CatalogueDBAdapter mDb;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin_help;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setTitle(R.string.app_name);
            // Needed for sending com.eleybourn.bookcatalogue.debug info...
            mDb = new CatalogueDBAdapter(this);
            mDb.open();

            res = getResources();

            TextView webinstructions = findViewById(R.id.helpinstructions);
            webinstructions.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent loadweb = new Intent(Intent.ACTION_VIEW, Uri.parse(res.getString(R.string.helppage)));
                    startActivity(loadweb);
                }
            });

            TextView webpage = findViewById(R.id.helppage);
            webpage.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent loadweb = new Intent(Intent.ACTION_VIEW, Uri.parse(res.getString(R.string.helppage)));
                    startActivity(loadweb);
                }
            });

            Button sendInfo = findViewById(R.id.send_info);
            sendInfo.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    DebugReport.sendDebugInfo(Help.this, mDb);
                }
            });

            setupCleanupButton();

        } catch (Exception e) {
            Logger.logError(e);
        }
    }

    private void setupCleanupButton() {
        try {
            final Button cleanupBtn = findViewById(R.id.cleanup_button);
            final TextView cleanupTxt = findViewById(R.id.cleanup_text);

            cleanupBtn.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    StorageUtils.purgeFiles(true);
                    setupCleanupButton();
                }
            });

            final float space = StorageUtils.purgeFiles(false);
            if (space == 0) {
                cleanupBtn.setVisibility(View.GONE);
                cleanupTxt.setVisibility(View.GONE);
            } else {
                cleanupBtn.setVisibility(View.VISIBLE);
                cleanupTxt.setVisibility(View.VISIBLE);
                cleanupTxt.setText(getString(R.string.cleanup_files_text, Utils.formatFileSize(space)));
            }
        } catch (Exception e) {
            Logger.logError(e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupCleanupButton();
    }

    /**
     * Called when activity destroyed
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            mDb.close();
        } catch (Exception e) {
            Logger.logError(e);
        }
    }

}