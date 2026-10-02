package com.example.subtitleoverlay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import java.io.File;

public final class SettingsStore {
    private SettingsStore() {}
    private static final String PREF = "subtitle_settings";
    public static final int DEFAULT_TEXT = Color.WHITE;
    public static final int DEFAULT_BG = Color.argb(170, 0, 0, 0);
    public static final float DEFAULT_SIZE = 24f;
    public static final String DEFAULT_FONT = "sans-serif";

    public static float size(Context c) { return c.getSharedPreferences(PREF,0).getFloat("size", DEFAULT_SIZE); }
    public static void size(Context c,float v){ c.getSharedPreferences(PREF,0).edit().putFloat("size", clamp(v,12,72)).apply(); }
    public static int textColor(Context c){ return c.getSharedPreferences(PREF,0).getInt("text", DEFAULT_TEXT); }
    public static void textColor(Context c,int v){ c.getSharedPreferences(PREF,0).edit().putInt("text",v).apply(); }
    public static int bgColor(Context c){ return c.getSharedPreferences(PREF,0).getInt("bg", DEFAULT_BG); }
    public static void bgColor(Context c,int v){ c.getSharedPreferences(PREF,0).edit().putInt("bg",v).apply(); }
    public static String font(Context c){ return c.getSharedPreferences(PREF,0).getString("font", DEFAULT_FONT); }
    public static void font(Context c,String v){ c.getSharedPreferences(PREF,0).edit().putString("font",v).apply(); }
    public static long offset(Context c){ return c.getSharedPreferences(PREF,0).getLong("offset",0); }
    public static void offset(Context c,long v){ c.getSharedPreferences(PREF,0).edit().putLong("offset",v).apply(); }
    public static long offset2(Context c){ return c.getSharedPreferences(PREF,0).getLong("offset2",0); }
    public static void offset2(Context c,long v){ c.getSharedPreferences(PREF,0).edit().putLong("offset2",v).apply(); }
    public static float speed(Context c){ return c.getSharedPreferences(PREF,0).getFloat("speed",1f); }
    public static void speed(Context c,float v){ c.getSharedPreferences(PREF,0).edit().putFloat("speed", clamp(v,.25f,3f)).apply(); }
    public static String primaryUri(Context c){ return c.getSharedPreferences(PREF,0).getString("primary_uri", ""); }
    public static void primaryUri(Context c,String v){ c.getSharedPreferences(PREF,0).edit().putString("primary_uri",v).apply(); }
    public static String secondaryUri(Context c){ return c.getSharedPreferences(PREF,0).getString("secondary_uri", ""); }
    public static void secondaryUri(Context c,String v){ c.getSharedPreferences(PREF,0).edit().putString("secondary_uri",v).apply(); }
    public static String libraryTree(Context c) { return c.getSharedPreferences(PREF,0).getString("library_tree", ""); }
    public static void libraryTree(Context c,String v){ c.getSharedPreferences(PREF,0).edit().putString("library_tree",v).apply(); }
    public static boolean bgEnabled(Context c){ return c.getSharedPreferences(PREF,0).getBoolean("bg_enabled",true); }
    public static void bgEnabled(Context c,boolean v){ c.getSharedPreferences(PREF,0).edit().putBoolean("bg_enabled",v).apply(); }
    // 0 = full width, 1 = fit subtitle, 2 = none. New default is fit-to-subtitle.
    public static int bgMode(Context c){ return c.getSharedPreferences(PREF,0).getInt("bg_mode",1); }
    public static void bgMode(Context c,int v){ c.getSharedPreferences(PREF,0).edit().putInt("bg_mode",Math.max(0,Math.min(2,v))).apply(); }
    // Padding in dp around the subtitle background.
    public static int bgPadding(Context c){ return c.getSharedPreferences(PREF,0).getInt("bg_padding",6); }
    public static void bgPadding(Context c,int v){ c.getSharedPreferences(PREF,0).edit().putInt("bg_padding",Math.max(0,Math.min(24,v))).apply(); }
    // 30,50,70,85,100 percent.
    public static int bgOpacity(Context c){ return c.getSharedPreferences(PREF,0).getInt("bg_opacity",70); }
    public static void bgOpacity(Context c,int v){ c.getSharedPreferences(PREF,0).edit().putInt("bg_opacity",Math.max(10,Math.min(100,v))).apply(); }
    public static boolean doubleSubtitle(Context c){ return c.getSharedPreferences(PREF,0).getBoolean("double_subtitle",false); }
    public static void doubleSubtitle(Context c,boolean v){ c.getSharedPreferences(PREF,0).edit().putBoolean("double_subtitle",v).apply(); }
    public static boolean secondaryFirst(Context c){ return c.getSharedPreferences(PREF,0).getBoolean("secondary_first",false); }
    public static void secondaryFirst(Context c,boolean v){ c.getSharedPreferences(PREF,0).edit().putBoolean("secondary_first",v).apply(); }
    // Overlay controls auto-hide: 0 = always visible, otherwise seconds.
    public static int controlsAutoHide(Context c){ return c.getSharedPreferences(PREF,0).getInt("controls_auto_hide",3); }
    public static void controlsAutoHide(Context c,int v){ c.getSharedPreferences(PREF,0).edit().putInt("controls_auto_hide",v==0?0:Math.max(1,Math.min(60,v))).apply(); }

    // 0 = bottom, 1 = lower, 2 = center, 3 = upper, 4 = top.
    public static int position(Context c){ return c.getSharedPreferences(PREF,0).getInt("position",0); }
    public static void position(Context c,int v){ c.getSharedPreferences(PREF,0).edit().putInt("position", Math.max(0,Math.min(4,v))).apply(); }

    public static String customFontPath(Context c){ return c.getSharedPreferences(PREF,0).getString("custom_font_path", ""); }
    public static void customFontPath(Context c,String v){ c.getSharedPreferences(PREF,0).edit().putString("custom_font_path",v).apply(); }
    public static String customFontName(Context c){ return c.getSharedPreferences(PREF,0).getString("custom_font_name", ""); }
    public static void customFontName(Context c,String v){ c.getSharedPreferences(PREF,0).edit().putString("custom_font_name",v).apply(); }
    public static boolean hasCustomFont(Context c){ String p=customFontPath(c); return p!=null && !p.isEmpty() && new File(p).exists(); }

    public static String fontLabel(String f){
        if("custom".equals(f)) return "Custom";
        if("serif".equals(f)) return "Serif";
        if("monospace".equals(f)) return "Monospace";
        if("sans-serif-condensed".equals(f)) return "Condensed";
        if("sans-serif-medium".equals(f)) return "Sans Medium";
        return "Sans";
    }
    public static Typeface typeface(String f){ return Typeface.create(f, Typeface.NORMAL); }
    public static Typeface typeface(Context c,String f){
        if("custom".equals(f) && hasCustomFont(c)){
            try { return Typeface.createFromFile(customFontPath(c)); } catch(Exception ignored) {}
        }
        return typeface(f);
    }
    private static float clamp(float v,float a,float b){return Math.max(a,Math.min(b,v));}
}
