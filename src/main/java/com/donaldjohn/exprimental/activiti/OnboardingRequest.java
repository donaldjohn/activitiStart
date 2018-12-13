package com.donaldjohn.exprimental.activiti;

import org.activiti.engine.*;
import org.activiti.engine.form.FormData;
import org.activiti.engine.form.FormProperty;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.activiti.engine.impl.form.DateFormType;
import org.activiti.engine.impl.form.LongFormType;
import org.activiti.engine.impl.form.StringFormType;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class OnboardingRequest
{
    public static void main(String[] args) throws ParseException
    {
        ProcessEngineConfiguration cfg = new StandaloneProcessEngineConfiguration()
                .setJdbcUrl("jdbc:mysql://localhost:3306/activiti")
                .setJdbcUsername("root")
                .setJdbcPassword("123456")
                .setJdbcDriver("com.mysql.jdbc.Driver")
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);

        ProcessEngine processEngine = cfg.buildProcessEngine();
        String name = processEngine.getName();
        String ver = processEngine.VERSION;
        System.out.println("ProcessEngine [" + name + "] Version: [" + ver + "]");

        RepositoryService repositoryService = processEngine.getRepositoryService();
        Deployment deployment = repositoryService
                .createDeployment()
                .addClasspathResource("onboarding.bpmn20.xml")
                .deploy();

        ProcessDefinition processDefinition = repositoryService
                .createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();


        System.out.println("Found process definition ["
                + processDefinition.getName()
                + "] with id ["
                + processDefinition.getId() + "]");

        RuntimeService runtimeService = processEngine.getRuntimeService();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("onboarding");
        System.out.println("Onboarding process started with process instance id [" + processInstance.getProcessInstanceId() + "] key [" + processInstance.getProcessDefinitionKey() + "]");

        TaskService taskService = processEngine.getTaskService();
        FormService formService = processEngine.getFormService();

        HistoryService historyService = processEngine.getHistoryService();

        Scanner scanner = new Scanner(System.in);

        while (processInstance != null && !processInstance.isEnded())
        {
            List<Task> tasks = taskService.createTaskQuery().taskCandidateGroup("managers").list();

            System.out.println("Active outstanding tasks [" + tasks.size() + "]");

            for (int i = 0; i < tasks.size(); i++)
            {
                Task task = tasks.get(i);
                System.out.println("Processing Task [" + task.getName() + "]");
                Map<String, Object> variables = new HashMap<String, Object>();
                FormData formData = formService.getTaskFormData(task.getId());
                for (FormProperty formProperty : formData.getFormProperties())
                {

                    if (formProperty.getType() instanceof StringFormType)
                    {
                        System.out.println(formProperty.getName() + "?");
                        String value = scanner.nextLine();
                        variables.put(formProperty.getName(), value);
                    } else if (formProperty.getType() instanceof LongFormType)
                    {
                        System.out.println(formProperty.getName() + "?(Must be a whole number)");
                        Long value = Long.valueOf(scanner.nextLine());
                        variables.put(formProperty.getId(), value);
                    } else if (DateFormType.class.isInstance(formProperty.getType()))
                    {
                        System.out.println(formProperty.getName() + "? (Must be a date m/d/yy)");
                        DateFormat dateFormat = new SimpleDateFormat("m/d/yy");
                        Date value = dateFormat.parse(scanner.nextLine());
                        variables.put(formProperty.getId(), value);
                    } else
                    {
                        System.out.println("<form type not supported>");
                    }
                }

                taskService.complete(task.getId(), variables);

                HistoricActivityInstance endActivity = null;

                List<HistoricActivityInstance> activityInstances = historyService.createHistoricActivityInstanceQuery()
                        .processInstanceId(processInstance.getId()).finished()
                        .orderByHistoricActivityInstanceEndTime().asc().list();

                for (HistoricActivityInstance activity : activityInstances)
                {
                    if (activity.getActivityType() == "startEvent")
                    {
                        System.out.println("Begin " + processDefinition.getName()
                                + " [" + processInstance.getProcessDefinitionKey()
                                + "]" + activity.getStartTime());
                    }

                    if (activity.getActivityType() == "endEvent")
                    {
                        endActivity = activity;
                    } else
                    {
                        System.out.println("-- " + activity.getActivityName() + " [" + activity.getActivityId() + "] " + activity.getDurationInMillis() + "ms");
                    }
                }

                if (endActivity != null)
                {
                    System.out.println("-- " + endActivity.getActivityName() + " [" + endActivity.getActivityId() + "]" + endActivity.getDurationInMillis() + "ms");

                    System.out.println("COMPLETE " + processDefinition.getName() + " [" + processInstance.getProcessDefinitionKey() + "]" + endActivity.getEndTime());
                }

                processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(processInstance.getId()).singleResult();
            }
        }


        scanner.close();
    }

}
