package cn.paletale.companion;
import com.google.gson.JsonObject;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
final class DiskPagesClientTest {
 static void run()throws Exception{
  String scope="test-disk-pages:"+System.nanoTime();var row=new JsonObject();row.addProperty("name","fixture");DiskPages.write(scope,"one",row);
  var loaded=DiskPages.read(scope,"one",JsonObject.class).get(10,TimeUnit.SECONDS);if(loaded==null||!loaded.equals(row))throw new AssertionError("file snapshot survives without in-memory page map");
  if(DiskPages.read(scope+"-other","one",JsonObject.class).get(10,TimeUnit.SECONDS)!=null)throw new AssertionError("separate identities cannot read each other's snapshots");
  var archive=new ArchiveData.Page(1,4,0,false,java.util.List.of());DiskPages.write(scope,"archive",archive);if(DiskPages.read(scope,"archive",ArchiveData.Page.class).get(10,TimeUnit.SECONDS)==null)throw new AssertionError("archive records round trip");
  var file=DiskPages.class.getDeclaredMethod("file",String.class,String.class);file.setAccessible(true);Files.writeString((Path)file.invoke(null,scope,"one"),"{broken");if(DiskPages.read(scope,"one",JsonObject.class).get(10,TimeUnit.SECONDS)!=null)throw new AssertionError("corrupt file safely ignored");
  DiskPages.clear(scope);if(DiskPages.read(scope,"one",JsonObject.class).get(10,TimeUnit.SECONDS)!=null)throw new AssertionError("invalidated files are gone");
 }
}
