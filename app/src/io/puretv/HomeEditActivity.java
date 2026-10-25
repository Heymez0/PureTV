package io.puretv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Keep existing shortcuts, with one home-layout settings location. */
public class HomeEditActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        startActivity(new Intent(this,SettingsActivity.class).putExtra("home_settings",true));
        finish();
    }
}
