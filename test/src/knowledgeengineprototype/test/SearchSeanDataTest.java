package knowledgeengineprototype.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.junit.Ignore;
import org.junit.Test;

import be.tarsos.lsh.Vector;
import be.tarsos.lsh.VectorPacker;
import be.tarsos.lsh.families.CosineHashFamily;
import be.tarsos.lsh.families.DistanceMeasure;
import be.tarsos.lsh.families.HashFamily;
import us.kbase.common.service.UObject;

public class SearchSeanDataTest {
    
    @Ignore
    @Test
    public void prepareDataTest() throws Exception {
        int dimensions = 1000000;
        File input = new File("test_local/sparse/data/sparse.csv");
        BufferedReader br = new BufferedReader(new FileReader(input));
        PrintWriter pw = new PrintWriter(new File("test_local/sean_profiles.tsv"));
        try {
            pw.println(dimensions);
            int currentId = -1;
            List<Integer> row = new ArrayList<>();
            while (true) {
                String l = br.readLine();
                if (l == null || l.trim().length() == 0) {
                    break;
                }
                String[] parts = l.split(Pattern.quote("\t"));
                int id = (int)Math.round(Double.parseDouble(parts[0]));
                if (id != currentId) {
                    if (currentId > 0) {
                        pw.println(currentId + "\t" + createProfile(row));
                        currentId = -1;
                    }
                    currentId = id;
                    row = new ArrayList<>();
                }
                int profId = Integer.parseInt(parts[1]) - 1;
                row.add(profId);
            }
            if (currentId > 0) {
                pw.println(currentId + "\t" + createProfile(row));
            }
        } finally {
            br.close();
            pw.close();
        }
    }

    private static String createProfile(List<Integer> row) {
        Map<Integer, Integer> map = new TreeMap<>();
        for (int item : row) {
            map.put(item, 1);
        }
        return UObject.transformObjectToString(map);
    }
    
    @Test
    public void fileTest() throws Exception {
        int numberOfNeighbours = 100;
        File dbFile = new File("test_local/sean_profiles.tsv");
        VectorPacker packer = new FileVectorPacker(dbFile);
        List<Vector> queries = loadQueries(packer, 1);
        HashFamily family = new CosineHashFamily(queries.get(0).getDimensions());
        Vector query = queries.get(0);
        System.out.println("Query: " + query.getKey() + ", dimensions=" + query.getDimensions());
        searchFullScan(family, query, packer, numberOfNeighbours);
    }

    @Test
    public void fileFastTest() throws Exception {
        int numberOfNeighbours = 100;
        File dbFile = new File("test_local/sean_profiles.tsv");
        ProfileLoader packer = new ProfileLoader(dbFile);
        Map<String, Integer> query = loadQueryProfile(packer, "1");
        searchFullScanFast(query, packer, numberOfNeighbours);
    }

