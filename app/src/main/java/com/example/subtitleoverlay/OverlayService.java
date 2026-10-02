package com.example.subtitleoverlay;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
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
    private float timingScale;
    private long base=0, start;
    private boolean run=true;
    private long controlsHideAt;
    private static volatile long mediaPosition=-1, mediaDuration=-1, mediaUpdatedAt;
    private static volatile float mediaSpeed=1f;
    private static volatile boolean mediaPlaying=false;
    private static volatile long accessibilityPosition=-1, accessibilityUpdatedAt;
    private static volatile boolean accessibilityPlaying=true;
    private static volatile long lastAccessObserved=-1;
    private static volatile long mediaAuthorityUntil=0;
    private static volatile long lastMediaAccepted=-1;
    private static volatile long lastMediaUpdateObserved=-1;
    private static volatile int mediaDivergenceCount=0;

    private static final int[] TEXT_COLORS={Color.WHITE,Color.YELLOW,Color.CYAN,Color.GREEN,Color.RED};
    private static final int[] BG_COLORS={Color.argb(180,0,0,0),Color.argb(180,255,255,255),Color.argb(180,0,60,120),Color.TRANSPARENT};
    private static final String[] FONTS={"sans-serif","sans-serif-medium","sans-serif-condensed","serif","monospace","custom"};

    public static synchronized void setMediaPosition(long ms,float speed,long duration,String pkg,boolean playing){
        if(ms<0)return;
        long now=SystemClock.elapsedRealtime();
        float validSpeed=(speed>0f&&speed<4f)?speed:1f;

        // Always keep the latest MediaSession sample. It is the best source during
        // real seeks because Accessibility can lag or expose another timestamp.
        mediaPosition=ms;
        mediaSpeed=validSpeed;
        if(duration>0)mediaDuration=duration;
        mediaPlaying=playing;
        mediaUpdatedAt=now;

        if(accessibilityPosition>=0 && now-accessibilityUpdatedAt<4000){
            long predicted=accessibilityPosition;
            if(accessibilityPlaying){
                float sp=(mediaSpeed>0f&&mediaSpeed<4f)?mediaSpeed:1f;
                predicted+=(long)((now-accessibilityUpdatedAt)*sp);
            }
            long diff=Math.abs(ms-predicted);

            // A large MediaSession jump is treated as a possible seek. Requiring
            // two nearby MediaSession observations avoids handing authority to a
            // single noisy callback.
            if(diff>2500){
                if(lastMediaAccepted>=0 && Math.abs(ms-lastMediaAccepted)<1200){
                    mediaDivergenceCount++;
                }else{
                    mediaDivergenceCount=1;
                }
                lastMediaAccepted=ms;
                lastMediaUpdateObserved=now;
                if(mediaDivergenceCount>=2){
                    mediaAuthorityUntil=now+4500;
                }
            }else if(diff<1200){
                mediaDivergenceCount=0;
                if(mediaAuthorityUntil>now)mediaAuthorityUntil=now+700;
            }
        }else{
            mediaDivergenceCount=0;
        }
    }

    public static synchronized void setAccessibilityPosition(long ms){
        if(ms<0)return;
        long now=SystemClock.elapsedRealtime();
        if(lastAccessObserved>=0 && Math.abs(ms-lastAccessObserved)>1500){
            // A large Accessibility jump is itself a valid seek anchor.
            mediaAuthorityUntil=0;
            mediaDivergenceCount=0;
        }
        lastAccessObserved=ms;
        accessibilityPosition=ms;
        accessibilityUpdatedAt=now;
    }
    public static void setAccessibilityPlaying(boolean playing){accessibilityPlaying=playing;accessibilityUpdatedAt=SystemClock.elapsedRealtime();}

    @Override public void onCreate(){
        super.onCreate();instance=this;
        NotificationChannel c=new NotificationChannel("sub","Subtitle Overlay",NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(c);
        startForeground(9,new Notification.Builder(this,"sub").setContentTitle("Subtitle Overlay running").setSmallIcon(android.R.drawable.ic_media_play).setOngoing(true).build());
        offset=SettingsStore.offset(this); offset2=SettingsStore.offset2(this); timingScale=SettingsStore.speed(this);
    }

    @Override public int onStartCommand(Intent i,int f,int id){
        if(i!=null&&START.equals(i.getAction())){
            try(InputStream in=getContentResolver().openInputStream(Uri.parse(i.getStringExtra(URI)))){
                cues=SrtParser.parse(in);
                String u2=i.getStringExtra(URI2);
                if(u2!=null&&!u2.isEmpty()) try(InputStream in2=getContentResolver().openInputStream(Uri.parse(u2))){ if(in2!=null)cues2=SrtParser.parse(in2); }
                else cues2.clear();
                build();
            }catch(Exception e){Toast.makeText(this,"SRT error: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        }
        return START_STICKY;
    }

    private void build(){
        remove();wm=(WindowManager)getSystemService(WINDOW_SERVICE);
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
        LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);rows.setPadding(dp(4),dp(2),dp(4),dp(2));
        LinearLayout appearance=new LinearLayout(this);appearance.setOrientation(LinearLayout.HORIZONTAL);
        info=button("AUTO 00:00");appearance.addView(info);
        add(appearance,"A−",v->changeSize(-2));add(appearance,"A+",v->changeSize(2));add(appearance,"Color",v->showOverlayColorPicker(false));add(appearance,"BG",v->showOverlayColorPicker(true));add(appearance,"Font",v->cycleFont());add(appearance,"Pos",v->cyclePosition());
        add(appearance,"BG Mode",v->cycleBgMode());add(appearance,"Pad",v->cycleBgPadding());add(appearance,"Opacity",v->cycleBgOpacity());
        LinearLayout sync=new LinearLayout(this);sync.setOrientation(LinearLayout.HORIZONTAL);
        add(sync,"−0.5s",v->changeOffset(-500));add(sync,"+0.5s",v->changeOffset(500));add(sync,"Offset 0",v->resetOffset());add(sync,"Speed",v->cycleSpeed());
        add(sync,run?"Pause":"Play",v->{run=!run;if(run)start=SystemClock.uptimeMillis();((Button)v).setText(run?"Pause":"Play");touchControls();});
        add(sync,"Hide",v->cycleAutoHide());add(sync,"✕",v->stopSelf());
        rows.addView(appearance);rows.addView(sync);scroll.addView(rows);ctl=scroll;
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
    private void add(LinearLayout row,String text,View.OnClickListener l){Button b=button(text);b.setOnClickListener(l);row.addView(b);}
    public void refreshControlsVisibilityFromSettings(){
        if(ctl==null)return;
        if(SettingsStore.controlsAutoHide(this)==0){controlsHideAt=Long.MAX_VALUE;ctl.setVisibility(View.VISIBLE);setSubtitleTouchEnabled(false);}
        else touchControls();
    }
    private void touchControls(){int sec=SettingsStore.controlsAutoHide(this);if(ctl!=null)ctl.setVisibility(View.VISIBLE);if(sec==0){controlsHideAt=Long.MAX_VALUE;setSubtitleTouchEnabled(false);}else{controlsHideAt=SystemClock.elapsedRealtime()+sec*1000L;setSubtitleTouchEnabled(false);}}
    private void updateControlsVisibility(){int sec=SettingsStore.controlsAutoHide(this);if(ctl!=null){if(sec==0){ctl.setVisibility(View.VISIBLE);return;}if(SystemClock.elapsedRealtime()>=controlsHideAt){ctl.setVisibility(View.GONE);setSubtitleTouchEnabled(true);}}}
    private void setSubtitleTouchEnabled(boolean enabled){if(sub==null||wm==null)return;try{WindowManager.LayoutParams lp=(WindowManager.LayoutParams)sub.getLayoutParams();int flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;if(!enabled)flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;lp.flags=flags;wm.updateViewLayout(sub,lp);}catch(Exception ignored){}}

    public void refreshAppearance(){
        if(sub==null)return;
        sub.setTextColor(SettingsStore.textColor(this));
        sub.setTextSize(SettingsStore.size(this));
        sub.setTypeface(SettingsStore.typeface(this,SettingsStore.font(this)));
        lastSize=SettingsStore.size(this); lastTextColor=SettingsStore.textColor(this); lastFont=SettingsStore.font(this);
        int pad=dp(SettingsStore.bgPadding(this));
        sub.setPadding(pad,pad,pad,pad);
        applyBackground(false);
        refreshLayout();
    }

    private void changeSize(int d){float s=Math.max(12,Math.min(72,SettingsStore.size(this)+d));SettingsStore.size(this,s);if(sub!=null){sub.setTextSize(s);refreshLayout();}touchControls();}
    private void showOverlayColorPicker(boolean background){
        final int initial=background?SettingsStore.bgColor(this):SettingsStore.textColor(this);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(4),dp(18),0);
        SeekBar r=new SeekBar(this),g=new SeekBar(this),b=new SeekBar(this);r.setMax(255);g.setMax(255);b.setMax(255);r.setProgress(Color.red(initial));g.setProgress(Color.green(initial));b.setProgress(Color.blue(initial));
        box.addView(colorSlider("Red",r));box.addView(colorSlider("Green",g));box.addView(colorSlider("Blue",b));
        CheckBox transparent=new CheckBox(this);transparent.setText("Transparent / disable background");transparent.setVisibility(background?View.VISIBLE:View.GONE);transparent.setChecked(background&&(!SettingsStore.bgEnabled(this)||initial==Color.TRANSPARENT));box.addView(transparent);
        TextView hex=new TextView(this);hex.setGravity(Gravity.CENTER);box.addView(hex);
        Runnable update=()->{int c=Color.rgb(r.getProgress(),g.getProgress(),b.getProgress());hex.setText(String.format(Locale.US,"#%02X%02X%02X",Color.red(c),Color.green(c),Color.blue(c)));};
        SeekBar.OnSeekBarChangeListener l=new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){update.run();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}};r.setOnSeekBarChangeListener(l);g.setOnSeekBarChangeListener(l);b.setOnSeekBarChangeListener(l);update.run();
        AlertDialog d=new AlertDialog.Builder(this).setTitle(background?"Background color":"Subtitle color").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{int c=Color.rgb(r.getProgress(),g.getProgress(),b.getProgress());if(background){SettingsStore.bgColor(this,c);SettingsStore.bgEnabled(this,!transparent.isChecked());}else SettingsStore.textColor(this,c);refreshAppearance();d.dismiss();}));
        d.show();
    }
    private LinearLayout colorSlider(String name,SeekBar seek){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);TextView t=new TextView(this);t.setText(name);t.setTextColor(Color.WHITE);t.setTextSize(12);t.setLayoutParams(new LinearLayout.LayoutParams(dp(48),-2));r.addView(t);r.addView(seek,new LinearLayout.LayoutParams(0,-2,1));return r;}

    private void cycleTextColor(){int c=SettingsStore.textColor(this);int next=TEXT_COLORS[0];for(int i=0;i<TEXT_COLORS.length;i++)if(TEXT_COLORS[i]==c){next=TEXT_COLORS[(i+1)%TEXT_COLORS.length];break;}SettingsStore.textColor(this,next);if(sub!=null)sub.setTextColor(next);touchControls();}
    private void cycleBg(){int c=SettingsStore.bgColor(this);int next=BG_COLORS[0];for(int i=0;i<BG_COLORS.length;i++)if(BG_COLORS[i]==c){next=BG_COLORS[(i+1)%BG_COLORS.length];break;}SettingsStore.bgColor(this,next);SettingsStore.bgEnabled(this,next!=Color.TRANSPARENT);applyBackground(true);touchControls();}
    private void cycleFont(){String f=SettingsStore.font(this);int idx=0;for(int i=0;i<FONTS.length;i++)if(FONTS[i].equals(f)){idx=i;break;}String next=FONTS[(idx+1)%FONTS.length];if("custom".equals(next)&&!SettingsStore.hasCustomFont(this))next=FONTS[0];SettingsStore.font(this,next);if(sub!=null){sub.setTypeface(SettingsStore.typeface(this,next));refreshLayout();}touchControls();}
    private void cyclePosition(){int p=(SettingsStore.position(this)+1)%5;SettingsStore.position(this,p);if(sub!=null&&wm!=null)try{WindowManager.LayoutParams lp=(WindowManager.LayoutParams)sub.getLayoutParams();lp.y=positionY();wm.updateViewLayout(sub,lp);}catch(Exception ignored){}touchControls();}
    private void changeOffset(long d){offset+=d;SettingsStore.offset(this,offset);touchControls();}
    private void resetOffset(){offset=0;SettingsStore.offset(this,0);touchControls();}
    private void cycleSpeed(){float[] speeds={0.75f,0.9f,1f,1.1f,1.25f,1.5f};float cur=SettingsStore.speed(this);int idx=2;for(int i=0;i<speeds.length;i++)if(Math.abs(speeds[i]-cur)<0.001){idx=i;break;}timingScale=speeds[(idx+1)%speeds.length];SettingsStore.speed(this,timingScale);touchControls();}
    private void cycleAutoHide(){int cur=SettingsStore.controlsAutoHide(this);int[] vals={3,5,10,0};int idx=0;for(int i=0;i<vals.length;i++)if(vals[i]==cur){idx=i;break;}SettingsStore.controlsAutoHide(this,vals[(idx+1)%vals.length]);touchControls();}
    public void refreshTimingFromSettings(){offset=SettingsStore.offset(this);offset2=SettingsStore.offset2(this);timingScale=SettingsStore.speed(this);}
    public void refreshSubtitlesFromSettings(){refreshTimingFromSettings();}
    private void cycleBgMode(){SettingsStore.bgMode(this,(SettingsStore.bgMode(this)+1)%3);applyBackground(true);refreshLayout();touchControls();}
    private void cycleBgPadding(){int[] vals={0,2,4,6,8,12,16};int cur=SettingsStore.bgPadding(this),idx=0;for(int i=0;i<vals.length;i++)if(vals[i]==cur){idx=i;break;}SettingsStore.bgPadding(this,vals[(idx+1)%vals.length]);if(sub!=null){int p=dp(SettingsStore.bgPadding(this));sub.setPadding(p,p,p,p);refreshLayout();}touchControls();}
    private void cycleBgOpacity(){int[] vals={30,50,70,85,100};int cur=SettingsStore.bgOpacity(this),idx=2;for(int i=0;i<vals.length;i++)if(vals[i]==cur){idx=i;break;}SettingsStore.bgOpacity(this,vals[(idx+1)%vals.length]);applyBackground(true);touchControls();}
    private void applyBackground(boolean keepText){if(sub==null)return;int mode=SettingsStore.bgMode(this);if(mode==2||!SettingsStore.bgEnabled(this)||SettingsStore.bgColor(this)==Color.TRANSPARENT){sub.setBackgroundColor(Color.TRANSPARENT);return;}int c=SettingsStore.bgColor(this);int a=(int)(255f*SettingsStore.bgOpacity(this)/100f);c=Color.argb(a,Color.red(c),Color.green(c),Color.blue(c));GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(8));sub.setBackground(g);}
    private void refreshLayout(){if(sub==null||wm==null)return;try{WindowManager.LayoutParams lp=(WindowManager.LayoutParams)sub.getLayoutParams();lp.width=SettingsStore.bgMode(this)==0?-1:-2;lp.height=-2;lp.y=positionY();wm.updateViewLayout(sub,lp);}catch(Exception ignored){}}

    private long currentPosition(){
        long now=SystemClock.elapsedRealtime();
        boolean mediaAuthority=mediaAuthorityUntil>now && mediaPosition>=0 && now-mediaUpdatedAt<5000;
        if(!mediaAuthority && accessibilityPosition>=0&&now-accessibilityUpdatedAt<3000){
            long pos=accessibilityPosition;
            if(accessibilityPlaying){
                float sp=(mediaSpeed>0f&&mediaSpeed<4f)?mediaSpeed:1f;
                pos+=(long)((now-accessibilityUpdatedAt)*sp);
            }
            return Math.max(0,(long)(pos*timingScale)+offset);
        }
        if(mediaPosition>=0&&now-mediaUpdatedAt<5000){
            long pos=mediaPosition;
            if(mediaPlaying)pos+=(long)((now-mediaUpdatedAt)*mediaSpeed);
            return Math.max(0,(long)(pos*timingScale)+offset);
        }
        long pos=run?base+(SystemClock.uptimeMillis()-start):base;
        return Math.max(0,(long)(pos*timingScale)+offset);
    }
    private SrtCue find(List<SrtCue> list,long raw){for(SrtCue c:list){if(raw>=c.startMs&&raw<=c.endMs)return c;if(c.startMs>raw)break;}return null;}
    private String combineText(long videoPos){
        SrtCue a=find(cues,videoPos-offset), b=find(cues2,videoPos-offset2);
        if(!SettingsStore.doubleSubtitle(this)||cues2.isEmpty())return a==null?"":a.text;
        String first=SettingsStore.secondaryFirst(this)?(b==null?"":b.text):(a==null?"":a.text);
        String second=SettingsStore.secondaryFirst(this)?(a==null?"":a.text):(b==null?"":b.text);
        if(first.isEmpty())return second;if(second.isEmpty())return first;return first+"\n"+second;
    }
    private float lastSize=-1f; private int lastTextColor=Integer.MIN_VALUE; private String lastFont="";
    private void syncAppearanceIfChanged(){
        float sz=SettingsStore.size(this); int tc=SettingsStore.textColor(this); String ft=SettingsStore.font(this);
        if(sz!=lastSize){sub.setTextSize(sz);lastSize=sz;}
        if(tc!=lastTextColor){sub.setTextColor(tc);lastTextColor=tc;}
        if(!ft.equals(lastFont)){sub.setTypeface(SettingsStore.typeface(this,ft));lastFont=ft;}
    }

    private String lastText="";
    private int lastBgMode=-1,lastBgPadding=-1,lastBgOpacity=-1,lastPosition=-1;
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(sub==null)return;
        updateControlsVisibility();
        refreshTimingFromSettings();
        syncAppearanceIfChanged();
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
        if(info!=null){long now=SystemClock.elapsedRealtime();String mode=(mediaAuthorityUntil>now&&mediaPosition>=0)?"MEDIA":((accessibilityPosition>=0&&now-accessibilityUpdatedAt<3000)?"ACCESS":"MAN");info.setText(mode+" "+fmt(pos)+(mediaDuration>0?" / "+fmt(mediaDuration):""));}
        h.postDelayed(this,80);
    }};
    private String fmt(long ms){long sec=Math.max(0,ms)/1000;long m=sec/60;long s=sec%60;return String.format(Locale.US,"%02d:%02d",m,s);}
    private void remove(){if(wm==null)return;try{if(sub!=null)wm.removeView(sub);}catch(Exception ignored){}try{if(ctl!=null)wm.removeView(ctl);}catch(Exception ignored){}sub=null;ctl=null;}
    @Override public void onDestroy(){h.removeCallbacksAndMessages(null);remove();instance=null;super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
