package org.tonnacco;

import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.tonnacco.metrics.*;

import java.io.File;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;


public class WebMain {
    private static final Log log = LogFactory.getLog(Class.class);

    public static void main(String[] args) throws Exception {

        String hostname;
        String listenaddr;
        String diagdir;

        String cfgfile;
        if (args.length == 2 && args[0].equals("-h")) {
            System.out.println("syntax:  java xx.jar [ -config config.properties ]");
            System.exit(0);
        }

        if (args.length == 2 && args[0].equals("-config"))
            cfgfile = args[1];
        else
            cfgfile = System.getProperty("user.dir") + File.separator + "config.properties";
        PropertiesConfiguration cfg = new PropertiesConfiguration();
        try {
            cfg.load(cfgfile);
        } catch (ConfigurationException e) {
            log.warn(e);
        }

        hostname = cfg.getString("hostname", "");
        if (hostname.equals("")) {
            log.info("hostname not set ");
            hostname = InetAddress.getLocalHost().getHostName();
            if (hostname.equals("loopback")) {
                hostname = CmdUtils.RunAndGet("hostname").trim();
            }
        }
        log.info("hostname: " + hostname);
        listenaddr = cfg.getString("listenaddr", "0.0.0.0:5555");
        log.info("listenaddr: " + listenaddr);
        diagdir = cfg.getString("diagnostic_dest", "/u01/oracle/diag");
        log.info("diagnostic_dest: " + diagdir);


        String[] hostnamePort = listenaddr.split(":");
        int port;
        String host;

        if (hostnamePort.length == 2) {
            port = Integer.parseInt(hostnamePort[1]);
            host = hostnamePort[0];
        } else {
            port = Integer.parseInt(hostnamePort[0]);
            host = "0.0.0.0";
        }

        DefaultExports.initialize();
        runall(hostname, diagdir);

        log.info("listen on: " + host + ":" + port);
        new HTTPServer(host, port);
    }

    public static void runall(String hostname, String diagdir) {
        CpuUsage.Collect(hostname, "metrics_cpuusage");
        MemoryUsage.Collect(hostname, "metrics_memoryusage");
        DiskBusy.Collect(hostname, "metrics_diskbusy");
        NetworkTraffic.Collect();
        new MiscCollector(hostname, diagdir).register();

        Thread.currentThread().setName("metrics_mgr");

//        Signal.handle(new Signal("HUP"), new SignalHandler() {
//            @SuppressWarnings("restriction")
//            public void handle(Signal signal) {
//                CmdUtils.listMetricThread();
//                AlertLogfileSize.showStatus();
//            }
//        });
//        Signal.handle(new Signal("INT"), new SignalHandler() {
//            @SuppressWarnings("restriction")
//            public void handle(Signal signal) {
//                CmdUtils.killAllExternalProc();
//                System.exit(0);
//            }
//        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("statusCheck");
                for (; ; ) {
                    CmdUtils.listMetricThread();
                    MiscCollector.showStatus();
                    CmdUtils.listProcess();
                    try {
                        TimeUnit.MINUTES.sleep(600);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }
}
