/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tika.eval.app;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.TikaTest;
import org.apache.tika.utils.ProcessUtils;

public class TikaEvalCLITest extends TikaTest {
    //TODO: these barely reach the minimal acceptable stage for unit tests

    private final static String dbName = "testdb";

    @TempDir
    private static Path TEMP_DIR;
    private static Path extractsDir = Paths.get("src/test/resources/test-dirs");
    private static Path compareDBDir;
    private static Path profileDBDir;
    private static Path compareReportsDir;
    private static Path profileReportsDir;

    @BeforeAll
    public static void setUp() throws Exception {
        compareDBDir = Files.createTempDirectory(TEMP_DIR, "tika-eval-cli-compare-db-");
        profileDBDir = Files.createTempDirectory(TEMP_DIR, "tika-eval-cli-profile-db-");
        compareReportsDir = Files.createTempDirectory(TEMP_DIR, "tika-eval-cli-compare-reports-");
        profileReportsDir = Files.createTempDirectory(TEMP_DIR, "tika-eval-cli-profile-reports-");
        compare();
        profile();
        reportCompare();
        reportProfile();
    }

    private static void compare() throws IOException {
        List<String> args = new ArrayList<>();
        args.add("Compare");
        args.add("-a");
        args.add(ProcessUtils.escapeCommandLine(extractsDir
                .resolve("extractsA")
                .toAbsolutePath()
                .toString()));
        args.add("-b");
        args.add(ProcessUtils.escapeCommandLine(extractsDir
                .resolve("extractsB")
                .toAbsolutePath()
                .toString()));

        args.add("-d");
        args.add(ProcessUtils.escapeCommandLine(compareDBDir
                .toAbsolutePath()
                .toString() + "/" + dbName));

        execute(args, 60000);

    }

    private static void profile() throws IOException {
        List<String> args = new ArrayList<>();
        args.add("Profile");
        args.add("-e");
        args.add(ProcessUtils.escapeCommandLine(extractsDir
                .resolve("extractsA")
                .toAbsolutePath()
                .toString()));

        args.add("-d");
        args.add(ProcessUtils.escapeCommandLine(profileDBDir
                .toAbsolutePath()
                .toString() + "/" + dbName));
        execute(args, 60000);
    }

    private static void reportProfile() throws IOException {
        List<String> args = new ArrayList<>();
        args.add("Report");
        args.add("-db");
        args.add(ProcessUtils.escapeCommandLine(profileDBDir
                .toAbsolutePath()
                .toString() + "/" + dbName));
        args.add("-rd");
        args.add(ProcessUtils.escapeCommandLine(profileReportsDir
                .toAbsolutePath()
                .toString()));
        execute(args, 60000);
    }

    private static void reportCompare() throws IOException {
        List<String> args = new ArrayList<>();
        args.add("Report");
        args.add("-db");
        args.add(ProcessUtils.escapeCommandLine(compareDBDir
                .toAbsolutePath()
                .toString() + "/" + dbName));
        args.add("-rd");
        args.add(ProcessUtils.escapeCommandLine(compareReportsDir
                .toAbsolutePath()
                .toString()));
        execute(args, 60000);
    }

    private static void execute(List<String> incomingArgs, long maxMillis) throws IOException {
        List<String> args = new ArrayList<>();
        String cp = System.getProperty("java.class.path");
        args.add("java");
        args.add("-Djava.awt.headless=true");
        args.add("-cp");
        args.add(cp);
        args.add("org.apache.tika.eval.app.TikaEvalCLI");
        args.addAll(incomingArgs);

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process = pb.start();
        long started = System.currentTimeMillis();
        long elapsed = System.currentTimeMillis() - started;
        int exitValue = Integer.MIN_VALUE;
        while (elapsed < maxMillis && exitValue == Integer.MIN_VALUE) {
            try {
                exitValue = process.exitValue();
            } catch (IllegalThreadStateException e) {
                //swallow
            }
            elapsed = System.currentTimeMillis() - started;
        }
        if (exitValue == Integer.MIN_VALUE) {
            process.destroy();
            throw new RuntimeException("Process never exited within the allowed amount of time.\n" + "I needed to destroy it");
        }
    }

    @Test
    public void testBasicCompare() throws Exception {
        Set<String> fNames = new HashSet<>();
        for (File f : compareDBDir
                .toFile()
                .listFiles()) {
            fNames.add(f.getName());
        }
        assertContains(dbName + ".mv.db", fNames);
    }

    @Test
    public void testBasicProfile() throws Exception {
        Set<String> fNames = new HashSet<>();
        for (File f : profileDBDir
                .toFile()
                .listFiles()) {
            fNames.add(f.getName());
        }
        assertContains(dbName + ".mv.db", fNames);
    }

    @Test
    public void testProfileReports() throws Exception {
        CachingFileVisitor v = new CachingFileVisitor();
        Files.walkFileTree(profileReportsDir, v);
        int cnt = 0;
        for (Path report : v.getPaths()) {

            if (report
                    .getFileName()
                    .toString()
                    .endsWith(".xlsx")) {
                cnt++;
            }
        }
        assertTrue(cnt > 5);
    }

    @Test
    public void testComparisonReports() throws Exception {
        CachingFileVisitor v = new CachingFileVisitor();
        Files.walkFileTree(compareReportsDir, v);
        int cnt = 0;
        for (Path report : v.getPaths()) {
            if (report
                    .getFileName()
                    .toString()
                    .endsWith(".xlsx")) {
                cnt++;
            }
        }
        assertTrue(cnt > 33);
        // If there is a failure, check for SQL errors in the previous log.
        // If it's is a syntax error, for the position look for "[*]" in the exception message.
        // The "[42001-230]" is [<error number>-<build number].
    }

    @Test
    @Disabled("use this for development")
    public void testOneOff() throws Exception {
        List<String> args = new ArrayList<>();
        args.add("Compare");
        args.add("-extractsA");
        args.add(ProcessUtils.escapeCommandLine(extractsDir
                .resolve("extractsA")
                .toAbsolutePath()
                .toString()));
        args.add("-extractsB");
        args.add(ProcessUtils.escapeCommandLine(extractsDir
                .resolve("extractsB")
                .toAbsolutePath()
                .toString()));
        args.add("-db");
        args.add(ProcessUtils.escapeCommandLine(compareDBDir
                .toAbsolutePath()
                .toString() + "/" + dbName));

        execute(args, 60000);
        //      args.add("-drop");
//        args.add("-jdbc");
//        args.add("jdbc:postgresql:tika_eval?user=user&password=password");

    }

    private final static class CachingFileVisitor implements FileVisitor<Path> {
        Set<Path> paths = new HashSet<>();

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            paths.add(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        Set<Path> getPaths() {
            return paths;
        }
    }

}
