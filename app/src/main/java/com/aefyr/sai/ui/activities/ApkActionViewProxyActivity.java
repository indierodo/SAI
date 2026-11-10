package com.aefyr.sai.ui.activities;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

public class ApkActionViewProxyActivity extends ThemedActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent incomingIntent = getIntent();
        if ((!Intent.ACTION_VIEW.equals(incomingIntent.getAction()) && !Intent.ACTION_INSTALL_PACKAGE.equals(incomingIntent.getAction()))
                || incomingIntent.getData() == null) {
            finish();
            return;
        }

        Intent mainActivityIntent = new Intent(this, MainActivity.class);
        mainActivityIntent.setAction(Intent.ACTION_VIEW);
        mainActivityIntent.setDataAndType(incomingIntent.getData(), incomingIntent.getType());
        mainActivityIntent.setClipData(incomingIntent.getClipData());
        mainActivityIntent.addFlags(incomingIntent.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION));
        startActivity(mainActivityIntent);

        finish();
    }

    public static ComponentName getComponentName(Context context) {
        return new ComponentName(context, ApkActionViewProxyActivity.class);
    }
}
