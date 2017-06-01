# First past generating clusted metagenome-taxa set data 
#
# Currently, this just outputs rows of
#
#    metagenome_id, taxa_id
#    metagenome_id, taxa_id
#    metagenome_id, taxa_id
#
# where both ids are simply integers

Nclusters    <- 50
#Ntaxadict    <- 1000000
Ntaxadict    <- 1000
#Nmetagenomes <- 100000
Nmetagenomes <- 10000
#NtaxperMet   <- 1000
NtaxperMet   <- 10

# generate tax dict
taxadict   <- 1:Ntaxadict
taxalabels <- sprintf( "T%07d", taxadict )

# generate an exponential distribution of cluster sizes

sizes <- exp( -( (1:Nclusters)*70 ) / NtaxperMet )
sizes <- round(  Nmetagenomes * sizes / sum( sizes ) )
Nmetagenomes <- sum( sizes )  # readjust for roundoff error

metalabels <- sprintf( "M%06d", 1:Nmetagenomes )

# This didn't work - matrix was too big
#tab <- matrix( 0, Nmetagenomes, Ntaxadict )
#rownames( tab ) <- metalabels
#colnames( tab ) <- taxalabels

metagenome_id <- 1

for ( i in 1:Nclusters )   # for each cluster
   {
    nmembs <- sizes[i]     # get number of members from size distribution
    ntaxa <- round( rnorm( 1, NtaxperMet, NtaxperMet/10 ) )  # randomize 
    mdist <- ntaxa / 15   # 15 % change
    nchange <- round( rpois( 1, mdist ) )
    cat( "# cluster ", i, " has ", nmembs, "members", ntaxa, " taxa ",
              mdist, "mdist",  nchange, "nchange", "\n" )
    seed <- sample( taxadict, ntaxa )
    # print( seed )
    for ( j in 1:nmembs )
       {
        nchange <- round( rpois( 1, mdist ) )
        changepos <- sample( 1:ntaxa, nchange )
	cat( "# metagenome ", j, " changing ", nchange, ":", changepos , "\n")
	meta <- seed
	#print( meta )
	meta[ changepos ] <- sample( taxadict, nchange )

        #for ( pos in changepos )
	#    meta[[pos]] <- sample( taxadict, 1 )
 	#for ( t in meta )
	#   { tab[ metagenome_id, t ] <- 1 }
	#print( meta )

        for ( t in meta )
	   { cat( metagenome_id, "\t", t, "\n", sep="" ) }
	metagenome_id <- metagenome_id + 1
       }
   }

#cat( "writing graph_maxtrix.csv...\n" )
#write.csv( tab, "matrix.csv", sep="\t" )
#cat( "done\n" )
#