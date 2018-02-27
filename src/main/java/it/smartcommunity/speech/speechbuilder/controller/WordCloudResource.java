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

import java.util.HashMap;
import java.util.Map;

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

import it.smartcommunity.speech.speechbuilder.model.InputModel;

/**
 * @author raman
 *
 */
@Controller
public class WordCloudResource {

	private static Map<String, Integer> map = new HashMap<>();
	
	@Value("${workers.url}")
	private String workersUrl;
	@Value("${api.url}")
	private String apiUrl;
	@Value("${model}")
	private String modelLang;
	
	
	@RequestMapping("/")
    public String home(Model model) {
		model.addAttribute("apiUrl", apiUrl);
		model.addAttribute("workersUrl", workersUrl);
		model.addAttribute("mdl", modelLang);
        return "index";
    }
	
	@PostMapping("/api/update")
	public ResponseEntity<Map<String,Integer>> load(@RequestBody InputModel model) {
		if (StringUtils.hasText(model.getText())) {
			String[] arr = model.getText().split(" ");
			for (String word : arr) {
				map.put(word, map.getOrDefault(word, 0) + 1);
			}
		}
		return ResponseEntity.ok(map);
	} 
	
	@GetMapping("/api/words")
	public ResponseEntity<Map<String,Integer>> hello() {
	   return new ResponseEntity<Map<String,Integer>>(map, HttpStatus.OK);
   }
}
