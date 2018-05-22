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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.bind.annotation.PutMapping;
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

	private static final Logger logger = LoggerFactory.getLogger(WordCloudResource.class);
	
	@Autowired
	private InputRepository inputRepo;
	@Autowired
	private WordCloudRepository cloudRepo;
	
	private static final String DEFAULTCLOUD = "defaultCloud";
	private static final String DRAFTCLOUD = "draft";
	private static final String SOCIALCLOUD = "socialCloud";
	private static ExecutorService executor = Executors.newCachedThreadPool();
	
	@Value("${api.url}")
	private String apiUrl;
	@Value("${api.uploadUrl}")
	private String apiUploadUrl;
	@Value("${api.resource}")
	private String apiResource;
	@Value("${upload.dir}")
	private String uploadDir;
	
	@Value("${model}")
	private String modelLang;
	
	@Value("${tagbuilder.uri}")
	private String cloudBuilderUri;
	@Value("${tagbuilder.options}")
	private String cloudBuilderOptions;
	@Value("${tagsocial.uri}")
	private String cloudSocialUri;
	@Value("${tagsocial.options}")
	private String cloudSocialOptions;
	
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
		
		Path dir = Paths.get(uploadDir);
		Files.list(dir).forEach(subdir -> {
			try {
				Files.list(subdir).forEach(file -> {
					executor.execute(new Runnable() {
						@Override
						public void run() {
							try {
								processFile(subdir.getFileName().toString(), file);
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					});
				});
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
		
		WordCloudModel draft = cloudRepo.findByType(DRAFTCLOUD);
		if (draft == null) {
			WordCloudModel defaultCloud = cloudRepo.findByType(DEFAULTCLOUD);
			draft = new WordCloudModel();
			draft.setType(DRAFTCLOUD);
			draft.setModel(defaultCloud.getModel());
			draft.setTimestamp(System.currentTimeMillis());
			cloudRepo.save(draft);
		}

	}
	
	
	@RequestMapping("/")
    public String home(@RequestParam(required=false) String group, @RequestParam(required=false, defaultValue="false") Boolean simple, Model model) {
		model.addAttribute("apiUrl", apiUrl);
		model.addAttribute("apiResource", apiResource);
		model.addAttribute("mdl", modelLang);
		model.addAttribute("group", group);
        return simple ? "simple" : "index";
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

		Files.createDirectories(Paths.get(uploadDir + "/" + group));
		final Path nfile = Files.createFile(Paths.get(uploadDir + "/" + group +"/" + UUID.randomUUID().toString()));
		Files.write(nfile, file.getBytes());
		
		executor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					processFile(group, nfile);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		
		return ResponseEntity.ok(getCloud(group));
	}

	@GetMapping(value = "/api/publish")
	public ResponseEntity<List<String>> publish() {
		WordCloudModel draft = cloudRepo.findByType(DRAFTCLOUD);
		WordCloudModel publicCloud = cloudRepo.findByType(DEFAULTCLOUD);
		publicCloud.setModel(draft.getModel());
		publicCloud.setTimestamp(System.currentTimeMillis());
		cloudRepo.save(publicCloud);
		return ResponseEntity.ok(
				publicCloud.getModel().stream()
				.map(wc -> wc.getName())
				.sorted()
				.collect(Collectors.toList()));
	}
	@GetMapping(value = "/api/draft")
	public ResponseEntity<List<String>> draft() {
		WordCloudModel draft = cloudRepo.findByType(DRAFTCLOUD);
		return ResponseEntity.ok(
				draft.getModel().stream()
				.map(wc -> wc.getName())
				.sorted()
				.collect(Collectors.toList()));
	}
	
	private void processFile(String group, Path file) throws IOException {
		logger.info("Processing file "+file);
		String res = uploadStream(Files.readAllBytes(file));
		if (StringUtils.hasText(res)) {
			logger.info("Process result (length): "+res.length());
			InputModel model = new InputModel();
			model.setText(res);
			model.setTimestamp(System.currentTimeMillis());
			model.setType(group);
			inputRepo.save(model);
			updateCloud(group);
			updateCloud(DEFAULTCLOUD);
			Files.delete(file);
		} else {
			logger.info("Process failed ");
		}
	}

	
	
	/**
	 * @param file
	 * @throws IOException 
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private String uploadStream(byte[] bytes) throws IOException {
		String url = apiUploadUrl+"/recognize?lang="+modelLang;

		HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
		con.setRequestProperty("Content-Type", "audio/x-wav; rate=16000;");
		con.setRequestMethod("POST");
		con.setDoOutput(true);
		OutputStream wr = con.getOutputStream();
		wr.write(bytes);
		wr.flush();
		wr.close();
		String res ="";
		if (200 <= con.getResponseCode() && con.getResponseCode() <= 299) {
			BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));			
		    String line = null;
		    while ((line = br.readLine()) != null) {
		    	res += line;
		    }
		    System.err.println(res);
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

	@PutMapping("/api/archive/{group}")
	public ResponseEntity<List<WordCount>> archive(@PathVariable String group) {
		List<InputModel> list = inputRepo.findByType(group);
		final long ts = System.currentTimeMillis();
		list.forEach(i -> i.setArchived(ts));
		inputRepo.save(list);
		WordCloudModel cloud = cloudRepo.findByType(group);
		cloud.setModel(Collections.emptyList());
		cloudRepo.save(cloud);
	    return new ResponseEntity<List<WordCount>>(cloud.getModel(), HttpStatus.OK);
	}


	/**
	 * @return tag coud data
	 */
	private List<WordCount> getCloud(String group) {
		String cloudType = StringUtils.isEmpty(group) ? DEFAULTCLOUD : group;
		// create if not present
		WordCloudModel cloud = cloudRepo.findByType(cloudType);
		if (cloud == null) {
			updateCloud(cloudType);
			cloud = cloudRepo.findByType(cloudType);
			if (cloud == null) return Collections.emptyList();
		}
		List<WordCount> res = cloudRepo.findByType(cloudType).getModel();
		// for overall cloud merge data with social cloud 
		if (DEFAULTCLOUD.equals(cloudType)) {
			Map<String, WordCount> map = new HashMap<>();
			res.forEach(r -> map.put(r.getName(), r));
			
//			cloud = cloudRepo.findByType(SOCIALCLOUD);
//			if (cloud != null && cloud.getModel() != null) {
//				cloud.getModel().forEach(r -> {
//					if (map.containsKey(r.getName())) {
//						map.get(r.getName()).setValue(Math.max(r.getValue(), map.get(r.getName()).getValue()));
//					} else {
//						map.put(r.getName(), r);
//					}
//				});
//				res = new LinkedList<>(map.values());
//			}
		}
		return res;
	}

	/**
	 * Update cloud data. For main cloud the data is taken from all the input sources
	 * @param type
	 */
	private void updateCloud(String type) {
		List<InputModel> list = null;
		// do not update automatically public cloud data
		if (DEFAULTCLOUD.equalsIgnoreCase(type) || DRAFTCLOUD.equalsIgnoreCase(type)) return;
		if (SOCIALCLOUD.equalsIgnoreCase(type)) {
			try {
				updateSocial();
			} catch (Exception e) {
			}
			return;
		}

		// draft cloud data is taken from all sources
		if (DRAFTCLOUD.equals(type)) {
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
		model.setTimestamp(System.currentTimeMillis());
		cloudRepo.save(model);
   }
  	
	private void updateSocial() {
		List<WordCount> cloud = buildSocial();
		if (cloud == null) return;
		WordCloudModel model = cloudRepo.findByType(SOCIALCLOUD);
		if (model == null) {
			model = new WordCloudModel();
			model.setType(SOCIALCLOUD);
		}
		model.setModel(cloud);
		model.setTimestamp(System.currentTimeMillis());
		cloudRepo.save(model);
   }	
	
   private List<WordCount> buildCloud(String text, String type) {
	   HttpHeaders headers = new HttpHeaders();
	   headers.set("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
	   MultiValueMap<String, String> map= new LinkedMultiValueMap<String, String>();
	   map.add("options", cloudBuilderOptions);
	   map.add("text", text);
	   logger.error("sending: "+ text);
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
   
   
   private List<WordCount> buildSocial() {
	   HttpHeaders headers = new HttpHeaders();
	   headers.set("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
	   MultiValueMap<String, String> map = new LinkedMultiValueMap<String, String>();
	   map.add("options", cloudSocialOptions);
	   HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<MultiValueMap<String, String>>(map, headers);
	   String resStr = rest.postForEntity(cloudSocialUri, request, String.class).getBody();
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
   
   @Scheduled(fixedRate=30000)
   protected void refreshClouds() {
	   cloudRepo.findAll().forEach(c -> {
		   updateCloud(c.getType());
	   });
   }
}
