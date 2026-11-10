package cn.paletale.companion;
final class PrimaryPopulationRegressionTest {
 static void run(){
  String[] rejected={"全服在线：1000 主城：50","当前服务器在线：123","主服在线：12 主服在线：13","主服在线：1,234","主服在线：123.4","主服在线：10001"};
  for(String text:rejected)if(PrimaryPopulation.parse(text).isPresent())throw new AssertionError("Ambiguous main count: "+text);
  if(PrimaryPopulation.parse("§a主服在线: 0").orElseThrow().count()!=0||PrimaryPopulation.parse("主服在线：123").orElseThrow().count()!=123)throw new AssertionError("Explicit primary count rejected");
  System.out.println("PaleTale primary population checks passed: 8");
 }
}
