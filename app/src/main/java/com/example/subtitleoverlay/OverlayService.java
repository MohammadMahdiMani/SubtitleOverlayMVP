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
    public static final String START="START", URI="uri";
    static volatile OverlayService instance;
    private WindowManager wm;
    private TextView sub, info;
    private ViewGroup ctl;
    private List<SrtCue> cues=new ArrayList<>();
    private final Handler h=new Handler(Looper.getMainLooper());
    private long offset;
    private float timingScale;
    private long base=0, start;
    private boolean run=true;
    private static volatile long mediaPosition=-1, mediaDuration=-1, mediaUpdatedAt;
    private static volatile float mediaSpeed=1f;
    private static volatile boolean mediaPlaying=false;
    private static volatile long accessibilityPosition=-1, accessibilityUpdatedAt;

    private static final int[] TEXT_COLORS={Color.WHITE,Color.YELLOW,Color.CYAN,Color.GREEN,Color.RED};
    private static final int[] BG_COLORS={Color.argb(180,0,0,0),Color.argb(180,255,255,255),Color.argb(180,0,60,120),Color.TRANSPARENT};
    private static final String[] FONTS={"sans-serif","sans-serif-medium","sans-serif-condensed","serif","monospace"};

    public static void setMediaPosition(long ms,float speed,long duration,String pkg,boolean playing){
        mediaPosition=ms; mediaSpeed=speed; mediaDuration=duration; mediaPlaying=playing; mediaUpdatedAt=SystemClock.elapsedRealtime();
    }
    public static void setAccessibilityPosition(long ms){ accessibilityPosition=ms; accessibilityUpdatedAt=SystemClock.elapsedRealtime(); }

    @Override public void onCreate(){
        super.onCreate(); instance=this;
        NotificationChannel c=new NotificationChannel("sub","Subtitle Overlay",NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(c);
        startForeground(9,new Notification.Builder(this,"sub").setContentTitle("Subtitle Overlay running").setSmallIcon(android.R.drawable.ic_media_play).setOngoing(true).build());
        offset=SettingsStore.offset(this); timingScale=SettingsStore.speed(this);
    }

    @Override public int onStartCommand(Intent i,int f,int id){
        if(i!=null && START.equals(i.getAction())){
            try(InputStream in=getContentResolver().openInputStream(Uri.parse(i.getStringExtra(URI)))){
                cues=SrtParser.parse(in); build();
            }catch(Exception e){ Toast.makeText(this,"SRT error: "+e.getMessage(),Toast.LENGTH_LONG).show(); }
        }
        return START_STICKY;
    }

    private void build(){
        remove();
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);

        sub=new TextView(this);
        sub.setTextColor(SettingsStore.textColor(this));
        sub.setTextSize(SettingsStore.size(this));
        sub.setTypeface(SettingsStore.typeface(SettingsStore.font(this)));
        sub.setGravity(Gravity.CENTER);
        sub.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        sub.setShadowLayer(8,0,2,Color.BLACK);
        sub.setPadding(24,10,24,10);
        applyBackground();
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-2,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL; p.y=90;
        wm.addView(sub,p);

        HorizontalScrollView scroll=new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(4,2,4,2);
        info=button("AUTO 00:00"); row.addView(info);
        add(row,"A−",v->changeSize(-2)); add(row,"A+",v->changeSize(2));
        add(row,"Color",v->cycleTextColor()); add(row,"BG",v->cycleBg()); add(row,"Font",v->cycleFont());
        add(row,"−0.5s",v->changeOffset(-500)); add(row,"+0.5s",v->changeOffset(500)); add(row,"Offset 0",v->resetOffset());
        add(row,"Speed",v->cycleSpeed()); add(row,run?"Pause":"Play",v->{run=!run; if(run)start=SystemClock.uptimeMillis(); ((Button)v).setText(run?"Pause":"Play");});
        add(row,"✕",v->stopSelf());
        scroll.addView(row); ctl=scroll;
        WindowManager.LayoutParams q=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        q.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL; q.y=25; wm.addView(ctl,q);
        start=SystemClock.uptimeMillis();
        h.post(tick);
    }

    private Button button(String s){ Button b=new Button(this); b.setText(s); b.setTextSize(10); b.setMinHeight(1); b.setMinWidth(1); return b; }
    private void add(LinearLayout row,String text,View.OnClickListener l){Button b=button(text);b.setOnClickListener(l);row.addView(b);}

    private void changeSize(int d){float s=Math.max(12,Math.min(72,SettingsStore.size(this)+d));SettingsStore.size(this,s);if(sub!=null)sub.setTextSize(s);}
    private void cycleTextColor(){int c=SettingsStore.textColor(this);int next=TEXT_COLORS[0];for(int i=0;i<TEXT_COLORS.length;i++)if(TEXT_COLORS[i]==c){next=TEXT_COLORS[(i+1)%TEXT_COLORS.length];break;}SettingsStore.textColor(this,next);if(sub!=null)sub.setTextColor(next);}
    private void cycleBg(){int c=SettingsStore.bgColor(this);int next=BG_COLORS[0];for(int i=0;i<BG_COLORS.length;i++)if(BG_COLORS[i]==c){next=BG_COLORS[(i+1)%BG_COLORS.length];break;}SettingsStore.bgColor(this,next);SettingsStore.bgEnabled(this,next!=Color.TRANSPARENT);applyBackground();}
    private void cycleFont(){String f=SettingsStore.font(this);int idx=0;for(int i=0;i<FONTS.length;i++)if(FONTS[i].equals(f)){idx=i;break;}String next=FONTS[(idx+1)%FONTS.length];SettingsStore.font(this,next);if(sub!=null)sub.setTypeface(SettingsStore.typeface(next));}
    private void changeOffset(long d){offset+=d;SettingsStore.offset(this,offset);}
    private void resetOffset(){offset=0;SettingsStore.offset(this,0);}
    private void cycleSpeed(){float[] speeds={0.75f,0.9f,1f,1.1f,1.25f,1.5f};float cur=SettingsStore.speed(this);int idx=2;for(int i=0;i<speeds.length;i++)if(Math.abs(speeds[i]-cur)<0.001){idx=i;break;}timingScale=speeds[(idx+1)%speeds.length];SettingsStore.speed(this,timingScale);}
    private void applyBackground(){if(sub==null)return;int c=SettingsStore.bgColor(this);if(!SettingsStore.bgEnabled(this)||c==Color.TRANSPARENT){sub.setBackgroundColor(Color.TRANSPARENT);return;}GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(12);sub.setBackground(g);}

    private long currentPosition(){
        long pos;
        if(mediaPosition>=0 && SystemClock.elapsedRealtime()-mediaUpdatedAt<5000){
            pos=mediaPosition;
            if(mediaPlaying){long d=SystemClock.elapsedRealtime()-mediaUpdatedAt;pos+=(long)(d*mediaSpeed);}
        } else if(accessibilityPosition>=0 && SystemClock.elapsedRealtime()-accessibilityUpdatedAt<2500){
            pos=accessibilityPosition;
        } else {
            pos=run ? base+(SystemClock.uptimeMillis()-start) : base;
        }
        return (long)(pos*timingScale)+offset;
    }

    private final Runnable tick=new Runnable(){@Override public void run(){
        if(sub==null)return;
        long pos=Math.max(0,currentPosition());
        SrtCue found=null;
        for(SrtCue c:cues){if(pos>=c.startMs&&pos<=c.endMs){found=c;break;}if(c.startMs>pos)break;}
        sub.setText(found==null?"":found.text);
        if(info!=null){String mode=(mediaPosition>=0&&SystemClock.elapsedRealtime()-mediaUpdatedAt<5000)?"AUTO":"MAN";info.setText(mode+" "+fmt(pos)+(mediaDuration>0?" / "+fmt(mediaDuration):""));}
        h.postDelayed(this,60);
    }};

    private String fmt(long ms){long sec=Math.max(0,ms)/1000;long m=sec/60;long s=sec%60;return String.format(Locale.US,"%02d:%02d",m,s);}
    private void remove(){if(wm==null)return;try{if(sub!=null)wm.removeView(sub);}catch(Exception ignored){}try{if(ctl!=null)wm.removeView(ctl);}catch(Exception ignored){}sub=null;ctl=null;}
    @Override public void onDestroy(){h.removeCallbacksAndMessages(null);remove();instance=null;super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
