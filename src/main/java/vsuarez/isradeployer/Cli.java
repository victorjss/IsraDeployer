/*
 * Copyright (C) 2016 Víctor Suárez <victorjss@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package vsuarez.isradeployer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 *
 * @author  Víctor Suárez <victorjss@gmail.com>
 */
public class Cli {
    static final List<String> VALID_PARS =  Arrays.asList(new String[] {
        "name", "description", "desc", "version", "extension", "ext", "url", "file", "path", "repo", "provider"});
    
    static Map<String, String> parsePars(String... par) {
        HashMap<String, String> map = new HashMap<>();
        HashSet<String> set = new HashSet<>(VALID_PARS);
        
        for (String s : par) {
            String[] nameValue = s.split("=");
            if (nameValue.length != 2) { 
                throw new IllegalArgumentException("Wrong parameters set!");
            }
            final String name = nameValue[0];
            if (!set.contains(name)) {
                throw new IllegalArgumentException(String.format("Wrong parameter %s", name));
            }
            if ("desc".equals(name) || "description".equals(name)) {
                String desc = nameValue[1];
                if (desc.startsWith("'") || desc.startsWith("\"")) {
                    desc = desc.substring(1, desc.length() - 1);
                }
                map.put(name, desc);
            } else {
                map.put(name, nameValue[1]);
            }
        }
        
        return map;        
    }
    
    public static void main(String... arg) throws Exception { 
        Map<String, String> map = null;
        try {
            map = parsePars(arg);
        } catch (IllegalArgumentException e) {
            System.out.println("Wrong parameters!");
            printHelp();
            return;
        }
        String name = map.get("name");
        String desc = map.containsKey("desc") ? map.get("desc") : map.get("description");
        String version = map.get("version");
        String url = map.get("url");
        String path = map.containsKey("file") ? map.get("file") : map.get("path"); 
        String ext = map.containsKey("ext") ? map.get("ext") : map.get("extension"); 
        String repo = map.get("repo");
        String provider = map.containsKey("provider") ? map.get("provider") : "virtualbox";
        
        if (path == null || "".equals(path.trim())) {
            System.out.println("'path' parameter is mandatory.");
            printHelp();
            return;
        }        
        File file = new File(path);
        if (!file.exists()) {
            System.out.println(String.format("File %s not found!", path));
            printHelp();
            return;
        }
        
        if (repo == null || "".equals(repo.trim())) {
            System.out.println("'repo' parameter is mandatory.");
            printHelp();
            return;
        }
        File repoDir = new File(repo);
        if (!repoDir.exists() || !repoDir.isDirectory()) {
            System.out.println(String.format("Repo %s not found!", path));
            printHelp();
            return;
        }
        
        if (url == null || "".equals(url.trim())) {
            System.out.println("'url' parameter is mandatory.");
            printHelp();
            return;
        }
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            System.out.println(String.format("Malformed 'url' parameter %s", url));
            printHelp();
            return;
        }
        
        if (name == null || "".equals(name.trim())) {
            String fileName = file.getName();
            int versionPos = fileName.lastIndexOf("-") + 1;
            int dotPos = fileName.lastIndexOf(".");
            if (versionPos <= 1 || (dotPos >= 0 && versionPos >= dotPos)) {
                System.out.println(String.format("'name' parameter not specified and file name without 'name-version.ext' format: %s", fileName));
                printHelp();
                return;
            }
            int versionLength = dotPos >= 0 ? dotPos - versionPos : fileName.length() - versionPos;
            if (version == null || "".equals(version.trim())) {
                version = fileName.substring(versionPos, versionPos + versionLength);
            }
            if (version == null || "".equals(version.trim())) {
                System.out.println(String.format("'version' parameter not specified and file name without 'name-version.ext' format: %s", fileName));
                printHelp();
                return;
            }
            if ((dotPos >=0 && (fileName.length() - 1 - dotPos > 0)) && (ext == null || "".equals(ext.trim()))) {
                ext = fileName.substring(dotPos + 1);
            } 
            name = fileName.substring(0, versionPos - 1);
        }
        String newFilename = buildNewFilename(name, version, ext);
        
        File repoFile = new File(
                getRepoDirPath(repoDir, name) + ARTIFACT_SUBDIR + File.separator 
                        + newFilename);
        String parentDir = repoFile.getParent();
        new File(parentDir).mkdirs();

