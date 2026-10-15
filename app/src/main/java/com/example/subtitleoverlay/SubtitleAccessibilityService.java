package com.example.subtitleoverlay;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SubtitleAccessibilityService extends AccessibilityService {
    private static volatile String activePackage;
    private static final Pattern TIME = Pattern.compile("(?<!\\d)(\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?!\\d)");
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            scanCurrentWindow();
            handler.postDelayed(this, 500);
        }
    };

    public static String getActivePackage() { return activePackage; }

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        handler.removeCallbacks(poll);
        handler.post(poll);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getPackageName() != null) activePackage = e.getPackageName().toString();
        scanCurrentWindow();
    }

    private void scanCurrentWindow() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        try {
            if (root.getPackageName() != null) activePackage = root.getPackageName().toString();
            long range = findRangePosition(root);
            if (range >= 0) {
                OverlayService.setAccessibilityPosition(range);
                return;
            }
            long text = findLikelyCurrentTime(root);
            if (text >= 0) OverlayService.setAccessibilityPosition(text);
        } finally {
            root.recycle();
        }
    }

    private long findRangePosition(AccessibilityNodeInfo n) {
        if (n.getRangeInfo() != null) {
            AccessibilityNodeInfo.RangeInfo r = n.getRangeInfo();
            float current = r.getCurrent();
            float max = r.getMax();
            CharSequence cls = n.getClassName();
            if (current >= 0 && max > 0 && (cls == null || cls.toString().toLowerCase().contains("seek"))) {
                if (max > 120) return (long)(current * 1000f);
            }
        }
        for (int i=0;i<n.getChildCount();i++) {
            AccessibilityNodeInfo c=n.getChild(i);
            if(c!=null){ long v=findRangePosition(c); c.recycle(); if(v>=0)return v; }
        }
        return -1;
    }

    private long findLikelyCurrentTime(AccessibilityNodeInfo n) {
        long best = -1;
        CharSequence[] a={n.getText(),n.getContentDescription()};
        for(CharSequence x:a) if(x!=null){ long v=parseFirst(x.toString()); if(v>=0){best=v;break;} }
        for(int i=0;i<n.getChildCount() && best<0;i++){
            AccessibilityNodeInfo c=n.getChild(i);
            if(c!=null){best=findLikelyCurrentTime(c);c.recycle();}
        }
        return best;
    }

    private long parseFirst(String s){
        Matcher m=TIME.matcher(s);
        if(!m.find()) return -1;
        try{
            long a=Long.parseLong(m.group(1)), b=Long.parseLong(m.group(2));
            if(m.group(3)==null) return a*60000+b*1000;
            long c=Long.parseLong(m.group(3));
            if(b>=60 || c>=60)return -1;
            return a*3600000+b*60000+c*1000;
        }catch(Exception ignored){return -1;}
    }

    @Override public void onInterrupt() {}

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
