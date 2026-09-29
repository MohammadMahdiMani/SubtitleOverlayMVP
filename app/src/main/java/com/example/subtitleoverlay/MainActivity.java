package com.example.subtitleoverlay;

import android.app.*;import android.content.*;import android.graphics.Color;import android.net.Uri;import android.os.*;import android.provider.DocumentsContract;import android.provider.Settings;import android.view.*;import android.widget.*;import java.util.*;

public class MainActivity extends Activity{
    static final int PICK=10, PICK_LIBRARY=11; Uri uri; TextView status,libraryStatus; Spinner sizeSpinner,fontSpinner,speedSpinner; LinearLayout root;
    final int[] colors={Color.WHITE,Color.YELLOW,Color.CYAN,Color.GREEN,Color.RED};
    final int[] bgs={Color.argb(180,0,0,0),Color.argb(180,255,255,255),Color.argb(180,0,60,120),Color.TRANSPARENT};
    final String[] fonts={"sans-serif","sans-serif-medium","sans-serif-condensed","serif","monospace"};
    final String[] fontLabels={"Sans","Sans Medium","Condensed","Serif","Monospace"};
    final float[] sizes={16,20,24,28,32,40,48,56,64};
    final float[] speeds={0.75f,0.9f,1f,1.1f,1.25f,1.5f};

