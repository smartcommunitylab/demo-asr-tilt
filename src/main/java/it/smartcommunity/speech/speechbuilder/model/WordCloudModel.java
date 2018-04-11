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
package it.smartcommunity.speech.speechbuilder.model;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;

/**
 * @author raman
 *
 */
public class WordCloudModel {

	@Id
	private String id;

	private String type;
	private List<WordCount> model = new LinkedList<>();
	private long timestamp;
	
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}

	
	
	public static class WordCount {
		private String name;
		private Map<String, Object> itemStyle;
		private int value;
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		public Map<String, Object> getItemStyle() {
			return itemStyle;
		}
		public void setItemStyle(Map<String, Object> itemStyle) {
			this.itemStyle = itemStyle;
		}
		public int getValue() {
			return value;
		}
		public void setValue(int value) {
			this.value = value;
		}
	}


	public List<WordCount> getModel() {
		return model;
	}
	public void setModel(List<WordCount> model) {
		this.model = model;
	}
	public long getTimestamp() {
		return timestamp;
	}
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	
}
