package com.ericaerick.lockscreen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            Intent svc = new Intent(context, LockService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(svc);
            else context.startService(svc);
        } catch (Exception e) { /* ignora */ }
    }
}
