package cn.paletale.companion;
import java.util.*;
/** FIFO bounded by entries AND total retained UTF-16 characters, not a never-ending click registry. */
public final class ChatHistory<T> {
    public static final int CAPACITY=100, MAX_WEIGHT=1_000_000;
    private record Entry<T>(T value,int weight) {}
    private final LinkedHashMap<Long,Entry<T>> values=new LinkedHashMap<>();
    private long serial;private int weight;
    public long put(T value,int cost){
        if(cost<0||cost>MAX_WEIGHT)return -1;
        while(!values.isEmpty()&&(values.size()>=CAPACITY||weight+cost>MAX_WEIGHT)){var first=values.entrySet().iterator();weight-=first.next().getValue().weight();first.remove();}
        long id=++serial;values.put(id,new Entry<>(value,cost));weight+=cost;return id;
    }
    public T get(long id){var entry=values.get(id);return entry==null?null:entry.value();}
    public void clear(){values.clear();weight=0;}
    public int size(){return values.size();}public int weight(){return weight;}
}