    @Override public void onCreate(Bundle b){super.onCreate(b);build();}
    private void build(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(28,28,28,28);
        TextView h=new TextView(this);h.setText("Subtitle Overlay MVP v0.3.2");h.setTextSize(24);root.addView(h);
        status=new TextView(this);status.setText("Select an SRT file.");status.setPadding(0,12,0,12);root.addView(status);
        Button p=btn("Select SRT");p.setOnClickListener(v->pick());root.addView(p);
        Button chooseLib=btn("Choose Subtitle Library Folder");chooseLib.setOnClickListener(v->chooseLibrary());root.addView(chooseLib);
        Button openLib=btn("Open Subtitle Library");openLib.setOnClickListener(v->openLibrary());root.addView(openLib);
        libraryStatus=new TextView(this);libraryStatus.setText(SettingsStore.libraryTree(this).isEmpty()?"Library: not selected":"Library folder is selected");libraryStatus.setPadding(0,4,0,10);root.addView(libraryStatus);
        root.addView(label("Subtitle appearance"));
        sizeSpinner=spinner(new String[]{"16 px","20 px","24 px","28 px","32 px","40 px","48 px","56 px","64 px"});setSpinnerFloat(sizeSpinner,sizes,SettingsStore.size(this));root.addView(row("Font size",sizeSpinner));
        fontSpinner=spinner(fontLabels);int fi=indexOf(fonts,SettingsStore.font(this));fontSpinner.setSelection(fi);fontSpinner.setOnItemSelectedListener(new SimpleListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){SettingsStore.font(MainActivity.this,fonts[pos]);} });root.addView(row("Font family",fontSpinner));
        root.addView(colorRow("Font color",false));root.addView(colorRow("Background",true));
        speedSpinner=spinner(new String[]{"0.75x","0.9x","1.0x","1.1x","1.25x","1.5x"});setSpinnerFloat(speedSpinner,speeds,SettingsStore.speed(this));root.addView(row("Subtitle timing speed",speedSpinner));
        TextView off=new TextView(this);off.setText("Sync offset: "+formatOffset(SettingsStore.offset(this)));off.setPadding(0,8,0,8);root.addView(off);
        SeekBar seek=new SeekBar(this);seek.setMax(120);seek.setProgress((int)(SettingsStore.offset(this)/500)+60);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){long v=(p-60)*500L;SettingsStore.offset(MainActivity.this,v);off.setText("Sync offset: "+formatOffset(v));}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});root.addView(seek);
        Button overlay=btn("Allow overlay");overlay.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))));root.addView(overlay);
        Button access=btn("Enable Accessibility sync");access.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));root.addView(access);
        Button media=btn("Enable Media Session auto-sync");media.setOnClickListener(v->startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));root.addView(media);
        Button s=btn("START OVERLAY");s.setOnClickListener(v->start());root.addView(s);
        Button stop=btn("STOP OVERLAY");stop.setOnClickListener(v->stopService(new Intent(this,OverlayService.class)));root.addView(stop);
        TextView info=new TextView(this);info.setText("Auto-sync uses Android MediaSession when the browser exposes it. Accessibility is also used to detect player time and seeks. The overlay never reads or modifies the video stream.");info.setPadding(0,12,0,0);root.addView(info);
        ScrollView sv=new ScrollView(this);sv.addView(root);setContentView(sv);
    }
    private TextView label(String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(18);t.setPadding(0,18,0,4);return t;}
    private LinearLayout row(String title,View v){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);TextView t=new TextView(this);t.setText(title);t.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));r.addView(t);r.addView(v);return r;}
    private LinearLayout colorRow(String title,boolean bg){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);TextView t=new TextView(this);t.setText(title);t.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));r.addView(t);LinearLayout colorsRow=new LinearLayout(this);int[] arr=bg?bgs:colors;for(int c:arr){Button b=new Button(this);b.setText(c==Color.TRANSPARENT?"None":" ");b.setBackgroundColor(c==Color.TRANSPARENT?Color.LTGRAY:c);b.setMinWidth(60);b.setOnClickListener(v->{if(bg){SettingsStore.bgColor(this,c);SettingsStore.bgEnabled(this,c!=Color.TRANSPARENT);}else SettingsStore.textColor(this,c);});colorsRow.addView(b);}r.addView(colorsRow);return r;}
    private Spinner spinner(String[] a){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,a));return s;}
    private void setSpinnerFloat(Spinner s,float[] vals,float current){int idx=0;for(int i=0;i<vals.length;i++)if(Math.abs(vals[i]-current)<0.001){idx=i;break;}s.setSelection(idx);s.setOnItemSelectedListener(new SimpleListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){if(p==sizeSpinner)SettingsStore.size(MainActivity.this,vals[pos]);else SettingsStore.speed(MainActivity.this,vals[pos]);}});}
    private int indexOf(String[] a,String v){for(int i=0;i<a.length;i++)if(a[i].equals(v))return i;return 0;}
    private String formatOffset(long v){return (v>=0?"+":"")+(v/1000f)+" s";}
    private Button btn(String s){Button b=new Button(this);b.setText(s);return b;}
    private void pick(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/x-subrip");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/x-subrip","text/plain","*/*"});startActivityForResult(i,PICK);}

    private void chooseLibrary(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);startActivityForResult(i,PICK_LIBRARY);
    }
    private void openLibrary(){
        String tree=SettingsStore.libraryTree(this); if(tree.isEmpty()){chooseLibrary();return;}
        Uri rootUri=Uri.parse(tree); List<LibraryFile> files=listSrtFiles(rootUri);
        if(files.isEmpty()){new AlertDialog.Builder(this).setTitle("Subtitle Library").setMessage("No .srt files found in this folder.").setPositiveButton("OK",null).show();return;}
        String[] names=new String[files.size()];for(int i=0;i<files.size();i++)names[i]=files.get(i).name;
        new AlertDialog.Builder(this).setTitle("Choose subtitle").setItems(names,(d,which)->{uri=files.get(which).uri;status.setText("Selected: "+files.get(which).name);}).setNegativeButton("Cancel",null).show();
    }
    private List<LibraryFile> listSrtFiles(Uri tree){
        List<LibraryFile> out=new ArrayList<>();
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
        String[] projection={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE};
        try(android.database.Cursor c=getContentResolver().query(children,projection,null,null,DocumentsContract.Document.COLUMN_DISPLAY_NAME+" COLLATE NOCASE")){
            if(c==null)return out;
            int idCol=c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID), nameCol=c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME), mimeCol=c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
            while(c.moveToNext()){
                String name=c.getString(nameCol);String mime=c.getString(mimeCol);
                if(name!=null && name.toLowerCase(Locale.ROOT).endsWith(".srt")){
                    String id=c.getString(idCol);Uri u=DocumentsContract.buildDocumentUriUsingTree(tree,id);out.add(new LibraryFile(name,u));
                }
            }
        }catch(Exception e){Toast.makeText(this,"Could not read library: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        return out;
    }
    private static class LibraryFile{final String name;final Uri uri;LibraryFile(String n,Uri u){name=n;uri=u;}}

    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(c!=RESULT_OK||d==null)return;
        if(r==PICK&&d.getData()!=null){uri=d.getData();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}status.setText("Selected: "+uri.getLastPathSegment());}
        else if(r==PICK_LIBRARY&&d.getData()!=null){Uri tree=d.getData();try{getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(Exception ignored){try{getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored2){}}SettingsStore.libraryTree(this,tree.toString());libraryStatus.setText("Library folder selected");openLibrary();}
    }
    private void start(){if(uri==null){Toast.makeText(this,"Select an SRT first",Toast.LENGTH_SHORT).show();return;}if(!Settings.canDrawOverlays(this)){Toast.makeText(this,"Allow overlay first",Toast.LENGTH_LONG).show();return;}Intent i=new Intent(this,OverlayService.class).setAction(OverlayService.START).putExtra(OverlayService.URI,uri.toString());if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);status.setText("Overlay running");}
    abstract static class SimpleListener implements AdapterView.OnItemSelectedListener{public void onNothingSelected(AdapterView<?> p){}}
}
