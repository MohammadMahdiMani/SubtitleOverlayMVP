package com.example.subtitleoverlay;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.regex.*;
public class SrtParser {
 private static final Pattern P=Pattern.compile("(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})\\s*-->\\s*(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})");
 public static List<SrtCue> parse(InputStream in)throws IOException{String x=read(in).replace("\uFEFF","").replace("\r\n","\n").replace('\r','\n');List<SrtCue> out=new ArrayList<>();for(String b:x.split("\\n\\s*\\n")){String[] l=b.split("\\n");Matcher m=null;int ti=-1;for(int i=0;i<l.length;i++){Matcher q=P.matcher(l[i].trim());if(q.find()){m=q;ti=i;break;}}if(m==null)continue;long s=t(m,1),e=t(m,5);StringBuilder z=new StringBuilder();for(int i=ti+1;i<l.length;i++){if(z.length()>0)z.append('\n');z.append(l[i].trim());}String txt=z.toString().replaceAll("<br\\s*/?>","\n").replaceAll("<[^>]+>","").trim();if(!txt.isEmpty())out.add(new SrtCue(s,e,txt));}out.sort(Comparator.comparingLong(a->a.startMs));return out;}
 private static long t(Matcher m,int i){return Long.parseLong(m.group(i))*3600000L+Long.parseLong(m.group(i+1))*60000L+Long.parseLong(m.group(i+2))*1000L+Long.parseLong(m.group(i+3));}
 private static String read(InputStream i)throws IOException{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[]b=new byte[8192];int n;while((n=i.read(b))!=-1)o.write(b,0,n);return new String(o.toByteArray(),StandardCharsets.UTF_8);}
}
