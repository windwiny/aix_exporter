package org.tonnacco.metrics;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyGlobScanner {


    public static List<String> Scan(String basedir, String matstr) {

        String[] mat = matstr.split("/");
        Pattern[] patterns = new Pattern[mat.length];

        for (int i = 0; i < mat.length; i++) {
            StringBuilder sb = new StringBuilder();
//            mat[i].replace("*", ".*").replace("?", ".")
            for (char c : mat[i].toCharArray()) {
                if (c == '.') {
                    sb.append("\\.");
                } else if (c == '*') {
                    sb.append(".*");
                } else if (c == '?') {
                    sb.append('.');
                } else {
                    sb.append(c);
                }
            }

            patterns[i] = Pattern.compile(sb.toString());
        }

        List<String> mfs = new LinkedList<String>();
        int level = 0;
        File files0 = new File(basedir);
        forDir(mfs, files0, patterns, level);

        return mfs;
    }

    private static void forDir(List<String> mfs, File file1, Pattern[] mat, int level) {
        if (file1 == null || level >= mat.length) return;

        // last element is filename, other is directory
        if (level == (mat.length - 1)) {
            if (!file1.isDirectory()) return;

            for (File file2 : file1.listFiles()) {
                Matcher m = mat[mat.length - 1].matcher(file2.getName());
                if (m.find())
                    mfs.add(file2.getAbsolutePath());
            }
            return;
        }

        // [0..n-2] must be directory
        if (!file1.isDirectory()) return;
        for (File file2 : file1.listFiles()) {
            if (file2.isDirectory()) {
                forDir(mfs, file2, mat, level + 1);
            }
        }
    }

    public static void main(String[] args) {
        String basedir;
        String mat;
        if (args.length == 2) {
            basedir = args[0];
            mat = args[1];
        } else {
            basedir = "/u01/oracle/diag";
            mat = "rdbms/*/*/trace/alert_*.log";
            basedir = "/tmp/u01/";
            mat = "oracle/diag/rdbms/*/*/trace/ale?t_*.log";
        }

        long begin = System.nanoTime();
        List<String> files = Scan(basedir, mat);
        long end = System.nanoTime();
        System.out.println("used ms = " + (end - begin) / 1000000f + " files:" + files.size());
        for (String file : files) {
            System.out.println("file = " + file);
        }
    }


}

