package cn.paletale.companion;
import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
/** Messages the player starred from chat (★信息). Local-only per Minecraft account; disk work never runs on the render thread. */
final class SavedChatStore {
 record Saved(String text,long time,String sender,String source){}
 static final int LIMIT=500;
 private static final Gson JSON=new Gson();
 private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"paletale-saved-chat");t.setDaemon(true);return t;});
 private static String account="";private static List<Saved> rows=List.of();private static boolean ready;private static long revision;
 static synchronized void ensure(){String a=LedgerStore.account();if(a.equals(account))return;account=a;rows=List.of();ready=false;revision++;if(!a.matches("[a-fA-F0-9]{32}"))return;
  IO.execute(()->{List<Saved> loaded=List.of();try{var f=file(a);if(Files.isRegularFile(f)&&Files.size(f)<=4*1024*1024){var value=JSON.fromJson(Files.readString(f),Saved[].class);if(value!=null)loaded=Arrays.stream(value).filter(SavedChatStore::valid).limit(LIMIT).toList();}}catch(Exception ignored){}
   synchronized(SavedChatStore.class){if(account.equals(a)){var merged=new ArrayList<>(loaded);for(var row:rows)if(!merged.contains(row))merged.add(row);merged.sort(Comparator.comparingLong(Saved::time));rows=List.copyOf(merged.subList(Math.max(0,merged.size()-LIMIT),merged.size()));ready=true;revision++;}}});}
 static synchronized List<Saved> entries(){ensure();return rows;}
 static synchronized long revision(){ensure();return revision;}
 /** Returns false when the same message is already starred or the text is unusable. */
 static synchronized boolean add(String text,long time,String sender,String source){ensure();var row=new Saved(text==null?"":text.strip(),time,sender==null?"":sender,source==null?"":source);if(!account.matches("[a-fA-F0-9]{32}")||!valid(row)||rows.stream().anyMatch(old->old.text().equals(row.text())&&old.time()/1000==row.time()/1000))return false;var next=new ArrayList<>(rows);next.add(row);next.sort(Comparator.comparingLong(Saved::time));while(next.size()>LIMIT)next.removeFirst();rows=List.copyOf(next);revision++;save();return true;}
 static synchronized void remove(Saved row){ensure();var next=new ArrayList<>(rows);if(!next.remove(row))return;rows=List.copyOf(next);revision++;save();}
 private static boolean valid(Saved row){return row!=null&&row.text()!=null&&!row.text().isBlank()&&row.text().length()<=2000&&row.time()>0&&row.sender()!=null&&row.sender().length()<=40&&row.source()!=null&&row.source().length()<=120;}
 private static Path file(String a){return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("paletale/private-chat-v1").resolve(a+".saved.json");}
 private static void save(){String a=account;if(!ready){IO.execute(()->{synchronized(SavedChatStore.class){if(ready&&account.equals(a))save();}});return;}var snapshot=rows;
  IO.execute(()->{try{var f=file(a);Files.createDirectories(f.getParent());var temp=Files.createTempFile(f.getParent(),"saved-",".tmp");try{Files.writeString(temp,JSON.toJson(snapshot));try{Files.move(temp,f,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,f,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}}catch(Exception ignored){}});}
 private SavedChatStore(){}
}
