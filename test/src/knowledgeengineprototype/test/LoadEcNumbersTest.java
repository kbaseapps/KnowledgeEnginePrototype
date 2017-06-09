package knowledgeengineprototype.test;

import java.io.File;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import junit.framework.Assert;

import org.apache.commons.math.MathException;
import org.apache.commons.math.distribution.IntegerDistribution;
import org.apache.commons.math.distribution.PoissonDistribution;
import org.apache.commons.math.distribution.PoissonDistributionImpl;
import org.eclipse.collections.impl.block.factory.Functions;
import org.ini4j.Ini;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.auth.AuthConfig;
import us.kbase.auth.AuthToken;
import us.kbase.auth.ConfigurableAuthService;
import us.kbase.common.service.JsonServerSyslog;
import us.kbase.common.service.RpcContext;
import us.kbase.common.service.UObject;
import workspace.CreateWorkspaceParams;
import workspace.GetObjects2Params;
import workspace.ObjectSpecification;
import workspace.ProvenanceAction;
import workspace.WorkspaceClient;
import workspace.WorkspaceIdentity;

public class LoadEcNumbersTest {
    private static AuthToken token = null;
    private static Map<String, String> config = null;
    private static WorkspaceClient wsClient = null;
    private static String wsName = null;
    private static Path scratch;
    private static URL callbackURL;
    
    @BeforeClass
    public static void init() throws Exception {
        // Config loading
        String configFilePath = System.getenv("KB_DEPLOYMENT_CONFIG");
        if (configFilePath == null) {
            configFilePath = System.getProperty("KB_DEPLOYMENT_CONFIG");
        }
        File deploy = new File(configFilePath);
        Ini ini = new Ini(deploy);
        config = ini.get("KnowledgeEnginePrototype");
        // Token validation
        String authUrl = config.get("auth-service-url");
        String authUrlInsecure = config.get("auth-service-url-allow-insecure");
        ConfigurableAuthService authService = new ConfigurableAuthService(
                new AuthConfig().withKBaseAuthServerURL(new URL(authUrl))
                .withAllowInsecureURLs("true".equals(authUrlInsecure)));
        String tokenString = System.getenv("KB_AUTH_TOKEN");
        if (tokenString == null) {
            tokenString = System.getProperty("KB_AUTH_TOKEN");
        }
        token = authService.validateToken(tokenString);
        // Reading URLs from config
        wsClient = new WorkspaceClient(new URL(config.get("workspace-url")), token);
        wsClient.setIsInsecureHttpConnectionAllowed(true); // do we need this?
        //callbackURL = new URL(System.getenv("SDK_CALLBACK_URL"));
        scratch = Paths.get(config.get("scratch"));
        // These lines are necessary because we don't want to start linux syslog bridge service
        JsonServerSyslog.setStaticUseSyslog(false);
        JsonServerSyslog.setStaticMlogFile(new File(config.get("scratch"), "test.log")
            .getAbsolutePath());
    }
    
    private static String getWsName() throws Exception {
        if (wsName == null) {
            long suffix = System.currentTimeMillis();
            wsName = "test_KnowledgeEnginePrototype_" + suffix;
            wsClient.createWorkspace(new CreateWorkspaceParams().withWorkspace(wsName));
        }
        return wsName;
    }
    
    private static RpcContext getContext() {
        return new RpcContext().withProvenance(Arrays.asList(new ProvenanceAction()
            .withService("KnowledgeEnginePrototype").withMethod("please_never_use_it_in_production")
            .withMethodParams(new ArrayList<UObject>())));
    }
    
