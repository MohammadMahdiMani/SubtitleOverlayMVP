package com.example.subtitleoverlay;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.content.res.Configuration;
import android.app.NotificationManager;
import android.text.InputType;
import android.widget.EditText;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.InputStream;
import java.io.IOException;
import java.util.*;

public class MainActivity extends Activity {
    static final int PICK=10, PICK2=13, PICK_LIBRARY=11, PICK_FONT=12;
    Uri uri, uri2;
    TextView status, status2, libraryStatus, fontStatus;
    TextView overlayCheck, notificationCheck, accessibilityCheck;
    LinearLayout root;
    private boolean firstResume=true;
    private final Handler statusHandler=new Handler(Looper.getMainLooper());
    private final Runnable statusPoll=new Runnable(){ @Override public void run(){ refreshPermissionStatus(); statusHandler.postDelayed(this,1000); } };
    Spinner sizeSpinner, fontSpinner, speedSpinner, positionSpinner, bgModeSpinner, bgPaddingSpinner, bgOpacitySpinner;

    final int[] colors={Color.WHITE,Color.YELLOW,Color.CYAN,Color.GREEN,Color.RED};
    final int[] bgs={Color.argb(180,0,0,0),Color.argb(180,255,255,255),Color.argb(180,0,60,120),Color.TRANSPARENT};
    final String[] fontLabels={"Sans","Sans Medium","Condensed","Serif","Monospace","Custom"};
    final String[] fontVals={"sans-serif","sans-serif-medium","sans-serif-condensed","serif","monospace","custom"};
    final float[] sizes={16,20,24,28,32,40,48,56,64};
    final float[] speeds={0.75f,0.9f,1f,1.1f,1.25f,1.5f};

    final int PRIMARY=Color.rgb(63,81,181);
    final int PRIMARY_DARK=Color.rgb(48,63,159);
    final int DANGER=Color.rgb(198,40,40);

    private boolean dark(){ return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES; }
    private int text(){ return dark()?Color.rgb(238,238,238):Color.rgb(35,35,35); }
    private int muted(){ return dark()?Color.rgb(185,185,185):Color.rgb(95,95,95); }
    private int surface(){ return dark()?Color.rgb(34,34,34):Color.WHITE; }
    private int rootColor(){ return dark()?Color.rgb(18,18,18):Color.rgb(248,248,248); }
    private int border(){ return dark()?Color.rgb(70,70,70):Color.rgb(225,225,225); }
    private int secondaryFill(){ return dark()?Color.rgb(58,58,58):Color.rgb(236,239,241); }

    @Override public void onCreate(Bundle b){ super.onCreate(b); build(); }

    private void build(){
        ScrollView scroll=new ScrollView(this);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(18),dp(18),dp(28));
        root.setBackgroundColor(rootColor());
        String saved1=SettingsStore.primaryUri(this), saved2=SettingsStore.secondaryUri(this);
        if(saved1!=null&&!saved1.isEmpty()) uri=Uri.parse(saved1);
        if(saved2!=null&&!saved2.isEmpty()) uri2=Uri.parse(saved2);

        TextView title=new TextView(this);
        title.setText("Subtitle Overlay"); title.setTextSize(26); title.setTextColor(text()); title.setTypeface(null,1);
        root.addView(title,lp(-1,-2,0,0,0,4));
        TextView version=new TextView(this);
        version.setText("v1.1.2 • stable sync + unified UI"); version.setTextSize(13); version.setTextColor(muted());
        root.addView(version,lp(-1,-2,0,0,0,14));

        buildPrimarySection();
        buildSecondarySection();
        buildAppearanceSection();
        buildBackgroundSection();
        buildSyncSection();
        buildSystemSection();

