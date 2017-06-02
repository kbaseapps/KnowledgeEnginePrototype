###Dense Query Indexing

#### Challenges
10^5 metagenomes may have O(10^3) tax_ids on average from O(10^6) tax_id spaces.
If we naively store them, it will be about 750GB, which is relatively big
and query processing will be quite slow due to 10^3 query terms.

#### Approach
We project 10^6 tax_id spaces to O(1)~O(10) space, so that efficient query processing to be enabled
and yet we can still achieve reasonable accuracies.

#### Steps
1. Generate sample metagenome-tax_id relation ship
Use `meta_cluster_generator.R`
```

cat sparse.csv | awk -v OFS='\t' '{print $1, $2, 1}' > sparse_ijk.tsv
```
2. Run `dense-indexing.py`
It will generate `output.tsv` for the reduced dimensional projection
``` python
import numpy as np 
from sklearn.decomposition import TruncatedSVD
from scipy.sparse import csc_matrix

raw_data_path = "sparse_ijk.tsv"
out_data_path = "output.tsv"
query_projector = "query_proj.tsv"
svd_params = {
"n_components" : 5,
"algorithm" :  'randomized',
"n_iter" : 20}

d_ijk = np.loadtxt(raw_data_path,delimiter='\t',dtype=np.int32)
fm = csc_matrix((d_ijk[:,2],(d_ijk[:,0],d_ijk[:,1])),dtype=np.int8)
msvd = TruncatedSVD(n_components= svd_params['n_components'], algorithm=svd_params['algorithm'], n_iter=svd_params['n_iter'], random_state=None, tol=0.0)
proj_data = msvd.fit_transform(fm)
np.savetxt(out_data_path, proj_data, delimiter='\t')
np.savetxt(query_projector, msvd.components_, delimiter='\t')
```

3. During query time
Convert query into projected space by using `query_proj.tsv` 

#### Elastic Search
* We may want to use vectorized query plug-in test
* We may want to explore kd-tree indexing