    @AfterClass
    public static void cleanup() {
        if (wsName != null) {
            try {
                wsClient.deleteWorkspace(new WorkspaceIdentity().withWorkspace(wsName));
                System.out.println("Test workspace was deleted");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testLoadEcNumbers() throws Exception {
        Map<String, Object> data = wsClient.getObjects2(new GetObjects2Params().withObjects(Arrays.asList(
                new ObjectSpecification().withRef("22357/2/2")))).getData().get(0).getData().asClassInstance(Map.class);
        Map<String, Object> ecData = (Map<String, Object>)data.get("custom");
        new ObjectMapper().writeValue(new File("test/data/ec_numbers.json"), ecData);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testTransformEcNumbers() throws Exception {
        Map<String, Object> ecData = new ObjectMapper().readValue(new File("test/data/ec_numbers.json"), Map.class);
        //System.out.println(ecNumbers.keySet());
        Map<String, Integer> ecToCount = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> ecToGenomeToCount = new LinkedHashMap<>();
        for (String objectName : ecData.keySet()) {
            Map<String, Object> genome = (Map<String, Object>)ecData.get(objectName);
            Map<String, List<String>> geneToEc = (Map<String, List<String>>)genome.get("genes");
            for (String geneId : geneToEc.keySet()) {
                for (String ec : geneToEc.get(geneId)) {
                    if (!ecToCount.containsKey(ec)) {
                        ecToCount.put(ec, 0);
                    }
                    ecToCount.put(ec, ecToCount.get(ec) + 1);
                    if (!ecToGenomeToCount.containsKey(ec)) {
                        ecToGenomeToCount.put(ec, new LinkedHashMap<>());
                    }
                    Map<String, Integer> genomeToCount = ecToGenomeToCount.get(ec);
                    if (!genomeToCount.containsKey(objectName)) {
                        genomeToCount.put(objectName, 0);
                    }
                    genomeToCount.put(objectName, genomeToCount.get(objectName) + 1);
                }
            }
        }
        System.out.println("Total number of EC: " + ecToCount.size());
        List<String> ecList = new ArrayList<>(ecToCount.keySet());
        Collections.sort(ecList, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return ecToCount.get(o2).compareTo(ecToCount.get(o1));
            }
        });
        Map<String, Integer> ecToPos = new LinkedHashMap<>();
        for (int i = 0; i < ecList.size(); i++) {
            String ec = ecList.get(i);
            int totalNumber = ecToCount.get(ec);
            int genomeNumber = ecToGenomeToCount.get(ec).size();
            ecToPos.put(ec, i);
            if (i < 100) {
                System.out.println(ec + "\t" + genomeNumber + "\t" + 
                        ((double)totalNumber)/genomeNumber);
            }
        }
        PrintWriter pw = new PrintWriter(new File("test_local/ec_profiles.tsv"));
        PrintWriter pw2 = new PrintWriter(new File("test_local/ec_counts.tsv"));
        pw.println(ecList.size());
        for (String objectName : ecData.keySet()) {
            Map<String, Object> genome = (Map<String, Object>)ecData.get(objectName);
            String genomeName = (String)genome.get("genome_name");
            Map<Integer, Integer> ecPosToCount = new TreeMap<>();
            Map<String, List<String>> geneToEc = (Map<String, List<String>>)genome.get("genes");
            for (String geneId : geneToEc.keySet()) {
                for (String ec : geneToEc.get(geneId)) {
                    int ecPos = ecToPos.get(ec);
                    if (!ecPosToCount.containsKey(ecPos)) {
                        ecPosToCount.put(ecPos, 0);
                    }
                    ecPosToCount.put(ecPos, ecPosToCount.get(ecPos) + 1);
                }
            }
            pw.println(objectName + "\t" + UObject.transformObjectToString(ecPosToCount));
            pw2.println(objectName + "\t" + genomeName + "\t" + ecPosToCount.size() + "\t" + geneToEc.size());
        }
        pw2.close();
        Random rand = new Random(1234567890);
        PrintWriter pw3 = new PrintWriter(new File("test_local/generated_counts.tsv"));
        for (int n = ecData.size(); n < 100000; n++) {
            String objectName = "generated" + n;
            Map<Integer, Integer> ecPosToCount = new LinkedHashMap<>();
            for (String ec : ecList) {
                int genomeNumber = ecToGenomeToCount.get(ec).size();
                double genomeFraction = ((double)genomeNumber) / ecData.size() + 0.03;
                if (rand.nextDouble() > genomeFraction) {
                    continue;
                }
                int totalNumber = ecToCount.get(ec);
                double avg = ((double)totalNumber) / genomeNumber;
                IntegerDistribution dist = new PoissonDistributionImpl(avg); 
                int value = dist.inverseCumulativeProbability(rand.nextDouble());
                if (value <= 0) {
                    value = 1;
                }
                int ecPos = ecToPos.get(ec);
                ecPosToCount.put(ecPos, value);
            }
            pw.println(objectName + "\t" + UObject.transformObjectToString(ecPosToCount));
            pw3.println(ecPosToCount.size());
            if (n % 1000 == 0) {
                System.out.println(n + " genomes processed");
            }
        }
        pw.close();
        pw3.close();
    }
}
