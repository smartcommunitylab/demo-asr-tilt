/*******************************************************************************
 * Copyright 2015 Fondazione Bruno Kessler
 * 
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 * 
 *        http://www.apache.org/licenses/LICENSE-2.0
 * 
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 ******************************************************************************/
package it.smartcommunity.speech.speechbuilder.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.smartcommunity.speech.speechbuilder.model.InputModel;
import it.smartcommunity.speech.speechbuilder.model.WordCloudModel;
import it.smartcommunity.speech.speechbuilder.model.WordCloudModel.WordCount;
import it.smartcommunity.speech.speechbuilder.repository.InputRepository;
import it.smartcommunity.speech.speechbuilder.repository.WordCloudRepository;

/**
 * @author raman
 *
 */
@Controller
public class WordCloudResource {

	@Autowired
	private InputRepository inputRepo;
	@Autowired
	private WordCloudRepository cloudRepo;
	
	private static final String DEFAULTCLOUD = "defaultCloud";
	
	@Value("${api.url}")
	private String apiUrl;
	@Value("${api.uploadUrl}")
	private String apiUploadUrl;
	@Value("${api.resource}")
	private String apiResource;
	
	@Value("${model}")
	private String modelLang;
	
	@Value("${tagbuilder.uri}")
	private String cloudBuilderUri;
	@Value("${tagbuilder.options}")
	private String cloudBuilderOptions;
	
	private RestTemplate rest = new RestTemplate();
	private ObjectMapper mapper = new ObjectMapper();
	
	@PostConstruct
	private void init() throws IOException {
		WordCloudModel cloud = cloudRepo.findByType(DEFAULTCLOUD);
		if (cloud == null) {
			String text = StreamUtils.copyToString(getClass().getResourceAsStream("/init.txt"), Charset.forName("UTF-8"));
			text = text.replaceAll("(\\s)+", " ");
			InputModel input = new InputModel();
			input.setText(text);
			load(DEFAULTCLOUD, input);
		}
		rest.getMessageConverters()
        .add(0, new StringHttpMessageConverter(Charset.forName("UTF-8")));
	}
	
	
	@RequestMapping("/")
    public String home(@RequestParam(required=false) String group, Model model) {
		model.addAttribute("apiUrl", apiUrl);
		model.addAttribute("apiResource", apiResource);
		model.addAttribute("mdl", modelLang);
		model.addAttribute("group", group);
        return "index";
    }
	@RequestMapping("/processor")
    public String homeProcessor() {
        return "backintro";
    }
	@RequestMapping("/login")
    public String login() {
        return "login";
    }
	@RequestMapping("/processor/{group}")
    public String processor(Model model, @PathVariable String group) {
		model.addAttribute("apiUrl", apiUrl);
		model.addAttribute("apiResource", apiResource);
		model.addAttribute("mdl", modelLang);
		model.addAttribute("group", group);
        return "back";
    }
	

	@PostMapping("/api/update/{group}")
	public ResponseEntity<List<WordCount>> load(@PathVariable String group, @RequestBody InputModel model) {
		if (StringUtils.hasText(model.getText())) {
			model.setTimestamp(System.currentTimeMillis());
			model.setType(group);
			inputRepo.save(model);
		}
		return ResponseEntity.ok(getCloud(group));
	} 
	
	/**
	 * Upload an audio.
	 * @param file
	 * @return
	 * @throws Exception
	 */
	@PostMapping(value = "/api/upload/{group}")
	public ResponseEntity<List<WordCount>> uploadAudio(@PathVariable String group, @RequestParam("file") MultipartFile file) throws Exception {
		String res = uploadStream(file);
		if (StringUtils.hasText(res)) {
			InputModel model = new InputModel();
			model.setText(res);
			model.setTimestamp(System.currentTimeMillis());
			model.setType(group);
			inputRepo.save(model);
			updateCloud(group);
			updateCloud(DEFAULTCLOUD);
		}
		
		return ResponseEntity.ok(getCloud(group));
	}

	
	
