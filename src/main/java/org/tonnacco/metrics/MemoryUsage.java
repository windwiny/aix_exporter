package org.tonnacco.metrics;

import io.prometheus.client.Gauge;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.tonnacco.CmdUtils;

import java.util.concurrent.ArrayBlockingQueue;

public class MemoryUsage {
    private static final Log log = LogFactory.getLog(Class.class);
    private static int setmetrics = 0;

    private static final String[] cmdarray = "svmon -G -i 5 240".split("\\s+");
    private static final Gauge usage = Gauge.build()
            .name("aix_memory")
            .help("aix memory info,  svmon -G -i 5 240")
            .labelNames("hostname", "column")
            .register();

    public static void Collect(final String hostname, final String thread_name) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayBlockingQueue<String> queue = CmdUtils.RunAndWatching(cmdarray, thread_name + "_cmd");
                Thread.currentThread().setName(thread_name);

                for (setmetrics = 0; ; setmetrics++) {
                    String ss;
                    try {
                        ss = queue.take().trim();
                    } catch (InterruptedException e) {
                        continue;
                    }

                    try {
                        if (ss.startsWith("memory")) {
                            String[] split = ss.split("\\s+");
                            int memory_mb = Integer.parseInt(split[1]) * 4 / 1024;
                            usage.labels(hostname, "memory_mb").set(memory_mb);
                        } else if (ss.startsWith("pg space")) {
                            String[] split = ss.split("\\s+");
                            int pgspace_mb = Integer.parseInt(split[2]) * 4 / 1024;
                            int pgspace_used = Integer.parseInt(split[3]) * 4 / 1024;
                            usage.labels(hostname, "pgspace_mb").set(pgspace_mb);
                            usage.labels(hostname, "pgspace_used").set(pgspace_used);
                        } else if (ss.startsWith("in use")) {
                            String[] split = ss.split("\\s+");
                            int comp_mb = Integer.parseInt(split[2]) * 4 / 1024;
                            int noncomp_mb = Integer.parseInt(split[4]) * 4 / 1024;
                            usage.labels(hostname, "comp_mb").set(comp_mb);
                            usage.labels(hostname, "noncomp_mb").set(noncomp_mb);
                        }
                    } catch (NumberFormatException e) {
                        //continue;
                    }

                }
            }
        }).start();
    }


    private MemoryUsage() {
    }
}
