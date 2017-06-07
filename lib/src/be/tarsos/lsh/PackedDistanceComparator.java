package be.tarsos.lsh;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.tarsos.lsh.families.DistanceComparator;
import be.tarsos.lsh.families.DistanceMeasure;

public class PackedDistanceComparator extends DistanceComparator {
    
    private Map<String, Double> distanceCache = new HashMap<>();
    
    public PackedDistanceComparator(Vector query, DistanceMeasure distanceMeasure,
            VectorPacker packer, List<Vector> candidates) {
        super(query, distanceMeasure);
        packer.unpack(candidates, new VectorPacker.Callback() {
            
            @Override
            public void nextVector(Vector vec) {
                double dist = distanceMeasure.distance(query, vec);
                distanceCache.put(vec.getKey(), dist);
            }
        });
    }
    
    @Override
    public int compare(Vector one, Vector other) {
        double oneDistance = getDistance(one);
        double otherDistance = getDistance(other);
        return Double.compare(oneDistance, otherDistance);
    }
    
    private double getDistance(Vector vec) {
        String key = vec.getKey();
        if (!distanceCache.containsKey(key)) {
            throw new IllegalStateException("Unexpected key: " + vec.getKey());
        }
        return distanceCache.get(key);
    }
}