        scroll.addView(root);
        setContentView(scroll);
    }

    private void buildPrimarySection(){
        LinearLayout card=card();
        card.addView(sectionTitle("1. Primary subtitle"));
        status=new TextView(this); status.setText(uri==null?"No primary subtitle selected":"Primary: "+(uri.getLastPathSegment()==null?"selected":uri.getLastPathSegment())); status.setTextColor(text()); status.setPadding(0,dp(4),0,dp(10));
        card.addView(status);
        Button select=primaryButton("Select Primary SRT"); select.setOnClickListener(v->pick(PICK)); card.addView(select,fullButtonParams());
        Button library=secondaryButton("Choose from Subtitle Library"); library.setOnClickListener(v->openLibrary(false)); card.addView(library,fullButtonParams());
        Button folder=secondaryButton("Set / Change Library Folder"); folder.setOnClickListener(v->chooseLibrary()); card.addView(folder,fullButtonParams());
        libraryStatus=new TextView(this);
        libraryStatus.setText(SettingsStore.libraryTree(this).isEmpty()?"Library folder: not selected":"Library folder: selected");
        libraryStatus.setTextSize(12); libraryStatus.setTextColor(muted()); libraryStatus.setPadding(0,dp(6),0,0);
        card.addView(libraryStatus);
        root.addView(card,cardParams());
    }

    private void buildSecondarySection(){
        LinearLayout card=card();
        card.addView(sectionTitle("2. Secondary subtitle"));
        status2=new TextView(this); status2.setText(uri2==null?"No secondary subtitle selected":"Secondary: "+(uri2.getLastPathSegment()==null?"selected":uri2.getLastPathSegment())); status2.setTextColor(text()); status2.setPadding(0,dp(4),0,dp(10));
        card.addView(status2);
        Button select=secondaryPrimaryButton("Select Secondary SRT"); select.setOnClickListener(v->pick(PICK2)); card.addView(select,fullButtonParams());
        Button library=secondaryButton("Choose Secondary from Library"); library.setOnClickListener(v->openLibrary(true)); card.addView(library,fullButtonParams());
        Button clear=outlineButton("Clear Secondary Subtitle"); clear.setOnClickListener(v->{uri2=null;SettingsStore.secondaryUri(this,"");status2.setText("No secondary subtitle selected");}); card.addView(clear,fullButtonParams());
        CheckBox dual=new CheckBox(this);
        dual.setText("Enable dual subtitles"); dual.setTextSize(16); dual.setTextColor(text()); dual.setChecked(SettingsStore.doubleSubtitle(this));
        dual.setOnCheckedChangeListener((b,checked)->{SettingsStore.doubleSubtitle(this,checked);if(OverlayService.instance!=null)OverlayService.instance.refreshSubtitlesFromSettings();});
        card.addView(dual,lp(-1,-2,0,8,0,0));
        CheckBox reverse=new CheckBox(this);
        reverse.setText("Show secondary subtitle above primary"); reverse.setTextSize(14); reverse.setTextColor(text()); reverse.setChecked(SettingsStore.secondaryFirst(this));
        reverse.setOnCheckedChangeListener((b,checked)->{SettingsStore.secondaryFirst(this,checked);if(OverlayService.instance!=null)OverlayService.instance.refreshSubtitlesFromSettings();});
        card.addView(reverse,lp(-1,-2,0,0,0,0));
        TextView hint=smallText("Both subtitles share the same appearance and video sync. Only the secondary timing offset can be adjusted separately.");
        card.addView(hint,lp(-1,-2,0,6,0,0));
        root.addView(card,cardParams());
    }

    private void buildAppearanceSection(){
        LinearLayout card=card();
        card.addView(sectionTitle("3. Subtitle appearance"));
        sizeSpinner=spinner(new String[]{"16 px","20 px","24 px","28 px","32 px","40 px","48 px","56 px","64 px"});
        setSpinnerFloat(sizeSpinner,sizes,SettingsStore.size(this)); card.addView(row("Font size",sizeSpinner));
        fontSpinner=spinner(SettingsStore.hasCustomFont(this)?fontLabels:new String[]{"Sans","Sans Medium","Condensed","Serif","Monospace"});
        int fi=indexOf(fontVals,SettingsStore.font(this)); if(!SettingsStore.hasCustomFont(this)&&fi>4)fi=0; fontSpinner.setSelection(fi);
        fontSpinner.setOnItemSelectedListener(new SimpleListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){String f=fontVals[Math.min(pos,fontVals.length-1)];SettingsStore.font(MainActivity.this,f);if(OverlayService.instance!=null)OverlayService.instance.refreshAppearance();}});
        card.addView(row("Font family",fontSpinner));
        Button custom=secondaryButton("Upload TTF / OTF Font"); custom.setOnClickListener(v->pickFont()); card.addView(custom,fullButtonParams());
        fontStatus=new TextView(this); fontStatus.setText(SettingsStore.hasCustomFont(this)?"Custom font: "+SettingsStore.customFontName(this):"Custom font: none");
        fontStatus.setTextSize(12); fontStatus.setTextColor(muted()); card.addView(fontStatus,lp(-1,-2,0,4,0,4));
        card.addView(colorRow("Font color",false));
        card.addView(row("Subtitle position",positionSpinner()));
        root.addView(card,cardParams());
    }

    private Spinner positionSpinner(){
        positionSpinner=spinner(new String[]{"Bottom","Lower","Center","Upper","Top"});
        positionSpinner.setSelection(SettingsStore.position(this));
        positionSpinner.setOnItemSelectedListener(new SimpleListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){SettingsStore.position(MainActivity.this,pos);if(OverlayService.instance!=null)OverlayService.instance.refreshAppearance();}});
        return positionSpinner;
    }

    private void buildBackgroundSection(){
        LinearLayout card=card();
        card.addView(sectionTitle("4. Background"));
        card.addView(colorRow("Background color",true));
        bgModeSpinner=spinner(new String[]{"Full Width","Fit to Subtitle","No Background"}); bgModeSpinner.setSelection(SettingsStore.bgMode(this));
        bgModeSpinner.setOnItemSelectedListener(new SimpleListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){SettingsStore.bgMode(MainActivity.this,pos);if(OverlayService.instance!=null)OverlayService.instance.refreshAppearance();}}); card.addView(row("Background mode",bgModeSpinner));
        bgPaddingSpinner=spinner(new String[]{"0 px","2 px","4 px","6 px","8 px","12 px","16 px"});
        int bp=indexOfInt(new int[]{0,2,4,6,8,12,16},SettingsStore.bgPadding(this)); bgPaddingSpinner.setSelection(bp);
        bgPaddingSpinner.setOnItemSelectedListener(new SimpleListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){SettingsStore.bgPadding(MainActivity.this,Integer.parseInt(p.getItemAtPosition(pos).toString().replace(" px","")));if(OverlayService.instance!=null)OverlayService.instance.refreshAppearance();}}); card.addView(row("Padding",bgPaddingSpinner));
        bgOpacitySpinner=spinner(new String[]{"30%","50%","70%","85%","100%"});
        int bo=indexOfInt(new int[]{30,50,70,85,100},SettingsStore.bgOpacity(this)); bgOpacitySpinner.setSelection(bo);
        bgOpacitySpinner.setOnItemSelectedListener(new SimpleListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){SettingsStore.bgOpacity(MainActivity.this,Integer.parseInt(p.getItemAtPosition(pos).toString().replace("%","")));if(OverlayService.instance!=null)OverlayService.instance.refreshAppearance();}}); card.addView(row("Opacity",bgOpacitySpinner));
        root.addView(card,cardParams());
    }

    private void buildSyncSection(){
        LinearLayout card=card();
        card.addView(sectionTitle("5. Timing & sync"));
        speedSpinner=spinner(new String[]{"0.75x","0.9x","1.0x","1.1x","1.25x","1.5x"});
        setSpinnerFloat(speedSpinner,speeds,SettingsStore.speed(this)); card.addView(row("Subtitle timing speed",speedSpinner));
        card.addView(label("Primary subtitle offset")); addOffsetControls(card,SettingsStore.offset(this),false);
        card.addView(label("Secondary subtitle offset")); addOffsetControls(card,SettingsStore.offset2(this),true);
        TextView hint=smallText("Sync uses Accessibility first and MediaSession as an automatic fallback. Offsets affect subtitle timing only.");
        card.addView(hint,lp(-1,-2,0,8,0,0));
        root.addView(card,cardParams());
    }

    private void buildSystemSection(){
        LinearLayout card=card();
        card.addView(sectionTitle("6. Permissions & overlay"));
        overlayCheck=permissionRow(card,"Overlay permission",v->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))));
        notificationCheck=permissionRow(card,"Notifications & Media Session",v->openNotificationSettings());
        accessibilityCheck=permissionRow(card,"Accessibility sync",v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        Spinner hideSpinner=spinner(new String[]{"3 seconds","5 seconds","10 seconds","Always visible"});
        int hide=SettingsStore.controlsAutoHide(this); int hideIdx=hide==5?1:(hide==10?2:(hide==0?3:0)); hideSpinner.setSelection(hideIdx);
        hideSpinner.setOnItemSelectedListener(new SimpleListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){int[] vals={3,5,10,0};SettingsStore.controlsAutoHide(MainActivity.this,vals[pos]);if(OverlayService.instance!=null)OverlayService.instance.refreshControlsVisibilityFromSettings();}});
        card.addView(row("Overlay controls auto-hide",hideSpinner));
        TextView mediaHint=smallText("Media Session sync uses Notification Access. Enable it below if you want automatic MediaSession fallback.");
        card.addView(mediaHint,lp(-1,-2,0,2,0,4));
        Button media=secondaryButton("Enable Media Session / Notification Access"); media.setOnClickListener(v->startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))); card.addView(media,fullButtonParams());
        Space sp=new Space(this); card.addView(sp,lp(1,dp(8),0,8,0,8));
        Button start=primaryButton("START OVERLAY"); start.setTextSize(16); start.setOnClickListener(v->start()); card.addView(start,fullButtonParams());
        Button stop=dangerButton("STOP OVERLAY"); stop.setOnClickListener(v->stopService(new Intent(this,OverlayService.class))); card.addView(stop,fullButtonParams());
        TextView info=smallText("The overlay draws your SRT subtitles on top of the video. It does not read or modify the video stream.");
        card.addView(info,lp(-1,-2,0,10,0,0));
        root.addView(card,cardParams());
    }

    private TextView permissionRow(LinearLayout parent,String label,View.OnClickListener action){
        LinearLayout r=new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(0,dp(5),0,dp(5));
        TextView check=new TextView(this); check.setTextSize(14); check.setGravity(Gravity.CENTER_VERTICAL); check.setMinWidth(dp(30));
        TextView name=new TextView(this); name.setText(label); name.setTextSize(15); name.setTextColor(text()); name.setTypeface(null,1); name.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        Button open=outlineButton("Settings"); open.setTextSize(12); open.setMinHeight(dp(38)); open.setOnClickListener(action);
        r.addView(check); r.addView(name); r.addView(open,new LinearLayout.LayoutParams(dp(100),dp(40))); parent.addView(r);
        return check;
    }

    private void openNotificationSettings(){
        if(Build.VERSION.SDK_INT>=33 && !((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled()){
            Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS); i.putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()); startActivity(i);
        }else{
            Intent i=new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS); startActivity(i);
        }
    }

    private boolean accessibilityEnabled(){
        String enabled=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled!=null && enabled.toLowerCase(Locale.ROOT).contains(getPackageName().toLowerCase(Locale.ROOT));
    }
    private boolean notificationEnabled(){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        return Build.VERSION.SDK_INT<33 || nm.areNotificationsEnabled();
    }
    private boolean notificationListenerEnabled(){
        String enabled=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");
        return enabled!=null && enabled.toLowerCase(Locale.ROOT).contains(getPackageName().toLowerCase(Locale.ROOT));
    }
    private void refreshPermissionStatus(){
        if(overlayCheck==null)return;
        setCheck(overlayCheck,Settings.canDrawOverlays(this));
        setCheck(notificationCheck,notificationEnabled() && notificationListenerEnabled());
        boolean accEnabled=accessibilityEnabled();
        boolean accWorking=accEnabled && SubtitleAccessibilityService.isWorking();
        if(!accEnabled){
            accessibilityCheck.setText("! Disabled");
            accessibilityCheck.setTextColor(DANGER);
            accessibilityCheck.setContentDescription("Accessibility disabled");
        }else if(accWorking){
            accessibilityCheck.setText("✓ Working");
            accessibilityCheck.setTextColor(Color.rgb(46,125,50));
            accessibilityCheck.setContentDescription("Accessibility working");
        }else{
            accessibilityCheck.setText("⚠ Not working");
            accessibilityCheck.setTextColor(Color.rgb(245,124,0));
            accessibilityCheck.setContentDescription("Accessibility enabled but not working");
        }
        accessibilityCheck.setTypeface(null,1);
    }
    private void setCheck(TextView v,boolean ok){ v.setText(ok?"✓":"!"); v.setTextColor(ok?Color.rgb(46,125,50):DANGER); v.setTypeface(null,1); v.setContentDescription(ok?"Enabled":"Needs attention"); }

    @Override protected void onResume(){ super.onResume(); if(!firstResume) build(); firstResume=false; refreshPermissionStatus(); statusHandler.removeCallbacks(statusPoll); statusHandler.postDelayed(statusPoll,500); }
    @Override protected void onPause(){ statusHandler.removeCallbacks(statusPoll); super.onPause(); }

    private void addOffsetControls(LinearLayout parent,long initial,boolean second){
        TextView off=smallText("Current offset: "+formatOffset(initial)); parent.addView(off,lp(-1,-2,0,0,0,2));
        SeekBar seek=new SeekBar(this); seek.setMax(120); seek.setProgress(Math.max(0,Math.min(120,(int)(initial/500)+60)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean f){long v=(p-60)*500L;if(second)SettingsStore.offset2(MainActivity.this,v);else SettingsStore.offset(MainActivity.this,v);if(OverlayService.instance!=null)OverlayService.instance.refreshTimingFromSettings();off.setText("Current offset: "+formatOffset(v));}
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        }); parent.addView(seek,lp(-1,-2,0,0,0,4));
    }

    private LinearLayout card(){
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(14),dp(12),dp(14),dp(14));
        GradientDrawable bg=new GradientDrawable(); bg.setColor(surface()); bg.setCornerRadius(dp(12)); bg.setStroke(dp(1),border()); c.setBackground(bg);
        return c;
    }
    private TextView sectionTitle(String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(19);t.setTypeface(null,1);t.setTextColor(dark()?Color.rgb(144,164,255):PRIMARY_DARK);t.setPadding(0,0,0,dp(8));return t;}
    private TextView label(String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(15);t.setTextColor(text());t.setTypeface(null,1);t.setPadding(0,dp(10),0,dp(3));return t;}
    private TextView smallText(String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(12);t.setTextColor(muted());return t;}
    private LinearLayout row(String title,View v){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,dp(4),0,dp(4));TextView t=new TextView(this);t.setText(title);t.setTextSize(14);t.setTextColor(text());t.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));r.addView(t);v.setLayoutParams(new LinearLayout.LayoutParams(dp(170),-2));r.addView(v);return r;}
    private LinearLayout colorRow(String title,boolean bg){
        LinearLayout r=new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(0,dp(5),0,dp(5));
        TextView t=new TextView(this); t.setText(title); t.setTextSize(14); t.setTextColor(text()); t.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1)); r.addView(t);
        int c=bg?SettingsStore.bgColor(this):SettingsStore.textColor(this);
        Button picker=makeButton(colorLabel(c,bg),c==Color.TRANSPARENT?secondaryFill():c,contrastText(c)); picker.setTextSize(13); picker.setMinHeight(dp(42));
        picker.setOnClickListener(v->showColorPicker(bg,picker));
        r.addView(picker,new LinearLayout.LayoutParams(dp(150),dp(42)));
        return r;
    }
    private String colorLabel(int c,boolean bg){ if(bg&&(!SettingsStore.bgEnabled(this)||c==Color.TRANSPARENT))return "Transparent"; return String.format(Locale.US,"#%06X",0xFFFFFF&(c==Color.TRANSPARENT?Color.BLACK:c)); }
    private int contrastText(int c){ if(c==Color.TRANSPARENT)return text(); return ((Color.red(c)*299+Color.green(c)*587+Color.blue(c)*114)>=128000)?Color.BLACK:Color.WHITE; }
    private void showColorPicker(boolean background,Button target){
        final int initial=background?SettingsStore.bgColor(this):SettingsStore.textColor(this);
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20),dp(6),dp(20),0);
        TextView preview=new TextView(this); preview.setText("   Preview / Vorschau   "); preview.setGravity(Gravity.CENTER); preview.setTextSize(18); preview.setPadding(dp(10),dp(14),dp(10),dp(14)); box.addView(preview,lp(-1,-2,0,0,0,10));
        SeekBar r=new SeekBar(this),g=new SeekBar(this),b=new SeekBar(this); r.setMax(255);g.setMax(255);b.setMax(255);r.setProgress(Color.red(initial));g.setProgress(Color.green(initial));b.setProgress(Color.blue(initial));
        box.addView(colorSlider("Red",r)); box.addView(colorSlider("Green",g)); box.addView(colorSlider("Blue",b));
        CheckBox transparent=new CheckBox(this); transparent.setText("Transparent / disable background"); transparent.setVisibility(background?View.VISIBLE:View.GONE); transparent.setChecked(background&&(!SettingsStore.bgEnabled(this)||initial==Color.TRANSPARENT)); box.addView(transparent);
        TextView hex=new TextView(this); hex.setGravity(Gravity.CENTER); hex.setTextSize(13); box.addView(hex,lp(-1,-2,0,4,0,0));
        Runnable update=()->{int c=Color.rgb(r.getProgress(),g.getProgress(),b.getProgress());preview.setBackgroundColor(c);preview.setTextColor(contrastText(c));hex.setText(String.format(Locale.US,"#%02X%02X%02X",Color.red(c),Color.green(c),Color.blue(c)));};
        SeekBar.OnSeekBarChangeListener l=new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){update.run();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}};r.setOnSeekBarChangeListener(l);g.setOnSeekBarChangeListener(l);b.setOnSeekBarChangeListener(l);update.run();
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(background?"Background color":"Subtitle color").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            int c=Color.rgb(r.getProgress(),g.getProgress(),b.getProgress());
            if(background){
                SettingsStore.bgColor(this,c); SettingsStore.bgEnabled(this,!transparent.isChecked());
                boolean disabled=!SettingsStore.bgEnabled(this)||c==Color.TRANSPARENT;
                target.setText(colorLabel(c,true)); target.setBackground(makeButtonBackground(disabled?secondaryFill():c)); target.setTextColor(contrastText(disabled?Color.TRANSPARENT:c));
            }else{
                SettingsStore.textColor(this,c); target.setText(colorLabel(c,false)); target.setBackground(makeButtonBackground(c)); target.setTextColor(contrastText(c));
            }
            dialog.dismiss(); if(OverlayService.instance!=null)OverlayService.instance.refreshAppearance();
        }));
        dialog.show();
    }
    private LinearLayout colorSlider(String label,SeekBar seek){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);TextView t=new TextView(this);t.setText(label);t.setTextColor(text());t.setTextSize(13);t.setLayoutParams(new LinearLayout.LayoutParams(dp(50),-2));r.addView(t);r.addView(seek,new LinearLayout.LayoutParams(0,-2,1));return r;}

    private Spinner spinner(String[] a){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,a));return s;}
    private void setSpinnerFloat(Spinner s,float[] vals,float current){int idx=0;for(int i=0;i<vals.length;i++)if(Math.abs(vals[i]-current)<0.001){idx=i;break;}s.setSelection(idx);s.setOnItemSelectedListener(new SimpleListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){if(p==sizeSpinner){SettingsStore.size(MainActivity.this,vals[pos]);}else{SettingsStore.speed(MainActivity.this,vals[pos]);}if(OverlayService.instance!=null)OverlayService.instance.refreshAppearance();}});}
    private int indexOf(String[] a,String v){for(int i=0;i<a.length;i++)if(a[i].equals(v))return i;return 0;}
    private int indexOfInt(int[] a,int v){for(int i=0;i<a.length;i++)if(a[i]==v)return i;return 0;}
    private String formatOffset(long v){return (v>=0?"+":"")+(v/1000f)+" s";}

    private Button makeButton(String text,int fill,int textColor){
        Button b=new Button(this); b.setText(text); b.setTextColor(textColor); b.setAllCaps(false); b.setMinHeight(dp(44)); b.setPadding(dp(12),0,dp(12),0); b.setBackground(makeButtonBackground(fill));
        return b;
    }
    private StateListDrawable makeButtonBackground(int fill){
        GradientDrawable normal=new GradientDrawable(); normal.setColor(fill); normal.setCornerRadius(dp(9));
        GradientDrawable pressed=new GradientDrawable(); pressed.setColor(adjust(fill,dark()?-28:-22)); pressed.setCornerRadius(dp(9));
        GradientDrawable hover=new GradientDrawable(); hover.setColor(adjust(fill,dark()?18:14)); hover.setCornerRadius(dp(9));
        StateListDrawable states=new StateListDrawable(); states.addState(new int[]{android.R.attr.state_pressed},pressed); states.addState(new int[]{android.R.attr.state_hovered},hover); states.addState(new int[]{android.R.attr.state_focused},hover); states.addState(new int[]{},normal); return states;
    }
    private int adjust(int c,int d){return Color.rgb(clampColor(Color.red(c)+d),clampColor(Color.green(c)+d),clampColor(Color.blue(c)+d));}
    private int clampColor(int v){return Math.max(0,Math.min(255,v));}
    private Button primaryButton(String s){return makeButton(s,PRIMARY,Color.WHITE);}
    private Button secondaryPrimaryButton(String s){return makeButton(s,Color.rgb(0,121,107),Color.WHITE);}
    private Button secondaryButton(String s){return makeButton(s,secondaryFill(),text());}
    private Button outlineButton(String s){
        Button b=new Button(this); b.setText(s); b.setTextColor(DANGER); b.setAllCaps(false); b.setMinHeight(dp(44)); b.setPadding(dp(12),0,dp(12),0);
        GradientDrawable n=new GradientDrawable(); n.setColor(surface()); n.setCornerRadius(dp(9)); n.setStroke(dp(1),DANGER);
        GradientDrawable p=new GradientDrawable(); p.setColor(dark()?Color.rgb(75,45,45):Color.rgb(250,235,235)); p.setCornerRadius(dp(9)); p.setStroke(dp(1),DANGER);
        StateListDrawable st=new StateListDrawable(); st.addState(new int[]{android.R.attr.state_pressed},p); st.addState(new int[]{android.R.attr.state_hovered},p); st.addState(new int[]{},n); b.setBackground(st); return b;
    }
    private Button dangerButton(String s){return makeButton(s,DANGER,Color.WHITE);}
    private LinearLayout.LayoutParams fullButtonParams(){return lp(-1,dp(44),0,4,0,4);}
    private LinearLayout.LayoutParams cardParams(){return lp(-1,-2,0,0,0,12);}
    private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    private void pickFont(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"font/ttf","font/otf","application/x-font-ttf","application/x-font-opentype","application/octet-stream"});startActivityForResult(i,PICK_FONT);}
    private void pick(int request){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/x-subrip");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/x-subrip","text/plain","*/*"});startActivityForResult(i,request);}
    private void chooseLibrary(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);startActivityForResult(i,PICK_LIBRARY);}
    private void openLibrary(boolean second){String tree=SettingsStore.libraryTree(this);if(tree.isEmpty()){chooseLibrary();return;}List<LibraryFile> files=listSrtFiles(Uri.parse(tree));if(files.isEmpty()){new AlertDialog.Builder(this).setTitle("Subtitle Library").setMessage("No .srt files found in this folder.").setPositiveButton("OK",null).show();return;}String[] names=new String[files.size()];for(int i=0;i<files.size();i++)names[i]=files.get(i).name;new AlertDialog.Builder(this).setTitle(second?"Choose secondary subtitle":"Choose primary subtitle").setItems(names,(d,which)->{if(second){uri2=files.get(which).uri;SettingsStore.secondaryUri(this,uri2.toString());status2.setText("Secondary: "+files.get(which).name);}else{uri=files.get(which).uri;SettingsStore.primaryUri(this,uri.toString());status.setText("Primary: "+files.get(which).name);}}).setNegativeButton("Cancel",null).show();}
    private List<LibraryFile> listSrtFiles(Uri tree){List<LibraryFile> out=new ArrayList<>();Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));String[] projection={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE};try(android.database.Cursor c=getContentResolver().query(children,projection,null,null,DocumentsContract.Document.COLUMN_DISPLAY_NAME+" COLLATE NOCASE")){if(c==null)return out;int idCol=c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID),nameCol=c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);while(c.moveToNext()){String name=c.getString(nameCol);if(name!=null&&name.toLowerCase(Locale.ROOT).endsWith(".srt")){out.add(new LibraryFile(name,DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(idCol))));}}}catch(Exception e){Toast.makeText(this,"Could not read library: "+e.getMessage(),Toast.LENGTH_LONG).show();}return out;}
    private static class LibraryFile{final String name;final Uri uri;LibraryFile(String n,Uri u){name=n;uri=u;}}
    private void installCustomFont(Uri source){try(InputStream in=getContentResolver().openInputStream(source)){if(in==null)throw new IOException("Could not open font");String name=source.getLastPathSegment();if(name==null||name.trim().isEmpty())name="custom-font.ttf";name=name.replaceAll("[^A-Za-z0-9._-]","_");if(!name.toLowerCase(Locale.ROOT).endsWith(".ttf")&&!name.toLowerCase(Locale.ROOT).endsWith(".otf"))name+=".ttf";java.io.File out=new java.io.File(getFilesDir(),"font_"+name);try(java.io.FileOutputStream fos=new java.io.FileOutputStream(out)){byte[]buf=new byte[8192];int n;while((n=in.read(buf))!=-1)fos.write(buf,0,n);}android.graphics.Typeface.createFromFile(out);SettingsStore.customFontPath(this,out.getAbsolutePath());SettingsStore.customFontName(this,name);SettingsStore.font(this,"custom");fontStatus.setText("Custom font: "+name);Toast.makeText(this,"Custom font loaded",Toast.LENGTH_SHORT).show();if(OverlayService.instance!=null)OverlayService.instance.refreshAppearance();
        if(fontSpinner!=null && fontSpinner.getAdapter()!=null && fontSpinner.getAdapter().getCount()==5){ fontSpinner.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,fontLabels)); fontSpinner.setSelection(5); }}catch(Exception e){Toast.makeText(this,"Could not load font: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(c!=RESULT_OK||d==null)return;if((r==PICK||r==PICK2)&&d.getData()!=null){Uri selected=d.getData();try{getContentResolver().takePersistableUriPermission(selected,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}if(r==PICK){uri=selected;SettingsStore.primaryUri(this,selected.toString());status.setText("Primary: "+selected.getLastPathSegment());}else{uri2=selected;SettingsStore.secondaryUri(this,selected.toString());status2.setText("Secondary: "+selected.getLastPathSegment());}}else if(r==PICK_FONT&&d.getData()!=null){installCustomFont(d.getData());}else if(r==PICK_LIBRARY&&d.getData()!=null){Uri tree=d.getData();try{getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(Exception ignored){try{getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored2){}}SettingsStore.libraryTree(this,tree.toString());libraryStatus.setText("Library folder: selected");openLibrary(false);}}
    private void start(){if(uri==null){Toast.makeText(this,"Select a primary SRT first",Toast.LENGTH_SHORT).show();return;}if(SettingsStore.doubleSubtitle(this)&&uri2==null){Toast.makeText(this,"Select the secondary SRT or disable dual subtitles",Toast.LENGTH_LONG).show();return;}if(!Settings.canDrawOverlays(this)){Toast.makeText(this,"Allow overlay permission first",Toast.LENGTH_LONG).show();return;}Intent i=new Intent(this,OverlayService.class).setAction(OverlayService.START).putExtra(OverlayService.URI,uri.toString());if(uri2!=null)i.putExtra(OverlayService.URI2,uri2.toString());if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);status.setText("Primary: "+(uri.getLastPathSegment()==null?"selected":uri.getLastPathSegment())+" • overlay running");}
    abstract static class SimpleListener implements AdapterView.OnItemSelectedListener{public void onNothingSelected(AdapterView<?> p){}}
}
