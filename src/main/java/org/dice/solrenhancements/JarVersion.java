package org.dice.solrenhancements;

import org.slf4j.Logger;

import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

/**
 * Created by simon.hughes on 7/7/16.
 */
public class JarVersion {

    private class stub{

    }

    public static String getVersion(Logger log){

        Enumeration<URL> resources;
        StringBuilder stringBuilder = new StringBuilder();

        try {
            resources = stub.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                /* let's not read other jar's manifests */
                if (!url.toString().contains("DiceSolrEnhancements")) {
                    continue;
                }
                InputStream reader = url.openStream();
                while(reader.available() > 0) {
                    char c = (char) reader.read();
                    stringBuilder.append(c);
                    /* skip lines that don't contain the built-date */
                    if (stringBuilder.toString().contains(System.getProperty("line.separator")) &&
                            !stringBuilder.toString().contains("Build-Time")) {
                        stringBuilder.setLength(0);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read manifest during request for version!");
            return "Error reading manifest!";
        }
        return stringBuilder.toString();
    }
}
