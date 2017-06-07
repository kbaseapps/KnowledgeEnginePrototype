package be.tarsos.lsh;

import java.util.List;

public interface VectorPacker {
    public void init();
    
    public void pack(Vector vec);
    
    public void unpack(List<Vector> candidates, Callback callback);
    
    public interface Callback {
        public void nextVector(Vector vec);
    }
    
    public void flush();
}