	/**
	 * @param file
	 * @throws IOException 
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private String uploadStream(MultipartFile file) throws IOException {
		String url = apiUploadUrl+"/recognize?lang="+modelLang;

		HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
		con.setRequestProperty("Content-Type", "audio/x-wav; rate=16000;");
		con.setRequestMethod("POST");
		con.setDoOutput(true);
		OutputStream wr = con.getOutputStream();
		wr.write(file.getBytes());
		wr.flush();
		wr.close();
		String res ="";
		if (200 <= con.getResponseCode() && con.getResponseCode() <= 299) {
			BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));			
		    String line = null;
		    while ((line = br.readLine()) != null) {
		    	res += line;
		    }
		    Map<String, Object> resMap = mapper.readValue(res, new TypeReference<Map<String,Object>>() {});
		    if (resMap != null && resMap.containsKey("result")) {
		    	List<Map> list = (List<Map>) resMap.get("result");
		    	if (list.size() == 0) return null;
		    	Map<String, Object> firstMap = list.get(0);
		    	List<Map> resList = (List<Map>) (firstMap.containsKey("hypotheses") ? firstMap.get("hypotheses") : firstMap.get("alternative"));
		    	if (resList == null || resList.size() == 0) return null;
		    	return (String)resList.get(0).get("transcript");
		    }
		    return null;
		} else {
			throw new IOException("operation failed");
		}
	}

	@GetMapping("/api/words")
	public ResponseEntity<List<WordCount>> read(@RequestParam(required=false, defaultValue="defaultCloud") String group) {
	   return new ResponseEntity<List<WordCount>>(getCloud(group), HttpStatus.OK);
	}
	

	/**
	 * @return
	 */
	private List<WordCount> getCloud(String group) {
		String cloudType = StringUtils.isEmpty(group) ? DEFAULTCLOUD : group;
		WordCloudModel cloud = cloudRepo.findByType(cloudType);
		if (cloud == null) {
			updateCloud(cloudType);
			cloud = cloudRepo.findByType(cloudType);
			if (cloud == null) return Collections.emptyList();
		}
		return cloudRepo.findByType(cloudType).getModel();
	}

	private void updateCloud(String type) {
		List<InputModel> list = null; 
		if (DEFAULTCLOUD.equals(type)) {
			list = inputRepo.findAll(); //findByTypeAndTimestampGreaterThan(System.currentTimeMillis() - 24*60*60*1000);
		} else {
			list = inputRepo.findByType(type); //findByTypeAndTimestampGreaterThan(System.currentTimeMillis() - 24*60*60*1000);
		}
		String text = list.stream().map(InputModel::getText).collect(Collectors.joining(" "));
		if (!StringUtils.hasText(text)) {
			return;
		}
		List<WordCount> cloud = buildCloud(text, type);
		if (cloud == null) return;
		WordCloudModel model = cloudRepo.findByType(type);
		if (model == null) {
			model = new WordCloudModel();
			model.setType(type);
		}
		model.setModel(cloud);
		cloudRepo.save(model);
	}
   private List<WordCount> buildCloud(String text, String type) {
	   HttpHeaders headers = new HttpHeaders();
	   headers.set("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
	   MultiValueMap<String, String> map= new LinkedMultiValueMap<String, String>();
	   map.add("options", cloudBuilderOptions);
	   map.add("text", text);
	   HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<MultiValueMap<String, String>>(map, headers);
	   String resStr = rest.postForEntity(cloudBuilderUri, request, String.class).getBody();
	   try {
		   Map<String, Object> res = mapper.readValue(resStr, new TypeReference<Map<String,Object>>() {});
		   if (res.containsKey("wordcloudarray")) {
			   TypeReference<List<WordCount>> tr = new TypeReference<List<WordCount>>() {};
			   return mapper.convertValue(res.get("wordcloudarray"), tr);
		   }
		} catch (Exception e) {
			e.printStackTrace();
		}
	   return null;
   } 
   
   @Scheduled(fixedRate=600000)
   protected void refreshClouds() {
	   cloudRepo.findAll().forEach(c -> {
		   updateCloud(c.getType());
	   });
   }
}
