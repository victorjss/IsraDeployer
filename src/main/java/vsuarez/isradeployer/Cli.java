package vsuarez.isradeployer;

import java.io.File;
import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 *
 * @author vsuarez
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
            map.put(name, nameValue[1]);
        }
        
        return map;        
    }
    
    public static void main(String... arg) throws Exception { 
        Map<String, String> map = null;
        try {
            map = parsePars(arg);
        } catch (IllegalArgumentException e) {
            System.out.println("Wrong parameters!\n\n"
                    + "Valid parameters are: name=artifact_name description/desc=artifact_description\n"
                    + "version=1.0 ext/extension=.img\n"
                    + "url=http://my.repository.com/\nfile/path=/path/to/artifact\n"
                    + "repo=/path/to/repo/dir");
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
            return;
        }        
        File file = new File(path);
        if (!file.exists()) {
            System.out.println(String.format("File %s not found!", path));
            return;
        }
        
        if (repo == null || "".equals(repo.trim())) {
            System.out.println("'repo' parameter is mandatory.");
            return;
        }
        File repoDir = new File(repo);
        if (!repoDir.exists() || !repoDir.isDirectory()) {
            System.out.println(String.format("Repo %s not found!", path));
            return;
        }
        
        if (url == null || "".equals(url.trim())) {
            System.out.println("'url' parameter is mandatory.");
            return;
        }
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            System.out.println(String.format("Malformed 'url' parameter %s", url));
            return;
        }
        
        if (name == null || "".equals(name.trim())) {
            String fileName = repoDir.getName();
            int versionPos = fileName.lastIndexOf("-") + 1;
            int dotPos = fileName.lastIndexOf(".");
            if (versionPos <= 1 || (dotPos >= 0 && versionPos >= dotPos)) {
                System.out.println(String.format("'name' parameter not specified and file name without 'name-version.ext' format: %s", fileName));
                return;
            }
            int versionLength = dotPos >= 0 ? dotPos - versionPos : fileName.length() - versionPos;
            if (version == null || "".equals(version.trim())) {
                version = fileName.substring(versionPos, versionLength);
            }
            if (version == null || "".equals(version.trim())) {
                System.out.println(String.format("'version' parameter not specified and file name without 'name-version.ext' format: %s", fileName));
            }
            if ((dotPos >=0 && (fileName.length() - 1 - dotPos - versionPos > 0)) && (ext == null || "".equals(ext.trim()))) {
                ext = fileName.substring(dotPos);
            } 
            name = fileName.substring(0, versionPos);
        }
        
        File repoFile = new File(
                getMainFileName(repoDir, name, version) + "." 
                        + (ext == null || "".equals(ext.trim()) ? ".img" : ext));

        Files.copy(file.toPath(), repoFile.toPath());
        
        File jsonFile = new File(getMainFileName(repoDir, name, version) + ".json");
        
        if (!jsonFile.exists()) {
            String jsonString = "{\"name\":\"%s\",\n"
                    + "\"description\":\"%s\",\n"
                    + "\"versions\":[{\n"
                    + "\"version\":\"%s\",\"providers\":[{\"name\":\"%s\",\"url\":\"%s\",\"checksum_type\":\"sha256\",\"checksum\":\"%s\"\n"
                    + "}]}]}";
                    
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
            IntStream.range(0, digest.length).forEach(b -> sb.append(Integer.toHexString(0xff & b)));
            String hash = sb.toString();
        }
        
        
    }

    protected static String getMainFileName(File repoDir, String name, String version) {
        return repoDir.getAbsolutePath() + File.separator + name 
                + "-" + version;
    }
    
    static final class JsonDescriptor {
        String name;
        String description;
        List<JsonVersion> versions;

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
        List<JsonProvider> providers;

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
