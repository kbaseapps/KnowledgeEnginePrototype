package knowledgeengineprototype;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import us.kbase.common.service.JsonServerMethod;
import us.kbase.common.service.JsonServerServlet;
import us.kbase.common.service.JsonServerSyslog;

//BEGIN_HEADER
//END_HEADER

/**
 * <p>Original spec-file module name: KnowledgeEnginePrototype</p>
 * <pre>
 * A KBase module: KnowledgeEnginePrototype
 * </pre>
 */
public class KnowledgeEnginePrototypeServer extends JsonServerServlet {
    private static final long serialVersionUID = 1L;
    private static final String version = "0.0.1";
    private static final String gitUrl = "https://github.com/kbaseapps/KnowledgeEnginePrototype";
    private static final String gitCommitHash = "f01ad8fe4937bf1b25f9883f2a10a57dc07509e4";

    //BEGIN_CLASS_HEADER
    //END_CLASS_HEADER

    public KnowledgeEnginePrototypeServer() throws Exception {
        super("KnowledgeEnginePrototype");
        //BEGIN_CONSTRUCTOR
        //END_CONSTRUCTOR
    }
    @JsonServerMethod(rpc = "KnowledgeEnginePrototype.status")
    public Map<String, Object> status() {
        Map<String, Object> returnVal = null;
        //BEGIN_STATUS
        returnVal = new LinkedHashMap<String, Object>();
        returnVal.put("state", "OK");
        returnVal.put("message", "");
        returnVal.put("version", version);
        returnVal.put("git_url", gitUrl);
        returnVal.put("git_commit_hash", gitCommitHash);
        //END_STATUS
        return returnVal;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            new KnowledgeEnginePrototypeServer().startupServer(Integer.parseInt(args[0]));
        } else if (args.length == 3) {
            JsonServerSyslog.setStaticUseSyslog(false);
            JsonServerSyslog.setStaticMlogFile(args[1] + ".log");
            new KnowledgeEnginePrototypeServer().processRpcCall(new File(args[0]), new File(args[1]), args[2]);
        } else {
            System.out.println("Usage: <program> <server_port>");
            System.out.println("   or: <program> <context_json_file> <output_json_file> <token>");
            return;
        }
    }
}
