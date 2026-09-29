package com.example.subtitleoverlay;
import android.accessibilityservice.AccessibilityService;import android.view.accessibility.*;import java.util.regex.*;
public class SubtitleAccessibilityService extends AccessibilityService{
 public void onAccessibilityEvent(AccessibilityEvent e){AccessibilityNodeInfo r=getRootInActiveWindow();if(r!=null){long v=find(r);r.recycle();if(v>=0)OverlayService.setExternal(v);}}
 long find(AccessibilityNodeInfo n){long best=-1;CharSequence[] a={n.getText(),n.getContentDescription()};for(CharSequence x:a)if(x!=null)best=Math.max(best,parse(x.toString()));for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null){best=Math.max(best,find(c));c.recycle();}}return best;}
 long parse(String s){Matcher m=Pattern.compile("(?<!\\d)(\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?!\\d)").matcher(s);long best=-1;while(m.find())try{long a=Long.parseLong(m.group(1)),b=Long.parseLong(m.group(2)),c=m.group(3)==null?0:Long.parseLong(m.group(3));long ms=m.group(3)==null?a*60000+b:a*3600000+b*60000+c;if((m.group(3)==null?b:c)<60&&(m.group(3)==null||b<60))best=Math.max(best,ms);}catch(Exception ignored){}return best;}
 public void onInterrupt(){}
}
