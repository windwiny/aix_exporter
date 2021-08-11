package org.tonnacco;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class CmdUtils {
    public static final Log log = LogFactory.getLog(Class.class);
    public static final HashMap<Process, String> runExternalProc = new HashMap<Process, String>();

    public static LinkedBlockingQueue<String> RunOne(final String[] cmdarray, int lines) {
        String cmdss = Arrays.toString(cmdarray);

        Process process;
        try {
            process = new ProcessBuilder().command(Arrays.asList(cmdarray)).start();
        } catch (IOException e) {
            log.error(e);
            return null;
        }

        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<String>(lines);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        final InputStream errorStream = process.getErrorStream();
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        if ((errorStream.read() == -1)) break;
                    } catch (IOException e) {
                        break;
                    }
                }
            }
        }).start();

        for (; ; ) {
            try {
                String s1 = bufferedReader.readLine();
                if (s1 == null) break;
                if (queue.remainingCapacity() == 0) {
                    process.destroy();
                    break;
                }
                queue.add(s1);
            } catch (IOException e) {
                log.error(e);
            }
        }

        int res;
        try {
            for (; ; ) {
                String s = null;
                try {
                    s = bufferedReader.readLine();
                } catch (IOException ignored) {
                }
                if (s == null) break;
            }
            res = process.waitFor();
            if (res != 0)
                log.warn("END " + cmdss + " res: " + res);
        } catch (InterruptedException e) {
            log.warn(e);
        }

        return queue;
    }

    public static ArrayBlockingQueue<String> RunAndWatching(final String[] cmdarray, final String thread_name) {
        final ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<String>(20);

        new Thread(new Runnable() {
            public void run() {
                Thread.currentThread().setName(thread_name);
                runAndWatching(cmdarray, queue);
            }
        }).start();
        return queue;
    }

    private static void runAndWatching(String[] cmdarray, ArrayBlockingQueue<String> queue) {
        Process process;

        for (int run_num = 1; ; run_num++) {
            String cmdss = "(" + run_num + ")" + Arrays.toString(cmdarray);

            log.info("BEGIN " + cmdss);
            try {
                process = new ProcessBuilder().command(Arrays.asList(cmdarray)).start();
            } catch (IOException e) {
                System.err.println("BEGIN " + cmdss + " FAILED! exit . ");
                log.error(e);
                return;
            }

            InputStream inputStream = process.getInputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

            // FIXME
            runExternalProc.put(process, Thread.currentThread().getName() + "_" + run_num);
            log.warn("add pool: " + process);

            final InputStream errorStream = process.getErrorStream();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            if (errorStream.read() == -1) break;
                        } catch (IOException e) {
                            break;
                        }
                    }
                }
            }).start();

            int err = 0;
            for (int i = 0; ; i++) {    // read STDOUT
                try {
                    String s = bufferedReader.readLine();
                    if (s == null) break;
                    if (queue.remainingCapacity() == 0) {
                        log.info(cmdss + " queue full! row no: " + i);
                        try {
                            TimeUnit.MILLISECONDS.sleep(50);
                        } catch (InterruptedException ignored) {
                        }
                    } else {
                        try {
                            queue.put(s);
                        } catch (InterruptedException e) {
                            err++;
                            if (err > 100) {
                                log.warn(cmdss + " failed too much , exit");
                                //break;
                            }
                        }
                    }
                } catch (IOException e) {
                    log.error("  thread read IO " + e + " break.");
                    //break;
                }
            }

            log.info("WILL END " + cmdss);
            int res;
            try {
                for (; ; ) {
                    String s = null;
                    try {
                        s = bufferedReader.readLine();
                    } catch (IOException ignored) {
                    }
                    if (s == null) break;
                }
                res = process.waitFor();
                log.info("END " + cmdss + " res: " + res);
            } catch (InterruptedException e) {
                log.info("END " + cmdss + " err: ");
                e.printStackTrace();
            }

            runExternalProc.remove(process);
            log.warn("remove from pool: " + process);

            try {
                TimeUnit.SECONDS.sleep(4);
            } catch (InterruptedException ignored) {
            }
        }
    }

    public static void listMetricThread() {
        Date t = new Date();
        Set<Thread> threads = Thread.getAllStackTraces().keySet();
        for (Thread thread : threads) {
            String name = thread.getName();
            if (name.startsWith("metrics")) {
                log.info(name + "  is Alive at " + t);
            }
        }
        log.info(runExternalProc.size());
    }

    public static boolean checkThreadAlive(String thread_naem_prefix) {
        Set<Thread> threads = Thread.getAllStackTraces().keySet();
        for (Thread thread : threads) {
            String name = thread.getName();
            if (name.startsWith(thread_naem_prefix + "_cmd")) {
                return thread.isAlive();
            }
        }
        return false;
    }

    public static void killAllExternalProc() {
        for (Process in : runExternalProc.keySet()) {
            in.destroy();
            log.warn(" destory process  " + runExternalProc.get(in));
//                log.warn("close stdout for process ok.   ");
        }
    }

    private CmdUtils() {
    }

    public static List<String> RunAndGet(String cmd) {
        return RunAndGet(new String[]{cmd});
    }

    public static List<String> RunAndGet(String[] cmdarray) {
        LinkedList<String> sb = new LinkedList<String>();

        try {
            Process exec = Runtime.getRuntime().exec(cmdarray);
            BufferedReader bf = new BufferedReader(new InputStreamReader(exec.getInputStream()));
            String s;
            while ((s = bf.readLine()) != null) {
                sb.add(s);
            }
            exec.waitFor();
        } catch (IOException e) {
            log.warn(e);
        } catch (SecurityException e) {
            log.warn(e);
        } catch (NullPointerException e) {
            log.warn(e);
        } catch (IllegalArgumentException e) {
            log.warn(e);
        } catch (InterruptedException e) {
            log.warn(e);
        }
        return sb;
    }

    public static void listProcess() {
        for (Process process : runExternalProc.keySet()) {
            log.info(" run -> " + process);
        }
    }
}
