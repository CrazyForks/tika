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
package org.apache.tika.detect.zip;

import static org.apache.tika.detect.zip.PackageConstants.KMZ;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Locale;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;

/**
 * This looks for a single file with a name ending in ".kml" at the root level of the zip file.
 * <p>
 * As of Tika 3.2.1, we allow other files at the root level.
 * <p>
 * We could make this more robust by requiring xml root detection on the *.kml file.
 */
public class KMZDetector implements ZipContainerDetector {


    @Override
    public MediaType detect(ZipFile zip, TikaInputStream tis) throws IOException {
        Enumeration<ZipArchiveEntry> entries = zip.getEntries();
        int kmlCount = 0;
        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!entry.isDirectory() && name.indexOf('/') == -1 && name.indexOf('\\') == -1) {
                if (name.toLowerCase(Locale.ROOT).endsWith(".kml")) {
                    kmlCount++;
                }
                if (kmlCount > 1) {
                    return null;
                }
            }
        }
        if (kmlCount == 1) {
            return KMZ;
        }
        return null;
    }

    @Override
    public MediaType streamingDetectUpdate(ZipArchiveEntry zae, InputStream zis,
                                           StreamingDetectContext detectContext) {
        String name = zae.getName();

        if (name.indexOf('/') != -1 || name.indexOf('\\') != -1) {
            return null;
        }
        if (name.toLowerCase(Locale.ROOT).endsWith(".kml")) {
            KMLCounter counter = detectContext.get(KMLCounter.class);
            if (counter == null) {
                counter = new KMLCounter();
                detectContext.set(KMLCounter.class, counter);
            }
            counter.increment();
        }
        return null;
    }

    @Override
    public MediaType streamingDetectFinal(StreamingDetectContext detectContext) {
        if (detectContext.get(KMLCounter.class) != null) {
            if (detectContext.get(KMLCounter.class).getCount() == 1) {
                return KMZ;
            }
        }
        return null;
    }

    private static class KMLCounter {
        private int cnt = 0;

        int getCount() {
            return cnt;
        }

        void increment() {
            cnt++;
        }
    }
}
