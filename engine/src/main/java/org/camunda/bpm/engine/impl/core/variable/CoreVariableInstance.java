/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.engine.impl.core.variable;

/**
 * @author Daniel Meyer
 * @author Roman Smirnov
 * @author Sebastian Menski
 *
 */
public interface CoreVariableInstance {

  String getName();

  Object getValue();

  boolean isAbleToStore(Object value);

  /**
   * @param value the value to test for
   * @param datatypeName the name of the datatype in which the serialized value is provided
   * @return true if the variable instance is able to store the provided value
   */
  boolean isAbleToStoreSerialized(Object value, String datatypeName);

}
