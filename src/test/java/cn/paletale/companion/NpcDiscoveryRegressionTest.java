package cn.paletale.companion;

final class NpcDiscoveryRegressionTest {
    private static int checks;
    static void run() {
        String name="妖精奇遇商店兑换(左键跳转到宝箱获取界面)";
        var p=NpcDiscovery.place(CityNavigation.WORLD,"minecraft:player",name,154.6841865802189,39,44.70572288187195,false);
        require(p!=null&&p.name().equals(name),"The reported unregistered fairy shop becomes a local discovery candidate");
        require(CityCatalog.matchesName(p,"\ue001§a"+name,""),"Name matching uses the same cleanup as discovery and capture records");
        require(NpcDiscovery.valid(p)&&p.id().equals("scan-77e78c9591c88e485a6294f9"),"Automatic record identity matches the website's UTF-8 hash contract");
        require(NpcDiscovery.place(CityNavigation.MAIN_WORLD,"minecraft:player",name,p.x(),p.y(),p.z(),false)==null,"Mainland entities are never city discoveries");
        require(NpcDiscovery.place(CityNavigation.WORLD,"minecraft:player",name,p.x(),p.y(),p.z(),true)==null,"A listed real player is excluded even with a service-like display name");
        for(String type:new String[]{"minecraft:armor_stand","minecraft:item_display","minecraft:painting"})require(NpcDiscovery.place(CityNavigation.WORLD,type,name,1,39,2,false)==null,"Decorative entities are not collected: "+type);
        for(String label:new String[]{"SomePlayer","梦幻魔女套装","活动时间10月1日-12月15日",""})require(NpcDiscovery.place(CityNavigation.WORLD,"minecraft:player",label,1,39,2,false)==null,"Ordinary players and display labels lack NPC service evidence");
        var seen=new NpcDiscovery.Seen(name,p.x(),p.y(),p.z());
        require(!NpcDiscovery.stable(null,seen)&&NpcDiscovery.stable(seen,seen),"Discovery requires at least two stable observations");
        require(!NpcDiscovery.stable(seen,new NpcDiscovery.Seen(name,p.x()+2,p.y(),p.z())),"A moving candidate is not registered as a fixed service NPC");
        require(CityCatalog.discover(p)&&!CityCatalog.discover(p)&&CityCatalog.npcs().stream().anyMatch(n->n.id().equals(p.id())),"A new NPC is added once to the actual navigation catalog");
        require(NpcProfile.of(p,"a".repeat(64)).valid()&&NpcProfile.of(p,null,true).valid(),"New NPC appearances can queue fixed hashes or a viewer-dependent marker");
        require(NpcProfile.of(p,null).valid(),"New NPC coordinates are uploadable before a skin is ready");
        require(!new NpcProfile("scan-"+"0".repeat(24),CityNavigation.WORLD,name,p.entityType(),p.x(),p.y(),p.z(),"a".repeat(64),false).valid(),"A changed identity cannot be uploaded under another discovery id");
        System.out.println("PaleTale NPC discovery checks passed: "+checks);
    }
    private static void require(boolean ok,String message) { checks++;if(!ok)throw new AssertionError(message); }
}