        Files.copy(file.toPath(), repoFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        
        try {
            File jsonFile = new File(getRepoDirPath(repoDir, name) + name + ".json");
            
            JsonDescriptor jsonDescriptor = null;
            
            ObjectMapper mapper = new ObjectMapper();
            if (!jsonFile.exists()) {
                jsonDescriptor = buildJsonDescriptor(desc, name, version, newFilename, provider, url, file);

            } else {
                jsonDescriptor = mapper.readValue(jsonFile, JsonDescriptor.class);
                int versionIndex = -1;
                for (int i = 0; i < jsonDescriptor.getVersions().size(); i++) {
                    if (version.equals(jsonDescriptor.getVersions().get(i).getVersion())) {
                        versionIndex = i;
                        break;
                    }
                }
                JsonVersion jsonVersion = buildJsonVersion(version, provider, url, file, newFilename);
                if (versionIndex < 0) {
                    jsonDescriptor.getVersions().add(jsonVersion);
                } else {
                    jsonDescriptor.getVersions().set(versionIndex, jsonVersion);
                }
            }
            FileWriter fw = new FileWriter(jsonFile);
            mapper.writeValue(fw, jsonDescriptor);

        } catch (Exception e) {
            e.printStackTrace();
            //undo changes
            if (repoFile.exists()) {
                repoFile.delete();
            }
        }
        
        
    }

    protected static void printHelp() {
        System.out.println("\n\nExample of valid parameters are:\n\nname=artifact_name description/desc=artifact_description \\\n"
                + "version=1.0 ext/extension=.img \\\n"
                + "url=http://my.repository.com/ \\\n"
                + "file/path=/path/to/artifact \\\n"
                + "repo=/path/to/repo/dir");
    }

    protected static final String ARTIFACT_SUBDIR = "boxes";
    
    protected static String buildNewFilename(String name, String version, String ext) {
        return name + "-" + version + "." + (ext == null || "".equals(ext.trim()) ? ".img" : ext);
    }

    protected static String getRepoDirPath(File repoDir, String name) {
        return repoDir.getAbsolutePath() + File.separator + name + File.separator;
    }

    protected static JsonDescriptor buildJsonDescriptor(String desc, String name, String version, String newFilename, String provider, String url, File file) throws NoSuchAlgorithmException, IOException {
        JsonDescriptor jsonDescriptor = new JsonDescriptor();
        jsonDescriptor.setDescription(desc);
        jsonDescriptor.setName(name);
        JsonVersion jsonVersion = buildJsonVersion(version, provider, url, file, newFilename);
        jsonDescriptor.getVersions().add(jsonVersion);
        return jsonDescriptor;
    }

    protected static JsonVersion buildJsonVersion(String version, String provider, String url, File file, String newFilename) throws IOException, NoSuchAlgorithmException {
        final JsonVersion jsonVersion = new JsonVersion();
        jsonVersion.setVersion(version);
        final JsonProvider jsonProvider = new JsonProvider();
        jsonVersion.getProviders().add(jsonProvider);
        jsonProvider.setChecksum_type("sha256");
        jsonProvider.setName(provider);
        jsonProvider.setUrl(url + "/" + ARTIFACT_SUBDIR + "/" + newFilename);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[512];
            int r = 0;
            while ((r = fis.read(buffer)) > 0) {
                md.update(buffer, 0, r);
            }
            
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        IntStream.range(0, digest.length).map(i -> digest[i]).forEach(b -> sb.append(Integer.toHexString(0xff & b)));
        String hash = sb.toString();
        jsonProvider.setChecksum(hash.toLowerCase());
        return jsonVersion;
    }

    static final class JsonDescriptor {
        String name;
        String description;
        List<JsonVersion> versions = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<JsonVersion> getVersions() {
            return versions;
        }

        public void setVersions(List<JsonVersion> versions) {
            this.versions = versions;
        }
        
        
    }
    
    static final class JsonVersion {
        String version;
        List<JsonProvider> providers = new ArrayList<>();

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public List<JsonProvider> getProviders() {
            return providers;
        }

        public void setProviders(List<JsonProvider> providers) {
            this.providers = providers;
        }
    }
    
    static final class JsonProvider {
        String name;
        String url;
        String checksum_type;
        String checksum;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getChecksum_type() {
            return checksum_type;
        }

        public void setChecksum_type(String checksum_type) {
            this.checksum_type = checksum_type;
        }

        public String getChecksum() {
            return checksum;
        }

        public void setChecksum(String checksum) {
            this.checksum = checksum;
        }
    }
}
