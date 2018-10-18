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
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.debug.DebugReport;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * This is the Help page. It contains details about the app, links to my website and email,
 * functions to export and import books and functions to manage bookshelves.
 *
 * @author Evan Leybourn
 */
public class Help extends BaseActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin_help;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setTitle(R.string.app_name);

            findViewById(R.id.help_instructions).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse(getString(R.string.help_page)));
                    startActivity(intent);
                }
            });

            findViewById(R.id.help_page).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse(getString(R.string.help_page)));
                    startActivity(intent);
                }
            });

            findViewById(R.id.send_info).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    DebugReport.sendDebugInfo(Help.this);
                }
            });

            initCleanupButton();

        } catch (Exception e) {
            Logger.error(e);
        }
    }

    private void initCleanupButton() {
        try {
            final Button cleanupBtn = findViewById(R.id.cleanup_button);
            final TextView cleanupTxt = findViewById(R.id.cleanup_text);

            cleanupBtn.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    StorageUtils.purgeFiles(true);
                    initCleanupButton();
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
            Logger.error(e);
        }
    }

    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();
        initCleanupButton();
    }
}