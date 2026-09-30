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
    public static float speed(Context c){ return c.getSharedPreferences(PREF,0).getFloat("speed",1f); }
    public static void speed(Context c,float v){ c.getSharedPreferences(PREF,0).edit().putFloat("speed", clamp(v,.25f,3f)).apply(); }
    public static String libraryTree(Context c) { return c.getSharedPreferences(PREF,0).getString("library_tree", ""); }
    public static void libraryTree(Context c,String v){ c.getSharedPreferences(PREF,0).edit().putString("library_tree",v).apply(); }
    public static boolean bgEnabled(Context c){ return c.getSharedPreferences(PREF,0).getBoolean("bg_enabled",true); }
    public static void bgEnabled(Context c,boolean v){ c.getSharedPreferences(PREF,0).edit().putBoolean("bg_enabled",v).apply(); }

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
