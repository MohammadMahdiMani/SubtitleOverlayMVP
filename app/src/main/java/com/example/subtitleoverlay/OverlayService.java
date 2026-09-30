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

    private static final int[] TEXT_COLORS={Color.WHITE,Color.YELLOW,Color.CYAN,Color.GREEN,Color.RED};
    private static final int[] BG_COLORS={Color.argb(180,0,0,0),Color.argb(180,255,255,255),Color.argb(180,0,60,120),Color.TRANSPARENT};
    private static final String[] FONTS={"sans-serif","sans-serif-medium","sans-serif-condensed","serif","monospace","custom"};

    public static void setMediaPosition(long ms,float speed,long duration,String pkg,boolean playing){
        if(ms<0)return;
        long now=SystemClock.elapsedRealtime();
        if(accessibilityPosition>=0 && now-accessibilityUpdatedAt<10000){
            long predicted=accessibilityPosition;
            if(accessibilityPlaying){float sp=(speed>0f&&speed<4f)?speed:1f;predicted+=(long)((now-accessibilityUpdatedAt)*sp);}
            if(Math.abs(ms-predicted)>2500){
                mediaSpeed=(speed>0f&&speed<4f)?speed:mediaSpeed;
                if(duration>0)mediaDuration=duration;
                mediaPlaying=playing;
                return;
            }
        }
        mediaPosition=ms;mediaSpeed=(speed>0f&&speed<4f)?speed:1f;mediaDuration=duration;mediaPlaying=playing;mediaUpdatedAt=now;
    }
    public static void setAccessibilityPosition(long ms){
        if(ms<0)return;
        long now=SystemClock.elapsedRealtime();
        if(lastAccessObserved>=0 && Math.abs(ms-lastAccessObserved)>1500){ accessibilityUpdatedAt=now; }
        lastAccessObserved=ms;accessibilityPosition=ms;accessibilityUpdatedAt=now;
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
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(4,2,4,2);
        TextView info=button("AUTO 00:00");row.addView(info);
        add(row,"A−",v->changeSize(-2));add(row,"A+",v->changeSize(2));add(row,"Color",v->cycleTextColor());add(row,"BG",v->cycleBg());add(row,"Font",v->cycleFont());add(row,"Pos",v->cyclePosition());
        add(row,"BG Mode",v->cycleBgMode());add(row,"Pad",v->cycleBgPadding());add(row,"Opacity",v->cycleBgOpacity());
        add(row,"−0.5s",v->changeOffset(-500));add(row,"+0.5s",v->changeOffset(500));add(row,"Offset 0",v->resetOffset());add(row,"Speed",v->cycleSpeed());
        add(row,run?"Pause":"Play",v->{run=!run;if(run)start=SystemClock.uptimeMillis();((Button)v).setText(run?"Pause":"Play");touchControls();});
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
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(10);b.setMinHeight(1);b.setMinWidth(1);b.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN)touchControls();return false;});return b;}
    private void add(LinearLayout row,String text,View.OnClickListener l){Button b=button(text);b.setOnClickListener(l);row.addView(b);}
    private void touchControls(){controlsHideAt=SystemClock.elapsedRealtime()+3000;if(ctl!=null)ctl.setVisibility(View.VISIBLE);setSubtitleTouchEnabled(false);}
    private void updateControlsVisibility(){if(ctl!=null&&SystemClock.elapsedRealtime()>=controlsHideAt){ctl.setVisibility(View.GONE);setSubtitleTouchEnabled(true);}}
    private void setSubtitleTouchEnabled(boolean enabled){if(sub==null||wm==null)return;try{WindowManager.LayoutParams lp=(WindowManager.LayoutParams)sub.getLayoutParams();int flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;if(!enabled)flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;lp.flags=flags;wm.updateViewLayout(sub,lp);}catch(Exception ignored){}}

    private void changeSize(int d){float s=Math.max(12,Math.min(72,SettingsStore.size(this)+d));SettingsStore.size(this,s);if(sub!=null){sub.setTextSize(s);refreshLayout();}touchControls();}
    private void cycleTextColor(){int c=SettingsStore.textColor(this);int next=TEXT_COLORS[0];for(int i=0;i<TEXT_COLORS.length;i++)if(TEXT_COLORS[i]==c){next=TEXT_COLORS[(i+1)%TEXT_COLORS.length];break;}SettingsStore.textColor(this,next);if(sub!=null)sub.setTextColor(next);touchControls();}
    private void cycleBg(){int c=SettingsStore.bgColor(this);int next=BG_COLORS[0];for(int i=0;i<BG_COLORS.length;i++)if(BG_COLORS[i]==c){next=BG_COLORS[(i+1)%BG_COLORS.length];break;}SettingsStore.bgColor(this,next);SettingsStore.bgEnabled(this,next!=Color.TRANSPARENT);applyBackground(true);touchControls();}
    private void cycleFont(){String f=SettingsStore.font(this);int idx=0;for(int i=0;i<FONTS.length;i++)if(FONTS[i].equals(f)){idx=i;break;}String next=FONTS[(idx+1)%FONTS.length];if("custom".equals(next)&&!SettingsStore.hasCustomFont(this))next=FONTS[0];SettingsStore.font(this,next);if(sub!=null){sub.setTypeface(SettingsStore.typeface(this,next));refreshLayout();}touchControls();}
    private void cyclePosition(){int p=(SettingsStore.position(this)+1)%5;SettingsStore.position(this,p);if(sub!=null&&wm!=null)try{WindowManager.LayoutParams lp=(WindowManager.LayoutParams)sub.getLayoutParams();lp.y=positionY();wm.updateViewLayout(sub,lp);}catch(Exception ignored){}touchControls();}
    private void changeOffset(long d){offset+=d;SettingsStore.offset(this,offset);touchControls();}
    private void resetOffset(){offset=0;SettingsStore.offset(this,0);touchControls();}
    private void cycleSpeed(){float[] speeds={0.75f,0.9f,1f,1.1f,1.25f,1.5f};float cur=SettingsStore.speed(this);int idx=2;for(int i=0;i<speeds.length;i++)if(Math.abs(speeds[i]-cur)<0.001){idx=i;break;}timingScale=speeds[(idx+1)%speeds.length];SettingsStore.speed(this,timingScale);touchControls();}
    private void cycleBgMode(){SettingsStore.bgMode(this,(SettingsStore.bgMode(this)+1)%3);applyBackground(true);refreshLayout();touchControls();}
    private void cycleBgPadding(){int[] vals={0,2,4,6,8,12,16};int cur=SettingsStore.bgPadding(this),idx=0;for(int i=0;i<vals.length;i++)if(vals[i]==cur){idx=i;break;}SettingsStore.bgPadding(this,vals[(idx+1)%vals.length]);if(sub!=null){int p=dp(SettingsStore.bgPadding(this));sub.setPadding(p,p,p,p);refreshLayout();}touchControls();}
    private void cycleBgOpacity(){int[] vals={30,50,70,85,100};int cur=SettingsStore.bgOpacity(this),idx=2;for(int i=0;i<vals.length;i++)if(vals[i]==cur){idx=i;break;}SettingsStore.bgOpacity(this,vals[(idx+1)%vals.length]);applyBackground(true);touchControls();}
    private void applyBackground(boolean keepText){if(sub==null)return;int mode=SettingsStore.bgMode(this);if(mode==2||!SettingsStore.bgEnabled(this)||SettingsStore.bgColor(this)==Color.TRANSPARENT){sub.setBackgroundColor(Color.TRANSPARENT);return;}int c=SettingsStore.bgColor(this);int a=(int)(255f*SettingsStore.bgOpacity(this)/100f);c=Color.argb(a,Color.red(c),Color.green(c),Color.blue(c));GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(8));sub.setBackground(g);}
    private void refreshLayout(){if(sub==null||wm==null)return;try{WindowManager.LayoutParams lp=(WindowManager.LayoutParams)sub.getLayoutParams();lp.width=SettingsStore.bgMode(this)==0?-1:-2;lp.height=-2;lp.y=positionY();wm.updateViewLayout(sub,lp);}catch(Exception ignored){}}

    private long currentPosition(){
        long now=SystemClock.elapsedRealtime();
        if(accessibilityPosition>=0&&now-accessibilityUpdatedAt<3000){long pos=accessibilityPosition;if(accessibilityPlaying){float sp=(mediaSpeed>0f&&mediaSpeed<4f)?mediaSpeed:1f;pos+=(long)((now-accessibilityUpdatedAt)*sp);}return Math.max(0,(long)(pos*timingScale)+offset);}
        if(mediaPosition>=0&&now-mediaUpdatedAt<5000){long pos=mediaPosition;if(mediaPlaying)pos+=(long)((now-mediaUpdatedAt)*mediaSpeed);return Math.max(0,(long)(pos*timingScale)+offset);}
        long pos=run?base+(SystemClock.uptimeMillis()-start):base;return Math.max(0,(long)(pos*timingScale)+offset);
    }
    private SrtCue find(List<SrtCue> list,long raw){for(SrtCue c:list){if(raw>=c.startMs&&raw<=c.endMs)return c;if(c.startMs>raw)break;}return null;}
    private String combineText(long videoPos){
        SrtCue a=find(cues,videoPos-offset), b=find(cues2,videoPos-offset2);
        if(!SettingsStore.doubleSubtitle(this)||cues2.isEmpty())return a==null?"":a.text;
        String first=SettingsStore.secondaryFirst(this)?(b==null?"":b.text):(a==null?"":a.text);
        String second=SettingsStore.secondaryFirst(this)?(a==null?"":a.text):(b==null?"":b.text);
        if(first.isEmpty())return second;if(second.isEmpty())return first;return first+"\n"+second;
    }
    private final Runnable tick=new Runnable(){@Override public void run(){if(sub==null)return;updateControlsVisibility();long pos=currentPosition();String text=combineText(pos);sub.setText(text);sub.setVisibility(text.isEmpty()?View.INVISIBLE:View.VISIBLE);applyBackground(false);if(sub.getVisibility()==View.VISIBLE)refreshLayout();h.postDelayed(this,60);}};
    private String fmt(long ms){long sec=Math.max(0,ms)/1000;long m=sec/60;long s=sec%60;return String.format(Locale.US,"%02d:%02d",m,s);}
    private void remove(){if(wm==null)return;try{if(sub!=null)wm.removeView(sub);}catch(Exception ignored){}try{if(ctl!=null)wm.removeView(ctl);}catch(Exception ignored){}sub=null;ctl=null;}
    @Override public void onDestroy(){h.removeCallbacksAndMessages(null);remove();instance=null;super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
