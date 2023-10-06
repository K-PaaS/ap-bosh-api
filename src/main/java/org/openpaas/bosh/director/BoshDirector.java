package org.openpaas.bosh.director;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openpaas.bosh.code.BoshCode;
import org.openpaas.bosh.util.SSLUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.net.HttpURLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


public class BoshDirector extends BoshCode {

    private String client_id;

    private String client_secret;

    private String bosh_url;

    private OAuth2AccessToken oAuth2AccessToken;

    private int expiresIn;

    private String oauth_url;
    private String bosh_version;
    public String getBosh_version() {
        return bosh_version;
    }
    ObjectMapper objectMapper = new ObjectMapper();

    final RestTemplate restTemplate = new RestTemplate();

    final RestTemplate not_redirect_restTemplate = new RestTemplate( new SimpleClientHttpRequestFactory(){
        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod ) {
            connection.setInstanceFollowRedirects(false);
        }
    } );

    public BoshDirector(String client_id, String client_secret, String bosh_url, String oauth_url) {
        this.client_id = client_id;
        this.client_secret = client_secret;
        this.bosh_url = bosh_url;
        this.oauth_url = oauth_url;
        try {
            SSLUtils.turnOffSslChecking();
            oAuth2AccessToken = this.getAccessToken();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
    }

    public BoshDirector(String client_id, String client_secret, String bosh_url, String oauth_url, String bosh_version) {
        this.client_id = client_id;
        this.client_secret = client_secret;
        this.bosh_url = bosh_url;
        this.oauth_url = oauth_url;
        this.bosh_version = bosh_version;
        try {
            SSLUtils.turnOffSslChecking();
            oAuth2AccessToken = this.getAccessToken();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
    }

    private OAuth2AccessToken getAccessToken() {
        return restTemplate.exchange(oauth_url + "/oauth/token?client_id=" + client_id + "&client_secret=" + client_secret + "&grant_type=client_credentials", HttpMethod.POST, null, OAuth2AccessToken.class).getBody();
    }

    private HttpEntity<Object> getHeader(String content_type, Object param) {
        if (oAuth2AccessToken.getExpiresIn() <= 180) {
            oAuth2AccessToken = this.getAccessToken();
        }
        HttpHeaders reqHeaders = new HttpHeaders();
        reqHeaders.add("Authorization", oAuth2AccessToken.getTokenType() + " " + oAuth2AccessToken.getValue());
        reqHeaders.add("Content-Type", content_type);
        HttpEntity httpEntity = null;
        if (param != null) {
            httpEntity = new HttpEntity<>(param, reqHeaders);
        } else {
            httpEntity = new HttpEntity<>(reqHeaders);
        }
        return httpEntity;
    }

    private List<Map> resEntityList(String endpoint, HttpMethod method, String context_type, Object param) throws Exception {
        String json = "";
        if (param != null) {
            json = restTemplate.exchange(bosh_url + endpoint, method, getHeader(context_type, param), String.class).getBody();
        } else {
            json = restTemplate.exchange(bosh_url + endpoint, method, getHeader(context_type, null), String.class).getBody();
        }
        return objectMapper.readValue(json, new TypeReference<List<Map>>() {
        });
    }

    private HttpHeaders resEntityListHeader(String endpoint, HttpMethod method, String context_type, Object param) throws Exception {
        HttpHeaders json;
        if (param != null) {
            json = not_redirect_restTemplate.exchange(bosh_url + endpoint, method, getHeader(context_type, param), String.class).getHeaders();
        } else {
            json = not_redirect_restTemplate.exchange(bosh_url + endpoint, method, getHeader(context_type, null), HttpHeaders.class).getHeaders();
        }
        return json;
    }

    private Map resEntityMap(String endpoint, HttpMethod method, String context_type, Object param) throws Exception {
        String json = "";
        if (param != null) {
            json = restTemplate.exchange(bosh_url + endpoint, method, getHeader(context_type, param), String.class).getBody();
        }
        json = restTemplate.exchange(bosh_url + endpoint, method, getHeader(context_type, null), String.class).getBody();
        return objectMapper.readValue(json, Map.class);
    }


    private String resEntityS(String endpoint, HttpMethod method, String context_type, String param) {
        if (param != null) {
            return restTemplate.exchange(bosh_url + endpoint, method, getHeader(context_type, param), String.class).getBody();
        }
        return restTemplate.exchange(bosh_url + endpoint, method, getHeader(context_type, null), String.class).getBody();
    }

    private Map resEntityM(String endpoint, HttpMethod method, String context_type, String param) {
        if (param != null) {
            return restTemplate.exchange(bosh_url + endpoint, method, getHeader(context_type, param), Map.class).getBody();
        }
        return restTemplate.exchange(bosh_url + endpoint, method, getHeader(context_type, null), Map.class).getBody();
    }

    ///// get /////

    //Info
    public Map getInfo() throws Exception {
        return resEntityMap("/info", HttpMethod.GET, ContentsType.TextYaml, null);
    }

    //List configs
    public String getConfigs(String name, String type, boolean latest) throws Exception {
        String request_body = "?";
        if (!(type == null || type.equals(""))) {
            request_body += "type=" + type;
            request_body += "&";
        }
        if (!(name != null || !name.equals(""))) {
            request_body += "name=" + name;
            request_body += "&";
        }
        request_body += latest ? "latest=true" : "latest=false";
        return resEntityS("/configs" + request_body, HttpMethod.GET, ContentsType.TextYaml, null);
    }


    //List all tasks
    public List<Map> getListTasks() throws Exception {
        return resEntityList("/tasks", HttpMethod.GET, ContentsType.TextYaml, null);
    }

    //List currently running tasks
    public List<Map> getListRunningTasks() throws Exception {
        return resEntityList("/tasks?state=queued,processing,cancelling", HttpMethod.GET, ContentsType.TextYaml, null);
    }

    //List tasks associated with a deployment
    public List<Map> getListDeploymentAssociatedWithTasks(String deployment_name) throws Exception {
        return resEntityList("/tasks?deployment=" + deployment_name, HttpMethod.GET, ContentsType.TextYaml, null);
    }

    //List tasks associated with a context ID
    public List<Map> getListContextIDAssociatedWithTasks(String context_id) throws Exception {
        return resEntityList("/tasks?context_id=" + context_id, HttpMethod.GET, ContentsType.TextYaml, null);
    }

    //Retrieve single task
    public Map getTask(String task_id) throws Exception {
        return resEntityMap("/tasks/" + task_id, HttpMethod.GET, ContentsType.TextYaml, null);
    }

    //Retrieve task's log(tpye ex.. debug, event, result)
    public List<Map> getResultRetrieveTasksLog(String task_id) throws Exception {
            String result = resEntityS("/tasks/" + task_id + "/output?type=result", HttpMethod.GET, ContentsType.TextHtml, null);
            result = "["+result+"]";
            result = result.replace("false}","false},");
            result = result.replace("},\n]","}\n]");
            return objectMapper.readValue(result, new TypeReference<List<Map>>() {});
    }

    //director version over v270.11 current v271.2 2020-10-20
    public List<Map> getResultRetrieveTasksLogv271(String task_id) throws Exception {
        String result = resEntityS("/tasks/" + task_id + "/output?type=result", HttpMethod.GET, ContentsType.TextHtml, null);
        result = result.replace("{\"vm_cid",",{\"vm_cid");
        result = result.substring(1);
        result = "["+result+"]";
        return objectMapper.readValue(result, new TypeReference<List<Map>>() {});
    }

    //Retrieve task's log(tpye ex.. debug, event, result)
    public String getDebugRetrieveTasksLog(String task_id) throws Exception {
        return resEntityS("/tasks/" + task_id + "/output?type=debug", HttpMethod.GET, ContentsType.TextHtml, null);
    }

    public String getUpdateVMIPS(String task_id) throws Exception{
        String result = getDebugRetrieveTasksLog(task_id);
        int point = result.indexOf("Allocated dynamic IP");
        result = result.substring(point,point+50);
        point = result.indexOf("'");
        result = result.substring(point+1);
        point = result.indexOf("'");
        return result.substring(0,point);
    }

    public String getStartVMIPS(String task_id, String instance_name, String instance_id) throws Exception{
        String result = getDebugRetrieveTasksLog(task_id);
        String instanceName = "instance=" + instance_name + "/" + instance_id;
        result = result.substring(result.indexOf(instanceName)+10);
        String subresult = result.substring(result.indexOf(instanceName)-50, result.indexOf(instanceName)+300);
        result = subresult.substring(subresult.indexOf("ip=")+3);
        result = result.substring(0, result.indexOf(","));
        if(result.length() < 3){
            result = subresult.substring(subresult.lastIndexOf("ip=")+3);
            result = result.substring(0, result.indexOf(","));
        }
        return result;
    }

    public String getUpdateVMInstance(String task_id, String instance_name) throws Exception{
        String result = getDebugRetrieveTasksLog(task_id);
        result = result.substring(result.indexOf("INSERT INTO \"instances\""));
        int point = result.indexOf(instance_name+"/");
        int lenght = instance_name.length() +1;
        result = result.substring(point + lenght, point + lenght + 36);
        return result;
    }


    //List all uploaded stemcells
    public List<Map> getListStemcells() throws Exception {
        return resEntityList("/stemcells", HttpMethod.GET, ContentsType.TextYaml, null);
    }

    //List all uploaded stemcells
    public List<Map> getUploadReleases() throws Exception {
        return resEntityList("/releases", HttpMethod.GET, ContentsType.TextYaml, null);
    }

    //List all deployments
    public List<Map> getListDeployments() throws Exception {
        return resEntityList("/deployments", HttpMethod.GET, ContentsType.TextYaml, null);
    }

    //List all deployments without configs, releases, and stemcells
    public List<Map> getListDeploymentsWithoutCRS() throws Exception {
        return resEntityList("/deployments?exclude_configs=true&exclude_releases=true&exclude_stemcells=true", HttpMethod.GET, ContentsType.TextYaml, null);
    }

    //Retrieve single deployment
    public Map getDeployments(String deployment_name) throws Exception {
        return resEntityMap("/deployments/" + deployment_name, HttpMethod.GET, ContentsType.TextYaml, null);
    }

    //List all instances
    public List<Map> getListInstances(String deployment_name) throws Exception {
        return resEntityList("/deployments/" + deployment_name + "/instances", HttpMethod.GET, ContentsType.TextYaml, null);
    }

    //List details of instances
    public String getListDetailOfInstances(String deployment_name) throws Exception {
        String re[] = resEntityListHeader("/deployments/" + deployment_name + "/instances?format=full", HttpMethod.GET, ContentsType.TextHtml, null).toSingleValueMap().get("Location").split("/");
        int length = re.length;
        return re[length-1];
    }

    //List all VMs
    public List<Map> getListDeploymentsVMS(String deployment_name) throws Exception {
        return resEntityList("/deployments/" + deployment_name + "/vms", HttpMethod.GET, ContentsType.TextYaml, null);
    }

    //List VM details
    public String getListDetailDeploymentsVMS(String deployment_name) throws Exception {
        String re[] = resEntityListHeader("/deployments/" + deployment_name + "/vms?format=full", HttpMethod.GET, ContentsType.TextHtml, null).toSingleValueMap().get("Location").split("/");
        int length = re.length;
        return re[length-1];
    }

    //List events
    public List<Map> getListEvents() throws Exception {
        return resEntityList("/events", HttpMethod.GET, ContentsType.TextYaml, null);
    }

    //List events
    public Map getEvents(String event_id) throws Exception {
        return resEntityMap("/events/" + event_id, HttpMethod.GET, ContentsType.TextYaml, null);
    }

    //List deployment_locks
    public String getListLocks() throws Exception{
        return resEntityS("/locks", HttpMethod.GET, ContentsType.TextYaml, null);
    }

    ///// post /////

    //Create config
    public String postCreateConfigs(String name, String type, String content) throws Exception {
        String param = "{\"name\": \"" + name + "\", \"type\": \"" + type + "\", \"content\": \"" + content + "\"}";
        return resEntityS("/configs", HttpMethod.POST, ContentsType.TextYaml, param);
    }

    //Diff config
    public String postDiffConfig(String name, String type, String content) throws Exception {
        String param = "{\"name\": \"" + name + "\", \"type\": \"" + type + "\", \"content\": \"" + content + "\"}";
        return resEntityS("/configs/diff", HttpMethod.POST, ContentsType.TextYaml, param);
    }

    public String postCreateAndUpdateDeployment(String param) throws Exception {
        return restTemplate.exchange(bosh_url + "/deployments", HttpMethod.POST, getHeader(ContentsType.TextYaml, param), String.class).getBody();
    }

    ///// put /////
    public List<Map> updateIgnoreInstance(String deployment_name, String instance_name, String instance_id, boolean ignore) throws Exception {
        String param = "{\"ignore\":" + ignore + "}";
        return resEntityList("/deployments/" + deployment_name + "/instance_groups/" + instance_name + "/" + instance_id + "/ignore", HttpMethod.PUT, ContentsType.TextYaml, param);
    }

    public boolean updateInstanceState(String deployment_name, String job_name, String job_id, String state) throws Exception {
        boolean status = false;
        try {
            resEntityList("/deployments/" + deployment_name + "/jobs/" + job_name + "/" + job_id + "?state=" + state, HttpMethod.PUT, ContentsType.TextYaml, null);
            status = true;
        } catch (Exception e) {
            if (e instanceof NullPointerException) {
                status = true;
            } else {
                status = false;
            }
        }
        return status;
    }


    ///// delete /////

    //Marks configs as deleted
    public String deleteConfig(String name, String type) throws Exception {
        if (name == null || name.equals("")) {
            throw new Exception("not setting name");
        }
        if (type == null || name.equals("")) {
            throw new Exception("not setting type");
        }
        return resEntityS("/configs?type=" + type + "&name=" + name, HttpMethod.DELETE, ContentsType.TextYaml, null);
    }

    public String deleteDeployment(String deployment_name) {
        return resEntityS("/deployments/" + deployment_name, HttpMethod.DELETE, ContentsType.TextYaml, null);
    }



    /*
     * 서비스 증가 및 생성
     */
    public boolean deploy(String deployment_name, String service_name) throws Exception {
        return this.deploy(deployment_name, service_name, null);
    }
    
    public boolean deploy(String deployment_name, String service_name, String staticIp) throws Exception {
        try {
            Map manifest_map = getDeployments(deployment_name);
            if (manifest_map.size() == 0) {
                throw new Exception("Not found manifest");
            }

            String manifest = manifest_map.get("manifest").toString();

            /*
             * Manifest 값의 특정 인스턴스 값을 변환
             */
            manifest = manifestParser(manifest, service_name, staticIp);

            /*
             * 배포
             */
            postCreateAndUpdateDeployment(manifest);
            return true;
        } catch (Exception e){
            throw new Exception("Deploy Error : " + e.getMessage());
        }
    }


    private boolean deployTask(String deployment_name) throws Exception {
        boolean processing = true;
        int count = 0;
        while (processing) {
            List<Map> deployTask = getListRunningTasks();
            if (deployTask.size() > 0) {
                int cnt = 0;
                for (Map map : deployTask) {
                    if (map.get("deployment").equals(deployment_name)) {
                        cnt++;
                    }
                }
                if (cnt == 0) {
                    return true;
                }
                Thread.sleep(10000);
            } else {
                processing = false;
            }
        }
        return true;
    }

    private String manifestParser(String manifest, String service_name, String staticIp) throws Exception {
    	/*
         * String을 Map으로 변환하여, instances 값 찾아 변환
         */
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml loader = new Yaml(options);
        Map manifest_map = loader.load(manifest);
        List<Map> instance_groups = (List<Map>) manifest_map.get("instance_groups");
        for (Map map : instance_groups) {
            if (map.get("name").equals(service_name)) {
                String strEa = map.get("instances").toString();
                int ea = Integer.parseInt(strEa);
                ea = ea + 1;
                map.put("instances", ea);
                
                // vm 에 public 할당. (manifest 에 임의 추가, web ide 이외의 vm 은 구조 확인 필요)
                if(!StringUtils.isEmpty(staticIp)) {
                	// vip setting
                    List<Map<String, Object>> networksList = (List<Map<String, Object>>)map.get("networks");
                    boolean hasNetwork = false;
                    for(Map<String, Object> networkMap : networksList) {
                    	if("vip".equals(networkMap.get("name"))) {
                    		hasNetwork = true;
                    		List<String> staticIpList = (List<String>)networkMap.get("static_ips");
                    		staticIpList.add(staticIp);
                    	}
                    }
                    
                    if(!hasNetwork) {
                    	Map<String, Object> networkMap = new HashMap<String, Object>();
                    	List<String> staticIpList = new ArrayList<String>();
                    	staticIpList.add(staticIp);
                    	networkMap.put("name", "vip");
                    	networkMap.put("static_ips", staticIpList);
                    	
                    	networksList.add(networkMap);
                    }
                }
            }
        }
        manifest_map.put("instance_groups", instance_groups);
        return loader.dump(manifest_map);
    }

    public String updateServiceInstance(String deployment_name, String job_name, String vm_type) throws Exception {
        Map deployment = getDeployments(deployment_name);
        String deployment_manifest = deployment.get("manifest").toString();
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml loader = new Yaml(options);
        Map manifest_map = loader.load(deployment_manifest);
        List<Map> instance_groups = (List<Map>) manifest_map.get("instance_groups");
        for (Map map : instance_groups) {
            if (map.get("name").equals(job_name)) {
                map.put("vm_type", vm_type);
            }
        }
        postCreateAndUpdateDeployment(loader.dump(manifest_map));
        return loader.dump(manifest_map);
    }

    public String createServiceInstance(String job_name, String vm_type, String yml) throws Exception {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        
        Yaml loader = new Yaml(options);
        
        Map manifest_map = loader.load(yml);
        manifest_map.put("name",manifest_map.get("name").toString()+"-"+ UUID.randomUUID());
        
        List<Map> instance_groups = (List<Map>) manifest_map.get("instance_groups");
        for (Map map : instance_groups) {
            if (map.get("name").equals(job_name)) {
                map.put("vm_type", vm_type);
            }
        }
        postCreateAndUpdateDeployment(loader.dump(manifest_map));
        return loader.dump(manifest_map);
    }
}
