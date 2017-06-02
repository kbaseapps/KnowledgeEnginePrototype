import pandas as pd
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
