package cn.paletale.companion;
final class ServerItemCatalogTest {
 static void run(){
  ServerItemCatalog.clearPublished();
  if(ServerItemCatalog.sameName("【玩偶头】奥波勒斯·扳机","✧✧奥波勒斯・扳机✧✧"))throw new AssertionError("Before the names are downloaded nothing is known");
  if(ServerItemCatalog.appearance("幅变芯片")!=null)throw new AssertionError("No mapping before the catalog is downloaded");
  ServerItemCatalog.applyNames(java.util.List.of("奥波勒斯·扳机","狡兔屋·安比","狡兔屋·妮可","风箱","蜂箱"));
  if(!ServerItemCatalog.sameName("【玩偶头】奥波勒斯·扳机","✧✧奥波勒斯・扳机✧✧"))throw new AssertionError("Known doll label/sign aliases match");
  if(ServerItemCatalog.sameName("【玩偶头】狡兔屋·安比","❈❈狡兔屋・妮可❈❈"))throw new AssertionError("Different dolls never match");
  if(ServerItemCatalog.sameName("风箱","蜂箱"))throw new AssertionError("Pinyin is not identity");
  if(ServerItemCatalog.key("大豆 ★★★").equals(ServerItemCatalog.key("大豆 ★★✰")))throw new AssertionError("Quality stars are preserved");
  try{ServerItemCatalog.decodeNames("{\"version\":2,\"names\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));throw new AssertionError("Unknown names version rejected");}catch(IllegalArgumentException expected){}
  if(!ServerItemCatalog.decodeNames("{\"version\":1,\"names\":[\"a\",\"b\"]}".getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(java.util.List.of("a","b")))throw new AssertionError("Names file decodes");
  ServerItemCatalog.clearPublished();
  if(ServerItemCatalog.sameName("风箱","风箱"))throw new AssertionError("Site change forgets the old names");
  ServerItemCatalogSyncTest.run();
 }
}
