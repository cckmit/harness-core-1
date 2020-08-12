package io.harness.batch.processing.config;

import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.AwsECSClusterDataSyncTasklet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class AwsECSClusterDataSyncConfiguration {
  @Bean
  public Tasklet awsECSClusterDataSyncTasklet() {
    return new AwsECSClusterDataSyncTasklet();
  }

  @Bean
  @Autowired
  @Qualifier(value = "awsECSClusterDataSyncJob")
  public Job awsECSClusterDataSyncJob(JobBuilderFactory jobBuilderFactory, Step awsECSClusterDataSyncStep) {
    return jobBuilderFactory.get(BatchJobType.AWS_ECS_CLUSTER_DATA_SYNC.name())
        .incrementer(new RunIdIncrementer())
        .start(awsECSClusterDataSyncStep)
        .build();
  }

  @Bean
  public Step awsECSClusterDataSyncStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("awsECSClusterDataSyncStep").tasklet(awsECSClusterDataSyncTasklet()).build();
  }
}
