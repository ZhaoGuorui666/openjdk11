import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) {

        ExecutorService executor = Executors.newFixedThreadPool(4);

        // 提交任务给线程池执行
        for (int i = 0; i < 10; i++) {
            int taskNumber = i;
            executor.submit(() -> {
                System.out.println("执行任务 " + taskNumber + "：" + Thread.currentThread().getName());
            });
        }

        // 关闭线程池
        executor.shutdown();
    }
}