package com.example.subtitleoverlay;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public class SrtParser {
    private static final Pattern P=Pattern.compile("(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})\\s*-->\\s*(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})");
    private static final Pattern BRACE_TAG=Pattern.compile("\\{([^}\\r\\n]*)\\}");

    public static List<SrtCue> parse(InputStream in)throws IOException{
        String x=read(in).replace("\uFEFF","").replace("\r\n","\n").replace('\r','\n');
        List<SrtCue> out=new ArrayList<>();
        for(String b:x.split("\\n\\s*\\n")){
            String[] l=b.split("\\n"); Matcher m=null; int ti=-1;
            for(int i=0;i<l.length;i++){ Matcher q=P.matcher(l[i].trim()); if(q.find()){m=q;ti=i;break;} }
            if(m==null)continue;
            long s=t(m,1),e=t(m,5); StringBuilder z=new StringBuilder();
            for(int i=ti+1;i<l.length;i++){ if(z.length()>0)z.append('\n'); z.append(l[i].trim()); }
            String txt=cleanText(z.toString());
            if(!txt.isEmpty())out.add(new SrtCue(s,e,txt));
        }
        out.sort(Comparator.comparingLong(a->a.startMs)); return out;
    }

    private static String cleanText(String text){
        // Remove ASS/SSA-style override tags such as {\\an8}, {\\pos(...)}, {\\c&HFFFFFF&}, {\\i1}.
        text=BRACE_TAG.matcher(text).replaceAll(m -> looksLikeSubtitleTag(m.group(1)) ? "" : m.group(0));
        // Common ASS line-break/control escapes.
        text=text.replace("\\N","\n").replace("\\n","\n").replace("\\h"," ");
        // Remove HTML/SRT markup while keeping <br> as a line break.
        text=text.replaceAll("(?i)<br\\s*/?>","\\n").replaceAll("<[^>]+>","");
        // A few subtitle exporters put literal ASS tags outside braces.
        text=text.replaceAll("(?i)\\\\(an|pos|move|fad|fade|fs|fn|c|1c|2c|3c|4c|alpha|1a|2a|3a|4a|b|i|u|s|bord|shad|fr|frx|fry|frz|q|k|K|kf|ko|t|r)(?:\\([^)]*\\)|[^\\s]*)", "");
        return text.trim();
    }

    private static boolean looksLikeSubtitleTag(String s){
        String v=s.trim().toLowerCase(Locale.ROOT);
        if(v.isEmpty()) return false;
        if(v.startsWith("\\")) return true;
        if(v.contains("&h")) return true;
        return v.matches("(?:an|pos|move|fad|fade|fs|fn|c|1c|2c|3c|4c|alpha|1a|2a|3a|4a|b|i|u|s|bord|shad|fr|frx|fry|frz|q|k|kf|ko|t|r).*" );
    }

    private static long t(Matcher m,int i){return Long.parseLong(m.group(i))*3600000L+Long.parseLong(m.group(i+1))*60000L+Long.parseLong(m.group(i+2))*1000L+Long.parseLong(m.group(i+3));}
    private static String read(InputStream i)throws IOException{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[]b=new byte[8192];int n;while((n=i.read(b))!=-1)o.write(b,0,n);return new String(o.toByteArray(),StandardCharsets.UTF_8);}
}