    private List<Vector> loadQueries(VectorPacker packer, int count) {
        List<Vector> ret = new ArrayList<>();
        List<Vector> candidates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            candidates.add(new Vector(String.valueOf(i + 1), null));
        }
        packer.unpack(candidates, new VectorPacker.Callback() {
            @Override
            public void nextVector(Vector vec) {
                ret.add(vec);
            }
        });
        return ret;
    }

    private Map<String, Integer> loadQueryProfile(ProfileLoader packer, String key) {
        List<Map<String, Integer>> ret = new ArrayList<>();
        Set<String> candidates = new HashSet<String>();
        candidates.add(key);
        packer.unpack(candidates, new ProfileCallback() {
            @Override
            public void nextProfile(String key, Map<String, Integer> profile) {
                ret.add(profile);
            }
        });
        return ret.get(0);
    }

    private static void searchFullScan(HashFamily family, Vector query, VectorPacker packer, 
            int numberOfNeighbours) {
        System.out.println("Searching");
        long t4 = System.currentTimeMillis();
        DistanceMeasure measure = family.createDistanceMeasure();
        Map<String, Double> keyToDist = new HashMap<>();
        packer.unpack(null, new VectorPacker.Callback() {
            @Override
            public void nextVector(Vector vec) {
                double dist = measure.distance(query, vec);
                keyToDist.put(vec.getKey(), dist);
            }
        });
        List<String> keys = new ArrayList<String>(keyToDist.keySet());
        Collections.sort(keys, new Comparator<String>() {
            @Override
            public int compare(String key1, String key2) {
                return keyToDist.get(key1).compareTo(keyToDist.get(key2));
            }
        });
        Set<String> foundKeys = new LinkedHashSet<>(keys.subList(0, numberOfNeighbours));
        System.out.println("\ttime: " + (System.currentTimeMillis() - t4) + " ms");
        for (String key : foundKeys) {
            System.out.println("Found " + key + ", distance=" + keyToDist.get(key));
        }
    }

    private static void searchFullScanFast(Map<String, Integer> query, ProfileLoader packer, 
            int numberOfNeighbours) {
        System.out.println("Searching");
        long t4 = System.currentTimeMillis();
        Map<String, Double> keyToDist = new HashMap<>();
        packer.unpack(null, new ProfileCallback() {
            @Override
            public void nextProfile(String key, Map<String, Integer> vec) {
                double dist = distance(query, vec);
                keyToDist.put(key, dist);
            }
        });
        List<String> keys = new ArrayList<String>(keyToDist.keySet());
        Collections.sort(keys, new Comparator<String>() {
            @Override
            public int compare(String key1, String key2) {
                return keyToDist.get(key1).compareTo(keyToDist.get(key2));
            }
        });
        Set<String> foundKeys = new LinkedHashSet<>(keys.subList(0, numberOfNeighbours));
        System.out.println("\ttime: " + (System.currentTimeMillis() - t4) + " ms");
        for (String key : foundKeys) {
            System.out.println("Found " + key + ", distance=" + keyToDist.get(key));
        }
    }

    private static double distance(Map<String, Integer> p1, Map<String, Integer> p2) {
        double distance=0;
        double similarity = dot(p1, p2) / Math.sqrt(scalarSquare(p1) * scalarSquare(p2));
        distance = 1 - similarity;
        return distance;
    }

    private static double dot(Map<String, Integer> p1, Map<String, Integer> p2) {
        double sum = 0.0;
        for (Map.Entry<String, Integer> pair : p1.entrySet()) {
            Integer v2 = p2.get(pair.getKey());
            if (v2 != null) {
                sum += pair.getValue() * v2;
            }
        }
        return sum;
    }
    
    private static double scalarSquare(Map<String, Integer> p1) {
        double ret = 0;
        for (int value : p1.values()) {
            ret += value * value;
        }
        return ret;
    }

    private static Vector parseVectorLine(String key, String l, int div, int dimensions) {
        String json = l.substring(div + 1);
        Object obj = UObject.transformStringToObject(json, Object.class);
        double[] values = null;
        @SuppressWarnings("unchecked")
        Map<String, Integer> map = (Map<String, Integer>)obj;
        values = new double[dimensions];
        for (String pos : map.keySet()) {
            values[Integer.parseInt(pos)] = map.get(pos);
        }
        return new Vector(key, values);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> parseProfileLine(String key, String l, int div) {
        String json = l.substring(div + 1);
        return (Map<String, Integer>)UObject.transformStringToObject(json, Object.class);
    }

    public static class FileVectorPacker implements VectorPacker {
        private final File cacheFile;
        
        public FileVectorPacker(File cacheFile) {
            this.cacheFile = cacheFile;
        }
        
        @Override
        public void init() {
        }
        
        @Override
        public void unpack(List<Vector> candidates, Callback callback) {
            Set<String> keys = null;
            if (candidates != null) {
                keys = new HashSet<>();
                for (Vector vec : candidates) {
                    keys.add(vec.getKey());
                }
            }
            try (BufferedReader br = new BufferedReader(new FileReader(cacheFile))) {
                int dimensions = Integer.parseInt(br.readLine());
                while (true) {
                    String l = br.readLine();
                    if (l == null) {
                        break;
                    }
                    int div = l.indexOf('\t');
                    String key = l.substring(0, div);
                    if (keys != null && !keys.contains(key)) {
                        continue;
                    }
                    Vector vec = parseVectorLine(key, l, div, dimensions);
                    callback.nextVector(vec);
                    if (keys != null) {
                        keys.remove(key);
                        if (keys.isEmpty()) {
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        
        @Override
        public void pack(Vector vec) {
        }
        
        public void flush() {
        }
    }

    public static class ProfileLoader {
        private final File cacheFile;
        
        public ProfileLoader(File cacheFile) {
            this.cacheFile = cacheFile;
        }
        
        public void unpack(Set<String> keys, ProfileCallback callback) {
            try (BufferedReader br = new BufferedReader(new FileReader(cacheFile))) {
                br.readLine();
                while (true) {
                    String l = br.readLine();
                    if (l == null) {
                        break;
                    }
                    int div = l.indexOf('\t');
                    String key = l.substring(0, div);
                    if (keys != null && !keys.contains(key)) {
                        continue;
                    }
                    Map<String, Integer> profile = parseProfileLine(key, l, div);
                    callback.nextProfile(key, profile);
                    if (keys != null) {
                        keys.remove(key);
                        if (keys.isEmpty()) {
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
    
    public static interface ProfileCallback {
        public void nextProfile(String key, Map<String, Integer> profile);
    }
}

