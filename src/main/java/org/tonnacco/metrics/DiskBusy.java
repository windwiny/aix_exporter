package org.tonnacco.metrics;

import io.prometheus.client.Gauge;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.tonnacco.CmdUtils;

import java.util.concurrent.ArrayBlockingQueue;

public class DiskBusy {
    private static final Log log = LogFactory.getLog(Class.class);

    private static int setmetrics = 0;
    private static final String[] cmdarray = "sar -b 1 999".split("\\s+");
    private static final Gauge usage = Gauge.build()
            .name("aix_diskbusy")
            .help("aix disk busy info,  sar -b 1 999")
            .labelNames("hostname", "column")
            .register();

    public static void Collect(final String hostname, final String thread_name) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayBlockingQueue<String> queue = CmdUtils.RunAndWatching(cmdarray, thread_name + "_cmd");
                Thread.currentThread().setName(thread_name);
                for (setmetrics = 0; ; setmetrics++) {
                    String s;
                    try {
                        s = queue.take().trim();
                    } catch (InterruptedException e) {
                        continue;
                    }


                    String[] split = s.split("\\s+");
                    if (split.length != 9 || split[0].length() <= 3 || split[0].charAt(2) != ':')
                        continue;

                    int br, bw;

                    try {
                        br = Integer.parseInt(split[1]);
                        bw = Integer.parseInt(split[4]);
                    } catch (NumberFormatException e) {
                        continue; // skip title
                    }

                    usage.labels(hostname, "block_read").set(br);
                    usage.labels(hostname, "block_write").set(bw);

                }
            }
        }).start();
    }


    private DiskBusy() {
    }
}
