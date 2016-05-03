/*
 * Copyright 2016 camunda services GmbH.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package org.camunda.bpm.engine.test.api.runtime.migration.history;

import static org.camunda.bpm.engine.test.api.runtime.migration.ModifiableBpmnModelInstance.modify;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.history.HistoricActivityInstanceQuery;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.history.HistoricProcessInstanceQuery;
import org.camunda.bpm.engine.history.HistoricTaskInstance;
import org.camunda.bpm.engine.history.HistoricTaskInstanceQuery;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.history.HistoricVariableInstanceQuery;
import org.camunda.bpm.engine.migration.MigrationPlan;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.RequiredHistoryLevel;
import org.camunda.bpm.engine.test.api.runtime.migration.MigrationTestRule;
import org.camunda.bpm.engine.test.api.runtime.migration.ModifiableBpmnModelInstance;
import org.camunda.bpm.engine.test.api.runtime.migration.models.ProcessModels;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 *
 * @author Christopher Zell
 */
public class MigrationHistoryProcessInstanceTest {

  protected ProcessEngineRule rule = new ProvidedProcessEngineRule();
  protected MigrationTestRule testHelper = new MigrationTestRule(rule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(rule).around(testHelper);

  protected RuntimeService runtimeService;
  protected HistoryService historyService;

  //============================================================================
  //===================================Migration================================
  //============================================================================
  protected ProcessDefinition sourceProcessDefinition;
  protected ProcessDefinition targetProcessDefinition;
  protected MigrationPlan migrationPlan;

  @Before
  public void initTest() {
    runtimeService = rule.getRuntimeService();
    historyService = rule.getHistoryService();


    sourceProcessDefinition = testHelper.deployAndGetDefinition(ProcessModels.ONE_TASK_PROCESS);

    ModifiableBpmnModelInstance modifiedModel = modify(ProcessModels.ONE_TASK_PROCESS).changeElementId("Process", "Process2")
                                                                                      .changeElementId("userTask", "userTask2");
    targetProcessDefinition = testHelper.deployAndGetDefinition(modifiedModel);
    migrationPlan = runtimeService.createMigrationPlan(sourceProcessDefinition.getId(),
                                                       targetProcessDefinition.getId())
                                  .mapActivities("userTask", "userTask2")
                                  .build();
    runtimeService.startProcessInstanceById(sourceProcessDefinition.getId());
  }

  @Test
  @RequiredHistoryLevel(ProcessEngineConfiguration.HISTORY_ACTIVITY)
  public void testMigrateHistoryProcessInstance() {
    //given
    HistoricProcessInstanceQuery sourceHistoryProcessInstanceQuery = historyService.createHistoricProcessInstanceQuery()
                                                                                   .processDefinitionId(sourceProcessDefinition.getId());
    HistoricProcessInstanceQuery targetHistoryProcessInstanceQuery = historyService.createHistoricProcessInstanceQuery()
                                                                                    .processDefinitionId(targetProcessDefinition.getId());


    //when
    assertEquals(1, sourceHistoryProcessInstanceQuery.count());
    assertEquals(0, targetHistoryProcessInstanceQuery.count());
    ProcessInstanceQuery sourceProcessInstanceQuery = runtimeService.createProcessInstanceQuery().processDefinitionId(sourceProcessDefinition.getId());
    runtimeService.newMigration(migrationPlan)
      .processInstanceQuery(sourceProcessInstanceQuery)
      .execute();

    //then
    assertEquals(0, sourceHistoryProcessInstanceQuery.count());
    assertEquals(1, targetHistoryProcessInstanceQuery.count());

    HistoricProcessInstance instance = targetHistoryProcessInstanceQuery.singleResult();
    assertEquals(instance.getProcessDefinitionKey(), targetProcessDefinition.getKey());
  }


  @Test
  @RequiredHistoryLevel(ProcessEngineConfiguration.HISTORY_ACTIVITY)
  public void testMigrateHistoryActivityInstance() {
    //given
    HistoricActivityInstanceQuery sourceHistoryActivityInstanceQuery = historyService.createHistoricActivityInstanceQuery()
                                                                                   .processDefinitionId(sourceProcessDefinition.getId());
    HistoricActivityInstanceQuery targetHistoryActivityInstanceQuery = historyService.createHistoricActivityInstanceQuery()
                                                                                    .processDefinitionId(targetProcessDefinition.getId());

    //when
    assertEquals(2, sourceHistoryActivityInstanceQuery.count());
    assertEquals(0, targetHistoryActivityInstanceQuery.count());
    ProcessInstanceQuery sourceProcessInstanceQuery = runtimeService.createProcessInstanceQuery().processDefinitionId(sourceProcessDefinition.getId());
    runtimeService.newMigration(migrationPlan)
      .processInstanceQuery(sourceProcessInstanceQuery)
      .execute();

    //then 5 start activities which already ended belongs to the source process
    //and 5 active activities are now migrated to the target process
    assertEquals(1, sourceHistoryActivityInstanceQuery.count());
    assertEquals(1, targetHistoryActivityInstanceQuery.count());

    HistoricActivityInstance instance = targetHistoryActivityInstanceQuery.singleResult();
    assertEquals(instance.getProcessDefinitionKey(), targetProcessDefinition.getKey());
    assertEquals(instance.getActivityId(), "userTask2");
  }

  @Test
  @RequiredHistoryLevel(ProcessEngineConfiguration.HISTORY_ACTIVITY)
  public void testMigrateHistoricSubProcessInstance() {
    //given
    ProcessDefinition processDefinition = testHelper.deployAndGetDefinition(ProcessModels.SCOPE_TASK_SUBPROCESS_PROCESS);

    MigrationPlan migrationPlan = rule.getRuntimeService()
        .createMigrationPlan(processDefinition.getId(), processDefinition.getId())
        .mapEqualActivities()
        .build();

    ProcessInstance processInstance = rule.getRuntimeService().startProcessInstanceById(processDefinition.getId());

    // when
    rule.getRuntimeService().newMigration(migrationPlan)
      .processInstanceIds(Arrays.asList(processInstance.getId()))
      .execute();

    // then
    List<HistoricActivityInstance> historicInstances = historyService
        .createHistoricActivityInstanceQuery()
        .processInstanceId(processInstance.getId())
        .unfinished()
        .orderByActivityId()
        .asc()
        .list();

    Assert.assertEquals(2, historicInstances.size());

    assertMigratedTo(historicInstances.get(0), processDefinition, "subProcess");
    assertMigratedTo(historicInstances.get(1), processDefinition, "userTask");
  }

  @Test
  @RequiredHistoryLevel(ProcessEngineConfiguration.HISTORY_ACTIVITY)
  public void testMigrateHistoricSubProcessRename() {
    //given
    ProcessDefinition sourceDefinition = testHelper.deployAndGetDefinition(ProcessModels.SUBPROCESS_PROCESS);
    ProcessDefinition targetDefinition = testHelper.deployAndGetDefinition(modify(ProcessModels.SUBPROCESS_PROCESS)
        .changeElementId("subProcess", "newSubProcess"));

    MigrationPlan migrationPlan = rule.getRuntimeService()
        .createMigrationPlan(sourceDefinition.getId(), targetDefinition.getId())
        .mapActivities("subProcess", "newSubProcess")
        .mapActivities("userTask", "userTask")
        .build();

    ProcessInstance processInstance = rule.getRuntimeService().startProcessInstanceById(sourceDefinition.getId());

    // when
    rule.getRuntimeService().newMigration(migrationPlan)
      .processInstanceIds(Arrays.asList(processInstance.getId()))
      .execute();

    // then
    List<HistoricActivityInstance> historicInstances = historyService
        .createHistoricActivityInstanceQuery()
        .processInstanceId(processInstance.getId())
        .unfinished()
        .orderByActivityId()
        .asc()
        .list();

    Assert.assertEquals(2, historicInstances.size());

    assertMigratedTo(historicInstances.get(0), targetDefinition, "newSubProcess");
    assertMigratedTo(historicInstances.get(1), targetDefinition, "userTask");
  }

  @Test
  @RequiredHistoryLevel(ProcessEngineConfiguration.HISTORY_ACTIVITY)
  public void testHistoricActivityInstanceBecomeScope() {
    //given
    ProcessDefinition sourceDefinition = testHelper.deployAndGetDefinition(ProcessModels.ONE_TASK_PROCESS);
    ProcessDefinition targetDefinition = testHelper.deployAndGetDefinition(ProcessModels.SCOPE_TASK_PROCESS);

    MigrationPlan migrationPlan = rule.getRuntimeService()
        .createMigrationPlan(sourceDefinition.getId(), targetDefinition.getId())
        .mapEqualActivities()
        .build();

    ProcessInstance processInstance = rule.getRuntimeService().startProcessInstanceById(sourceDefinition.getId());

    // when
    rule.getRuntimeService().newMigration(migrationPlan)
      .processInstanceIds(Arrays.asList(processInstance.getId()))
      .execute();

    // then
    List<HistoricActivityInstance> historicInstances = historyService
        .createHistoricActivityInstanceQuery()
        .processInstanceId(processInstance.getId())
        .unfinished()
        .orderByActivityId()
        .asc()
        .list();

    Assert.assertEquals(1, historicInstances.size());

    assertMigratedTo(historicInstances.get(0), targetDefinition, "userTask");
  }

  protected void assertMigratedTo(HistoricActivityInstance activityInstance, ProcessDefinition processDefinition, String activityId) {
    Assert.assertEquals(processDefinition.getId(), activityInstance.getProcessDefinitionId());
    Assert.assertEquals(processDefinition.getKey(), activityInstance.getProcessDefinitionKey());
    Assert.assertEquals(activityId, activityInstance.getActivityId());
  }
  // TODO: assert HistoricActivityInstance#activityName

  @Test
  @RequiredHistoryLevel(ProcessEngineConfiguration.HISTORY_ACTIVITY)
  public void testMigrateHistoryUserTaskInstance() {
    //given
    HistoricTaskInstanceQuery sourceHistoryTaskInstanceQuery = historyService.createHistoricTaskInstanceQuery()
                                                                                   .processDefinitionId(sourceProcessDefinition.getId());
    HistoricTaskInstanceQuery targetHistoryTaskInstanceQuery = historyService.createHistoricTaskInstanceQuery()
                                                                                    .processDefinitionId(targetProcessDefinition.getId());

    //when
    assertEquals(1, sourceHistoryTaskInstanceQuery.count());
    assertEquals(0, targetHistoryTaskInstanceQuery.count());
    ProcessInstanceQuery sourceProcessInstanceQuery = runtimeService.createProcessInstanceQuery().processDefinitionId(sourceProcessDefinition.getId());
    runtimeService.newMigration(migrationPlan)
      .processInstanceQuery(sourceProcessInstanceQuery)
      .execute();

    //then
    assertEquals(0, sourceHistoryTaskInstanceQuery.count());
    assertEquals(1, targetHistoryTaskInstanceQuery.count());

    HistoricTaskInstance instance = targetHistoryTaskInstanceQuery.singleResult();
    assertEquals(instance.getProcessDefinitionKey(), targetProcessDefinition.getKey());
    assertEquals(instance.getProcessDefinitionId(), targetProcessDefinition.getId());
  }

  @Test
  @RequiredHistoryLevel(ProcessEngineConfiguration.HISTORY_ACTIVITY)
  public void testMigrateHistoryVariableInstance() {
    //given
    ProcessInstanceQuery sourceProcessInstanceQuery = runtimeService.createProcessInstanceQuery().processDefinitionId(sourceProcessDefinition.getId());
    runtimeService.setVariable(sourceProcessInstanceQuery.singleResult().getId(), "test", 3537);
    HistoricVariableInstance instance = historyService.createHistoricVariableInstanceQuery().singleResult();

    //when
    runtimeService.newMigration(migrationPlan)
      .processInstanceQuery(sourceProcessInstanceQuery)
      .execute();

    //then
    HistoricVariableInstance migratedInstance = historyService.createHistoricVariableInstanceQuery().singleResult();
    assertEquals(migratedInstance.getProcessDefinitionKey(), targetProcessDefinition.getKey());
    assertEquals(migratedInstance.getProcessDefinitionId(), targetProcessDefinition.getId());
    assertEquals(instance.getActivityInstanceId(), migratedInstance.getActivityInstanceId());
    assertEquals(instance.getExecutionId(), migratedInstance.getExecutionId());
  }
}
