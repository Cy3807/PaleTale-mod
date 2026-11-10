package cn.paletale.companion;
final class ModVersionTest {
 static void run(){
  if(!ModVersion.published("1.0.1-beta")||ModVersion.compare("1.0.1-beta","1.0.1-beta.2")>=0||ModVersion.compare("1.0.1-beta","1.0.1")>=0)throw new AssertionError("Unnumbered beta is a prerelease");
  if(!ModVersion.published("0.12.25-beta.1")||!ModVersion.published("0.11.13")||ModVersion.published("0.12.25-dev")||ModVersion.published("0.12.25-beta.0")||ModVersion.published("0.12.25-beta.1/evil"))throw new AssertionError("Only stable/public beta versions are accepted");
  if(ModVersion.compare("0.12.25-beta.1","0.12.22")<=0||ModVersion.compare("0.12.25","0.12.25-beta.9")<=0||ModVersion.compare("0.12.25-beta.10","0.12.25-beta.2")<=0||ModVersion.compare("0.12.25-beta.1","0.12.25-dev")<=0||ModVersion.compare("0.12.25-beta.1","0.12.25-beta.1")!=0)throw new AssertionError("Beta/stable/dev comparisons are ordered correctly");
 }
}
