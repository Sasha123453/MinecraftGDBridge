package local.gdbridge;
import java.util.function.IntConsumer;
import net.minecraft.server.MinecraftServer;

/** At most one bounded world operation per tick; UI/IPC remain responsive. */
public final class WorldWorkQueue {
    private record Job(String name,int total,int batch,IntConsumer cell,Runnable completed) {}
    private static volatile Job job;
    public static volatile int progress,total;
    public static volatile String operation="idle";
    private WorldWorkQueue(){}
    public static boolean busy(){return job!=null;}
    public static void cancel(){job=null;operation="idle";progress=total=0;}
    public static void start(String name,int count,int batch,IntConsumer cell,Runnable completed){if(busy()){WorldEditor.message="Wait for "+operation;return;}progress=0;total=count;operation=name;job=new Job(name,count,batch,cell,completed);}
    public static void tick(MinecraftServer server){
        Job current=job;if(current==null)return;long deadline=System.nanoTime()+8_000_000L;int limit=Math.min(current.total,progress+current.batch);
        try{while(job==current&&progress<limit&&System.nanoTime()<deadline){current.cell.accept(progress);progress++;}if(job!=current)return;
            WorldEditor.message=current.name+": "+progress+" / "+current.total;
            if(progress>=current.total){current.completed.run();if(job==current){job=null;operation="idle";}}
        }catch(RuntimeException error){job=null;operation="failed";WorldEditor.message=current.name+" failed: "+error.getMessage();}
    }
}
