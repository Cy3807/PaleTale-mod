package cn.paletale.companion;
final class SoldOutBatchTest {
 static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void run(){var batch=new SoldOutBatch();for(int i=0;i<100;i++)batch.offer("a","shop"+i,1000+i);batch.offer("a","shop1",1200);require(batch.drain(2000)==0,"Burst waits briefly to aggregate");require(batch.drain(2400)==100,"100 shops become one summary count with deduplication");require(batch.drain(2500)==0,"A summary is not repeatedly emitted");batch.offer("a","shop101",3000);require(batch.drain(4300)==101,"Later nearby events update the same summary");batch.offer("a","shop1",33000);require(batch.drain(34300)==1,"New window counts new changes");batch.offer("b","other",35000);require(batch.drain(36300)==1,"Different account/world does not inherit prior shops");batch.reset();require(batch.drain(40000)==0,"Disconnect clears pending notices");}
}
