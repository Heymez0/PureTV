package io.puretv;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Bundled notices load only when explicitly requested; no network access or background work. */
final class Credits {
    private Credits() {}
    static void show(Activity activity) {
        String[] labels={"Credits", "PureTV — MIT", "Launcher portions — MIT", "Inter — SIL OFL", "Material Symbols — Apache 2.0"};
        String[] files={null,"licenses/LICENSE","licenses/third_party/LICENSE-launcher-MIT.txt","fonts/OFL.txt","symbols/LICENSE-material-symbols.txt"};
        new AlertDialog.Builder(activity).setTitle(Ui.tr("Licences"))
            .setItems(labels,(dialog,index)->document(activity,labels[index],files[index]))
            .setNegativeButton(Ui.tr("Cancel"),null).show();
    }
    private static void document(Activity activity,String title,String file) {
        boolean dark=HomeData.get(activity).isNight(System.currentTimeMillis());
        int pad=Math.round(24*activity.getResources().getDisplayMetrics().density);
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(pad,pad,pad,pad);
        body.setBackgroundColor(dark?0xFF141519:0xFFF4F4F6);
        TextView text=new TextView(activity);text.setTextSize(17);text.setTextColor(dark?Color.WHITE:0xFF151619);
        text.setLinkTextColor(dark?0xFFE7BD61:0xFF715000);text.setTextIsSelectable(true);
        if(file==null){
            ImageView logo=new ImageView(activity);logo.setAdjustViewBounds(true);
            try(InputStream stream=activity.getAssets().open("licenses/tmdb-logo.png")){logo.setImageDrawable(android.graphics.drawable.Drawable.createFromStream(stream,null));}catch(Exception ignored){}
            logo.setContentDescription("The Movie Database (TMDB)");
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(pad*6,pad*2);size.bottomMargin=pad;body.addView(logo,size);
            text.setText(Html.fromHtml("<b>PureTV</b> — MIT launcher. Original copyright and third-party notices are bundled.<br><br>"
                +"<b>Weather data</b>: <a href=\"https://open-meteo.com/\">Open-Meteo</a>, <a href=\"https://creativecommons.org/licenses/by/4.0/\">CC BY 4.0</a>. "
                +"Location search: <a href=\"https://www.geonames.org/\">GeoNames</a>, CC BY 4.0. Values are formatted and converted to your chosen units.<br><br>"
                +"<b>TMDB</b>: This product uses the TMDB API but is not endorsed or certified by TMDB. "
                +"<a href=\"https://www.themoviedb.org/\">The Movie Database</a>. Catalogue artwork remains its owners' property. An optional API key is supplied by the person building the app.<br><br>"
                +"<b>Inter</b>: The Inter Project Authors, SIL Open Font License 1.1.<br>"
                +"<b>Material Symbols</b>: Google, Apache License 2.0. Symbols were converted to PNG for this interface.<br><br>"
                +"<b>AirPlay</b>: Optional separate GPL-3.0 receiver; it is not included in the launcher APK. Its source and notices accompany its own distribution.<br><br>"
                +"App logos, recommendation artwork and names belong to their owners. No vendor affiliation is implied.",Html.FROM_HTML_MODE_LEGACY));
            text.setMovementMethod(LinkMovementMethod.getInstance());
        }else{
            try(InputStream stream=activity.getAssets().open(file)){
                text.setText(new String(BoundedInput.read(stream,128*1024),StandardCharsets.UTF_8));
            }catch(Exception ignored){text.setText("Bundled notice could not be opened.");}
        }
        body.addView(text);ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(true);scroll.addView(body);
        new AlertDialog.Builder(activity).setTitle(title).setView(scroll).setPositiveButton("OK",null).show();
    }
}
