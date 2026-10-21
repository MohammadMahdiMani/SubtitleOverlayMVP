package com.example.subtitleoverlay;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.text.InputType;
import android.widget.*;
import java.io.InputStream;
import java.util.*;

public class OverlayService extends Service {
    public static final String START="START", URI="uri", URI2="uri2";
    static volatile OverlayService instance;
    private WindowManager wm;
    private TextView sub;
    private ViewGroup ctl;
    private TextView info;
    private List<SrtCue> cues=new ArrayList<>(), cues2=new ArrayList<>();
    private final Handler h=new Handler(Looper.getMainLooper());
    private long offset, offset2;
    // Last trustworthy video-time anchor used only when both external sources go stale.
    private long fallbackPosition=0, fallbackUpdatedAt=0;
    private boolean fallbackHasAnchor=false, fallbackPlaying=true;
    private boolean run=true;
    private boolean manualMode;
    private int sourceMode;
    private long manualPosition, manualStartedAt;
    private float manualSpeed=1f;
    private long controlsHideAt;
    private Button offsetMinusButton, offsetPlusButton, offsetResetButton, speedButton;
    private static volatile long mediaPosition=-1, mediaDuration=-1, mediaUpdatedAt;
    private static volatile float mediaSpeed=1f;
    private static volatile boolean mediaPlaying=false;
    private static volatile long accessibilityPosition=-1, accessibilityUpdatedAt;
    private static volatile boolean accessibilityPlaying=true;

    private static final int[] TEXT_COLORS={Color.WHITE,Color.YELLOW,Color.CYAN,Color.GREEN,Color.RED};
    private static final int[] BG_COLORS={Color.argb(180,0,0,0),Color.argb(180,255,255,255),Color.argb(180,0,60,120),Color.TRANSPARENT};
    private static final String[] FONTS={"sans-serif","sans-serif-medium","sans-serif-condensed","serif","monospace","custom"};

    public static synchronized void setMediaPosition(long ms,float speed,long duration,String pkg,boolean playing){
        if(ms<0)return;
        float validSpeed=(speed>0f&&speed<4f)?speed:1f;
        mediaPosition=ms;
        mediaSpeed=validSpeed;
        if(duration>0)mediaDuration=duration;
        mediaPlaying=playing;
        mediaUpdatedAt=SystemClock.elapsedRealtime();
    }

    public static synchronized void setAccessibilityPosition(long ms){
        if(ms<0)return;
        accessibilityPosition=ms;
        accessibilityUpdatedAt=SystemClock.elapsedRealtime();
    }
    public static void setAccessibilityPlaying(boolean playing){accessibilityPlaying=playing;}

