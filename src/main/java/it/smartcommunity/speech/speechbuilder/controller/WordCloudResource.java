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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestTemplate;

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
	@Value("${model}")
	private String modelLang;
	
	@Value("${tagbuilder.uri}")
	private String cloudBuilderUri;
	@Value("${tagbuilder.options}")
	private String cloudBuilderOptions;
	
	private RestTemplate rest = new RestTemplate();
	private ObjectMapper mapper = new ObjectMapper();
	
	@RequestMapping("/")
    public String home(Model model) {
		model.addAttribute("apiUrl", apiUrl);
		model.addAttribute("mdl", modelLang);
        return "index";
    }
	
	@PostMapping("/api/update")
	public ResponseEntity<List<WordCount>> load(@RequestBody InputModel model) {
		if (StringUtils.hasText(model.getText())) {
			model.setTimestamp(System.currentTimeMillis());
			inputRepo.save(model);
		}
		return ResponseEntity.ok(getCloud());
	} 
	

	@GetMapping("/api/words")
	public ResponseEntity<List<WordCount>> read() {
	   return new ResponseEntity<List<WordCount>>(getCloud(), HttpStatus.OK);
	}
	

	/**
	 * @return
	 */
	private List<WordCount> getCloud() {
		WordCloudModel cloud = cloudRepo.findByType(DEFAULTCLOUD);
		if (cloud == null) {
			updateCloud(DEFAULTCLOUD);
			cloud = cloudRepo.findByType(DEFAULTCLOUD);
			if (cloud == null) return Collections.emptyList();
		}
		return cloudRepo.findByType(DEFAULTCLOUD).getModel();
	}

	private void updateCloud(String type) {
		List<InputModel> list = inputRepo.findByTimestampGreaterThan(System.currentTimeMillis() - 24*60*60*1000);
		String text = list.stream().map(InputModel::getText).collect(Collectors.joining(" "));
		if (!StringUtils.hasText(text)) {
			text = "una prova molto semplice, prova molto difficile, prova meno semplice";
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
	   Map<String, Integer> model = new HashMap<>();
	   
	   String resStr = rest.postForObject(cloudBuilderUri+"?options={options}&text={text}", null, String.class, cloudBuilderOptions, text);
	   try {
		   Map<String, Object> res = mapper.readValue(resStr, Map.class);
		   if (res.containsKey("wordcloudarray")) {
			   TypeReference<List<WordCount>> tr = new TypeReference<List<WordCount>>() {};
			   return mapper.convertValue(res.get("wordcloudarray"), tr);
		   }
	} catch (Exception e) {
		e.printStackTrace();
	}
	   return null;
   } 
}
