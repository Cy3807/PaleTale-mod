package cn.paletale.companion;
/** Public beta versions are distinct from stable builds and internal dev snapshots. */
final class ModVersion {
 static boolean published(String value){return value.matches("[0-9]+(?:\\.[0-9]+){1,3}(?:-beta(?:\\.[1-9][0-9]*)?)?");}
 static int compare(String a,String b){
  String[] left=a.split("-",2),right=b.split("-",2),x=left[0].split("\\."),y=right[0].split("\\.");
  for(int i=0;i<Math.max(x.length,y.length);i++){int p=i<x.length?number(x[i]):0,q=i<y.length?number(y[i]):0;if(p!=q)return Integer.compare(p,q);}
  int lc=channel(left),rc=channel(right);if(lc!=rc)return Integer.compare(lc,rc);
  return lc==1?Integer.compare(betaNumber(left[1]),betaNumber(right[1])):0;
 }
 private static int channel(String[] value){return value.length==1?2:(value[1].equals("beta")||value[1].startsWith("beta."))?1:0;}
 private static int betaNumber(String value){return value.equals("beta")?1:number(value.substring(5));}
 private static int number(String value){try{return Integer.parseInt(value);}catch(NumberFormatException invalid){return 0;}}
}
