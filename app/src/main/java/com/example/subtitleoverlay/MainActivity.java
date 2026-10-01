package com.example.subtitleoverlay;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
    LinearLayout root;
    Spinner sizeSpinner, fontSpinner, speedSpinner, positionSpinner, bgModeSpinner, bgPaddingSpinner, bgOpacitySpinner;

    final int[] colors={Color.WHITE,Color.YELLOW,Color.CYAN,Color.GREEN,Color.RED};
    final int[] bgs={Color.argb(180,0,0,0),Color.argb(180,255,255,255),Color.argb(180,0,60,120),Color.TRANSPARENT};
    final String[] fontLabels={"Sans","Sans Medium","Condensed","Serif","Monospace","Custom"};
    final String[] fontVals={"sans-serif","sans-serif-medium","sans-serif-condensed","serif","monospace","custom"};
    final float[] sizes={16,20,24,28,32,40,48,56,64};
    final float[] speeds={0.75f,0.9f,1f,1.1f,1.25f,1.5f};

    final int PRIMARY=Color.rgb(63,81,181);
    final int PRIMARY_DARK=Color.rgb(48,63,159);
    final int SECONDARY=Color.rgb(236,239,241);
    final int DANGER=Color.rgb(198,40,40);
    final int TEXT=Color.rgb(35,35,35);

    @Override public void onCreate(Bundle b){ super.onCreate(b); build(); }

    private void build(){
        ScrollView scroll=new ScrollView(this);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(18),dp(18),dp(28));
        root.setBackgroundColor(Color.rgb(248,248,248));

        TextView title=new TextView(this);
        title.setText("Subtitle Overlay"); title.setTextSize(26); title.setTextColor(TEXT); title.setTypeface(null,1);
        root.addView(title,lp(-1,-2,0,0,0,4));
        TextView version=new TextView(this);
        version.setText("v1.1.1 • stable sync + dual subtitles"); version.setTextSize(13); version.setTextColor(Color.DKGRAY);
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
        status=new TextView(this); status.setText("No primary subtitle selected"); status.setTextColor(TEXT); status.setPadding(0,dp(4),0,dp(10));
        card.addView(status);
        Button select=primaryButton("Select Primary SRT"); select.setOnClickListener(v->pick(PICK)); card.addView(select,fullButtonParams());
        Button library=secondaryButton("Choose from Subtitle Library"); library.setOnClickListener(v->openLibrary(false)); card.addView(library,fullButtonParams());
        Button folder=secondaryButton("Set / Change Library Folder"); folder.setOnClickListener(v->chooseLibrary()); card.addView(folder,fullButtonParams());
        libraryStatus=new TextView(this);
        libraryStatus.setText(SettingsStore.libraryTree(this).isEmpty()?"Library folder: not selected":"Library folder: selected");
        libraryStatus.setTextSize(12); libraryStatus.setTextColor(Color.DKGRAY); libraryStatus.setPadding(0,dp(6),0,0);
        card.addView(libraryStatus);
        root.addView(card,cardParams());
    }

    private void buildSecondarySection(){
        LinearLayout card=card();
        card.addView(sectionTitle("2. Secondary subtitle"));
        status2=new TextView(this); status2.setText("No secondary subtitle selected"); status2.setTextColor(TEXT); status2.setPadding(0,dp(4),0,dp(10));
        card.addView(status2);
        Button select=secondaryPrimaryButton("Select Secondary SRT"); select.setOnClickListener(v->pick(PICK2)); card.addView(select,fullButtonParams());
        Button library=secondaryButton("Choose Secondary from Library"); library.setOnClickListener(v->openLibrary(true)); card.addView(library,fullButtonParams());
        Button clear=outlineButton("Clear Secondary Subtitle"); clear.setOnClickListener(v->{uri2=null;status2.setText("No secondary subtitle selected");}); card.addView(clear,fullButtonParams());
        CheckBox dual=new CheckBox(this);
        dual.setText("Enable dual subtitles"); dual.setTextSize(16); dual.setTextColor(TEXT); dual.setChecked(SettingsStore.doubleSubtitle(this));
        dual.setOnCheckedChangeListener((b,checked)->SettingsStore.doubleSubtitle(this,checked));
        card.addView(dual,lp(-1,-2,0,8,0,0));
        CheckBox reverse=new CheckBox(this);
        reverse.setText("Show secondary subtitle above primary"); reverse.setTextSize(14); reverse.setTextColor(TEXT); reverse.setChecked(SettingsStore.secondaryFirst(this));
        reverse.setOnCheckedChangeListener((b,checked)->SettingsStore.secondaryFirst(this,checked));
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
        fontSpinner.setOnItemSelectedListener(new SimpleListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){SettingsStore.font(MainActivity.this,fontVals[Math.min(pos,fontVals.length-1)]);}});
        card.addView(row("Font family",fontSpinner));
        Button custom=secondaryButton("Upload TTF / OTF Font"); custom.setOnClickListener(v->pickFont()); card.addView(custom,fullButtonParams());
        fontStatus=new TextView(this); fontStatus.setText(SettingsStore.hasCustomFont(this)?"Custom font: "+SettingsStore.customFontName(this):"Custom font: none");
        fontStatus.setTextSize(12); fontStatus.setTextColor(Color.DKGRAY); card.addView(fontStatus,lp(-1,-2,0,4,0,4));
        card.addView(colorRow("Font color",false));
        card.addView(row("Subtitle position",positionSpinner()));
        root.addView(card,cardParams());
    }

    private Spinner positionSpinner(){
        positionSpinner=spinner(new String[]{"Bottom","Lower","Center","Upper","Top"});
        positionSpinner.setSelection(SettingsStore.position(this));
        positionSpinner.setOnItemSelectedListener(new SimpleListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){SettingsStore.position(MainActivity.this,pos);}});
        return positionSpinner;
    }

    private void buildBackgroundSection(){
        LinearLayout card=card();
        card.addView(sectionTitle("4. Background"));
        card.addView(colorRow("Background color",true));
        bgModeSpinner=spinner(new String[]{"Full Width","Fit to Subtitle","No Background"}); bgModeSpinner.setSelection(SettingsStore.bgMode(this));
        bgModeSpinner.setOnItemSelectedListener(new SimpleListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){SettingsStore.bgMode(MainActivity.this,pos);}}); card.addView(row("Background mode",bgModeSpinner));
        bgPaddingSpinner=spinner(new String[]{"0 px","2 px","4 px","6 px","8 px","12 px","16 px"});
        int bp=indexOfInt(new int[]{0,2,4,6,8,12,16},SettingsStore.bgPadding(this)); bgPaddingSpinner.setSelection(bp);
        bgPaddingSpinner.setOnItemSelectedListener(new SimpleListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){SettingsStore.bgPadding(MainActivity.this,Integer.parseInt(p.getItemAtPosition(pos).toString().replace(" px","")));}}); card.addView(row("Padding",bgPaddingSpinner));
        bgOpacitySpinner=spinner(new String[]{"30%","50%","70%","85%","100%"});
        int bo=indexOfInt(new int[]{30,50,70,85,100},SettingsStore.bgOpacity(this)); bgOpacitySpinner.setSelection(bo);
        bgOpacitySpinner.setOnItemSelectedListener(new SimpleListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){SettingsStore.bgOpacity(MainActivity.this,Integer.parseInt(p.getItemAtPosition(pos).toString().replace("%","")));}}); card.addView(row("Opacity",bgOpacitySpinner));
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
        Button overlay=primaryButton("Allow Overlay Permission"); overlay.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())))); card.addView(overlay,fullButtonParams());
        Button access=secondaryButton("Enable Accessibility Sync"); access.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))); card.addView(access,fullButtonParams());
        Button media=secondaryButton("Enable Media Session Auto-Sync"); media.setOnClickListener(v->startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))); card.addView(media,fullButtonParams());
        Space sp=new Space(this); card.addView(sp,lp(1,dp(8),0,8,0,8));
        Button start=primaryButton("START OVERLAY"); start.setTextSize(16); start.setOnClickListener(v->start()); card.addView(start,fullButtonParams());
        Button stop=dangerButton("STOP OVERLAY"); stop.setOnClickListener(v->stopService(new Intent(this,OverlayService.class))); card.addView(stop,fullButtonParams());
        TextView info=smallText("The overlay draws your SRT subtitles on top of the video. It does not read or modify the video stream.");
        card.addView(info,lp(-1,-2,0,10,0,0));
        root.addView(card,cardParams());
    }

    private void addOffsetControls(LinearLayout parent,long initial,boolean second){
        TextView off=smallText("Current offset: "+formatOffset(initial)); parent.addView(off,lp(-1,-2,0,0,0,2));
        SeekBar seek=new SeekBar(this); seek.setMax(120); seek.setProgress(Math.max(0,Math.min(120,(int)(initial/500)+60)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean f){long v=(p-60)*500L;if(second)SettingsStore.offset2(MainActivity.this,v);else SettingsStore.offset(MainActivity.this,v);off.setText("Current offset: "+formatOffset(v));}
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        }); parent.addView(seek,lp(-1,-2,0,0,0,4));
    }

    private LinearLayout card(){
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(14),dp(12),dp(14),dp(14));
        GradientDrawable bg=new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(12)); bg.setStroke(dp(1),Color.rgb(225,225,225)); c.setBackground(bg);
        return c;
    }
    private TextView sectionTitle(String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(19);t.setTypeface(null,1);t.setTextColor(PRIMARY_DARK);t.setPadding(0,0,0,dp(8));return t;}
    private TextView label(String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(15);t.setTextColor(TEXT);t.setTypeface(null,1);t.setPadding(0,dp(10),0,dp(3));return t;}
    private TextView smallText(String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(12);t.setTextColor(Color.DKGRAY);return t;}
    private LinearLayout row(String title,View v){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,dp(4),0,dp(4));TextView t=new TextView(this);t.setText(title);t.setTextSize(14);t.setTextColor(TEXT);t.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));r.addView(t);v.setLayoutParams(new LinearLayout.LayoutParams(dp(170),-2));r.addView(v);return r;}
    private LinearLayout colorRow(String title,boolean bg){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,dp(5),0,dp(5));TextView t=new TextView(this);t.setText(title);t.setTextSize(14);t.setTextColor(TEXT);t.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));r.addView(t);LinearLayout colorsRow=new LinearLayout(this);int[] arr=bg?bgs:colors;for(int c:arr){TextView sw=new TextView(this);sw.setText(c==Color.TRANSPARENT?"×":"");sw.setTextSize(16);sw.setGravity(Gravity.CENTER);sw.setTextColor(c==Color.WHITE?Color.BLACK:Color.WHITE);sw.setBackgroundColor(c==Color.TRANSPARENT?Color.LTGRAY:c);sw.setOnClickListener(v->{if(bg){SettingsStore.bgColor(this,c);SettingsStore.bgEnabled(this,c!=Color.TRANSPARENT);}else SettingsStore.textColor(this,c);});LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(34),dp(34));sp.setMargins(dp(3),0,dp(3),0);colorsRow.addView(sw,sp);}r.addView(colorsRow);return r;}

    private Spinner spinner(String[] a){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,a));return s;}
    private void setSpinnerFloat(Spinner s,float[] vals,float current){int idx=0;for(int i=0;i<vals.length;i++)if(Math.abs(vals[i]-current)<0.001){idx=i;break;}s.setSelection(idx);s.setOnItemSelectedListener(new SimpleListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){if(p==sizeSpinner)SettingsStore.size(MainActivity.this,vals[pos]);else SettingsStore.speed(MainActivity.this,vals[pos]);}});}
    private int indexOf(String[] a,String v){for(int i=0;i<a.length;i++)if(a[i].equals(v))return i;return 0;}
    private int indexOfInt(int[] a,int v){for(int i=0;i<a.length;i++)if(a[i]==v)return i;return 0;}
    private String formatOffset(long v){return (v>=0?"+":"")+(v/1000f)+" s";}

    private Button makeButton(String text,int fill,int textColor){Button b=new Button(this);b.setText(text);b.setTextColor(textColor);b.setAllCaps(false);b.setMinHeight(dp(44));b.setPadding(dp(12),0,dp(12),0);GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(9));b.setBackground(d);return b;}
    private Button primaryButton(String s){return makeButton(s,PRIMARY,Color.WHITE);}
    private Button secondaryPrimaryButton(String s){return makeButton(s,Color.rgb(0,121,107),Color.WHITE);}
    private Button secondaryButton(String s){return makeButton(s,SECONDARY,TEXT);}
    private Button outlineButton(String s){Button b=makeButton(s,Color.WHITE,DANGER);GradientDrawable d=new GradientDrawable();d.setColor(Color.WHITE);d.setCornerRadius(dp(9));d.setStroke(dp(1),DANGER);b.setBackground(d);return b;}
    private Button dangerButton(String s){return makeButton(s,DANGER,Color.WHITE);}
    private LinearLayout.LayoutParams fullButtonParams(){return lp(-1,dp(44),0,4,0,4);}
    private LinearLayout.LayoutParams cardParams(){return lp(-1,-2,0,0,0,12);}
    private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    private void pickFont(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"font/ttf","font/otf","application/x-font-ttf","application/x-font-opentype","application/octet-stream"});startActivityForResult(i,PICK_FONT);}
    private void pick(int request){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/x-subrip");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/x-subrip","text/plain","*/*"});startActivityForResult(i,request);}
    private void chooseLibrary(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);startActivityForResult(i,PICK_LIBRARY);}
    private void openLibrary(boolean second){String tree=SettingsStore.libraryTree(this);if(tree.isEmpty()){chooseLibrary();return;}List<LibraryFile> files=listSrtFiles(Uri.parse(tree));if(files.isEmpty()){new AlertDialog.Builder(this).setTitle("Subtitle Library").setMessage("No .srt files found in this folder.").setPositiveButton("OK",null).show();return;}String[] names=new String[files.size()];for(int i=0;i<files.size();i++)names[i]=files.get(i).name;new AlertDialog.Builder(this).setTitle(second?"Choose secondary subtitle":"Choose primary subtitle").setItems(names,(d,which)->{if(second){uri2=files.get(which).uri;status2.setText("Secondary: "+files.get(which).name);}else{uri=files.get(which).uri;status.setText("Primary: "+files.get(which).name);}}).setNegativeButton("Cancel",null).show();}
    private List<LibraryFile> listSrtFiles(Uri tree){List<LibraryFile> out=new ArrayList<>();Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));String[] projection={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE};try(android.database.Cursor c=getContentResolver().query(children,projection,null,null,DocumentsContract.Document.COLUMN_DISPLAY_NAME+" COLLATE NOCASE")){if(c==null)return out;int idCol=c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID),nameCol=c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);while(c.moveToNext()){String name=c.getString(nameCol);if(name!=null&&name.toLowerCase(Locale.ROOT).endsWith(".srt")){out.add(new LibraryFile(name,DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(idCol))));}}}catch(Exception e){Toast.makeText(this,"Could not read library: "+e.getMessage(),Toast.LENGTH_LONG).show();}return out;}
    private static class LibraryFile{final String name;final Uri uri;LibraryFile(String n,Uri u){name=n;uri=u;}}
    private void installCustomFont(Uri source){try(InputStream in=getContentResolver().openInputStream(source)){if(in==null)throw new IOException("Could not open font");String name=source.getLastPathSegment();if(name==null||name.trim().isEmpty())name="custom-font.ttf";name=name.replaceAll("[^A-Za-z0-9._-]","_");if(!name.toLowerCase(Locale.ROOT).endsWith(".ttf")&&!name.toLowerCase(Locale.ROOT).endsWith(".otf"))name+=".ttf";java.io.File out=new java.io.File(getFilesDir(),"font_"+name);try(java.io.FileOutputStream fos=new java.io.FileOutputStream(out)){byte[]buf=new byte[8192];int n;while((n=in.read(buf))!=-1)fos.write(buf,0,n);}android.graphics.Typeface.createFromFile(out);SettingsStore.customFontPath(this,out.getAbsolutePath());SettingsStore.customFontName(this,name);SettingsStore.font(this,"custom");fontStatus.setText("Custom font: "+name);Toast.makeText(this,"Custom font loaded",Toast.LENGTH_SHORT).show();recreate();}catch(Exception e){Toast.makeText(this,"Could not load font: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(c!=RESULT_OK||d==null)return;if((r==PICK||r==PICK2)&&d.getData()!=null){Uri selected=d.getData();try{getContentResolver().takePersistableUriPermission(selected,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}if(r==PICK){uri=selected;status.setText("Primary: "+selected.getLastPathSegment());}else{uri2=selected;status2.setText("Secondary: "+selected.getLastPathSegment());}}else if(r==PICK_FONT&&d.getData()!=null){installCustomFont(d.getData());}else if(r==PICK_LIBRARY&&d.getData()!=null){Uri tree=d.getData();try{getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(Exception ignored){try{getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored2){}}SettingsStore.libraryTree(this,tree.toString());libraryStatus.setText("Library folder: selected");openLibrary(false);}}
    private void start(){if(uri==null){Toast.makeText(this,"Select a primary SRT first",Toast.LENGTH_SHORT).show();return;}if(SettingsStore.doubleSubtitle(this)&&uri2==null){Toast.makeText(this,"Select the secondary SRT or disable dual subtitles",Toast.LENGTH_LONG).show();return;}if(!Settings.canDrawOverlays(this)){Toast.makeText(this,"Allow overlay permission first",Toast.LENGTH_LONG).show();return;}Intent i=new Intent(this,OverlayService.class).setAction(OverlayService.START).putExtra(OverlayService.URI,uri.toString());if(uri2!=null)i.putExtra(OverlayService.URI2,uri2.toString());if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);status.setText("Primary: "+(uri.getLastPathSegment()==null?"selected":uri.getLastPathSegment())+" • overlay running");}
    abstract static class SimpleListener implements AdapterView.OnItemSelectedListener{public void onNothingSelected(AdapterView<?> p){}}
}
