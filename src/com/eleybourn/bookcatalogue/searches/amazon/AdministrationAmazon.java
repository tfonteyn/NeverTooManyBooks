package com.eleybourn.bookcatalogue.searches.amazon;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.debug.Logger;

public class AdministrationAmazon extends BookCatalogueActivity {

    @Override
    protected int getLayoutId(){
        return R.layout.activity_administration_amazon;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setTitle(R.string.amazon);
            setupPage();
        } catch (Exception ignore) {
            Logger.logError(ignore);
        }
    }

    private void setupPage() {
        /* Host link */
        final EditText url = findViewById(R.id.url);
        url.setText(AmazonManager.getBaseURL());

        /* Save Button */
        Button btn = findViewById(R.id.confirm);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newURL = url.getText().toString();
                //TODO: add sanity checks
                if (!newURL.isEmpty()) {
                    AmazonManager.setBaseURL(newURL);
                    finish();
                }
            }
        });

        Button cancelBtn = findViewById(R.id.cancel);
        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
}
