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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.Test;

import be.tarsos.lsh.Index;
import be.tarsos.lsh.Vector;
import be.tarsos.lsh.VectorPacker;
import be.tarsos.lsh.families.CosineHashFamily;
import be.tarsos.lsh.families.DistanceMeasure;
import be.tarsos.lsh.families.HashFamily;
import us.kbase.common.service.UObject;

public class KnnLshTest {
    
    @Test
    public void mainTest() throws Exception {
        int numberOfHashTables = 4;
        int numberOfHashes = 4;
        int datasetSize = 1000;
        int fulfillCount = 1000;
        int dimensions = 1000000;
        HashFamily family = new CosineHashFamily(dimensions);
        Random rand = new Random(1234567890);
        File dbFile = new File("cache.dat");
        //VectorPacker packer = new MemoryVectorPacker(dimensions);
        VectorPacker packer = new FileVectorPacker(dbFile, dimensions);
        Vector query = generate(family, numberOfHashes, numberOfHashTables, 
                dimensions, datasetSize, fulfillCount, rand, packer);
        //Vector query = generateSeed(dimensions, fulfillCount, rand);
        int numberOfNeighbours = 100;
        //search(family, numberOfHashes, numberOfHashTables, query, packer, numberOfNeighbours);
        search2(family, query, dbFile, dimensions, numberOfNeighbours);
    }
    
    private Vector generate(HashFamily family,int numberOfHashes, int numberOfHashTables,
            int dimensions, int datasetSize, int fulfillCount, Random rand, 
            VectorPacker packer) throws Exception {
        System.out.println("Data generation");
        packer.init();
        long t1 = System.currentTimeMillis();
        Index index = new Index(family, numberOfHashes, numberOfHashTables);
        index.setVectorPacker(packer);
        Vector v1 = generateSeed(dimensions, fulfillCount, rand);
        Vector query = v1;
        index.index(new Vector(v1));
        for (int n = 1; n < datasetSize; n++) {
            Vector v2 = new Vector(v1);
            v2.setKey("" + n);
            int pos0 = findRandomPos(rand, v1, false);
            int pos1 = findRandomPos(rand, v1, true);
            if (v1.get(pos0) > 0 || v1.get(pos1) == 0) {
                throw new IllegalStateException();
            }
            v2.set(pos0, 1.0);
            v2.set(pos1, 0.0);
            index.index(new Vector(v2));
            v1 = v2;
            if (n % 1000 == 0) {
                System.out.println("\t" + n + " items processed");
            }
        }
        System.out.println("\ttime: " + (System.currentTimeMillis() - t1) + " ms");
        Index.serialize(index);
        packer.flush();
        return query;
    }
    
    private static Vector generateSeed(int dimensions, int fulfillCount, Random rand) {
        Vector v1 = new Vector(dimensions);
        v1.setKey("" + 0);
        for (int i = 0; i < fulfillCount; i++) {
            while (true) {
                int pos = rand.nextInt(dimensions);
                if (v1.get(pos) == 0) {
                    v1.set(pos, 1.0);
                    break;
                }
            }
        }
        return v1;
    }
    
    private static void search(HashFamily family,int numberOfHashes, 
            int numberOfHashTables, Vector query, VectorPacker packer, int numberOfNeighbours) {
        Index index = Index.deserialize(family, numberOfHashes, numberOfHashTables);
        index.setVectorPacker(packer);
        System.out.println("Searching");
        long t3 = System.currentTimeMillis();
        List<Vector> neighbours = index.query(query, numberOfNeighbours);
        System.out.println("\ttime: " + (System.currentTimeMillis() - t3) + " ms");
        Set<String> foundKeys = new HashSet<>();
        for (int i = 0; i < neighbours.size(); i++) {
            //System.out.println("Found: " + neighbours.get(i).getKey());
            foundKeys.add(neighbours.get(i).getKey());
        }
        checkResult(foundKeys, numberOfNeighbours);
        /*
        int datasetSize = 100000;
        int fulfillCount = 1000;
        int dimensions = 1000000;
        Data generation
            time: 1940512 ms
        Searching
        Index: #candidates=25817
            time: 111650 ms
        Lost: 0/100
        */
    }
    
    private static void checkResult(Set<String> foundKeys, int numberOfNeighbours) {
        int lost = 0;
        for (int i = 0; i < numberOfNeighbours; i++) {
            if (!foundKeys.contains("" + i)) {
                lost++;
            }
        }
        System.out.println("Lost: " + lost + "/" + numberOfNeighbours);
    }
    