    @Override public void onCreate(){
        super.onCreate();instance=this;
        NotificationChannel c=new NotificationChannel("sub","Subtitle Overlay",NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(c);
        startForeground(9,new Notification.Builder(this,"sub").setContentTitle("Subtitle Overlay running").setSmallIcon(android.R.drawable.ic_media_play).setOngoing(true).build());
        offset=SettingsStore.offset(this); offset2=SettingsStore.offset2(this);
        manualMode=SettingsStore.manualMode(this);
        sourceMode=SettingsStore.sourceMode(this);
        manualPosition=SettingsStore.manualPosition(this);
        manualSpeed=SettingsStore.manualSpeed(this);
        manualStartedAt=SystemClock.elapsedRealtime();
        fallbackUpdatedAt=SystemClock.elapsedRealtime();
    }

    @Override public int onStartCommand(Intent i,int f,int id){
        // START_STICKY may restart the service with a null Intent. Restore the selected
        // subtitle instead of leaving a foreground service alive with a blank overlay.
        if(i==null || START.equals(i.getAction())){
            String primary = i==null ? SettingsStore.primaryUri(this) : i.getStringExtra(URI);
            String secondary = i==null ? SettingsStore.secondaryUri(this) : i.getStringExtra(URI2);
            if(primary==null || primary.isEmpty()) return START_STICKY;
            try(InputStream in=getContentResolver().openInputStream(Uri.parse(primary))){
                if(in==null) throw new IllegalStateException("Could not open primary subtitle");
                cues=SrtParser.parse(in);
                cues2.clear();
                if(secondary!=null&&!secondary.isEmpty()) try(InputStream in2=getContentResolver().openInputStream(Uri.parse(secondary))){ if(in2!=null)cues2=SrtParser.parse(in2); }
                build();
            }catch(Exception e){Toast.makeText(this,"SRT error: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        }
        return START_STICKY;
    }

    private void build(){
        h.removeCallbacks(tick);
        remove();lastText="";wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        sub=new TextView(this);
        sub.setTextColor(SettingsStore.textColor(this));
        sub.setTextSize(SettingsStore.size(this));
        sub.setTypeface(SettingsStore.typeface(this,SettingsStore.font(this)));
        sub.setGravity(Gravity.CENTER);
        sub.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        sub.setShadowLayer(8,0,2,Color.BLACK);
        sub.setPadding(dp(SettingsStore.bgPadding(this)),dp(SettingsStore.bgPadding(this)),dp(SettingsStore.bgPadding(this)),dp(SettingsStore.bgPadding(this)));
        sub.setMaxWidth((int)(screenWidth()*0.92f));
        sub.setMinWidth(0);
        applyBackground(false);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;p.y=positionY();wm.addView(sub,p);
        sub.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN){touchControls();return true;}return true;});
        setSubtitleTouchEnabled(false);

        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(4,2,4,2);
        info=button("AUTO 00:00");row.addView(info);
        add(row,"Sync",v->showSyncDialog());
        add(row,"A−",v->changeSize(-2));add(row,"A+",v->changeSize(2));add(row,"Color",v->cycleTextColor());add(row,"BG",v->cycleBg());add(row,"Font",v->cycleFont());add(row,"Pos",v->cyclePosition());
        add(row,"BG Mode",v->cycleBgMode());add(row,"Pad",v->cycleBgPadding());add(row,"Opacity",v->cycleBgOpacity());
        offsetMinusButton=add(row,"−0.5s",v->changeOffset(-500)); offsetPlusButton=add(row,"+0.5s",v->changeOffset(500)); offsetResetButton=add(row,"Offset 0",v->resetOffset()); speedButton=add(row,"Speed",v->cycleSpeed()); updateManualTimingButtons();
        add(row,run?"Pause":"Play",v->{toggleRun((Button)v);});
        add(row,"✕",v->stopSelf());scroll.addView(row);ctl=scroll;
        WindowManager.LayoutParams q=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        q.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;q.y=25;wm.addView(ctl,q);
        controlsHideAt=SystemClock.elapsedRealtime()+3000;touchControls();h.post(tick);
    }

    private float screenWidth(){android.util.DisplayMetrics dm=new android.util.DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(dm);return dm.widthPixels;}
    private int positionY(){
        android.util.DisplayMetrics dm=new android.util.DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(dm);float H=dm.heightPixels;
        switch(SettingsStore.position(this)){case 1:return (int)(H*.67f);case 2:return (int)(H*.45f);case 3:return (int)(H*.25f);case 4:return dp(42);default:return (int)(H*.82f);}
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private Button button(String s){
        Button b=new Button(this);b.setText(s);b.setTextSize(10);b.setMinHeight(1);b.setMinWidth(1);
        GradientDrawable n=new GradientDrawable();n.setColor(Color.argb(235,40,40,40));n.setCornerRadius(dp(6));
        GradientDrawable p=new GradientDrawable();p.setColor(Color.argb(255,75,75,75));p.setCornerRadius(dp(6));
        android.graphics.drawable.StateListDrawable st=new android.graphics.drawable.StateListDrawable();st.addState(new int[]{android.R.attr.state_pressed},p);st.addState(new int[]{android.R.attr.state_hovered},p);st.addState(new int[]{},n);b.setBackground(st);b.setTextColor(Color.WHITE);
        b.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN)touchControls();return false;});return b;
    }
    private Button add(LinearLayout row,String text,View.OnClickListener l){Button b=button(text);b.setOnClickListener(l);row.addView(b);return b;}
    private void touchControls(){controlsHideAt=SystemClock.elapsedRealtime()+3000;if(ctl!=null)ctl.setVisibility(View.VISIBLE);setSubtitleTouchEnabled(false);}
    private void updateControlsVisibility(){if(ctl!=null&&SystemClock.elapsedRealtime()>=controlsHideAt){ctl.setVisibility(View.GONE);setSubtitleTouchEnabled(true);}}
    private void setSubtitleTouchEnabled(boolean enabled){if(sub==null||wm==null)return;try{WindowManager.LayoutParams lp=(WindowManager.LayoutParams)sub.getLayoutParams();int flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;if(!enabled)flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;lp.flags=flags;wm.updateViewLayout(sub,lp);}catch(Exception ignored){}}

    public void refreshAppearance(){
        if(sub==null)return;
        sub.setTextColor(SettingsStore.textColor(this));
        sub.setTextSize(SettingsStore.size(this));
        sub.setTypeface(SettingsStore.typeface(this,SettingsStore.font(this)));
        int pad=dp(SettingsStore.bgPadding(this));
        sub.setPadding(pad,pad,pad,pad);
        applyBackground(false);
        refreshLayout();
    }

    private void changeSize(int d){float s=Math.max(12,Math.min(72,SettingsStore.size(this)+d));SettingsStore.size(this,s);if(sub!=null){sub.setTextSize(s);refreshLayout();}touchControls();}
    private void cycleTextColor(){int c=SettingsStore.textColor(this);int next=TEXT_COLORS[0];for(int i=0;i<TEXT_COLORS.length;i++)if(TEXT_COLORS[i]==c){next=TEXT_COLORS[(i+1)%TEXT_COLORS.length];break;}SettingsStore.textColor(this,next);if(sub!=null)sub.setTextColor(next);touchControls();}
    private void cycleBg(){int c=SettingsStore.bgColor(this);int next=BG_COLORS[0];for(int i=0;i<BG_COLORS.length;i++)if(BG_COLORS[i]==c){next=BG_COLORS[(i+1)%BG_COLORS.length];break;}SettingsStore.bgColor(this,next);SettingsStore.bgEnabled(this,next!=Color.TRANSPARENT);applyBackground(true);touchControls();}
    private void cycleFont(){String f=SettingsStore.font(this);int idx=0;for(int i=0;i<FONTS.length;i++)if(FONTS[i].equals(f)){idx=i;break;}String next=FONTS[(idx+1)%FONTS.length];if("custom".equals(next)&&!SettingsStore.hasCustomFont(this))next=FONTS[0];SettingsStore.font(this,next);if(sub!=null){sub.setTypeface(SettingsStore.typeface(this,next));refreshLayout();}touchControls();}
    private void cyclePosition(){int p=(SettingsStore.position(this)+1)%5;SettingsStore.position(this,p);if(sub!=null&&wm!=null)try{WindowManager.LayoutParams lp=(WindowManager.LayoutParams)sub.getLayoutParams();lp.y=positionY();wm.updateViewLayout(sub,lp);}catch(Exception ignored){}touchControls();}
    private void updateManualTimingButtons(){
        boolean enabled=manualMode;
        if(offsetMinusButton!=null)offsetMinusButton.setEnabled(enabled);
        if(offsetPlusButton!=null)offsetPlusButton.setEnabled(enabled);
        if(offsetResetButton!=null)offsetResetButton.setEnabled(enabled);
        if(speedButton!=null)speedButton.setEnabled(enabled);
    }

    public void refreshManualTimingSettings(){
        float savedSpeed=SettingsStore.manualSpeed(this);
        if(manualMode && Math.abs(savedSpeed-manualSpeed)>0.001f){
            manualPosition=currentPosition();
            manualStartedAt=SystemClock.elapsedRealtime();
            SettingsStore.manualPosition(this,manualPosition);
        }
        offset=SettingsStore.offset(this);
        offset2=SettingsStore.offset2(this);
        manualSpeed=savedSpeed;
        manualMode=SettingsStore.manualMode(this);
        updateManualTimingButtons();
        if(info!=null)touchControls();
    }
    private void changeOffset(long d){if(!manualMode)return;offset+=d;SettingsStore.offset(this,offset);touchControls();}
    private void resetOffset(){if(!manualMode)return;offset=0;SettingsStore.offset(this,0);touchControls();}
    private void cycleSpeed(){
        if(!manualMode)return;
        float[] speeds={0.5f,0.75f,0.9f,1f,1.1f,1.25f,1.5f,1.75f,2f};float cur=SettingsStore.manualSpeed(this);int idx=3;
        for(int i=0;i<speeds.length;i++)if(Math.abs(speeds[i]-cur)<0.001f){idx=i;break;}
        long now=SystemClock.elapsedRealtime();
        manualPosition=currentPosition();
        manualStartedAt=now;
        SettingsStore.manualPosition(this,manualPosition);
        manualSpeed=speeds[(idx+1)%speeds.length];SettingsStore.manualSpeed(this,manualSpeed);touchControls();
    }
    private void cycleBgMode(){SettingsStore.bgMode(this,(SettingsStore.bgMode(this)+1)%3);applyBackground(true);refreshLayout();touchControls();}
    private void cycleBgPadding(){int[] vals={0,2,4,6,8,12,16};int cur=SettingsStore.bgPadding(this),idx=0;for(int i=0;i<vals.length;i++)if(vals[i]==cur){idx=i;break;}SettingsStore.bgPadding(this,vals[(idx+1)%vals.length]);if(sub!=null){int p=dp(SettingsStore.bgPadding(this));sub.setPadding(p,p,p,p);refreshLayout();}touchControls();}
    private void cycleBgOpacity(){int[] vals={30,50,70,85,100};int cur=SettingsStore.bgOpacity(this),idx=2;for(int i=0;i<vals.length;i++)if(vals[i]==cur){idx=i;break;}SettingsStore.bgOpacity(this,vals[(idx+1)%vals.length]);applyBackground(true);touchControls();}
    private void applyBackground(boolean keepText){if(sub==null)return;int mode=SettingsStore.bgMode(this);if(mode==2||!SettingsStore.bgEnabled(this)||SettingsStore.bgColor(this)==Color.TRANSPARENT){sub.setBackgroundColor(Color.TRANSPARENT);return;}int c=SettingsStore.bgColor(this);int a=(int)(255f*SettingsStore.bgOpacity(this)/100f);c=Color.argb(a,Color.red(c),Color.green(c),Color.blue(c));GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(8));sub.setBackground(g);}
    private void refreshLayout(){if(sub==null||wm==null)return;try{WindowManager.LayoutParams lp=(WindowManager.LayoutParams)sub.getLayoutParams();lp.width=SettingsStore.bgMode(this)==0?-1:-2;lp.height=-2;lp.y=positionY();wm.updateViewLayout(sub,lp);}catch(Exception ignored){}}

    private String currentSource(long now) {
        if(manualMode) return "MANUAL";
        boolean accessFresh = accessibilityPosition >= 0 && now - accessibilityUpdatedAt < 3000;
        boolean mediaFresh = mediaPosition >= 0 && now - mediaUpdatedAt < 1800;
        if(sourceMode==1) return accessFresh ? "ACCESS" : "HOLD";
        if(sourceMode==2) return mediaFresh ? "MEDIA" : "HOLD";
        // Automatic: MediaSession is preferred when fresh; Accessibility is fallback.
        if(mediaFresh) return "MEDIA";
        if(accessFresh) return "ACCESS";
        return "HOLD";
    }

    private long currentPosition(){
        long now=SystemClock.elapsedRealtime();
        String source=currentSource(now);
        if("MANUAL".equals(source)){
            long pos=manualPosition;
            if(run) pos+=(long)((now-manualStartedAt)*manualSpeed);
            return Math.max(0,pos);
        }
        if("ACCESS".equals(source)){
            long pos=accessibilityPosition;
            boolean playing=accessibilityPlaying;
            if(playing){
                float sp=(mediaSpeed>0f&&mediaSpeed<4f)?mediaSpeed:1f;
                pos+=(long)((now-accessibilityUpdatedAt)*sp);
            }
            rememberFallback(pos, playing, now);
            return Math.max(0,pos);
        }
        if("MEDIA".equals(source)){
            long pos=mediaPosition;
            if(mediaPlaying)pos+=(long)((now-mediaUpdatedAt)*mediaSpeed);
            rememberFallback(pos, mediaPlaying, now);
            return Math.max(0,pos);
        }
        // No live source: retain the last valid video-time anchor. Never use device uptime.
        long pos=fallbackPosition;
        if(fallbackHasAnchor && fallbackPlaying){
            float sp=(mediaSpeed>0f&&mediaSpeed<4f)?mediaSpeed:1f;
            pos+=(long)((now-fallbackUpdatedAt)*sp);
        }
        return Math.max(0,pos);
    }

    private void showSyncDialog(){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20),dp(8),dp(20),dp(4));
        TextView sourceLabel=new TextView(this); sourceLabel.setText("Automatic source mode"); box.addView(sourceLabel);
        Spinner source=new Spinner(this); String[] sourceLabels={"Auto (MediaSession preferred, Accessibility fallback)","Accessibility only","MediaSession / notification only"}; source.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,sourceLabels)); source.setSelection(sourceMode); box.addView(source);
        CheckBox manual=new CheckBox(this); manual.setText("Enable manual time override (overrides automatic source)"); manual.setChecked(manualMode); box.addView(manual);
        TextView timeLabel=new TextView(this); timeLabel.setText("Start time (MM:SS or HH:MM:SS)"); box.addView(timeLabel);
        EditText time=new EditText(this); time.setSingleLine(true); time.setInputType(InputType.TYPE_CLASS_TEXT); time.setHint("e.g. 12:34"); time.setText(formatLongTime(manualMode?manualPosition:currentPosition())); box.addView(time);
        TextView speedLabel=new TextView(this); speedLabel.setText("Manual playback speed"); box.addView(speedLabel);
        Spinner speed=new Spinner(this); String[] speedLabels={"0.5x","0.75x","0.9x","1.0x","1.1x","1.25x","1.5x","1.75x","2.0x"}; float[] speedValues={0.5f,0.75f,0.9f,1f,1.1f,1.25f,1.5f,1.75f,2f}; speed.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,speedLabels)); int selected=3; for(int j=0;j<speedValues.length;j++)if(Math.abs(speedValues[j]-manualSpeed)<0.001f){selected=j;break;} speed.setSelection(selected); box.addView(speed);
        TextView offsetLabel=new TextView(this); offsetLabel.setText("Subtitle offset in seconds (+ later / − earlier)"); box.addView(offsetLabel);
        EditText off=new EditText(this); off.setSingleLine(true); off.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED|InputType.TYPE_NUMBER_FLAG_DECIMAL); off.setText(String.format(Locale.US,"%.3f",offset/1000f)); box.addView(off);
        Runnable updateManualFields=()->{boolean enabled=manual.isChecked();time.setEnabled(enabled);speed.setEnabled(enabled);off.setEnabled(enabled);timeLabel.setEnabled(enabled);speedLabel.setEnabled(enabled);offsetLabel.setEnabled(enabled);};
        manual.setOnCheckedChangeListener((button,checked)->updateManualFields.run());
        updateManualFields.run();
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Subtitle synchronization").setView(box)
            .setPositiveButton("Start / Apply",(d,w)->{
                boolean useManual=manual.isChecked();
                try{
                    long handoffPosition=currentPosition();
                    sourceMode=Math.max(0,Math.min(2,source.getSelectedItemPosition())); SettingsStore.sourceMode(this,sourceMode);
                    if(useManual){
                        long entered=parseLongTime(time.getText().toString());
                        float newSpeed=speedValues[Math.max(0,Math.min(speedValues.length-1,speed.getSelectedItemPosition()))];
                        double offSeconds=Double.parseDouble(off.getText().toString().trim().replace(',','.'));
                        offset=(long)(offSeconds*1000d); SettingsStore.offset(this,offset);
                        manualPosition=entered;manualSpeed=newSpeed;manualStartedAt=SystemClock.elapsedRealtime();run=true;
                        SettingsStore.manualPosition(this,entered);SettingsStore.manualSpeed(this,newSpeed);
                    } else {
                        fallbackPosition=Math.max(0,handoffPosition);fallbackUpdatedAt=SystemClock.elapsedRealtime();fallbackHasAnchor=true;fallbackPlaying=true;
                    }
                    manualMode=useManual; SettingsStore.manualMode(this,useManual);
                    updateManualTimingButtons();
                    touchControls();
                }catch(Exception ex){Toast.makeText(this,"Invalid time or offset. Use MM:SS / HH:MM:SS and a numeric offset.",Toast.LENGTH_LONG).show();}
            }).setNegativeButton("Cancel",null).create();
        if(dialog.getWindow()!=null)dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        dialog.show();
    }

    private long parseLongTime(String raw){
        String[] parts=raw.trim().split(":"); if(parts.length<2||parts.length>3)throw new IllegalArgumentException("time format");
        long total=0; for(int i=0;i<parts.length;i++){String part=parts[i].trim();if(part.isEmpty())throw new IllegalArgumentException("time format");long n=Long.parseLong(part);if(n<0||(i>0&&n>=60))throw new IllegalArgumentException("time range");total=total*60+n;} return total*1000L;
    }
    private String formatLongTime(long ms){long sec=Math.max(0,ms)/1000;long h=sec/3600;long m=(sec%3600)/60;long s=sec%60;return h>0?String.format(Locale.US,"%d:%02d:%02d",h,m,s):String.format(Locale.US,"%02d:%02d",m,s);}
    private void toggleRun(Button button){
        long now=SystemClock.elapsedRealtime();
        if(manualMode){if(run)manualPosition+=(long)((now-manualStartedAt)*manualSpeed);manualStartedAt=now;}
        run=!run;
        button.setText(run?"Pause":"Play");touchControls();
    }

    private void rememberFallback(long position, boolean playing, long now){
        fallbackPosition=Math.max(0,position);
        fallbackUpdatedAt=now;
        fallbackPlaying=playing;
        fallbackHasAnchor=true;
    }
    private SrtCue find(List<SrtCue> list,long raw){for(SrtCue c:list){if(raw>=c.startMs&&raw<=c.endMs)return c;if(c.startMs>raw)break;}return null;}
    private String combineText(long videoPos){
        long primaryOffset=manualMode?offset:0L;
        long secondaryOffset=manualMode?offset2:0L;
        SrtCue a=find(cues,videoPos-primaryOffset), b=find(cues2,videoPos-secondaryOffset);
        if(!SettingsStore.doubleSubtitle(this)||cues2.isEmpty())return a==null?"":a.text;
        String first=SettingsStore.secondaryFirst(this)?(b==null?"":b.text):(a==null?"":a.text);
        String second=SettingsStore.secondaryFirst(this)?(a==null?"":a.text):(b==null?"":b.text);
        if(first.isEmpty())return second;if(second.isEmpty())return first;return first+"\n"+second;
    }
    private String lastText="";
    private int lastBgMode=-1,lastBgPadding=-1,lastBgOpacity=-1,lastPosition=-1;
    private final Runnable tick=new Runnable(){@Override public void run(){
        try {
            if(sub==null)return;
            updateControlsVisibility();
            long pos=currentPosition();
            String text=combineText(pos);
            boolean visible=!text.isEmpty();
            if(!text.equals(lastText)){sub.setText(text);lastText=text;}
            if(sub.getVisibility()!=(visible?View.VISIBLE:View.INVISIBLE))sub.setVisibility(visible?View.VISIBLE:View.INVISIBLE);
            int bm=SettingsStore.bgMode(OverlayService.this),bp=SettingsStore.bgPadding(OverlayService.this),bo=SettingsStore.bgOpacity(OverlayService.this),pp=SettingsStore.position(OverlayService.this);
            if(bm!=lastBgMode||bp!=lastBgPadding||bo!=lastBgOpacity){
                int pad=dp(bp);sub.setPadding(pad,pad,pad,pad);applyBackground(false);refreshLayout();lastBgMode=bm;lastBgPadding=bp;lastBgOpacity=bo;
            }
            if(pp!=lastPosition){refreshLayout();lastPosition=pp;}
            if(info!=null){long now=SystemClock.elapsedRealtime();String mode=currentSource(now);info.setText(mode+" "+fmt(pos)+(mediaDuration>0?" / "+fmt(mediaDuration):""));}
        } catch (Exception e) {
            // Keep the UI loop alive if Android temporarily invalidates an overlay view.
            android.util.Log.w("SubtitleOverlay", "Overlay tick recovered", e);
        } finally {
            if(sub!=null)h.postDelayed(this,80);
        }
    }};
    private String fmt(long ms){long sec=Math.max(0,ms)/1000;long m=sec/60;long s=sec%60;return String.format(Locale.US,"%02d:%02d",m,s);}
    private void remove(){if(wm==null)return;try{if(sub!=null)wm.removeView(sub);}catch(Exception ignored){}try{if(ctl!=null)wm.removeView(ctl);}catch(Exception ignored){}sub=null;ctl=null;}
    @Override public void onDestroy(){h.removeCallbacksAndMessages(null);remove();instance=null;super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
