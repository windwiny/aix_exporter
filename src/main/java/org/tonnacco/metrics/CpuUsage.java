package org.tonnacco.metrics;

import io.prometheus.client.Gauge;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.tonnacco.CmdUtils;

import java.util.concurrent.ArrayBlockingQueue;

public class CpuUsage {
    private static final Log log = LogFactory.getLog(Class.class);
    private static int setmetrics = 0;

    private static final String[] cmdarray = "sar 1 999".split("\\s+");
    private static final Gauge usage = Gauge.build()
            .name("aix_cpu")
            .help("aix cpu info,  sar 1 999")
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
                    // shared cpu 7 cols, de 6 cols
                    if (split.length < 6 || split[0].length() <= 3 || split[0].charAt(2) != ':')
                        continue;

                    int usr, sys, wio, idea;

                    try {
                        usr = Integer.parseInt(split[1]);
                        sys = Integer.parseInt(split[2]);
                        wio = Integer.parseInt(split[3]);
                        idea = Integer.parseInt(split[4]);
                    } catch (NumberFormatException e) {
                        continue; // skip title
                    }

                    usage.labels(hostname, "user").set(usr);
                    usage.labels(hostname, "sys").set(sys);
                    usage.labels(hostname, "io wait").set(wio);
                    usage.labels(hostname, "idea").set(idea);

                }
            }
        }).start();
    }


    private CpuUsage() {
    }
}