    private static void search2(HashFamily family, Vector query, File cacheFile, 
            int dimensions, int numberOfNeighbours) {
        System.out.println("Searching");
        long t4 = System.currentTimeMillis();
        DistanceMeasure measure = family.createDistanceMeasure();
        Map<String, Double> keyToDist = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(cacheFile))) {
            while (true) {
                String l = br.readLine();
                if (l == null) {
                    break;
                }
                int div = l.indexOf('\t');
                String key = l.substring(0, div);
                String json = l.substring(div + 1);
                Object obj = UObject.transformStringToObject(json, Object.class);
                double[] values = null;
                if (obj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Double> list = (List<Double>)obj;
                    values = list.stream().mapToDouble(Double::doubleValue).toArray();
                } else {
                    @SuppressWarnings("unchecked")
                    Map<String, Double> map = (Map<String, Double>)obj;
                    values = new double[dimensions];
                    for (String pos : map.keySet()) {
                        values[Integer.parseInt(pos)] = map.get(pos);
                    }
                }
                Vector vec = new Vector(key, values);
                double dist = measure.distance(query, vec);
                keyToDist.put(key, dist);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        List<String> keys = new ArrayList<String>(keyToDist.keySet());
        Collections.sort(keys, new Comparator<String>() {
            @Override
            public int compare(String key1, String key2) {
                return keyToDist.get(key1).compareTo(keyToDist.get(key2));
            }
        });
        Set<String> foundKeys = new LinkedHashSet<>(keys.subList(0, numberOfNeighbours));
        System.out.println("\ttime: " + (System.currentTimeMillis() - t4) + " ms");
        checkResult(foundKeys, numberOfNeighbours);
        /*
        int datasetSize = 100000;
        int fulfillCount = 1000;
        int dimensions = 1000000;
        Searching
            time: 420855 ms
        Lost: 0/100
        */
    }
    
    private static int findRandomPos(Random rand, Vector vec, boolean nonZero) {
        int dimensions = vec.getDimensions();
        int ret = rand.nextInt(dimensions);
        for (int iter = 0; vec.get(ret) > 0.0 != nonZero; iter++) {
            ret = (ret + 1) % dimensions;
            if (iter >= dimensions) {
                throw new IllegalStateException("Too many iterations");
            }
        }
        return ret;
    }
    
    /*private static String toString(Vector vec) {
        StringBuilder ret = new StringBuilder(vec.getKey()).append('\t');
        for (int i = 0; i < vec.getDimensions(); i++) {
            ret.append(vec.get(i) > 0 ? '1' : '0');
        }
        return ret.toString();
    }
    
    private static void putToDiskMap(Vector vec, PrintWriter pw) {
        //List<Double> list = DoubleStream.of(vec.values).mapToObj(
        //        Double::valueOf).collect(Collectors.toList());
        //map.put(vec.getKey(), UObject.transformObjectToString(list));
        //map.put(vec.getKey(), vec.values);
        double[] values = vec.values;
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            if (values[i] > 0) {
                map.put(String.valueOf(i), values[i]);
            }
        }
        pw.println(vec.getKey() + "\t" + UObject.transformObjectToString(map));
    }*/
    
    public static class MemoryVectorPacker implements VectorPacker {
        private final int dimensions;
        private Map<String, Map<Integer, Double>> cache = new HashMap<>();
        
        public MemoryVectorPacker(int dimensions) {
            this.dimensions = dimensions;
        }
        
        @Override
        public void init() {
            if (cache.size() > 0) {
                cache = new HashMap<>();
            }
        }
        
        @Override
        public void pack(Vector vec) {
            double[] values = vec.values;
            Map<Integer, Double> map = new LinkedHashMap<>();
            for (int i = 0; i < values.length; i++) {
                if (values[i] > 0) {
                    map.put(i, values[i]);
                }
            }
            cache.put(vec.getKey(), map);
        }
        
        @Override
        public void unpack(List<Vector> candidates, Callback callback) {
            for (Vector v1 : candidates) {
                String key = v1.getKey();
                Map<Integer, Double> map = cache.get(key);
                double[] values = new double[dimensions];
                for (Integer pos : map.keySet()) {
                    values[pos] = map.get(pos);
                }
                Vector vec = new Vector(key, values);
                callback.nextVector(vec);
            }
        }
        
        @Override
        public void flush() {
        }
    }
    
    public static class FileVectorPacker implements VectorPacker {
        private final File cacheFile;
        private PrintWriter tempPw = null;
        private final int dimensions;
        
        public FileVectorPacker(File cacheFile, int dimensions) {
            this.cacheFile = cacheFile;
            this.dimensions = dimensions;
        }
        
        @Override
        public void init() {
            if (cacheFile.exists()) {
                cacheFile.delete();
            }
        }
        
        @Override
        public void unpack(List<Vector> candidates, Callback callback) {
            if (tempPw != null) {
                flush();
            }
            Set<String> keys = new HashSet<>();
            for (Vector vec : candidates) {
                keys.add(vec.getKey());
            }
            try (BufferedReader br = new BufferedReader(new FileReader(cacheFile))) {
                while (true) {
                    String l = br.readLine();
                    if (l == null) {
                        break;
                    }
                    int div = l.indexOf('\t');
                    String key = l.substring(0, div);
                    if (!keys.contains(key)) {
                        continue;
                    }
                    String json = l.substring(div + 1);
                    Object obj = UObject.transformStringToObject(json, Object.class);
                    double[] values = null;
                    if (obj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Double> list = (List<Double>)obj;
                        values = list.stream().mapToDouble(Double::doubleValue).toArray();
                    } else {
                        @SuppressWarnings("unchecked")
                        Map<String, Double> map = (Map<String, Double>)obj;
                        values = new double[dimensions];
                        for (String pos : map.keySet()) {
                            values[Integer.parseInt(pos)] = map.get(pos);
                        }
                    }
                    Vector vec = new Vector(key, values);
                    callback.nextVector(vec);
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        
        @Override
        public void pack(Vector vec) {
            if (tempPw == null) {
                try {
                    tempPw = new PrintWriter(cacheFile);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            double[] values = vec.values;
            Map<String, Double> map = new LinkedHashMap<>();
            for (int i = 0; i < values.length; i++) {
                if (values[i] > 0) {
                    map.put(String.valueOf(i), values[i]);
                }
            }
            tempPw.println(vec.getKey() + "\t" + UObject.transformObjectToString(map));
        }
        
        public void flush() {
            tempPw.close();
            tempPw = null;
        }
    }

}

