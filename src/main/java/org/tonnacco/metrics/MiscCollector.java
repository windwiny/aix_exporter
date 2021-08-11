package org.tonnacco.metrics;

import io.prometheus.client.Collector;
import io.prometheus.client.Gauge;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.tonnacco.CmdUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MiscCollector extends Collector {
    private static final Log log = LogFactory.getLog(Class.class);
    private static final List<MetricFamilySamples> _list = new ArrayList<MetricFamilySamples>();
    private static String hostname;
    private static String basedir;

    public MiscCollector(final String hostname2, final String basedir2) {
        hostname = hostname2;
        basedir = basedir2;
    }

    public static void showStatus() {
        log.warn(String.format(
                "ierr_pswc: %d   ierr_errpt: %d   ierr_alert: %d   ierr_jfs2: %d\n",
                ierr_pswc, ierr_errpt, ierr_alert, ierr_jfs2));
    }


    private static final Gauge aix_miscmetrics_used_time = Gauge.build()
            .name("aix_misc_metrics_used_time")
            .help("aix collect misc metrics used time")
            .labelNames("hostname", "column")
            .register();

    @Override
    public List<MetricFamilySamples> collect() {
        aix_miscmetrics_used_time.clear();

        long b1, e1;
        long begin = System.nanoTime();

        b1 = System.nanoTime();
        getProcessNumber();
        e1 = System.nanoTime();
        aix_miscmetrics_used_time.labels(hostname, "misc_1").set((e1 - b1) / 1000000f);

        b1 = System.nanoTime();
        getErrptWc();
        e1 = System.nanoTime();
        aix_miscmetrics_used_time.labels(hostname, "misc_2").set((e1 - b1) / 1000000f);

        b1 = System.nanoTime();
        getFileSystemFree();
        e1 = System.nanoTime();
        aix_miscmetrics_used_time.labels(hostname, "misc_3").set((e1 - b1) / 1000000f);

        b1 = System.nanoTime();
        getAlertlogfileSize();
        e1 = System.nanoTime();
        aix_miscmetrics_used_time.labels(hostname, "misc_4").set((e1 - b1) / 1000000f);

        long end = System.nanoTime();
        aix_miscmetrics_used_time.labels(hostname, "misc_total").set((end - begin) / 1000000f);

        return _list;
    }

    private static final Gauge pswc_usage = Gauge.build()
            .name("aix_pswc")
            .help("aix ps number info,  ps -ef | wc")
            .labelNames("hostname")
            .register();

    private static int ierr_pswc;

    private static void getProcessNumber() {
        pswc_usage.clear();

        final String[] cmdarray = new String[]{"sh", "-c", "ps -ef | wc"};
        long begin = System.nanoTime();
        List<String> strs = CmdUtils.RunAndGet(cmdarray);
        long end = System.nanoTime();
        aix_miscmetrics_used_time.labels(hostname, "pswc").set((end - begin) / 1000000f);

        if (strs.size() < 1) return;

        int pswc;
        String[] split = strs.get(0).trim().split("\\s+");
        try {
            pswc = Integer.parseInt(split[0]);
            pswc_usage.labels(hostname).set(pswc);
        } catch (NumberFormatException e) {
            ierr_pswc++;
            if (ierr_pswc < 10)
                log.warn(e);
        }
    }


    private static final Gauge errptusage = Gauge.build()
            .name("aix_errpt")
            .help("aix errpt info")
            .labelNames("hostname", "etype", "eclass")
            .register();

    private static int ierr_errpt;

    private static void getErrptWc() {
        errptusage.clear();

        List<String> ress1, ress2;
        int eno;
        try {
            long begin = System.nanoTime();
            ress1 = CmdUtils.RunAndGet(new String[]{"sh", "-c", "errpt -T PERM -d H | wc "});
            ress2 = CmdUtils.RunAndGet(new String[]{"sh", "-c", "errpt -T PERM -d S | wc "});
            long end = System.nanoTime();
            aix_miscmetrics_used_time.labels(hostname, "errpt").set((end - begin) / 1000000f);

            if (ress1.size() > 0) {
                eno = Integer.parseInt(ress1.get(0).trim().split(" ")[0]);
                errptusage.labels(hostname, "PERM", "HARD").set(eno);
            }
            if (ress2.size() > 0) {
                eno = Integer.parseInt(ress2.get(0).trim().split(" ")[0]);
                errptusage.labels(hostname, "PERM", "SOFT").set(eno);
            }
        } catch (NumberFormatException e) {
            ierr_errpt++;
            if (ierr_errpt < 10)
                log.warn(e);
        }
    }


    private static final Gauge jfs2_usage = Gauge.build()
            .name("aix_jfs2")
            .help("aix jfs2 info,  /usr/sysv/bin/df -lg")
            .labelNames("hostname", "mountpoint", "filetype", "column")
            .register();

    private static int ierr_jfs2;

    private static void getFileSystemFree() {
        jfs2_usage.clear();

        final String[] cmdarray = "/usr/sysv/bin/df -lg".split("\\s+");
        long begin = System.nanoTime();
        List<String> ress = CmdUtils.RunAndGet(cmdarray);
        long end = System.nanoTime();
        aix_miscmetrics_used_time.labels(hostname, "df").set((end - begin) / 1000000f);

        Pattern reg_block_size = Pattern.compile("(\\d+) block size");
        Pattern reg_total_avail_blocks_total_fs = Pattern.compile("(\\d+) total blocks.*?(\\d+) available.*?(\\d+) total files");
        Pattern reg_free_fs = Pattern.compile(".*?(\\d+) free files");
        Pattern reg_fs_type = Pattern.compile(".*?(\\w+) fstype");

        int fsnum = 0;
        String mountpoint;
        String str;
        String[] split;
        Matcher m;
        for (int i = 0; i < ress.size(); i++) {
            str = ress.get(i);

            if (str.length() > 1 && str.charAt(0) == '/') {
                split = str.split("\\(");
                mountpoint = split[0].trim();

//                m = procLine(str, reg_block_size, "err proc block size");
//                int block_size; // 4096, not 512 byte peer sector
//                if (m != null)
//                    block_size = Integer.parseInt(m.group(1));
//                else break;

                i++;
                str = ress.get(i);
                m = procLine(str, reg_total_avail_blocks_total_fs, "err proc total avail");
                float total_blocks;
                float available_blocks;
                float total_files;
                if (m != null) {
                    total_blocks = Float.parseFloat(m.group(1));
                    available_blocks = Float.parseFloat(m.group(2));
                    total_files = Float.parseFloat(m.group(3));
                } else {
                    ierr_jfs2++;
                    break;
                }

                i++;
                str = ress.get(i);
                m = procLine(str, reg_free_fs, "err proc free fs");
                float free_files;
                if (m != null)
                    free_files = Float.parseFloat(m.group(1));
                else {
                    ierr_jfs2++;
                    break;
                }

                i++;
                str = ress.get(i);
                m = procLine(str, reg_fs_type, "err proc fs type");
                String filetype;
                if (m != null)
                    filetype = m.group(1);
                else {
                    ierr_jfs2++;
                    break;
                }

                if (mountpoint.equals("/proc")) continue;

                jfs2_usage.labels(hostname, mountpoint, filetype, "total_byte").set(total_blocks * 512);
                jfs2_usage.labels(hostname, mountpoint, filetype, "free_byte").set(available_blocks * 512);
                jfs2_usage.labels(hostname, mountpoint, filetype, "total_files").set(total_files);
                jfs2_usage.labels(hostname, mountpoint, filetype, "free_files").set(free_files);
                fsnum++;
            }
        }
    }

    private static Matcher procLine(String str, Pattern reg, String msg) {
        Matcher m;
        m = reg.matcher(str);
        if (!m.find()) {
            log.warn(msg + " not match " + reg + "  " + str);
            return null;
        }
        return m;
    }


    private static final Gauge alertlogusage = Gauge.build()
            .name("oracle_alert_log")
            .help("oracle alert_XX.log file size")
            .labelNames("hostname", "SID")
            .register();

    private static int ierr_alert = 0;

    private static Map<String, Long> file2size = new HashMap<String, Long>();

    private static void getAlertlogfileSize() {
        alertlogusage.clear();

        if (basedir == null || basedir.equals(""))
            basedir = "/u01/oracle/diag";
        final String mat = "rdbms/*/*/trace/alert_*.log";

        long begin = System.nanoTime();
        List<String> files = MyGlobScanner.Scan(basedir, mat);
        long end = System.nanoTime();
        aix_miscmetrics_used_time.labels(hostname, "alert_log").set((end - begin) / 1000000f);

        for (String full_filename : files) {
            full_filename = basedir + File.separator + full_filename;
            File fo1 = new File(full_filename);

            long lastTime = fo1.lastModified();
            long currTime = System.currentTimeMillis();

            long thDay = 24 * 60 * 60 * 1000;
            if ((currTime - lastTime) > thDay) {
                // skip modifer time > 24 hours
                continue;
            }

            String instname = fo1.getName().replace("alert_", "").replace(".log", "");
            long prev_size = 0;
            Long ii = file2size.get(full_filename);
            if (ii != null) prev_size = ii;
            long curr_size = fo1.length();

            alertlogusage.labels(hostname, instname).inc(curr_size); // - prev_size
            file2size.put(full_filename, curr_size);
        }
    }

}
