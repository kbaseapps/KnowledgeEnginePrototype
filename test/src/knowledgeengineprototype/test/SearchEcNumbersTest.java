package knowledgeengineprototype.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import be.tarsos.lsh.Index;
import be.tarsos.lsh.Vector;
import be.tarsos.lsh.VectorPacker;
import be.tarsos.lsh.families.CosineHashFamily;
import be.tarsos.lsh.families.DistanceMeasure;
import be.tarsos.lsh.families.HashFamily;
import us.kbase.common.service.UObject;

public class SearchEcNumbersTest {
    
    @Test
    public void fileTest() throws Exception {
        int numberOfHashTables = 10;
        int numberOfHashes = 10;
        int numberOfNeighbours = 100;
        boolean useIndex = false;
        boolean reindexData = false;
        File dbFile = new File("test_local/ec_profiles.tsv");
        VectorPacker packer = new FileVectorPacker(dbFile);
        List<Vector> queries = loadQueries(dbFile); //.subList(0, 100);
        HashFamily family = new CosineHashFamily(queries.get(0).getDimensions());
        if (useIndex) {
            if (reindexData) {
                index(family, numberOfHashes, numberOfHashTables, packer);
            }
            System.out.println("" + queries.size() + " queries");
            long t1 = System.currentTimeMillis();
            for (Vector query: queries) {
                search(family, numberOfHashes, numberOfHashTables, query, packer, numberOfNeighbours);
            }
            System.out.println("Average time: " + (System.currentTimeMillis() - t1) / queries.size());
        } else {
            Vector query = queries.get(0);
            System.out.println("Query: " + query.getKey() + ", dimensions=" + query.getDimensions());
            search2(family, query, packer, numberOfNeighbours);
        }
    }
    
    @Test
    public void memoryTest() throws Exception {
        int numberOfHashTables = 10;
        int numberOfHashes = 10;
        int numberOfNeighbours = 100;
        boolean useIndex = true;
        boolean reindexData = false;
        File dbFile = new File("test_local/ec_profiles.tsv");
        VectorPacker filePacker = new FileVectorPacker(dbFile);
        VectorPacker packer = new MemoryVectorPacker();
        filePacker.unpack(null, new VectorPacker.Callback() {
            @Override
            public void nextVector(Vector vec) {
                packer.pack(vec);
            }
        });
        List<Vector> queries = loadQueries(dbFile);
        int dimensions = queries.get(0).getDimensions();
        HashFamily family = new CosineHashFamily(dimensions);
        if (useIndex) {
            if (reindexData) {
                index(family, numberOfHashes, numberOfHashTables, filePacker);
            }
            System.out.println("" + queries.size() + " queries");
            long t1 = System.currentTimeMillis();
            for (Vector query: queries) {
                search(family, numberOfHashes, numberOfHashTables, query, packer, numberOfNeighbours);
            }
            System.out.println("Average time: " + (System.currentTimeMillis() - t1) / queries.size());
        } else {
            Vector query = queries.get(0);
            System.out.println("Query: " + query.getKey() + ", dimensions=" + query.getDimensions());
            search2(family, query, packer, numberOfNeighbours);
        }
    }
    
    private List<Vector> loadQueries(File cacheFile) {
        List<Vector> ret = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(cacheFile))) {
            int dimensions = Integer.parseInt(br.readLine());
            while (true) {
                String l = br.readLine();
                if (l == null) {
                    break;
                }
                int div = l.indexOf('\t');
                String key = l.substring(0, div);
                if (key.startsWith("generated")) {
                    break;
                }
                ret.add(parseVectorLine(key, l, div, dimensions));
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return ret;
    }
    
    private void index(HashFamily family,int numberOfHashes, int numberOfHashTables,
            VectorPacker packer) throws Exception {
        System.out.println("Indexing");
        packer.init();
        long t1 = System.currentTimeMillis();
        Index index = new Index(family, numberOfHashes, numberOfHashTables);
        index.setVectorPacker(packer);
        int[] count = {0};
        packer.unpack(null, new VectorPacker.Callback() {
            @Override
            public void nextVector(Vector vec) {
                index.index(vec);
                count[0]++;
            }
        });
        System.out.println("\ttime: " + (System.currentTimeMillis() - t1) + " ms, " + 
                "indexed: " + count[0]);
        Index.serialize(index);
        packer.flush();
    }
    
    private static void search(HashFamily family,int numberOfHashes, 
            int numberOfHashTables, Vector query, VectorPacker packer, int numberOfNeighbours) {
        Index index = Index.deserialize(family, numberOfHashes, numberOfHashTables);
        index.setVectorPacker(packer);
        System.out.println("Searching");
        long t3 = System.currentTimeMillis();
        List<Vector> neighbours = index.query(query, numberOfNeighbours);
        boolean found = false;
        for (int i = 0; i < neighbours.size(); i++) {
            String foundKey = neighbours.get(i).getKey();
            if (foundKey.equals(query.getKey())) {
                found = true;
                break;
            }
        }
        System.out.println("\ttime: " + (System.currentTimeMillis() - t3) + " ms,  found: " + found);
    }
    
    private static void search2(HashFamily family, Vector query, VectorPacker packer, 
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

    public static class MemoryVectorPacker implements VectorPacker {
        private Map<String, Vector> cache = new HashMap<>();
        
        public MemoryVectorPacker() {
        }
        
        @Override
        public void init() {
            if (cache.size() > 0) {
                cache = new HashMap<>();
            }
        }
        
        @Override
        public void pack(Vector vec) {
            cache.put(vec.getKey(), vec);
        }
        
        @Override
        public void unpack(List<Vector> candidates, Callback callback) {
            if (candidates == null) {
                for (Vector vec : cache.values()) {
                    callback.nextVector(vec);
                }
            } else {
                for (Vector v1 : candidates) {
                    callback.nextVector(cache.get(v1.getKey()));
                }
            }
        }
        
        @Override
        public void flush() {
        }
    }
}

