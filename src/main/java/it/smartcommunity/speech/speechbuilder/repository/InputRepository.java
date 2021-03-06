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
package it.smartcommunity.speech.speechbuilder.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import it.smartcommunity.speech.speechbuilder.model.InputModel;

/**
 * @author raman
 *
 */
public interface InputRepository extends MongoRepository<InputModel, String>{

	List<InputModel> findByTypeAndTimestampGreaterThan(String type, long timestamp);
	List<InputModel> findByTimestampGreaterThan(long timestamp);
	@Query("{ 'type' : ?0, archived: null}")
	List<InputModel> findByType(String type);
}
